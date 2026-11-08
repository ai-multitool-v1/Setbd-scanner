import { promises as fs } from "node:fs";
import path from "node:path";
import type { FileNode } from "./types";

/** All scraped sites live under <project>/storage/<jobId>/ */
export const STORAGE_ROOT = path.join(process.cwd(), "storage");

export function jobDir(jobId: string): string {
  return path.join(STORAGE_ROOT, jobId);
}

/**
 * Resolve a job-relative path, refusing path traversal.
 * Returns the absolute file path, or null when unsafe/invalid.
 */
export function safeResolve(jobId: string, relPath: string): string | null {
  if (typeof relPath !== "string" || relPath.length === 0 || relPath.length > 512) return null;
  if (relPath.includes("\0")) return null;
  const normalized = path.posix.normalize(relPath);
  if (normalized.startsWith("..") || path.posix.isAbsolute(normalized)) return null;
  const abs = path.resolve(jobDir(jobId), normalized);
  const root = path.resolve(jobDir(jobId));
  if (abs !== root && !abs.startsWith(root + path.sep)) return null;
  return abs;
}

const MIME_MAP: Record<string, string> = {
  html: "text/html; charset=utf-8",
  htm: "text/html; charset=utf-8",
  css: "text/css; charset=utf-8",
  js: "text/javascript; charset=utf-8",
  mjs: "text/javascript; charset=utf-8",
  cjs: "text/javascript; charset=utf-8",
  json: "application/json; charset=utf-8",
  webmanifest: "application/manifest+json; charset=utf-8",
  txt: "text/plain; charset=utf-8",
  md: "text/markdown; charset=utf-8",
  xml: "application/xml; charset=utf-8",
  csv: "text/csv; charset=utf-8",
  map: "application/json; charset=utf-8",
  png: "image/png",
  jpg: "image/jpeg",
  jpeg: "image/jpeg",
  gif: "image/gif",
  svg: "image/svg+xml",
  webp: "image/webp",
  avif: "image/avif",
  ico: "image/x-icon",
  bmp: "image/bmp",
  woff: "font/woff",
  woff2: "font/woff2",
  ttf: "font/ttf",
  otf: "font/otf",
  eot: "application/vnd.ms-fontobject",
  mp4: "video/mp4",
  webm: "video/webm",
  mp3: "audio/mpeg",
  wav: "audio/wav",
  ogg: "audio/ogg",
  pdf: "application/pdf",
  zip: "application/zip",
};

export function mimeFor(fileName: string): string {
  const ext = fileName.split(".").pop()?.toLowerCase() ?? "";
  return MIME_MAP[ext] ?? "application/octet-stream";
}

const TEXT_EXTS = new Set([
  "html", "htm", "css", "js", "mjs", "cjs", "json", "webmanifest", "txt",
  "md", "xml", "svg", "csv", "map", "ts", "jsx", "tsx", "yml", "yaml",
]);

export function isTextFile(fileName: string): boolean {
  const ext = fileName.split(".").pop()?.toLowerCase() ?? "";
  return TEXT_EXTS.has(ext);
}

/** Build a FileNode tree from the job folder on disk. */
export async function buildTree(jobId: string): Promise<FileNode | null> {
  const root = jobDir(jobId);
  try {
    await fs.access(root);
  } catch {
    return null;
  }

  async function walk(dir: string, rel: string): Promise<FileNode[]> {
    let entries: import("node:fs").Dirent[];
    try {
      entries = await fs.readdir(dir, { withFileTypes: true });
    } catch {
      return [];
    }
    const nodes: FileNode[] = [];
    for (const entry of entries) {
      const childRel = rel ? `${rel}/${entry.name}` : entry.name;
      if (entry.isDirectory()) {
        const children = await walk(path.join(dir, entry.name), childRel);
        nodes.push({ name: entry.name, path: childRel, type: "dir", size: 0, children });
      } else if (entry.isFile()) {
        let size = 0;
        try {
          size = (await fs.stat(path.join(dir, entry.name))).size;
        } catch {
          /* ignore */
        }
        nodes.push({ name: entry.name, path: childRel, type: "file", size });
      }
    }
    return nodes;
  }

  const children = await walk(root, "");
  return { name: "site", path: "", type: "dir", size: 0, children };
}

/** Recursively sum file sizes + count under a job dir. */
export async function measureJobDir(
  jobId: string
): Promise<{ files: number; totalSize: number }> {
  const root = jobDir(jobId);
  let files = 0;
  let totalSize = 0;

  async function walk(dir: string): Promise<void> {
    let entries: import("node:fs").Dirent[];
    try {
      entries = await fs.readdir(dir, { withFileTypes: true });
    } catch {
      return;
    }
    for (const entry of entries) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) await walk(full);
      else if (entry.isFile()) {
        try {
          totalSize += (await fs.stat(full)).size;
          files += 1;
        } catch {
          /* ignore */
        }
      }
    }
  }

  await walk(root);
  return { files, totalSize };
}
