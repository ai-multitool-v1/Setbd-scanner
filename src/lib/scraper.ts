import * as cheerio from "cheerio";
import sharp from "sharp";
import { promises as fs } from "node:fs";
import path from "node:path";
import { db } from "@/lib/db";
import { jobDir } from "@/lib/storage";

/* ------------------------------------------------------------------ */
/* Limits                                                              */
/* ------------------------------------------------------------------ */

const MAX_HTML_BYTES = 10 * 1024 * 1024; // 10 MB
const MAX_ASSET_BYTES = 12 * 1024 * 1024; // 12 MB per asset
const MAX_TOTAL_BYTES = 180 * 1024 * 1024; // 180 MB per job
const MAX_FILES = 600;
const CONCURRENCY = 6;
const MAX_CSS_DEPTH = 3;
const FETCH_TIMEOUT_MS = 20_000;
const ASSET_TIMEOUT_MS = 15_000;
const COMPRESS_MIN_BYTES = 24 * 1024; // don't bother compressing tiny images
const MIN_IMAGE_DIM = 8; // skip tiny spacers/trackers
const MAX_IMAGE_DIM = 6000; // decompression-bomb guard

const UA =
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 SetBDScanner/1.0";

/* ------------------------------------------------------------------ */
/* Types                                                               */
/* ------------------------------------------------------------------ */

type AssetKind = "css" | "js" | "img" | "font" | "media" | "other";

interface AssetTask {
  abs: string; // absolute URL
  kind: AssetKind;
  depth: number; // CSS recursion depth
}

const FOLDER_BY_KIND: Record<AssetKind, string> = {
  css: "assets/css",
  js: "assets/js",
  img: "assets/img",
  font: "assets/fonts",
  media: "assets/media",
  other: "assets/misc",
};

