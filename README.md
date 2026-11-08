# SetBD Scanner 🔎

> **Scrape any website. Own the source.**

SetBD Scanner is an installable PWA (Next.js 16) that downloads the **publicly accessible** parts of any webpage — HTML, CSS, JS, images, fonts & media — into a clean, ready-to-run folder structure. Browse and **edit the source live** in the app, preview it, then export everything as a **ZIP**.

![Build](https://github.com/ai-multitool-v1/Setbd-scanner/actions/workflows/build.yml/badge.svg)

## ✨ Features

| | |
|---|---|
| 🌐 **Smart scraping** | Parses the page with Cheerio and maps every public stylesheet, script, image, font, video & icon — including `url()` / `@import` references inside CSS (nested up to 3 levels). |
| 🗂 **Clean structure** | Everything lands in `assets/css`, `assets/js`, `assets/img`, `assets/fonts`, `assets/media` + a rewritten `index.html` with relative paths. |
| ✍️ **Live source editor** | Built-in file tree + code editor (line numbers, ⌘/Ctrl+S, dirty tracking) to view & edit any scraped file. |
| 👁 **Live preview** | Sandbox iframe that serves the scraped site with correct MIME types, so your edits show instantly. |
| 🖼 **Image compression** | Downloaded images are re-encoded with sharp (mozjpeg / palette PNG / WebP) — smaller size, same visual quality. |
| 📦 **One-click ZIP** | Export the entire scrape as a ZIP anytime. |
| 📱 **Installable PWA** | Full manifest + iOS support: apple-touch-icons in every size and generated splash screens for 14 device classes. |
| 🌗 **Dark / Light theme** | Cyber-scanner aesthetic with smooth Framer Motion animations everywhere. |

## 🚀 Getting started

```bash
bun install
cp .env.example .env            # or set DATABASE_URL yourself
bun run db:push                 # create the SQLite database
bun run dev                     # http://localhost:3000
```

`.env` needs a single line:

```
DATABASE_URL=file:<abs path to project>/db/custom.db
```

## 🧱 Stack

- **Next.js 16** (App Router) + TypeScript
- **Tailwind CSS 4** + shadcn/ui + Framer Motion
- **Prisma** + SQLite (scan history)
- **Cheerio** (HTML parsing), **sharp** (image compression), **JSZip** (export)

## 🔌 API

| Endpoint | Description |
|---|---|
| `POST /api/scrape` | Start a scan `{ url }` |
| `GET /api/jobs` | Scan history |
| `GET /api/jobs/:id` | Job status + file tree |
| `GET/PUT /api/jobs/:id/file` | Read / save a scraped file |
| `GET /api/jobs/:id/zip` | Download ZIP |
| `GET /api/jobs/:id/raw/*` | Serve scraped file (preview) |

## 🤖 CI

GitHub Actions builds the project on every push to `main` (install → prisma generate → db push → lint → standalone build) and uploads the artifact.

## ⚖️ Ethical use

Only **publicly accessible** assets are fetched. Scraping may be legally sensitive — respect copyright, terms of service and `robots.txt` of the target site. Use for education, backups of your own sites, and debugging.

---

**SetBD Scanner v1.0** • SCAN • SCRAPE • OWN THE SOURCE
