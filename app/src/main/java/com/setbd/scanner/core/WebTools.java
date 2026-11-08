package com.setbd.scanner.core;

import com.setbd.scanner.data.M;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

/**
 * Web-focused analysis modules. Every active probe is rate limited and only runs
 * after the UI confirmed target authorization. Probes use benign canaries only —
 * no destructive payloads, no exploitation.
 */
public class WebTools {

    // ---------- shared helpers ----------

    static String normUrl(String t) {
        t = t == null ? "" : t.trim();
        if (!t.startsWith("http://") && !t.startsWith("https://")) t = "https://" + t;
        return t;
    }

    static Net.Resp get(Tool.Ctx c, String url) throws IOException {
        return Net.request("GET", url, null, null, c.userAgent);
    }

    static String host(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return url;
        }
    }

    static String resolve(String base, String href) {
        if (href == null) return null;
        href = href.trim();
        if (href.isEmpty() || href.startsWith("#") || href.startsWith("javascript:")
                || href.startsWith("mailto:") || href.startsWith("tel:") || href.startsWith("data:"))
            return null;
        try {
            return new java.net.URL(new java.net.URL(base), href).toString();
        } catch (Exception e) {
            return null;
        }
    }

    static List<String> regex1(String body, String rx) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile(rx, Pattern.CASE_INSENSITIVE).matcher(body);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    static String fmtDate(long ms) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(ms));
    }

    // ---------- HTTP security header audit ----------

    public static void secHeaders(Tool.Ctx c) throws Exception {
        String url = normUrl(c.target);
        c.print("GET " + url);
        Net.Resp r = get(c, url);
        c.print("Status: " + r.code + " " + r.message + "  (" + r.timeMs + " ms)");
        c.print("");
        c.print("== Response headers ==");
        for (Map.Entry<String, List<String>> e : r.headers.entrySet())
            c.print(e.getKey() + ": " + String.join(", ", e.getValue()));

        boolean https = url.startsWith("https://");
        String hsts = r.header("Strict-Transport-Security");
        String csp = r.header("Content-Security-Policy");
        String xfo = r.header("X-Frame-Options");
        String xcto = r.header("X-Content-Type-Options");
        String refp = r.header("Referrer-Policy");
        String perms = r.header("Permissions-Policy");
        String server = r.header("Server");
        String powered = r.header("X-Powered-By");

        if (https && hsts == null)
            c.finding("MEDIUM", "5.3", "Strict-Transport-Security (HSTS) missing",
                    "The site does not force browsers to use HTTPS for future visits, allowing SSL-stripping and downgrade attacks.",
                    "No Strict-Transport-Security header on " + url, url,
                    "Add: Strict-Transport-Security: max-age=31536000; includeSubDomains; preload");
        else if (hsts != null) c.print("\nHSTS: " + hsts);

        if (csp == null)
            c.finding("MEDIUM", "5.4", "Content-Security-Policy missing",
                    "Without CSP the browser has no restriction on script sources, increasing XSS impact.",
                    "No Content-Security-Policy header on " + url, url,
                    "Add a CSP restricting script/style/img sources, e.g. default-src 'self'.");
        if (xfo == null && (csp == null || !csp.contains("frame-ancestors")))
            c.finding("LOW", "3.7", "Clickjacking protection missing",
                    "Neither X-Frame-Options nor CSP frame-ancestors prevents framing of the page.",
                    "No X-Frame-Options / frame-ancestors on " + url, url,
                    "Send X-Frame-Options: DENY (or SAMEORIGIN) or add frame-ancestors to CSP.");
        if (xcto == null)
            c.finding("LOW", "3.1", "X-Content-Type-Options missing",
                    "Browsers may MIME-sniff responses and execute uploaded content.",
                    "No X-Content-Type-Options header on " + url, url,
                    "Add: X-Content-Type-Options: nosniff");
        if (refp == null)
            c.finding("LOW", "2.4", "Referrer-Policy missing",
                    "Full URLs (potentially containing tokens) may leak to third parties via Referer.",
                    "No Referrer-Policy header on " + url, url,
                    "Add: Referrer-Policy: strict-origin-when-cross-origin");
        if (perms == null) c.print("Permissions-Policy: not set (informational)");
        if (server != null && server.matches(".*\\d+\\..*"))
            c.finding("LOW", "2.0", "Server version disclosed",
                    "The Server header reveals an exact version which helps attackers find known CVEs.",
                    "Server: " + server, url,
                    "Remove the version from the Server header (server_tokens off in nginx).");
        if (powered != null)
            c.finding("LOW", "2.0", "Technology version disclosed via X-Powered-By",
                    "X-Powered-By reveals backend technology.",
                    "X-Powered-By: " + powered, url,
                    "Remove the X-Powered-By header.");
        c.print("\nAnalysis complete: " + Net.summarize(c.findings));
    }

    // ---------- Cookie security ----------

    public static void cookies(Tool.Ctx c) throws Exception {
        String url = normUrl(c.target);
        c.print("GET " + url);
        Net.Resp r = get(c, url);
        List<String> sc = r.headers.get("set-cookie");
        if (sc == null || sc.isEmpty()) {
            c.print("No Set-Cookie headers in response.");
            return;
        }
        for (String raw : sc) {
            c.print("\nCookie: " + raw);
            String name = raw.split("=", 2)[0];
            boolean secure = raw.toLowerCase(Locale.US).contains("secure");
            boolean httponly = raw.toLowerCase(Locale.US).contains("httponly");
            boolean samesite = raw.toLowerCase(Locale.US).contains("samesite");
            if (!secure)
                c.finding("MEDIUM", "4.3", "Cookie without Secure flag: " + name,
                        "The cookie may be sent over plain HTTP and intercepted.",
                        raw, url, "Add the Secure attribute to every cookie.");
            if (!httponly)
                c.finding("LOW", "3.5", "Cookie without HttpOnly flag: " + name,
                        "JavaScript can read this cookie, increasing XSS impact.",
                        raw, url, "Add HttpOnly to session cookies.");
            if (!samesite)
                c.finding("LOW", "3.1", "Cookie without SameSite attribute: " + name,
                        "Cookie may be attached to cross-site requests (CSRF helper).",
                        raw, url, "Add SameSite=Lax or Strict.");
        }
    }

    // ---------- CORS misconfiguration ----------

    public static void cors(Tool.Ctx c) throws Exception {
        String url = normUrl(c.target);
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Origin", "https://setbd-probe.example");
        c.print("GET " + url + "  (Origin: https://setbd-probe.example)");
        Net.Resp r = Net.request("GET", url, h, null, c.userAgent);
        String acao = r.header("Access-Control-Allow-Origin");
        String acac = r.header("Access-Control-Allow-Credentials");
        c.print("Access-Control-Allow-Origin: " + (acao == null ? "(none)" : acao));
        c.print("Access-Control-Allow-Credentials: " + (acac == null ? "(none)" : acac));
        if (acao != null && acao.contains("setbd-probe.example")) {
            c.finding("HIGH", "7.5", "CORS reflects arbitrary Origin",
                    "The server reflects any attacker-controlled Origin, allowing cross-origin reads.",
                    "ACAO echoed https://setbd-probe.example" + (acac != null ? " with credentials: " + acac : ""), url,
                    "Whitelist trusted origins instead of reflecting the Origin header.");
        } else if (acao != null && acao.trim().equals("*")) {
            c.finding("INFO", "0.0", "CORS wildcard origin",
                    "Any origin may read responses. Harmful only if combined with credentials.",
                    "Access-Control-Allow-Origin: *", url,
                    "Restrict ACAO to required origins, especially for authenticated APIs.");
        } else {
            c.print("No permissive CORS reflection detected.");
        }
    }

    // ---------- CSP analysis ----------

    public static void csp(Tool.Ctx c) throws Exception {
        String url = normUrl(c.target);
        Net.Resp r = get(c, url);
        String csp = r.header("Content-Security-Policy");
        c.print("CSP header: " + (csp == null ? "(not set)" : csp));
        if (csp == null) {
            c.finding("MEDIUM", "5.4", "No Content-Security-Policy",
                    "No CSP header was sent.", "GET " + url, url,
                    "Deploy a CSP; start in report-only mode to avoid breakage.");
            return;
        }
        Map<String, String> dirs = new LinkedHashMap<>();
        for (String part : csp.split(";")) {
            String[] kv = part.trim().split("\\s+", 2);
            if (kv.length > 0 && !kv[0].isEmpty()) dirs.put(kv[0], kv.length > 1 ? kv[1] : "");
        }
        for (Map.Entry<String, String> e : dirs.entrySet()) c.print(e.getKey() + ": " + e.getValue());
        if (dirs.containsKey("default-src") || dirs.containsKey("script-src")) {
            String eff = dirs.containsKey("script-src") ? dirs.get("script-src") : dirs.get("default-src");
            if (eff != null && eff.contains("unsafe-inline"))
                c.finding("MEDIUM", "5.0", "CSP allows inline scripts (unsafe-inline)",
                        "Inline script allowance defeats most of the XSS protection a CSP offers.",
                        "script-src/default-src contains unsafe-inline", url,
                        "Move inline scripts to external files or use nonces/hashes.");
            if (eff != null && eff.contains("unsafe-eval"))
                c.finding("LOW", "3.7", "CSP allows eval (unsafe-eval)",
                        "eval() allowance weakens CSP.", "unsafe-eval present", url,
                        "Remove unsafe-eval unless a framework requires it.");
            if (eff != null && eff.contains("*"))
                c.finding("MEDIUM", "5.0", "CSP script-src wildcard",
                        "Wildcard sources let attackers load scripts from any host.",
                        "script-src contains *", url, "Enumerate required script hosts explicitly.");
        } else {
            c.finding("LOW", "3.1", "CSP without script-src/default-src",
                    "Directives exist but scripts are not restricted.", csp, url,
                    "Define default-src or script-src.");
        }
    }

    // ---------- Technology fingerprinting ----------

    public static void tech(Tool.Ctx c) throws Exception {
        String url = normUrl(c.target);
        Net.Resp r = get(c, url);
        String hay = (r.header("Server") + " " + r.header("X-Powered-By") + " " + r.body).toLowerCase(Locale.US);
        c.print("Target: " + r.url + "  Status: " + r.code);
        Map<String, String> sigs = new LinkedHashMap<>();
        sigs.put("Cloudflare", "cloudflare");
        sigs.put("Nginx", "nginx");
        sigs.put("Apache", "apache");
        sigs.put("IIS", "microsoft-iis");
        sigs.put("PHP", "x-powered-by: php|php/");
        sigs.put("ASP.NET", "asp.net|x-aspnet");
        sigs.put("Express.js", "x-powered-by: express");
        sigs.put("WordPress", "/wp-content|/wp-includes|wp-json");
        sigs.put("Drupal", "drupal|sites/default/files");
        sigs.put("Joomla", "joomla");
        sigs.put("Shopify", "cdn.shopify.com|shopify");
        sigs.put("React", "react.production|data-reactroot|_reactlistening");
        sigs.put("Next.js", "__next|__next_data__");
        sigs.put("Nuxt", "__nuxt");
        sigs.put("Vue.js", "vue.runtime|data-v-");
        sigs.put("Angular", "ng-version|angular.min.js|zone.js");
        sigs.put("jQuery", "jquery");
        sigs.put("Bootstrap", "bootstrap");
        sigs.put("Tailwind CSS", "tailwind");
        sigs.put("Google Analytics", "googletagmanager|google-analytics");
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, String> e : sigs.entrySet()) {
            for (String sig : e.getValue().split("\\|")) {
                if (hay.contains(sig)) {
                    found.add(e.getKey());
                    c.print("[+] " + e.getKey() + "  (matched: " + sig + ")");
                    break;
                }
            }
        }
        String gen = null;
        Matcher gm = Pattern.compile("<meta[^>]+name=[\"']generator[\"'][^>]+content=[\"']([^\"']+)",
                Pattern.CASE_INSENSITIVE).matcher(r.body);
        if (gm.find()) {
            gen = gm.group(1);
            c.print("[+] Generator meta: " + gen);
            found.add(gen);
        }
        if (!found.isEmpty())
            c.finding("INFO", "0.0", "Technologies identified",
                    "Passive fingerprinting result (useful for attack-surface mapping).",
                    String.join(", ", found), url, "Minimize version exposure of listed technologies.");
        else c.print("No known technology signatures matched.");
    }

    // ---------- robots.txt / sitemap ----------

    public static void robots(Tool.Ctx c) throws Exception {
        String url = Net.rootOf(normUrl(c.target)) + "/robots.txt";
        c.print("GET " + url);
        Net.Resp r = get(c, url);
        if (r.code != 200) {
            c.print("HTTP " + r.code + " — robots.txt not available.");
            return;
        }
        int disallow = 0, allow = 0, sitemaps = 0;
        for (String line : r.body.split("\n")) {
            String l = line.trim();
            c.print(l);
            if (l.toLowerCase(Locale.US).startsWith("disallow")) disallow++;
            else if (l.toLowerCase(Locale.US).startsWith("allow")) allow++;
            else if (l.toLowerCase(Locale.US).startsWith("sitemap")) sitemaps++;
        }
        c.print("\nSummary: " + disallow + " Disallow, " + allow + " Allow, " + sitemaps + " Sitemap directives.");
    }

    public static void sitemap(Tool.Ctx c) throws Exception {
        String url = Net.rootOf(normUrl(c.target)) + "/sitemap.xml";
        c.print("GET " + url);
        Net.Resp r = get(c, url);
        if (r.code != 200) {
            c.print("HTTP " + r.code + " — sitemap.xml not available.");
            return;
        }
        List<String> locs = regex1(r.body, "<loc>\\s*([^<\\s]+)\\s*</loc>");
        c.print("Entries: " + locs.size());
        if (r.body.contains("sitemapindex")) c.print("(This is a sitemap index — showing referenced sitemaps/URLs.)");
        int shown = 0;
        Set<String> seen = new LinkedHashSet<>();
        for (String s : locs) {
            if (seen.add(s) && shown++ < 200) c.print(s);
        }
        if (locs.size() > 200) c.print("... " + (locs.size() - 200) + " more");
    }

    // ---------- Wayback / archive discovery ----------

    public static void wayback(Tool.Ctx c) throws Exception {
        String domain = host(normUrl(c.target));
        String api = "https://web.archive.org/cdx/search/cdx?url=" + java.net.URLEncoder.encode(domain, "UTF-8")
                + "&output=json&collapse=urlkey&fl=original,statuscode&limit=500";
        c.print("Querying Wayback CDX API for " + domain + " ...");
        Net.Resp r = get(c, api);
        JSONArray arr = new JSONArray(r.body);
        int n = Math.min(arr.length(), 400);
        for (int i = 1; i < n; i++) {
            JSONArray row = arr.getJSONArray(i);
            c.print(row.optString(1) + "  " + row.optString(0));
        }
        c.print("\nArchived URL count (capped 500): " + Math.max(0, arr.length() - 1));
    }

    // ---------- JS / endpoint / email / image / link / meta extraction ----------

    public static void jsFiles(Tool.Ctx c) throws Exception {
        String url = normUrl(c.target);
        Net.Resp r = get(c, url);
        List<String> srcs = regex1(r.body, "<script[^>]+src=[\"']([^\"']+)[\"']");
        c.print("JavaScript files referenced on " + r.url + " : " + srcs.size());
        Set<String> seen = new LinkedHashSet<>();
        for (String s : srcs) {
            String abs = resolve(r.url, s);
            if (abs != null && seen.add(abs)) {
                boolean ext = !host(abs).equalsIgnoreCase(host(r.url));
                c.print((ext ? "[external] " : "[internal] ") + abs);
            }
        }
    }

    public static void endpoints(Tool.Ctx c) throws Exception {
        String url = normUrl(c.target);
        Net.Resp r = get(c, url);
        Set<String> eps = new LinkedHashSet<>();
        String[] rxs = {
                "fetch\\(\\s*[\"']([^\"']{3,})[\"']",
                "axios[.(]\\s*[\"']([^\"']{3,})[\"']",
                "[\"'](/api/[a-zA-Z0-9_/?=&.-]{2,})[\"']",
                "[\"'](/v[0-9]+/[a-zA-Z0-9_/?=&.-]{2,})[\"']",
                "url\\s*:\\s*[\"']([^\"']{3,})[\"']",
                "action=[\"']([^\"']{3,})[\"']"
        };
        for (String rx : rxs) {
            for (String s : regex1(r.body, rx)) {
                if (!s.contains(" ") && !s.startsWith("http")) eps.add(s);
            }
        }
        c.print("Endpoint candidates: " + eps.size());
        int i = 0;
        for (String e : eps) {
            if (i++ > 200) {
                c.print("...");
                break;
            }
            c.print(e);
        }
        if (!eps.isEmpty())
            c.finding("INFO", "0.0", "API endpoints discovered", "Static extraction from HTML/JS.",
                    eps.size() + " candidates on " + r.url, r.url,
                    "Review each endpoint for authorization and input validation.");
    }

    public static void emails(Tool.Ctx c) throws Exception {
        String url = normUrl(c.target);
        Net.Resp r = get(c, url);
        Set<String> found = new LinkedHashSet<>();
        for (String s : regex1(r.body, "mailto:([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})")) found.add(s);
        Matcher m = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(r.body);
        while (m.find() && found.size() < 100) {
            String e = m.group().toLowerCase(Locale.US);
            if (!e.contains(".png") && !e.contains(".jpg") && !e.contains("example.") && !e.contains("sentry"))
                found.add(e);
        }
        c.print("Public emails on " + r.url + ": " + found.size());
        for (String e : found) c.print(e);
        if (!found.isEmpty())
            c.finding("INFO", "0.0", "Public email addresses found",
                    "Emails published on public pages (OSINT value).",
                    String.join(", ", found), r.url,
                    "Consider obfuscating contact addresses to reduce spam/recon value.");
    }

    public static void images(Tool.Ctx c) throws Exception {
        String url = normUrl(c.target);
        Net.Resp r = get(c, url);
        Set<String> out = new LinkedHashSet<>();
        for (String s : regex1(r.body, "<img[^>]+src=[\"']([^\"']+)[\"']")) {
            String abs = resolve(r.url, s);
            if (abs != null) out.add(abs);
        }
        for (String s : regex1(r.body, "property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)"))
            out.add(resolve(r.url, s));
        c.print("Image URLs: " + out.size());
        int i = 0;
        for (String s : out) {
            if (i++ > 150) {
                c.print("...");
                break;
            }
            c.print(s);
        }
    }

    public static void cssJs(Tool.Ctx c) throws Exception {
        String url = normUrl(c.target);
        Net.Resp r = get(c, url);
        Set<String> js = new LinkedHashSet<>(), css = new LinkedHashSet<>();
        for (String s : regex1(r.body, "<script[^>]+src=[\"']([^\"']+)[\"']")) {
            String abs = resolve(r.url, s);
            if (abs != null) js.add(abs);
        }
        for (String s : regex1(r.body, "<link[^>]+href=[\"']([^\"']+\\.css[^\"']*)[\"']")) {
            String abs = resolve(r.url, s);
            if (abs != null) css.add(abs);
        }
        c.print("== JavaScript (" + js.size() + ") ==");
        for (String s : js) c.print(s);
        c.print("\n== CSS (" + css.size() + ") ==");
        for (String s : css) c.print(s);
    }

    public static void links(Tool.Ctx c) throws Exception {
        String url = normUrl(c.target);
        Net.Resp r = get(c, url);
        Set<String> internal = new LinkedHashSet<>(), external = new LinkedHashSet<>();
        for (String s : regex1(r.body, "<a[^>]+href=[\"']([^\"'#]+)[\"']")) {
            String abs = resolve(r.url, s);
            if (abs == null) continue;
            if (host(abs).equalsIgnoreCase(host(r.url))) internal.add(abs);
            else external.add(abs);
        }
        c.print("== Internal links (" + internal.size() + ") ==");
        int i = 0;
        for (String s : internal) {
            if (i++ > 100) {
                c.print("...");
                break;
            }
            c.print(s);
        }
        c.print("\n== External links (" + external.size() + ") ==");
        i = 0;
        for (String s : external) {
            if (i++ > 100) {
                c.print("...");
                break;
            }
            c.print(s);
        }
    }

    public static void metaTags(Tool.Ctx c) throws Exception {
        String url = normUrl(c.target);
        Net.Resp r = get(c, url);
        Matcher title = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                .matcher(r.body);
        c.print("URL: " + r.url);
        if (title.find()) c.print("Title: " + title.group(1).trim());
        Matcher meta = Pattern.compile("<meta[^>]+(?:name|property)=[\"']([^\"']+)[\"'][^>]*content=[\"']([^\"']*)[\"']",
                Pattern.CASE_INSENSITIVE).matcher(r.body);
        int n = 0;
        while (meta.find() && n++ < 60) c.print(meta.group(1) + ": " + meta.group(2));
        for (Map.Entry<String, List<String>> e : r.headers.entrySet()) {
            if (e.getKey() != null && e.getKey().toLowerCase(Locale.US).startsWith("x-"))
                c.print(e.getKey() + ": " + String.join(", ", e.getValue()));
        }
    }

    public static void sourceView(Tool.Ctx c) throws Exception {
        String url = normUrl(c.target);
        c.print("GET " + url);
        Net.Resp r = get(c, url);
        c.print("Status: " + r.code + "  Content-Type: " + r.header("Content-Type")
                + "  Length: " + (r.contentLength < 0 ? r.body.length() : r.contentLength));
        c.print("========== RAW HTML ==========");
        c.print(r.body.length() > 120_000 ? r.body.substring(0, 120_000) + "\n...[truncated]" : r.body);
    }

    public static void pagination(Tool.Ctx c) throws Exception {
        String url = normUrl(c.target);
        Net.Resp r = get(c, url);
        Set<String> found = new LinkedHashSet<>();
        for (String s : regex1(r.body, "<link[^>]+rel=[\"']next[\"'][^>]*href=[\"']([^\"']+)|<a[^>]+href=[\"']([^\"']+)[\"'][^>]*rel=[\"']next[\"']")) {
            String hit = s.isEmpty() ? null : s;
            if (hit != null) found.add(hit);
        }
        for (String s : regex1(r.body, "href=[\"']([^\"']*(?:page|p|pg)[=/]\\d+[^\"']*)[\"']")) found.add(s);
        c.print("Pagination candidates on " + r.url + ": " + found.size());
        int i = 0;
        for (String s : found) {
            if (i++ > 60) break;
            String abs = resolve(r.url, s);
            c.print(abs == null ? s : abs);
        }
    }

    // ---------- SQL injection (passive, error-based) ----------

    static final String[] SQL_ERRORS = {
            "you have an error in your sql syntax", "warning: mysql", "unclosed quotation mark",
            "quoted string not properly terminated", "ora-\\d{5}", "sqlstate\\[", "sqlite3?\\.",
            "system.data.sqlite", "mysql_fetch", "mysqli?", "postgresql.*ERROR", "psql.*ERROR",
            "microsoft ole db provider", "odbc.*driver", "syntax error at or near", "unterminated string"
    };

    public static void sqliCheck(Tool.Ctx c) throws Exception {
        String url = normUrl(c.target);
        URI u = URI.create(url);
        String q = u.getQuery();
        if (q == null || q.isEmpty()) {
            c.print("URL has no query parameters to analyze. Example target:");
            c.print("https://host/item?id=1");
            c.print("Mode: PASSIVE error-based detection (2 extra requests max, safe mode).");
            return;
        }
        String[] kv = q.split("=", 2);
        String pname = kv[0];
        c.print("Analyzing parameter: " + pname + "  (passive error-based, benign probes only)");
        Net.Resp base = get(c, url);
        c.print("Baseline: HTTP " + base.code + ", " + base.body.length() + " bytes");
        String[] probes = {"'", "\""};
        for (String p : probes) {
            c.sleep();
            String probeUrl = url.replaceFirst("(\\?|&)" + Pattern.quote(pname) + "=",
                    "$1" + pname + "=" + java.net.URLEncoder.encode(kv[1] + p, "UTF-8"));
            Net.Resp r = get(c, probeUrl);
            c.print("Probe '" + p + "': HTTP " + r.code + ", " + r.body.length() + " bytes");
            String low = r.body.toLowerCase(Locale.US);
            for (String err : SQL_ERRORS) {
                if (low.matches("(?s).*" + err + ".*")) {
                    Matcher m = Pattern.compile("(?i).{0,80}" + err + ".{0,80}").matcher(low);
                    String ev = m.find() ? m.group().replaceAll("\\s+", " ") : err;
                    c.finding("HIGH", "8.6", "Possible SQL Injection (error-based) on parameter '" + pname + "'",
                            "The server returned a database error after a single-quote probe. Manual verification required.",
                            ev, probeUrl,
                            "Use parameterized queries / prepared statements. Do not concatenate user input into SQL.");
                    break;
                }
            }
            if (c.stop) return;
        }
        c.print("\nDone. No finding above means no obvious DB errors were triggered — absence of errors does NOT prove absence of SQLi.");
    }

    // ---------- XSS (canary reflection) ----------

    public static void xssReflected(Tool.Ctx c) throws Exception {
        String url = normUrl(c.target);
        URI u = URI.create(url);
        if (u.getQuery() == null || u.getQuery().isEmpty()) {
            c.print("URL needs at least one query parameter, e.g. https://host/search?q=test");
            return;
        }
        String[] kv = u.getQuery().split("=", 2);
        String pname = kv[0];
        String canary = "setbd7x9qz";
        c.print("Parameter: " + pname + "  | benign canary, no exploit payloads sent");
        c.sleep();
        Net.Resp r1 = get(c, url);
        boolean echo = r1.body.contains(canary) || r1.body.contains(kv[1]);
        c.print("Baseline reflection: " + (echo ? "yes" : "no"));
        String probe = java.net.URLEncoder.encode("<svg/x>" + canary, "UTF-8");
        String probeUrl = url.replaceFirst("(\\?|&)" + Pattern.quote(pname) + "=.*",
                "$1" + pname + "=" + probe);
        c.sleep();
        Net.Resp r2 = get(c, probeUrl);
        boolean raw = r2.body.contains("<svg/x>");
        boolean enc = r2.body.contains("&lt;svg") || r2.body.contains("%3Csvg");
        c.print("Encoded probe reflected raw (unfiltered): " + raw);
        c.print("Probe reflected HTML-encoded: " + enc);
        if (raw) {
            c.finding("MEDIUM", "5.4", "Unencoded user input reflection on parameter '" + pname + "'",
                    "Markup characters are reflected without output encoding — a strong indicator of reflected XSS. "
                            + "Manual verification in a browser is required before reporting.",
                    "GET " + probeUrl + " → response contains raw <svg/x> marker", probeUrl,
                    "HTML-encode all user input on output; add a CSP; validate input types.");
        } else if (echo) {
            c.print("Input is echoed but markup appears encoded/filtered — likely safe, verify manually.");
        }
    }

    public static void domXss(Tool.Ctx c) throws Exception {
        String url = normUrl(c.target);
        Net.Resp r = get(c, url);
        List<String> files = new ArrayList<>();
        files.add(r.url);
        int added = 0;
        for (String s : regex1(r.body, "<script[^>]+src=[\"']([^\"']+)[\"']")) {
            String abs = resolve(r.url, s);
            if (abs != null && host(abs).equalsIgnoreCase(host(r.url)) && added++ < 5) files.add(abs);
        }
        String[] sources = {"location\\.(href|search|hash|pathname)", "document\\.(URL|referrer|documentURI)",
                "window\\.name", "postMessage\\("};
        String[] sinks = {"\\.innerHTML\\s*=", "\\.outerHTML\\s*=", "document\\.write(ln)?\\(", "eval\\(",
                "new Function\\(", "insertAdjacentHTML\\(", "\\.html\\("};
        for (String f : files) {
            Net.Resp fr = f.equals(r.url) ? r : get(c, f);
            List<String> srcHits = new ArrayList<>(), sinkHits = new ArrayList<>();
            for (String s : sources) {
                Matcher m = Pattern.compile(s).matcher(fr.body);
                if (m.find()) srcHits.add(m.group().replaceAll("\\\\s+", " "));
            }
            for (String s : sinks) {
                Matcher m = Pattern.compile(s, Pattern.CASE_INSENSITIVE).matcher(fr.body);
                if (m.find()) sinkHits.add(m.group());
            }
            c.print("\n" + fr.url);
            c.print("  sources: " + (srcHits.isEmpty() ? "-" : String.join(", ", srcHits)));
            c.print("  sinks:   " + (sinkHits.isEmpty() ? "-" : String.join(", ", sinkHits)));
            if (!srcHits.isEmpty() && !sinkHits.isEmpty())
                c.finding("MEDIUM", "4.8", "Possible DOM XSS flow (static analysis hint)",
                        "A DOM source and a dangerous sink appear in the same script file. Manual data-flow review required.",
                        fr.url + " — sources: " + srcHits + " | sinks: " + sinkHits, fr.url,
                        "Avoid assigning user-controlled data to innerHTML/eval; use textContent and safe templating.");
        }
        c.print("\nStatic analysis complete — this is a hint, not a confirmed vulnerability.");
    }

    // ---------- TLS ----------

    public static void tlsInfo(Tool.Ctx c) throws Exception {
        String t = normUrl(c.target).replaceFirst("^https?://", "");
        String host = t.split("[:/]")[0];
        int port = t.contains(":") ? Integer.parseInt(t.split(":")[1].replaceAll("\\D", "")) : 443;
        c.print("Connecting to " + host + ":" + port + " ...");
        javax.net.ssl.SSLSocketFactory f = (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
        try (SSLSocket s = (SSLSocket) f.createSocket(host, port)) {
            s.startHandshake();
            Certificate[] chain = s.getSession().getPeerCertificates();
            X509Certificate x = (X509Certificate) chain[0];
            c.print("Subject:   " + x.getSubjectX500Principal().getName());
            c.print("Issuer:    " + x.getIssuerX500Principal().getName());
            c.print("Not before: " + fmtDate(x.getNotBefore().getTime()));
            c.print("Not after:  " + fmtDate(x.getNotAfter().getTime()));
            long days = (x.getNotAfter().getTime() - System.currentTimeMillis()) / 86_400_000L;
            c.print("Valid days remaining: " + days);
            c.print("Signature alg: " + x.getSigAlgName());
            c.print("Serial: " + x.getSerialNumber().toString(16));
            c.print("Chain length: " + chain.length);
            try {
                Collection<List<?>> sans = x.getSubjectAlternativeNames();
                if (sans != null) {
                    List<String> names = new ArrayList<>();
                    for (List<?> san : sans) if (san.size() > 1) names.add(String.valueOf(san.get(1)));
                    c.print("SANs: " + String.join(", ", names));
                }
            } catch (Exception ignored) { }
            if (days < 15)
                c.finding("HIGH", "7.5", "TLS certificate expires in " + days + " days",
                        "Certificate expiry will cause outages and browser warnings.",
                        "notAfter=" + fmtDate(x.getNotAfter().getTime()), "https://" + host,
                        "Renew the certificate (e.g. via Let's Encrypt automation).");
            else if (days < 30)
                c.finding("MEDIUM", "4.0", "TLS certificate expires soon (" + days + " days)",
                        "Certificate is close to expiry.", "notAfter=" + fmtDate(x.getNotAfter().getTime()),
                        "https://" + host, "Schedule renewal now.");
        } catch (javax.net.ssl.SSLHandshakeException e) {
            c.print("Handshake failed: " + e.getMessage());
            c.finding("HIGH", "7.4", "TLS certificate not trusted / handshake failure",
                    "Default trust store rejected the certificate chain.",
                    e.getMessage(), "https://" + host,
                    "Deploy a complete, valid certificate chain (including intermediates).");
        }
    }

    public static void tlsScan(Tool.Ctx c) throws Exception {
        String t = normUrl(c.target).replaceFirst("^https?://", "");
        String host = t.split("[:/]")[0];
        int port = t.contains(":") ? Integer.parseInt(t.split(":")[1].replaceAll("\\D", "")) : 443;
        c.print("Probing protocol support on " + host + ":" + port + " (authorized target assumed)");
        String[][] protos = {
                {"TLSv1.3", "ok"}, {"TLSv1.2", "ok"}, {"TLSv1.1", "obsolete"}, {"TLSv1", "obsolete"}, {"SSLv3", "dead"}
        };
        for (String[] p : protos) {
            if (c.stop) return;
            String proto = p[0], cls = p[1];
            try {
                SSLContext ctx = SSLContext.getInstance(proto);
                ctx.init(null, null, null);
                SSLSocket s = (SSLSocket) ctx.getSocketFactory().createSocket();
                s.connect(new java.net.InetSocketAddress(host, port), 6000);
                s.setEnabledProtocols(new String[]{proto});
                s.setSoTimeout(6000);
                s.startHandshake();
                String cipher = s.getSession().getCipherSuite();
                c.print("[supported]  " + proto + "  cipher: " + cipher);
                if ("obsolete".equals(cls))
                    c.finding("MEDIUM", "5.0", "Obsolete TLS protocol enabled: " + proto,
                            "Legacy protocol versions are vulnerable to known attacks (POODLE/BEAST family) and fail compliance scans.",
                            proto + " handshake succeeded", "https://" + host + ":" + port,
                            "Disable " + proto + "; serve TLSv1.2+ only.");
                if ("dead".equals(cls))
                    c.finding("HIGH", "8.2", "Deprecated protocol enabled: " + proto,
                            proto + " is cryptographically broken and must not be offered.",
                            proto + " handshake succeeded", "https://" + host + ":" + port,
                            "Immediately disable " + proto + ".");
                s.close();
            } catch (Exception e) {
                c.print("[rejected ]  " + proto);
            }
            c.sleep();
        }
        c.print("\nNote: Android's TLS stack cannot always emulate old client versions; 'rejected' results may be client-side limitations.");
    }
}
