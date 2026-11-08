#!/usr/bin/env bun
/**
 * build-assets.mjs — SetBD Scanner brand asset pipeline.
 *
 * Consumes the 5 raw uploads in ./upload and produces:
 *   - public/images/  : optimized webp backgrounds + logo, raw scanner.gif copy
 *   - public/icons/   : apple-touch icons, PWA manifest icons (plain + maskable), favicons
 *   - public/splash/  : iOS apple-touch-startup-image PNGs (14 device sizes)
 *   - public/manifest.json
 *
 * Idempotent: safe to re-run, every output is overwritten deterministically.
 * Run: bun scripts/build-assets.mjs
 */
import sharp from "sharp";
import { copyFile, mkdir, writeFile, stat } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = fileURLToPath(new URL("..", import.meta.url));
const UPLOAD = path.join(ROOT, "upload");
const IMG_DIR = path.join(ROOT, "public", "images");
const ICO_DIR = path.join(ROOT, "public", "icons");
const SPL_DIR = path.join(ROOT, "public", "splash");
const PUB_DIR = path.join(ROOT, "public");

/* ---------------------------------------------------------------- inputs */
const IN = {
  bgDark: "file_000000008c1082109466654d279fca7c.png", // dark blue abstract waves
  bgHeroDark: "file_00000000ab6c82109689f65b129835ee.png", // cyber shield/lock + world map
  bgHeroLight: "file_00000000413c821092c29f6e1fe6cf54.png", // light blue technology
  logo: "file_00000000d578821086e788803e6088e7.png", // PENTEST SUITE logo (has alpha)
  gif: "file_0000000078348210a4e94f4af0953e2b.gif", // scanner animation
};
const src = (f) => path.join(UPLOAD, f);

const BRAND_BG = "#050810"; // solid background for icon flattening
const SPLASH_MAX_BYTES = 1.5 * 1024 * 1024; // 1.5 MB cap per splash screen

/* Apple touch icon sizes. 57..180 are the canonical iOS set (+180 canonical
 * apple-touch-icon.png); 1024/512/256/128 are supplementary sizes so the
 * pack totals 15 apple-touch files as per the build spec. */
const APPLE_SIZES = [57, 60, 72, 76, 114, 120, 144, 152, 167, 180, 1024, 512, 256, 128];
const MANIFEST_SIZES = [192, 512];
const FAVICON_SIZES = [16, 32, 48];
const MASKABLE_RATIO = 0.78; // Android maskable safe-zone: logo at 78% of canvas

/* iOS apple-touch-startup-image sizes (portrait). */
const SPLASH_SIZES = [
  [1290, 2796], //  iPhone 12/13/14 Pro Max
  [1284, 2778], //  iPhone 12/13/14 Plus, 14 Pro
  [1170, 2532], //  iPhone 12/13/14, 13 Pro
  [1125, 2436], //  iPhone X/XS/11 Pro
  [1242, 2688], //  iPhone XS Max/11 Pro Max
  [1242, 2208], //  iPhone 6/7/8 Plus
  [828, 1792], //   iPhone XR/11
  [750, 1334], //   iPhone 6/7/8
  [1488, 2266], //  iPad Pro 11" (M4)
  [1640, 2360], //  iPad Pro 10.5"
  [1668, 2388], //  iPad Pro 11"
  [1668, 2224], //  iPad Air 10.5" / Pro 10.5"
  [1536, 2048], //  iPad Mini/Air non-retina-ish
  [2048, 2732], //  iPad Pro 12.9"
];

/* ---------------------------------------------------------------- helpers */
const results = [];
const errors = [];

function fmt(n) {
  return n.toLocaleString("en-US");
}
function kb(n) {
  return (n / 1024).toFixed(1) + " KB";
}

/** Log one produced file (relpath from project root) with its final size. */
async function record(absPath, group) {
  const { size } = await stat(absPath);
  const rel = path.relative(ROOT, absPath);
  results.push({ file: rel, size, group });
  console.log(`  ✔ ${rel}  —  ${fmt(size)} bytes (${kb(size)})`);
  return size;
}

async function fail(scope, err) {
  errors.push(`${scope}: ${err.message}`);
  console.error(`  ✖ ${scope}: ${err.message}`);
}

/* ------------------------------------------------------------------ steps */

