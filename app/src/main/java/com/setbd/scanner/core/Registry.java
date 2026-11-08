package com.setbd.scanner.core;

import com.setbd.scanner.R;
import com.setbd.scanner.data.M;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.net.ssl.HttpsURLConnection;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Catalog of every tool with honest status metadata.
 *  NATIVE  — implemented inside this app, runs on-device, real results.
 *  TERMUX  — needs the named binary (usually via Termux). The app never fakes output.
 *  EXTERNAL— needs a desktop tool or an API key; documented with a supported workflow.
 */
public class Registry {

    public static class ToolInfo {
        public String id, name, category, desc, status, version, deps, doc;
        public int icon;
        public String targetLabel;   // null → no target field
        public String paramLabel;    // null → no extra field
        public Tool.Impl impl;       // null → external / not bundled
        public boolean needsAuth;    // active test → explicit authorization required
    }

    private static final List<ToolInfo> TOOLS = new ArrayList<>();

    public static List<ToolInfo> all() {
        ensure();
        return TOOLS;
    }

    public static List<ToolInfo> byCategory(String cat) {
        ensure();
        List<ToolInfo> out = new ArrayList<>();
        for (ToolInfo t : TOOLS) if (t.category.equals(cat)) out.add(t);
        return out;
    }

    public static ToolInfo byId(String id) {
        ensure();
        for (ToolInfo t : TOOLS) if (t.id.equals(id)) return t;
        return null;
    }

    public static String[] categories() {
        return new String[]{
                "Reconnaissance", "Web Discovery", "Web Vulnerability", "SQL Injection", "XSS",
                "Scraper", "API Security", "Network", "OSINT", "Android", "Utilities"
        };
    }

    public static int categoryIcon(String cat) {
        switch (cat) {
            case "Web Discovery": return R.drawable.ic_globe;
            case "Web Vulnerability": return R.drawable.ic_bug;
            case "SQL Injection":
            case "XSS": return R.drawable.ic_code;
            case "Scraper": return R.drawable.ic_download;
            case "API Security": return R.drawable.ic_swap;
            case "Network": return R.drawable.ic_wifi;
            case "OSINT": return R.drawable.ic_eye;
            case "Android": return R.drawable.ic_terminal;
            case "Utilities": return R.drawable.ic_key;
            default: return R.drawable.ic_search;
        }
    }

    // ---------- builders ----------

    private static ToolInfo n(String id, String cat, String name, String desc, String tl, String pl,
                              Tool.Impl impl, boolean auth, String deps) {
        ToolInfo t = new ToolInfo();
        t.id = id;
        t.name = name;
        t.category = cat;
        t.desc = desc;
        t.status = "NATIVE";
        t.version = "1.0";
        t.deps = deps == null ? "Built-in (no external dependencies)" : deps;
        t.icon = categoryIcon(cat);
        t.targetLabel = tl;
        t.paramLabel = pl;
        t.impl = impl;
        t.needsAuth = auth;
        return t;
    }

    private static ToolInfo x(String id, String cat, String name, String desc, String status,
                              String tl, String install, String example) {
        ToolInfo t = new ToolInfo();
        t.id = id;
        t.name = name;
        t.category = cat;
        t.desc = desc;
        t.status = status;
        t.version = "external";
        t.deps = status.equals("TERMUX")
                ? "Requires Termux + the '" + id + "' binary (not bundled)"
                : "Not available on Android — remote/desktop execution required";
        t.icon = categoryIcon(cat);
        t.targetLabel = tl;
        t.paramLabel = null;
        t.impl = null;
        t.needsAuth = false;
        t.doc = "SETBD SCANNER DOES NOT SHIP OR FAKE THIS TOOL.\n\n"
                + "Install (Termux):\n  " + install + "\n\n"
                + "Example usage:\n  " + example + "\n\n"
                + "Workflow options:\n"
                + "  • Run it inside Termux on the same device (pkg-based or via pip/go).\n"
                + "  • Run it on a remote VPS you control and reach it over SSH.\n"
                + "  • Pair with a desktop attacker machine on the same VPN.\n\n"
                + "Only use against targets you are explicitly authorized to test.";
        return t;
    }