const CSS_URL_RE = /url\(\s*(['"]?)([^'")]+)\1\s*\)/gi;
const CSS_IMPORT_RE = /@import\s+(?:url\(\s*['"]?|['"])([^'")]+)['"]?\s*\)?\s*[^;]*;/gi;

function kindFromExtension(urlPath: string, declared?: AssetKind): AssetKind {
  const ext = (urlPath.split(".").pop() ?? "").toLowerCase();
  if (["css"].includes(ext)) return "css";
  if (["js", "mjs", "cjs"].includes(ext)) return "js";
  if (["png", "jpg", "jpeg", "gif", "webp", "svg", "avif", "ico", "bmp"].includes(ext)) return "img";
  if (["woff", "woff2", "ttf", "otf", "eot"].includes(ext)) return "font";
  if (["mp4", "webm", "mp3", "wav", "ogg", "m4a", "mov"].includes(ext)) return "media";
  return declared ?? "other";
}

function skipUrl(raw: string): boolean {
  const t = raw.trim();
  if (!t) return true;
  if (
    t.startsWith("data:") ||
    t.startsWith("blob:") ||
    t.startsWith("javascript:") ||
    t.startsWith("mailto:") ||
    t.startsWith("tel:") ||
    t.startsWith("about:") ||
    t.startsWith("#")
  ) {
    return true;
  }
  return false;
}

function toAbsolute(raw: string, base: string): string | null {
  if (skipUrl(raw)) return null;
  try {
    const url = new URL(raw, base);
    if (url.protocol !== "http:" && url.protocol !== "https:") return null;
    url.hash = "";
    return url.href;
  } catch {
    return null;
  }
}

function sanitizeName(name: string): string {
  const cleaned = name.replace(/[^a-zA-Z0-9._-]/g, "-").replace(/-{2,}/g, "-");
  return cleaned.slice(-96); // keep tail (extension survives)
}

function hash8(input: string): string {
  let h = 5381;
  for (let i = 0; i < input.length; i++) {
    h = ((h << 5) + h + input.charCodeAt(i)) | 0;
  }
  return Math.abs(h).toString(36).slice(0, 8);
}

function srcsetUrls(srcset: string): string[] {
  return srcset
    .split(",")
    .map((part) => part.trim().split(/\s+/)[0])
    .filter(Boolean);
}

/* ------------------------------------------------------------------ */
/* Image compression (smaller size, same visual quality)               */
/* ------------------------------------------------------------------ */

async function compressImage(buf: Buffer, ext: string): Promise<Buffer | null> {
  if (buf.length < COMPRESS_MIN_BYTES) return null;
  if (ext === "svg" || ext === "gif" || ext === "ico") return null;
  try {
    const img = sharp(buf, { animated: false, limitInputPixels: 268402689 });
    const meta = await img.metadata();
    if (!meta.width || !meta.height) return null;
    if (meta.width < MIN_IMAGE_DIM || meta.height < MIN_IMAGE_DIM) return null; // trackers
    if (meta.width > MAX_IMAGE_DIM || meta.height > MAX_IMAGE_DIM) return null;

    let out: sharp.Sharp;
    switch (ext) {
      case "jpg":
      case "jpeg":
        out = img.jpeg({ quality: 78, mozjpeg: true, progressive: true });
        break;
      case "png": {
        out = meta.hasAlpha
          ? img.png({ compressionLevel: 9, effort: 7 })
          : img.png({ compressionLevel: 9, effort: 7, palette: true, quality: 85, dither: 1 });
        break;
      }
      case "webp":
        out = img.webp({ quality: 80 });
        break;
      case "avif":
        out = img.avif({ quality: 60 });
        break;
      default:
        return null;
    }
    const outBuf = await out.toBuffer();
    return outBuf.length < buf.length ? outBuf : null;
  } catch {
    return null;
  }
}

/* ------------------------------------------------------------------ */
/* Downloader                                                          */
/* ------------------------------------------------------------------ */

class Downloader {
  readonly dir: string;
  readonly saved = new Map<string, string>(); // absolute URL -> local relative path
  private readonly inflight = new Map<string, Promise<string | null>>();
  private readonly usedNames = new Set<string>();
  bytes = 0;
  files = 0;
  bytesSavedByCompression = 0;
  capReached = false;

  constructor(dir: string) {
    this.dir = dir;
  }

  has(abs: string): boolean {
    return this.saved.has(abs);
  }

  localPath(abs: string): string | null {
    return this.saved.get(abs) ?? null;
  }

  /** Enqueue (dedup + inflight merge) and await the local path of an asset. */
  ensure(task: AssetTask): Promise<string | null> {
    const existing = this.saved.get(task.abs);
    if (existing) return Promise.resolve(existing);
    const running = this.inflight.get(task.abs);
    if (running) return running;
    const p = this.download(task).finally(() => this.inflight.delete(task.abs));
    this.inflight.set(task.abs, p);
    return p;
  }

  private async download(task: AssetTask): Promise<string | null> {
    if (this.capReached) return null;
    if (this.files >= MAX_FILES || this.bytes >= MAX_TOTAL_BYTES) {
      this.capReached = true;
      return null;
    }

    let res: Response;
    try {
      res = await fetch(task.abs, {
        redirect: "follow",
        signal: AbortSignal.timeout(ASSET_TIMEOUT_MS),
        headers: {
          "User-Agent": UA,
          Accept: "*/*",
          Referer: task.abs,
        },
      });
    } catch {
      return null; // asset unavailable — keep remote URL in the page
    }
    if (!res.ok) return null;

    const declaredLen = Number(res.headers.get("content-length") ?? "0");
    if (declaredLen > MAX_ASSET_BYTES) return null;

    let buf: Buffer;
    try {
      const ab = await res.arrayBuffer();
      buf = Buffer.from(ab);
    } catch {
      return null;
    }
    if (buf.length === 0 || buf.length > MAX_ASSET_BYTES) return null;
    if (this.bytes + buf.length > MAX_TOTAL_BYTES) {
      this.capReached = true;
      return null;
    }

    /* ----- decide local name & folder ----- */
    const urlObj = new URL(task.abs);
    const rawName = path.posix.basename(decodeURIComponent(urlObj.pathname)) || "file";
    let ext = (rawName.includes(".") ? rawName.split(".").pop() : "")?.toLowerCase() ?? "";
    const kind = kindFromExtension(urlObj.pathname, task.kind);
    if (!ext || ext.length > 8) {
      const mime = (res.headers.get("content-type") ?? "").split(";")[0].trim();
      ext =
        MIME_EXT[mime] ??
        (kind === "css" ? "css" : kind === "js" ? "js" : kind === "other" ? "bin" : "dat");
    }

    const stem = sanitizeName(
      rawName.includes(".") ? rawName.slice(0, rawName.lastIndexOf(".")) : rawName || "file"
    ).replace(/^[-.]+|[-.]+$/g, "");
    const stemSafe = stem && stem.length > 0 ? stem : "file";

    const folder = FOLDER_BY_KIND[kind];
    let fileName = `${stemSafe}.${ext}`;
    let relPath = `${folder}/${fileName}`;
    if (this.usedNames.has(relPath)) {
      fileName = `${stemSafe}-${hash8(task.abs)}.${ext}`;
      relPath = `${folder}/${fileName}`;
      while (this.usedNames.has(relPath)) {
        fileName = `${stemSafe}-${hash8(task.abs + Math.random())}.${ext}`;
        relPath = `${folder}/${fileName}`;
      }
    }
    this.usedNames.add(relPath);

    const absPath = path.join(this.dir, relPath);
    await fs.mkdir(path.dirname(absPath), { recursive: true });

    let finalBuf = buf;

    /* ----- CSS: parse sub-assets + rewrite urls ----- */
    if (kind === "css") {
      let cssText = buf.toString("utf8");
      const cssDir = `assets/css`;

      const subTasks: AssetTask[] = [];

      const queueSub = (raw: string) => {
        const abs = toAbsolute(raw, task.abs);
        if (!abs || this.saved.has(abs)) return;
        let subKind: AssetKind = "img";
        try {
          subKind = kindFromExtension(new URL(abs).pathname, "img");
        } catch {
          /* keep img */
        }
        subTasks.push({ abs, kind: subKind, depth: task.depth + 1 });
      };

      cssText.replace(CSS_URL_RE, (_m, _q, raw: string) => {
        if (!raw.startsWith("data:")) queueSub(raw);
        return _m;
      });
      cssText.replace(CSS_IMPORT_RE, (_m, raw: string) => {
        if (!raw.startsWith("data:")) queueSub(raw);
        return _m;
      });

      // download sub-assets first (they may recurse for nested CSS)
      if (task.depth < MAX_CSS_DEPTH) {
        await Promise.all(subTasks.map((t) => this.ensure(t)));
      }

      // rewrite CSS to relative paths (from assets/css/)
      cssText = cssText
        .replace(CSS_URL_RE, (match, quote, raw: string) => {
          if (raw.startsWith("data:")) return match;
          const abs = toAbsolute(raw, task.abs);
          const local = abs ? this.saved.get(abs) : undefined;
          if (!local) return match;
          const rel = path.posix.relative(cssDir, local);
          return `url(${quote}${rel}${quote})`;
        })
        .replace(CSS_IMPORT_RE, (match, raw: string) => {
          if (raw.startsWith("data:")) return match;
          const abs = toAbsolute(raw, task.abs);
          const local = abs ? this.saved.get(abs) : undefined;
          if (!local) return match;
          const rel = path.posix.relative(cssDir, local);
          return match.replace(raw, rel);
        });

      finalBuf = Buffer.from(cssText, "utf8");
    } else if (kind === "img") {
      const compressed = await compressImage(buf, ext);
      if (compressed) {
        this.bytesSavedByCompression += buf.length - compressed.length;
        finalBuf = compressed;
      }
    }

    await fs.writeFile(absPath, finalBuf);
    this.saved.set(task.abs, relPath);
    this.files += 1;
    this.bytes += finalBuf.length;
    return relPath;
  }
}

const MIME_EXT: Record<string, string> = {
  "text/css": "css",
  "text/javascript": "js",
  "application/javascript": "js",
  "application/json": "json",
  "image/png": "png",
  "image/jpeg": "jpg",
  "image/gif": "gif",
  "image/webp": "webp",
  "image/avif": "avif",
  "image/svg+xml": "svg",
  "image/x-icon": "ico",
  "image/vnd.microsoft.icon": "ico",
  "font/woff": "woff",
  "font/woff2": "woff2",
  "font/ttf": "ttf",
  "font/otf": "otf",
  "video/mp4": "mp4",
  "video/webm": "webm",
  "audio/mpeg": "mp3",
};

/* ------------------------------------------------------------------ */
/* DB progress helpers                                                 */
/* ------------------------------------------------------------------ */

async function updateJob(jobId: string, data: Record<string, unknown>): Promise<void> {
  try {
    await db.scrapeJob.update({ where: { id: jobId }, data });
  } catch {
    /* job row may be deleted mid-run — ignore */
  }
}

/* ------------------------------------------------------------------ */
/* Main scrape routine                                                 */
/* ------------------------------------------------------------------ */

export async function runScrape(jobId: string, startUrl: string): Promise<void> {
  const dir = jobDir(jobId);
  const dl = new Downloader(dir);
  let lastTick = 0;

  const tick = (force = false) => {
    const now = Date.now();
    if (force || now - lastTick > 450) {
      lastTick = now;
      const progress = 25 + Math.round(45 * Math.min(1, dl.files / Math.max(1, expectedTotal)));
      void updateJob(jobId, {
        stage: "downloading",
        progress: Math.min(progress, 72),
        fileCount: dl.files,
        totalSize: dl.bytes,
      });
    }
  };

  let expectedTotal = 1;

  try {
    await fs.mkdir(dir, { recursive: true });
    await updateJob(jobId, { stage: "fetching", progress: 5, status: "running" });

    /* ---------------- 1. Fetch the main HTML ---------------- */
    let res: Response;
    try {
      res = await fetch(startUrl, {
        redirect: "follow",
        signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
        headers: {
          "User-Agent": UA,
          Accept: "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
          "Accept-Language": "en-US,en;q=0.9",
        },
      });
    } catch (err) {
      throw new Error(
        `Could not reach ${startUrl} — ${err instanceof Error ? err.message : "network error"}`
      );
    }
    if (!res.ok) {
      throw new Error(`Target responded with HTTP ${res.status} ${res.statusText}`);
    }
    const declaredLen = Number(res.headers.get("content-length") ?? "0");
    if (declaredLen > MAX_HTML_BYTES) {
      throw new Error(`Page is too large (${Math.round(declaredLen / 1048576)} MB)`);
    }
    let html = await res.text();
    if (html.length > MAX_HTML_BYTES) html = html.slice(0, MAX_HTML_BYTES);
    const finalUrl = res.url || startUrl;

    /* ---------------- 2. Parse & map assets ---------------- */
    await updateJob(jobId, { stage: "mapping", progress: 16 });
    const $ = cheerio.load(html);
    const title = $("title").first().text().trim() || null;

    const tasks: AssetTask[] = [];
    const seen = new Set<string>();
    const pushTask = (raw: string, kind: AssetKind, depth = 0) => {
      const abs = toAbsolute(raw, finalUrl);
      if (!abs || seen.has(abs)) return;
      seen.add(abs);
      tasks.push({ abs, kind, depth });
    };

    // Stylesheets
    $("link[href]").each((_i, el) => {
      const $el = $(el);
      const rel = ($el.attr("rel") ?? "").toLowerCase();
      const href = $el.attr("href") ?? "";
      if (rel.includes("stylesheet")) pushTask(href, "css");
      else if (rel.includes("icon") || rel.includes("apple-touch-icon")) pushTask(href, "img");
      else if (rel.includes("manifest")) pushTask(href, "other");
    });

    // Scripts
    $("script[src]").each((_i, el) => pushTask($(el).attr("src") ?? "", "js"));

    // Images
    $("img").each((_i, el) => {
      const $el = $(el);
      pushTask($el.attr("src") ?? "", "img");
      const srcset = $el.attr("srcset") ?? "";
      for (const u of srcsetUrls(srcset)) pushTask(u, "img");
    });

    // <source>, <video>, <audio>, <track>
    $("source").each((_i, el) => {
      const $el = $(el);
      const parentTag = ($el.parent().attr()?.tagName ?? "").toString().toLowerCase();
      const kind: AssetKind = parentTag === "picture" ? "img" : "media";
      pushTask($el.attr("src") ?? "", kind);
      const srcset = $el.attr("srcset") ?? "";
      for (const u of srcsetUrls(srcset)) pushTask(u, kind);
    });
    $("video[src]").each((_i, el) => pushTask($(el).attr("src") ?? "", "media"));
    $("video[poster]").each((_i, el) => pushTask($(el).attr("poster") ?? "", "img"));
    $("audio[src]").each((_i, el) => pushTask($(el).attr("src") ?? "", "media"));

    // Inline <style> blocks + inline style="" attributes → background images/fonts
    const scanInlineCss = (chunk: string) => {
      const re = new RegExp(CSS_URL_RE.source, "gi");
      let m: RegExpExecArray | null;
      while ((m = re.exec(chunk)) !== null) {
        if (!m[2].startsWith("data:")) {
          try {
            const abs = toAbsolute(m[2], finalUrl);
            if (abs) pushTask(m[2], kindFromExtension(new URL(abs).pathname, "img"));
          } catch {
            /* ignore */
          }
        }
      }
    };
    $("style").each((_i, el) => scanInlineCss($(el).text()));
    $("[style]").each((_i, el) => scanInlineCss($(el).attr("style") ?? ""));

    expectedTotal = Math.max(tasks.length, 1);
    await updateJob(jobId, {
      stage: "downloading",
      progress: 24,
      message: `${tasks.length} assets found`,
    });

    /* ---------------- 3. Download assets (concurrent, dynamic queue) ---------------- */
    let cursor = 0;
    const worker = async () => {
      while (cursor < tasks.length) {
        const task = tasks[cursor++];
        if (!task) break;
        if (dl.has(task.abs)) continue;
        await dl.ensure(task);
        tick();
      }
    };
    await Promise.all(Array.from({ length: CONCURRENCY }, worker));

    await updateJob(jobId, {
      stage: "optimizing",
      progress: 76,
      fileCount: dl.files,
      totalSize: dl.bytes,
      message:
        dl.bytesSavedByCompression > 0
          ? `Images optimized — ${Math.round(dl.bytesSavedByCompression / 1024)} KB saved`
          : null,
    });

    /* ---------------- 4. Rewrite HTML to local paths ---------------- */
    const rewrite = (raw: string): string => {
      const abs = toAbsolute(raw, finalUrl);
      return (abs && dl.localPath(abs)) || raw;
    };

    $("link[href]").each((_i, el) => {
      const $el = $(el);
      const href = $el.attr("href") ?? "";
      const rel = ($el.attr("rel") ?? "").toLowerCase();
      if (rel.includes("stylesheet") || rel.includes("icon") || rel.includes("manifest")) {
        $el.attr("href", rewrite(href));
      }
      $el.removeAttr("integrity");
    });
    $("script[src]").each((_i, el) => {
      const $el = $(el);
      $el.attr("src", rewrite($el.attr("src") ?? ""));
      $el.removeAttr("integrity");
    });
    $("img").each((_i, el) => {
      const $el = $(el);
      if ($el.attr("src")) $el.attr("src", rewrite($el.attr("src") ?? ""));
      const srcset = $el.attr("srcset");
      if (srcset) {
        $el.attr(
          "srcset",
          srcset
            .split(",")
            .map((part) => {
              const bits = part.trim().split(/\s+/);
              if (!bits[0]) return part;
              bits[0] = rewrite(bits[0]);
              return bits.join(" ");
            })
            .join(", ")
        );
      }
    });
    $("source").each((_i, el) => {
      const $el = $(el);
      if ($el.attr("src")) $el.attr("src", rewrite($el.attr("src") ?? ""));
      const srcset = $el.attr("srcset");
      if (srcset) {
        $el.attr(
          "srcset",
          srcset
            .split(",")
            .map((part) => {
              const bits = part.trim().split(/\s+/);
              if (!bits[0]) return part;
              bits[0] = rewrite(bits[0]);
              return bits.join(" ");
            })
            .join(", ")
        );
      }
    });
    $("video[src]").each((_i, el) => {
      $(el).attr("src", rewrite($(el).attr("src") ?? ""));
    });
    $("video[poster]").each((_i, el) => {
      $(el).attr("poster", rewrite($(el).attr("poster") ?? ""));
    });
    $("audio[src]").each((_i, el) => {
      $(el).attr("src", rewrite($(el).attr("src") ?? ""));
    });

    // Inline <style> + style="" url() references
    const rewriteCssChunk = (chunk: string): string =>
      chunk.replace(CSS_URL_RE, (match, quote, raw: string) => {
        if (raw.startsWith("data:")) return match;
        const abs = toAbsolute(raw, finalUrl);
        const local = abs ? dl.localPath(abs) : undefined;
        return local ? `url(${quote}${local}${quote})` : match;
      });
    $("style").each((_i, el) => {
      const $el = $(el);
      $el.text(rewriteCssChunk($el.text()));
    });
    $("[style]").each((_i, el) => {
      const $el = $(el);
      $el.attr("style", rewriteCssChunk($el.attr("style") ?? ""));
    });

    // <base> would break relative asset resolution — drop it
    $("base").remove();

    const outHtml = $.html();
    await fs.writeFile(path.join(dir, "index.html"), outHtml, "utf8");

    /* ---------------- 5. Done ---------------- */
    const totalFiles = dl.files + 1; // + index.html
    const totalSize = dl.bytes + Buffer.byteLength(outHtml, "utf8");
    await updateJob(jobId, {
      status: "completed",
      stage: "done",
      progress: 100,
      fileCount: totalFiles,
      totalSize,
      title,
      completedAt: new Date(),
      message:
        dl.capReached && dl.bytesSavedByCompression > 0
          ? `Size limits reached — images optimized, saved ${Math.round(
              dl.bytesSavedByCompression / 1024
            )} KB`
          : dl.capReached
            ? "Some assets were skipped (size limits)"
            : dl.bytesSavedByCompression > 0
              ? `Images optimized — saved ${Math.round(dl.bytesSavedByCompression / 1024)} KB`
              : null,
    });
  } catch (err) {
    const message = err instanceof Error ? err.message : "Unknown scraping error";
    await updateJob(jobId, {
      status: "failed",
      stage: "error",
      message,
    });
  }
}

/** Basic SSRF guard: refuse localhost / private ranges. */
export function isBlockedHost(hostname: string): boolean {
  const h = hostname.toLowerCase();
  if (h === "localhost" || h.endsWith(".local") || h.endsWith(".internal")) return true;
  if (h === "::1" || h === "[::1]" || h === "0.0.0.0") return true;
  const ipv4 = h.match(/^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/);
  if (ipv4) {
    const [a, b] = [Number(ipv4[1]), Number(ipv4[2])];
    if (a === 127 || a === 10 || a === 0) return true;
    if (a === 192 && b === 168) return true;
    if (a === 172 && b >= 16 && b <= 31) return true;
    if (a === 169 && b === 254) return true;
  }
  return false;
}