async function stepImages(meta) {
  console.log("\n[1/5] public/images — optimized webp + gif copy");
  await mkdir(IMG_DIR, { recursive: true });

  // Backgrounds: keep original dimensions (read live from metadata), q82.
  const bgs = [
    [IN.bgDark, "bg-dark.webp"],
    [IN.bgHeroDark, "bg-hero-dark.webp"],
    [IN.bgHeroLight, "bg-hero-light.webp"],
  ];
  for (const [inFile, outFile] of bgs) {
    try {
      await sharp(src(inFile))
        .webp({ quality: 82 })
        .toFile(path.join(IMG_DIR, outFile));
      await record(path.join(IMG_DIR, outFile), "images");
    } catch (err) {
      await fail(outFile, err);
    }
  }

  // Logo: quality 90, original dimensions, alpha preserved.
  try {
    await sharp(src(IN.logo)).webp({ quality: 90 }).toFile(path.join(IMG_DIR, "logo.webp"));
    await record(path.join(IMG_DIR, "logo.webp"), "images");
  } catch (err) {
    await fail("logo.webp", err);
  }

  // Scanner GIF: byte-for-byte copy.
  try {
    const dest = path.join(IMG_DIR, "scanner.gif");
    await copyFile(src(IN.gif), dest);
    await record(dest, "images");
  } catch (err) {
    await fail("scanner.gif", err);
  }
}

/** Square icon: logo cover-resized to SxS, flattened on solid brand bg. */
function logoIconPipeline(size) {
  return sharp(src(IN.logo))
    .resize(size, size, { fit: "cover" })
    .flatten({ background: BRAND_BG })
    .png({ compressionLevel: 9 });
}

/** Maskable icon: logo at MASKABLE_RATIO of canvas, centered on solid bg. */
function maskablePipeline(size) {
  const inner = Math.round(size * MASKABLE_RATIO);
  const padTotal = size - inner;
  const top = Math.floor(padTotal / 2);
  const left = Math.floor(padTotal / 2);
  return sharp(src(IN.logo))
    .resize(inner, inner, { fit: "cover" })
    .flatten({ background: BRAND_BG })
    .extend({
      top,
      bottom: padTotal - top,
      left,
      right: padTotal - left,
      background: BRAND_BG,
    })
    .png({ compressionLevel: 9 });
}

async function stepIcons() {
  console.log("\n[2/5] public/icons — apple-touch + manifest + favicons");
  await mkdir(ICO_DIR, { recursive: true });

  // Apple touch icons (full-bleed, flattened).
  for (const s of APPLE_SIZES) {
    const name = `apple-touch-icon-${s}.png`;
    try {
      await logoIconPipeline(s).toFile(path.join(ICO_DIR, name));
      await record(path.join(ICO_DIR, name), "apple-touch");
    } catch (err) {
      await fail(name, err);
    }
  }
  // Canonical apple-touch-icon.png (180x180).
  try {
    await logoIconPipeline(180).toFile(path.join(ICO_DIR, "apple-touch-icon.png"));
    await record(path.join(ICO_DIR, "apple-touch-icon.png"), "apple-touch");
  } catch (err) {
    await fail("apple-touch-icon.png", err);
  }

  // PWA manifest icons (plain).
  for (const s of MANIFEST_SIZES) {
    const name = `icon-${s}.png`;
    try {
      await logoIconPipeline(s).toFile(path.join(ICO_DIR, name));
      await record(path.join(ICO_DIR, name), "manifest-icon");
    } catch (err) {
      await fail(name, err);
    }
  }

  // PWA manifest icons (maskable, 78% safe-zone).
  for (const s of MANIFEST_SIZES) {
    const name = `icon-maskable-${s}.png`;
    try {
      await maskablePipeline(s).toFile(path.join(ICO_DIR, name));
      await record(path.join(ICO_DIR, name), "manifest-icon");
    } catch (err) {
      await fail(name, err);
    }
  }

  // Favicons.
  for (const s of FAVICON_SIZES) {
    const name = `favicon-${s}.png`;
    try {
      await logoIconPipeline(s).toFile(path.join(ICO_DIR, name));
      await record(path.join(ICO_DIR, name), "favicon");
    } catch (err) {
      await fail(name, err);
    }
  }
}

/** Render one iOS splash to a PNG buffer at a given palette quality. */
async function renderSplashBuffer(w, h, quality) {
  // 1. Background: original dark-waves PNG cover-fit to WxH (position centre).
  // 2. Darken overlay: uniform black at 0.35 alpha, full canvas.
  const overlay = await sharp({
    create: { width: w, height: h, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0.35 } },
  })
    .png()
    .toBuffer();
  // 4. Logo: width = 32% of min(W,H), aspect preserved, centered.
  const logoW = Math.round(0.32 * Math.min(w, h));
  const logoBuf = await sharp(src(IN.logo)).resize({ width: logoW }).png().toBuffer();

  return sharp(src(IN.bgDark))
    .resize(w, h, { fit: "cover", position: "centre" })
    .composite([
      { input: overlay, gravity: "centre" },
      { input: logoBuf, gravity: "centre" },
    ])
    .png({ palette: true, quality, effort: 7, compressionLevel: 9 })
    .toBuffer();
}