    private static synchronized void ensure() {
        if (!TOOLS.isEmpty()) return;

        // ================= RECONNAISSANCE =================
        String C = "Reconnaissance";
        TOOLS.add(n("dns", C, "DNS Lookup", "Resolve A/AAAA records, canonical names and reachability for a host.", "Domain or IP", null, NetTools::dns, false, null));
        TOOLS.add(n("whois", C, "WHOIS", "Registration data via IANA + registrar WHOIS servers (port 43).", "Domain", null, NetTools::whois, false, null));
        TOOLS.add(n("secheaders", C, "HTTP Header Analyzer", "Fetch a URL, show every response header and audit security headers.", "Target URL", null, WebTools::secHeaders, false, null));
        TOOLS.add(n("tlsinfo", C, "SSL/TLS Certificate Analyzer", "Certificate subject, issuer, validity, SANs and expiry warnings.", "Host[:port]", null, WebTools::tlsInfo, false, null));
        TOOLS.add(n("tlsscan", C, "SSL/TLS Protocol Scanner", "Probe TLSv1.0–1.3/SSLv3 support and flag obsolete protocols.", "Host[:port]", null, WebTools::tlsScan, true, null));
        TOOLS.add(n("ipinfo", C, "IP Information", "IP geolocation/ASN data via the ipinfo.io public API.", "IP or domain (empty = this device)", null, NetTools::ipInfo, false, null));
        TOOLS.add(x("nmap", C, "Nmap", "The classic network/port scanner with service and OS detection.", "TERMUX",
                "target", "pkg install nmap", "nmap -sV -Pn --top-ports 100 scanme.nmap.org"));
        TOOLS.add(x("whatweb", C, "WhatWeb", "Web technology fingerprinting (1000+ plugins).", "TERMUX",
                "target", "pkg install ruby && gem install whatweb", "whatweb https://example.com"));
        TOOLS.add(x("httpx", C, "HTTPX", "ProjectDiscovery fast HTTP prober for host lists.", "TERMUX",
                "targets file", "go install -v github.com/projectdiscovery/httpx/cmd/httpx@latest", "httpx -l hosts.txt -title -tech-detect"));
        TOOLS.add(x("subfinder", C, "Subfinder", "ProjectDiscovery passive subdomain discovery.", "TERMUX",
                "domain", "go install -v github.com/projectdiscovery/subfinder/v2/cmd/subfinder@latest", "subfinder -d example.com -all"));
        TOOLS.add(x("amass", C, "Amass (OWASP)", "In-depth attack surface mapping and asset discovery.", "TERMUX",
                "domain", "go install -v github.com/owasp-amass/amass/v4/...@master", "amass enum -passive -d example.com"));
        TOOLS.add(x("assetfinder", C, "Assetfinder", "Find domains and subdomains related to a domain.", "TERMUX",
                "domain", "go install github.com/tomnomnom/assetfinder@latest", "assetfinder --subs-only example.com"));
        TOOLS.add(x("theharvester", C, "theHarvester", "E-mails, subdomains and names from public sources.", "TERMUX",
                "domain", "pip install theHarvester", "theHarvester -d example.com -b bing"));

        // ================= WEB DISCOVERY =================
        C = "Web Discovery";
        TOOLS.add(n("robots", C, "Robots.txt Analyzer", "Fetch and summarize robots.txt directives.", "Website URL", null, WebTools::robots, false, null));
        TOOLS.add(n("sitemap", C, "Sitemap Parser", "Parse /sitemap.xml <loc> entries (follows indexes).", "Website URL", null, WebTools::sitemap, false, null));
        TOOLS.add(n("wayback", C, "Wayback URL Discovery", "Historic URLs via the archive.org CDX API.", "Domain", null, WebTools::wayback, false, null));
        TOOLS.add(n("jsfiles", C, "JavaScript File Discovery", "List every <script src> referenced by a page.", "Page URL", null, WebTools::jsFiles, false, null));
        TOOLS.add(n("endpoints", C, "Endpoint Discovery", "Extract API endpoint candidates from HTML/JS (fetch/axios/routes).", "Page URL", null, WebTools::endpoints, false, null));
        TOOLS.add(x("gobuster", C, "Gobuster", "Directory/file/DNS/vhost brute-forcer.", "TERMUX",
                "target", "go install github.com/OJ/gobuster/v3@latest", "gobuster dir -u https://target -w /path/wordlist.txt"));
        TOOLS.add(x("ffuf", C, "FFUF", "Fast web fuzzer written in Go.", "TERMUX",
                "target", "go install github.com/ffuf/ffuf/v2@latest", "ffuf -u https://target/FUZZ -w wordlist.txt"));
        TOOLS.add(x("dirsearch", C, "Dirsearch", "Web path discovery (Python).", "TERMUX",
                "target", "pip install dirsearch", "dirsearch -u https://target -w db/dicc.txt"));
        TOOLS.add(x("feroxbuster", C, "Feroxbuster", "Rust recursive content discovery.", "TERMUX",
                "target", "cargo install feroxbuster (Rust toolchain)", "feroxbuster -u https://target -w wordlist.txt"));

        // ================= WEB VULNERABILITY =================
        C = "Web Vulnerability";
        TOOLS.add(n("cookies", C, "Cookie Security Analyzer", "Audit Set-Cookie flags: Secure, HttpOnly, SameSite.", "Target URL", null, WebTools::cookies, false, null));
        TOOLS.add(n("cors", C, "CORS Misconfiguration Checker", "Send probe Origin headers and detect dangerous reflections.", "Target URL", null, WebTools::cors, true, null));
        TOOLS.add(n("csp", C, "CSP Analyzer", "Parse the Content-Security-Policy and flag weak directives.", "Target URL", null, WebTools::csp, false, null));
        TOOLS.add(n("tech", C, "Technology Fingerprinting", "Identify server/framework/JS-library signatures passively.", "Target URL", null, WebTools::tech, false, null));
        TOOLS.add(x("nuclei", C, "Nuclei", "ProjectDiscovery template-based vulnerability scanner.", "TERMUX",
                "target", "go install -v github.com/projectdiscovery/nuclei/v2/cmd/nuclei@latest", "nuclei -u https://target -severity low,medium,high,critical"));
        TOOLS.add(x("nikto", C, "Nikto", "Classic web server scanner (outdated software, dangerous files).", "TERMUX",
                "target", "pkg install perl && git clone https://github.com/sullo/nikto", "perl nikto.pl -h https://target"));
        TOOLS.add(x("zap", C, "OWASP ZAP", "Full proxy + active scanner; desktop tool.", "EXTERNAL",
                "target", "Desktop only: https://www.zaproxy.org (Linux/Windows/macOS)", "zap-cli quick-scan --spider https://target  (on a machine you control)"));

        // ================= SQL INJECTION =================
        C = "SQL Injection";
        TOOLS.add(n("sqlianalyzer", C, "SQLi Parameter Analyzer", "PASSIVE error-based detection: 2 benign quote probes, DB-error signature matching. No exploitation.", "URL with parameter\n(e.g. https://host/item?id=1)", null, WebTools::sqliCheck, true, null));
        TOOLS.add(x("sqlmap", C, "SQLmap", "Automatic SQL injection and database takeover — exploitation is out of scope for this app.", "TERMUX",
                "target", "git clone --depth 1 https://github.com/sqlmapproject/sqlmap && python sqlmap.py --help", "python sqlmap.py -u 'https://host/page?id=1' --batch --level=1"));

        // ================= XSS =================
        C = "XSS";
        TOOLS.add(n("xssreflected", C, "Reflected XSS Tester", "Reflect a benign canary incl. markup chars; flags unencoded reflection for manual verification.", "URL with parameter", null, WebTools::xssReflected, true, null));
        TOOLS.add(n("domxss", C, "DOM XSS Analysis", "Static scan of page + local JS for dangerous source→sink patterns.", "Page URL", null, WebTools::domXss, false, null));
        TOOLS.add(x("xssstored", C, "Stored XSS Assessment", "Manual, stateful testing — use ZAP/Burp on a machine you control.", "EXTERNAL",
                "target", "OWASP ZAP or Burp Suite on desktop", "Manual workflow: submit canary, revisit pages, review storage."));

        // ================= SCRAPER =================
        C = "Scraper";
        TOOLS.add(n("crawl", C, "Website Crawler", "Polite BFS crawler: robots.txt honored, scope/depth caps, pause/resume/stop, emails+JS+images collection.", "Start URL", "depth,maxPages (e.g. 2,20) — add 'sub' to include subdomains", ScraperTools::crawl, true, null));
        TOOLS.add(n("links", C, "Link Extractor", "Internal and external links from one page.", "Page URL", null, WebTools::links, false, null));
        TOOLS.add(n("emails", C, "Email Extractor", "Public email addresses on one page (mailto + text).", "Page URL", null, WebTools::emails, false, null));
        TOOLS.add(n("images", C, "Image URL Extractor", "All <img src> and og:image URLs, resolved absolute.", "Page URL", null, WebTools::images, false, null));
        TOOLS.add(n("cssjs", C, "JS & CSS Extractor", "Referenced JavaScript and stylesheet files.", "Page URL", null, WebTools::cssJs, false, null));
        TOOLS.add(n("meta", C, "Metadata Extractor", "Title, meta/OG/Twitter tags and X- headers.", "Page URL", null, WebTools::metaTags, false, null));
        TOOLS.add(n("source", C, "HTML Source Viewer", "Raw HTML source of a page.", "Page URL", null, WebTools::sourceView, false, null));
        TOOLS.add(n("pagination", C, "Pagination Detector", "Find pagination link patterns (rel=next, page=N).", "Page URL", null, WebTools::pagination, false, null));

        // ================= API SECURITY =================
        C = "API Security";
        TOOLS.add(n("restapi", C, "REST API Inspector", "Send a request and inspect status, headers, timing and pretty JSON.", "API endpoint URL", "HTTP method (default GET; JSON body goes after a '|', e.g. POST|{\"a\":1})", Registry::restApi, true, null));
        TOOLS.add(n("openapi", C, "OpenAPI Specification Viewer", "Fetch an OpenAPI/Swagger JSON spec and list every path+method.", "Spec URL (json)", null, Registry::openApi, false, null));
        TOOLS.add(n("jwt", C, "JWT Inspector", "Decode header/payload, flag alg=none and expired tokens — fully offline.", "JWT (paste in input)", null, UtilTools::jwt, false, null));
        TOOLS.add(n("wstest", C, "WebSocket Tester", "Open a WebSocket, send one message, print frames, close.", "ws:// or wss:// URL", "Message to send (default: ping)", Registry::wsTest, true, null));

        // ================= NETWORK =================
        C = "Network";
        TOOLS.add(n("ports", C, "Port Scanner", "TCP connect scan with banner grabbing and risky-service findings.", "Host", "Ports: 80,443 or 1-1024 (default: top 21)", NetTools::ports, true, null));
        TOOLS.add(n("banner", C, "Service / Banner Grabber", "Grab the service banner of one host:port.", "Host", "host:port", NetTools::banner, true, null));
        TOOLS.add(n("ping", C, "Ping", "ICMP echo via the system ping binary (4 packets).", "Host", null, NetTools::ping, false, null));
        TOOLS.add(n("traceroute", C, "Traceroute", "Hop-by-hop path. Reports honestly if the binary is missing on stock Android.", "Host", null, NetTools::traceroute, false, null));
        TOOLS.add(n("subnet", C, "Subnet Calculator", "CIDR → netmask, network, broadcast, host range, usable count.", "CIDR (e.g. 10.0.0.0/8)", null, NetTools::subnet, false, null));
        TOOLS.add(n("discover", C, "Network Device Discovery", "Ping sweep of a CIDR range (max /22). Authorization required.", "CIDR (e.g. 192.168.1.0/24)", null, NetTools::discover, true, null));
        TOOLS.add(n("ipcalc", C, "IP Calculator", "Same engine as Subnet Calculator, IPv4 focused.", "IP or CIDR", null, NetTools::subnet, false, null));
        TOOLS.add(x("netcat", C, "Netcat", "TCP/UDP swiss-army knife for raw connections.", "TERMUX",
                "host:port", "pkg install ncat", "ncat -v target 80"));

        // ================= OSINT =================
        C = "OSINT";
        TOOLS.add(n("subct", C, "Subdomain Finder (CT logs)", "Passive subdomain discovery via crt.sh certificate transparency logs.", "Domain", null, Registry::subCt, false, null));
        TOOLS.add(n("ctlookup", C, "Certificate Transparency Lookup", "Raw CT log entries (issuer, dates, names) for a domain.", "Domain", null, Registry::ctLookup, false, null));
        TOOLS.add(n("domainintel", C, "Domain Intelligence", "Combined report: DNS + WHOIS + CT subdomains + Wayback presence.", "Domain", null, Registry::domainIntel, false, null));
        TOOLS.add(n("emailosint", C, "Public Email Discovery", "Harvest public emails from a web page.", "Page URL", null, WebTools::emails, false, null));
        TOOLS.add(n("urlintel", C, "URL Intelligence", "Deconstruct a URL: scheme/host/port/path/params + redirect chain.", "URL", null, Registry::urlIntel, false, null));
        TOOLS.add(n("techintel", C, "Technology Intelligence", "Passive tech fingerprint (same engine as Web category).", "Target URL", null, WebTools::tech, false, null));
        TOOLS.add(x("dnshistory", C, "DNS History", "Historic DNS records — needs a paid API key (SecurityTrails/ViewDNS).", "EXTERNAL",
                "domain", "API key at securitytrails.com; then query via curl in Termux", "curl -s -H 'APIKEY: ...' https://api.securitytrails.com/v1/history/example.com/dns/a"));
        TOOLS.add(x("metaosint", C, "Metadata Analyzer (files)", "EXIF/office-doc metadata extraction.", "TERMUX",
                "file url", "pkg install exiftool", "exiftool document.pdf"));

        // ================= ANDROID =================
        C = "Android";
        TOOLS.add(n("apkinfo", C, "APK Analyzer", "Download an APK and analyze entries, ABIs, DEX count, certificate info.", "APK URL (https://.../*.apk)", null, Registry::apkInfo, false, null));
        TOOLS.add(n("apkcert", C, "Certificate Inspector", "Extract and parse the signing certificate chain of an APK.", "APK URL", null, Registry::apkCert, false, null));
        TOOLS.add(x("jadx", C, "JADX", "DEX to Java decompiler (desktop-grade).", "TERMUX",
                "apk", "Desktop recommended: https://github.com/skylot/jadx/releases", "jadx -d out target.apk"));
        TOOLS.add(x("apktool", C, "APKTool", "Decode resources / rebuild APKs.", "TERMUX",
                "apk", "pkg install apktool (or manual jar install)", "apktool d target.apk -o out"));
        TOOLS.add(x("manifest", C, "Android Manifest Viewer", "Binary AXML parsing needs aapt/apkanalyzer.", "EXTERNAL",
                "apk", "Termux: pkg install aapt  •  Desktop: $ANDROID_HOME/apkanalyzer", "aapt dump xmltree target.apk AndroidManifest.xml"));
        TOOLS.add(x("perms", C, "Permission Analyzer", "List requested permissions with risk notes.", "EXTERNAL",
                "apk", "Termux: pkg install aapt", "aapt dump permissions target.apk"));
        TOOLS.add(x("adbhelper", C, "ADB Helper", "Android Debug Bridge client.", "TERMUX",
                "device", "pkg install android-tools", "adb devices && adb shell pm list packages"));

        // ================= UTILITIES =================
        C = "Utilities";
        TOOLS.add(n("hash", C, "Hash Generator", "MD5 / SHA-1 / SHA-256 / SHA-512 / CRC32 of any text.", "Text to hash", null, UtilTools::hash, false, null));
        TOOLS.add(n("hashid", C, "Hash Identifier", "Identify hash format by length/charset/prefix heuristics.", "Hash string", null, UtilTools::hashId, false, null));
        TOOLS.add(n("b64", C, "Base64 Encoder/Decoder", "Standard + URL-safe Base64 with auto-detect.", "Text or Base64 ('decode' target forces decode)", null, UtilTools::b64, false, null));
        TOOLS.add(n("urlcodec", C, "URL Encoder/Decoder", "Percent-encoding round trip.", "Text or encoded URL", null, UtilTools::urlCodec, false, null));
        TOOLS.add(n("jsonfmt", C, "JSON Formatter", "Pretty-print and validate JSON.", "JSON text", null, UtilTools::jsonFmt, false, null));
        TOOLS.add(n("htmlfmt", C, "HTML Formatter", "Basic HTML re-indentation.", "HTML text", null, UtilTools::htmlFmt, false, null));
        TOOLS.add(n("regex", C, "Regex Tester", "Live regex matching with group extraction.", "Test text", "Regex pattern", UtilTools::regex, false, null));
        TOOLS.add(n("wordlist", C, "Wordlist Manager", "Generate mutation wordlists from base words.", "base words, comma separated", null, UtilTools::wordlist, false, null));
        TOOLS.add(n("payloads", C, "Payload & Marker Library", "Reference canaries for SQLi/XSS/traversal/command-injection detection.", "Category: sql | xss | traversal | cmd", null, UtilTools::payloads, false, null));
        TOOLS.add(n("textdiff", C, "Text Diff", "Line-based LCS diff between two texts (target vs input).", "Text A", "Text B", UtilTools::diff, false, null));
        TOOLS.add(n("timestamp", C, "Timestamp Converter", "Unix ↔ ISO-8601 conversion.", "Timestamp", null, UtilTools::timestamp, false, null));
    }

