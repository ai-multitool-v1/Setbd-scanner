# SETBD SCANNER 🛡️

**Discover. Analyze. Secure.**

An all-in-one, Android-native website pentesting & security research suite.
Build status: ![Build APK](https://github.com/ai-multitool-v1/Setbd-scanner/actions/workflows/android-build.yml/badge.svg)

> ⚠️ **Authorized use only.** Every active test requires you to confirm target
> authorization. Use this app exclusively on systems you own or have written
> permission to assess. The developers are not responsible for misuse.

---

## What it is

SETBD Scanner is a **native Android application** (Java, Material 3, dark/light
themes) that bundles a mobile-first toolkit for security research:

| Module | What you get |
| --- | --- |
| **Home** | Dashboard: quick scan, target manager, recent scans, favorites, live stats, network status, honest system-compatibility panel |
| **Tools** | 60+ tools across 11 categories, each with status, version, dependencies, install/launch/documentation actions |
| **Scanner** | Full-scan pipeline: Recon → Headers → TLS → Ports → SQLi/XSS probes → Crawler → OSINT, with safe mode, rate limiting, live console and a report generator (PDF/HTML/JSON/CSV) |
| **Traffic** | Burp-style workbench: HTTP repeater/request editor, header & body inspection (pretty/raw), saved request history, Decoder, JWT inspector, WebSocket tester, text diff, HAR export |
| **Settings** | Theme (dark/light/system), safe mode defaults, rate limit, custom User-Agent, audit log viewer, data wipe |

## Tool status policy (no fake results)

Every tool in the catalog carries an honest status badge:

- **NATIVE** — implemented inside the app in Java. It really runs on your
  device and prints real output: DNS, WHOIS, HTTP security-header audit,
  TLS certificate + protocol analysis, cookie/CORS/CSP analyzers, technology
  fingerprinting, port scanner with banner grabbing, ping, subnet calculator,
  ping-sweep device discovery, passive SQLi error-based detection, reflected
  XSS canary tester, DOM-XSS static analysis, polite website crawler
  (robots.txt-honoring), link/email/image/JS/CSS/metadata extractors, robots &
  sitemap parsers, Wayback + crt.sh CT lookups, OpenAPI viewer, REST inspector,
  WebSocket tester, JWT decoder, APK analyzer/certificate inspector, and the
  full offline utility set (hashes, Base64, URL codec, JSON/HTML formatting,
  regex tester, wordlist generator, payload canary library, text diff,
  timestamp converter).
- **TERMUX** — requires the named binary (e.g. `nmap`, `nuclei`, `sqlmap`,
  `ffuf`, `jadx`). The app ships **no binary and never fakes output**; the
  tool page shows exact Termux install + example commands.
- **EXTERNAL** — needs a desktop tool or a paid API key (OWASP ZAP, DNS
  history). Documented with a supported remote-execution workflow.

## Reports

Each scan produces findings with severity (HIGH/MEDIUM/LOW/INFO), an
approximate CVSS-style score, evidence, affected URLs and remediation steps,
exportable as **PDF / HTML / JSON / CSV**. Exports land in
`Downloads/SETBD Scanner/` and can be shared from the app.

## Build

The GitHub Actions workflow (`.github/workflows/android-build.yml`) builds a
debug APK and a release APK (signed with the debug key for easy testing) on
every push to `main`, uploads them as artifacts and attaches them to the
`v1.0-latest` release. To build locally see [INSTALL.md](INSTALL.md).

## Tech stack

- Java 17, Android SDK 34 (min 24)
- Material 3 components, day/night themes, rounded cards, spring-ish transitions
- OkHttp 4 for all network modules, SQLite for local storage
- Native `android.graphics.pdf.PdfDocument` for PDF reports
- Modular architecture: `core/` (engine + tools + registry), `ui/` (fragments,
  activities, adapters), `data/` (models + DB) — easy to extend with new tools
  by adding one `Registry` entry