async function stepSplash(meta) {
  console.log("\n[3/5] public/splash — iOS startup screens");
  await mkdir(SPL_DIR, { recursive: true });

  for (const [w, h] of SPLASH_SIZES) {
    const name = `ios-${w}x${h}.png`;
    try {
      let quality = 90;
      let buf = await renderSplashBuffer(w, h, quality);
      if (buf.length > SPLASH_MAX_BYTES) {
        quality = 80; // retry smaller per spec
        buf = await renderSplashBuffer(w, h, quality);
      }
      await writeFile(path.join(SPL_DIR, name), buf);
      const size = await record(path.join(SPL_DIR, name), "splash");
      if (size > SPLASH_MAX_BYTES) {
        console.warn(`  ⚠ ${name} is ${kb(size)} — above the 1.5 MB target even at q80`);
      }
    } catch (err) {
      await fail(name, err);
    }
  }
}

const MANIFEST = {
  name: "SetBD Scanner",
  short_name: "SetBD",
  description:
    "Scrape any public website — download HTML, CSS, JS & assets, edit source, export ZIP.",
  id: "/",
  start_url: "/",
  scope: "/",
  display: "standalone",
  orientation: "portrait",
  background_color: "#050810",
  theme_color: "#050810",
  categories: ["utilities", "productivity"],
  icons: [
    { src: "/icons/icon-192.png", sizes: "192x192", type: "image/png" },
    { src: "/icons/icon-512.png", sizes: "512x512", type: "image/png" },
    { src: "/icons/icon-maskable-192.png", sizes: "192x192", type: "image/png", purpose: "maskable" },
    { src: "/icons/icon-maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
  ],
};

async function stepManifest() {
  console.log("\n[4/5] public/manifest.json");
  try {
    await writeFile(path.join(PUB_DIR, "manifest.json"), JSON.stringify(MANIFEST, null, 2) + "\n");
    await record(path.join(PUB_DIR, "manifest.json"), "manifest");
  } catch (err) {
    await fail("manifest.json", err);
  }
}

function stepSummary() {
  console.log("\n[5/5] summary");
  const groups = ["images", "apple-touch", "manifest-icon", "favicon", "splash", "manifest"];
  const label = {
    images: "public/images",
    "apple-touch": "public/icons (apple-touch)",
    "manifest-icon": "public/icons (manifest)",
    favicon: "public/icons (favicon)",
    splash: "public/splash",
    manifest: "public/manifest.json",
  };
  let total = 0;
  const rows = [];
  for (const g of groups) {
    const rs = results.filter((r) => r.group === g);
    const bytes = rs.reduce((a, r) => a + r.size, 0);
    total += bytes;
    rows.push({ group: g, count: rs.length, bytes });
  }
  const w1 = Math.max(...rows.map((r) => label[r.group].length)) + 2;
  const w2 = 8;
  const w3 = 14;
  console.log("");
  console.log("  " + "OUTPUT".padEnd(w1) + "COUNT".padStart(w2) + "SIZE".padStart(w3));
  console.log("  " + "-".repeat(w1 + w2 + w3));
  for (const r of rows) {
    console.log(
      "  " + label[r.group].padEnd(w1) + String(r.count).padStart(w2) + kb(r.bytes).padStart(w3)
    );
  }
  console.log("  " + "-".repeat(w1 + w2 + w3));
  console.log(
    "  " + "TOTAL".padEnd(w1) + String(results.length).padStart(w2) + kb(total).padStart(w3)
  );
  console.log("");
  if (errors.length) {
    console.error(`  ${errors.length} error(s):`);
    for (const e of errors) console.error("   - " + e);
    process.exitCode = 1;
  } else {
    console.log(`  All ${results.length} outputs generated successfully.`);
  }
}

/* ------------------------------------------------------------------- main */
async function main() {
  const t0 = Date.now();
  console.log("SetBD Scanner — brand asset build");
  console.log("=================================");

  // Read real input metadata (never hardcode assumed dimensions).
  const meta = {};
  for (const [key, file] of Object.entries(IN)) {
    const m = await sharp(src(file), { animated: false }).metadata();
    meta[file] = m;
    console.log(
      `  input ${key.padEnd(12)} ${file}\n    format=${m.format} ${m.width}x${m.height}` +
        ` alpha=${m.hasAlpha}${m.pages ? ` pages=${m.pages}` : ""}`
    );
  }

  await mkdir(IMG_DIR, { recursive: true });
  await mkdir(ICO_DIR, { recursive: true });
  await mkdir(SPL_DIR, { recursive: true });

  await stepImages(meta);
  await stepIcons();
  await stepSplash(meta);
  await stepManifest();
  stepSummary();

  console.log(`\nDone in ${((Date.now() - t0) / 1000).toFixed(1)}s`);
}

main().catch((err) => {
  console.error("FATAL:", err);
  process.exit(1);
});