    // ---------- inline native implementations ----------

    static void restApi(Tool.Ctx c) throws Exception {
        String spec = (c.param == null || c.param.trim().isEmpty()) ? "GET" : c.param.trim();
        String method = "GET";
        String body = null;
        if (spec.contains("|")) {
            String[] pm = spec.split("\\|", 2);
            method = pm[0].trim().toUpperCase(Locale.US);
            body = pm[1].trim();
        }
        String url = WebTools.normUrl(c.target);
        c.print(method + " " + url + (body != null ? "\nbody: " + body : ""));
        Net.Resp r = Net.request(method, url, null, body, c.userAgent);
        c.print("\nStatus: " + r.code + " " + r.message + "   (" + r.timeMs + " ms)");
        c.print("Content-Type: " + r.header("Content-Type"));
        c.print("\n== HEADERS ==");
        for (Map.Entry<String, List<String>> e : r.headers.entrySet())
            c.print(e.getKey() + ": " + String.join(", ", e.getValue()));
        c.print("\n== BODY ==");
        String b = r.body.trim();
        if (b.startsWith("{") || b.startsWith("[")) {
            try {
                c.print(b.startsWith("[") ? new JSONArray(b).toString(2) : new JSONObject(b).toString(2));
            } catch (Exception e) {
                c.print(r.body);
            }
        } else {
            c.print(r.body.length() > 20_000 ? r.body.substring(0, 20_000) + "...[truncated]" : r.body);
        }
        if (r.code >= 500) c.finding("MEDIUM", "4.3", "Server error on API endpoint", "HTTP " + r.code + " returned.", r.code + " " + url, url, "Fix error handling; never leak stack traces.");
        else if (r.code == 401 || r.code == 403) c.print("\nAuth response code — endpoint is protected.");
        else if (r.code == 200 && !b.isEmpty()) c.print("\nEndpoint reachable and returned data — check if it should require auth.");
    }

