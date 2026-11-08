# SECURITY & RESPONSIBLE USE POLICY

## Scope

SETBD Scanner is a security-testing utility for professionals, students and
site owners. It follows the same ethics as tools like Nmap or OWASP ZAP:
powerful against targets **you are authorized to test**, useless for abuse by
design choices below.

## Built-in guardrails

1. **Authorization gates** — the full scanner and every active tool (port
   scans, CORS probes, SQLi/XSS checks, crawler, WebSocket tests, device
   discovery) require ticking an explicit "I am authorized" checkbox.
2. **Safe mode (default ON)** — all probes are benign: canary strings, no
   exploit payloads, no destructive requests, `SLEEP(0)` instead of real
   time-based checks.
3. **Rate limiting** — configurable delay (default 300 ms) between requests in
   every loop (crawler, port scan, sweeps, protocol probes).
4. **robots.txt & scope** — the crawler parses and honors `Disallow` rules,
   stays on-host by default, caps depth (≤5) and pages (≤100).
5. **No exploitation** — SQLmap-class exploitation is not bundled. The SQLi
   module is passive error-based detection only; confirmation is left to the
   analyst.
6. **Private-network guard** — scanning loopback/private ranges requires the
   explicit "Allow local targets" switch.
7. **Audit log** — every scan, tool run, target change and export is written
   to a local audit trail viewable in Settings.
8. **Command safety** — external processes (`ping`) are spawned with a fixed
   argv list, never through a shell, so user input can never become an
   injected command.
9. **Honest status** — TERMUX/EXTERNAL tools show setup instructions instead
   of pretend output. No fabricated "scan succeeded" states exist.
10. **Local-only data** — targets, history, requests and keys stay in the
    app's SQLite DB; nothing is sent to any backend (there is none).

## Legal

Unauthorized access to computer systems is illegal almost everywhere
(e.g. CFAA in the US, Computer Misuse Act in the UK, ICT Act §54/56 in
Bangladesh). Get written permission or use dedicated legal practice targets
(`scanme.nmap.org`, `testphp.vulnweb.com`, DVWA labs).

## Reporting issues

Open a GitHub issue. Do not include client-confidential scan data in reports.