    static void openApi(Tool.Ctx c) throws Exception {
        String url = WebTools.normUrl(c.target);
        c.print("GET " + url);
        Net.Resp r = Net.get(url, c.userAgent);
        JSONObject spec = new JSONObject(r.body);
        JSONObject info = spec.optJSONObject("info");
        if (info != null) {
            c.print("Title:   " + info.optString("title"));
            c.print("Version: " + info.optString("version"));
        }
        c.print("OpenAPI: " + spec.optString("openapi", spec.optString("swagger", "?")));
        JSONObject paths = spec.optJSONObject("paths");
        if (paths == null) {
            c.print("No 'paths' found — is this an OpenAPI JSON spec? (YAML needs conversion)");
            return;
        }
        int n = 0;
        for (java.util.Iterator<String> it = paths.keys(); it.hasNext(); ) {
            String p = it.next();
            JSONObject ops = paths.getJSONObject(p);
            List<String> methods = new ArrayList<>();
            for (java.util.Iterator<String> o = ops.keys(); o.hasNext(); ) {
                String m = o.next();
                if (m.matches("get|post|put|delete|patch|head|options")) methods.add(m.toUpperCase(Locale.US));
            }
            c.print(String.format(Locale.US, "%-8s %s", String.join(",", methods), p));
            n += methods.size();
        }
        c.print("\nPaths: " + paths.length() + ", operations: " + n);
    }

    static void wsTest(Tool.Ctx c) throws Exception {
        String url = c.target.trim();
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) throw new IllegalArgumentException("URL must start with ws:// or wss://");
        String msg = (c.param == null || c.param.isEmpty()) ? "ping" : c.param;
        c.print("Connecting to " + url + " ...");
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        OkHttpClient cl = new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build();
        Request req = new Request.Builder().url(url).build();
        final long t0 = System.currentTimeMillis();
        cl.newWebSocket(req, new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response response) {
                c.print("Connected. HTTP " + response.code() + " in " + (System.currentTimeMillis() - t0) + " ms");
                c.print("Send: " + msg);
                ws.send(msg);
            }

            @Override public void onMessage(WebSocket ws, String text) {
                c.print("RECV: " + (text.length() > 4000 ? text.substring(0, 4000) + "..." : text));
                c.print("Closing.");
                ws.close(1000, "done");
                latch.countDown();
            }

            @Override public void onFailure(WebSocket ws, Throwable t, Response response) {
                c.print("FAILED: " + t.getMessage());
                latch.countDown();
            }

            @Override public void onClosed(WebSocket ws, int code, String reason) {
                c.print("Closed (" + code + ").");
                latch.countDown();
            }
        });
        latch.await(15, TimeUnit.SECONDS);
        cl.dispatcher().executorService().shutdown();
    }

    static void subCt(Tool.Ctx c) throws Exception {
        String domain = WebTools.host(WebTools.normUrl(c.target));
        c.print("Querying crt.sh CT logs for %.{0,40}." + domain + " (this can take ~20s) ...");
        String api = "https://crt.sh/?q=%25." + java.net.URLEncoder.encode(domain, "UTF-8") + "&output=json";
        Net.Resp r = Net.get(api);
        JSONArray arr = new JSONArray(r.body);
        Set<String> subs = new TreeSet<>();
        for (int i = 0; i < arr.length() && subs.size() < 2000; i++) {
            for (String n : arr.getJSONObject(i).optString("name_value").split("\n")) {
                n = n.trim().toLowerCase(Locale.US).replace("*.", "");
                if (n.endsWith(domain)) subs.add(n);
            }
        }
        c.print("Unique subdomains: " + subs.size());
        int i = 0;
        for (String s : subs) {
            if (i++ > 500) {
                c.print("... more in exported output");
                break;
            }
            c.print(s);
        }
        if (!subs.isEmpty())
            c.finding("INFO", "0.0", "Subdomains via Certificate Transparency",
                    subs.size() + " unique names in public CT logs.", domain + " → " + Math.min(subs.size(), 500) + " listed", "https://crt.sh/?q=%25." + domain,
                    "Review for forgotten/staging hosts still exposing services.");
    }

    static void ctLookup(Tool.Ctx c) throws Exception {
        String domain = WebTools.host(WebTools.normUrl(c.target));
        c.print("Querying crt.sh for all certificates of " + domain + " ...");
        String api = "https://crt.sh/?q=" + java.net.URLEncoder.encode(domain, "UTF-8") + "&output=json";
        Net.Resp r = Net.get(api);
        JSONArray arr = new JSONArray(r.body);
        c.print("Certificate log entries: " + arr.length());
        int shown = 0;
        for (int i = 0; i < arr.length() && shown < 100; i++, shown++) {
            JSONObject o = arr.getJSONObject(i);
            c.print("• " + o.optString("common_name") + "  issuer: " + o.optString("issuer_name")
                    + "  from " + o.optString("not_before") + " to " + o.optString("not_after"));
        }
        if (arr.length() > 100) c.print("... " + (arr.length() - 100) + " more");
    }

    static void domainIntel(Tool.Ctx c) throws Exception {
        String domain = WebTools.host(WebTools.normUrl(c.target));
        c.print("========== DOMAIN INTELLIGENCE: " + domain + " ==========\n");
        c.print("-- DNS --");
        try {
            NetTools.dns(c);
        } catch (Exception e) {
            c.print("DNS failed: " + e.getMessage());
        }
        c.print("\n-- WHOIS --");
        try {
            NetTools.whois(c);
        } catch (Exception e) {
            c.print("WHOIS failed: " + e.getMessage());
        }
        c.print("\n-- Certificate Transparency --");
        try {
            subCt(c);
        } catch (Exception e) {
            c.print("CT lookup failed: " + e.getMessage());
        }
        c.print("\n-- Wayback presence --");
        try {
            WebTools.wayback(c);
        } catch (Exception e) {
            c.print("Wayback failed: " + e.getMessage());
        }
    }

    static void urlIntel(Tool.Ctx c) throws Exception {
        String url = WebTools.normUrl(c.target);
        try {
            java.net.URI u = java.net.URI.create(url);
            c.print("Scheme:    " + u.getScheme());
            c.print("Host:      " + u.getHost());
            c.print("Port:      " + (u.getPort() == -1 ? "(default)" : u.getPort()));
            c.print("Path:      " + u.getPath());
            c.print("Query:     " + u.getQuery());
            if (u.getQuery() != null)
                for (String p : u.getQuery().split("&"))
                    c.print("  param: " + p);
            c.print("Fragment:  " + u.getFragment());
            if (u.getUserInfo() != null) {
                c.print("UserInfo:  PRESENT (credentials in URL — bad practice)");
                c.finding("LOW", "2.0", "Credentials embedded in URL", "Userinfo present in URL.", url, url, "Never place credentials in URLs; they leak into logs.");
            }
        } catch (Exception e) {
            c.print("Invalid URL: " + e.getMessage());
            return;
        }
        c.print("\nFollowing redirect chain (max 8) ...");
        String cur = url;
        for (int i = 0; i < 8; i++) {
            c.sleep();
            Net.Resp r = Net.get(cur, c.userAgent);
            c.print("HTTP " + r.code + "  " + cur);
            String loc = r.header("Location");
            if (loc == null) break;
            cur = WebTools.resolve(cur, loc);
            c.print("  → redirect: " + loc);
        }
    }

    static void apkInfo(Tool.Ctx c) throws Exception {
        String url = c.target.trim();
        if (!url.endsWith(".apk")) throw new IllegalArgumentException("Provide a direct .apk URL");
        c.print("Downloading " + url + " ...");
        OkHttpClient cl = new OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build();
        Request req = new Request.Builder().url(url).header("User-Agent", Net.DEFAULT_UA).build();
        Response resp = cl.newCall(req).execute();
        c.print("HTTP " + resp.code());
        if (!resp.isSuccessful() || resp.body() == null) throw new IllegalStateException("Download failed");
        long total = 0;
        int entries = 0, dex = 0, assets = 0;
        Set<String> abis = new LinkedHashSet<>();
        String certFile = null;
        Set<String> certs = new LinkedHashSet<>();
        try (ZipInputStream z = new ZipInputStream(resp.body().byteStream())) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                entries++;
                total += e.getSize() > 0 ? e.getSize() : 0;
                String n = e.getName();
                if (n.startsWith("lib/") && n.split("/").length > 2) abis.add(n.split("/")[1]);
                if (n.endsWith(".dex")) dex++;
                if (n.startsWith("assets/")) assets++;
                if (n.startsWith("META-INF/") && (n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC")) && certFile == null) {
                    certFile = n;
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int rd;
                    while ((rd = z.read(buf)) > 0) bo.write(buf, 0, rd);
                    certs.add(n + "|" + android.util.Base64.encodeToString(bo.toByteArray(), android.util.Base64.NO_WRAP));
                }
                z.closeEntry();
            }
        }
        c.print("\n== APK METADATA ==");
        c.print("Zip entries:      " + entries);
        c.print("Uncompressed sum: " + (total / 1024 / 1024) + " MB (approx)");
        c.print("DEX files:        " + dex + (dex > 1 ? "  (multidex)" : ""));
        c.print("Assets entries:   " + assets);
        c.print("ABIs (native):    " + (abis.isEmpty() ? "(none — pure Java/Kotlin)" : String.join(", ", abis)));
        c.print("Signature block:  " + (certFile == null ? "not found in META-INF (may be v2/v3 scheme only)" : certFile));
        if (!abis.isEmpty())
            c.print("\nDevice ABI check: " + android.os.Build.SUPPORTED_ABIS[0] + " → " + (abis.contains(android.os.Build.SUPPORTED_ABIS[0]) ? "COMPATIBLE" : "may not run (missing native lib for this ABI)"));
    }

    static void apkCert(Tool.Ctx c) throws Exception {
        String url = c.target.trim();
        if (!url.endsWith(".apk")) throw new IllegalArgumentException("Provide a direct .apk URL");
        c.print("Downloading " + url + " (certificate scan) ...");
        HttpsURLConnection conn = (HttpsURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", Net.DEFAULT_UA);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        String certFile = null;
        byte[] certBytes = null;
        int entries = 0;
        try (ZipInputStream z = new ZipInputStream(conn.getInputStream())) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                entries++;
                String n = e.getName();
                if (n.startsWith("META-INF/") && (n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC"))) {
                    certFile = n;
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int rd;
                    while ((rd = z.read(buf)) > 0) bo.write(buf, 0, rd);
                    certBytes = bo.toByteArray();
                    break;
                }
                z.closeEntry();
            }
        }
        if (certBytes == null) {
            c.print("No PKCS#7 signature found in " + entries + " entries. The APK may be signed with the v2/v3-only scheme (visible via apksigner in Termux).");
            return;
        }
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        List<Certificate> chain = new ArrayList<>(cf.generateCertificates(new java.io.ByteArrayInputStream(certBytes)));
        c.print("Signature file: " + certFile);
        c.print("Certificates in chain: " + chain.size());
        for (Certificate cert : chain) {
            X509Certificate x = (X509Certificate) cert;
            c.print("\nSubject:  " + x.getSubjectX500Principal().getName());
            c.print("Issuer:   " + x.getIssuerX500Principal().getName());
            c.print("Valid:    " + x.getNotBefore() + " → " + x.getNotAfter());
            c.print("Serial:   " + x.getSerialNumber().toString(16));
            c.print("SHA-256:  " + sha256(x.getEncoded()));
            long days = (x.getNotAfter().getTime() - System.currentTimeMillis()) / 86_400_000L;
            c.print("Days to expiry: " + days);
        }
    }

    static String sha256(byte[] b) throws Exception {
        return UtilTools.hex(java.security.MessageDigest.getInstance("SHA-256").digest(b));
    }
}
