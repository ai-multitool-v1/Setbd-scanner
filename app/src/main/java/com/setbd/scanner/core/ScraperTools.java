package com.setbd.scanner.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Polite website crawler: respects robots.txt, same-host scope, rate limits,
 * pause/stop controls and page caps. Never bypasses authentication.
 */
public class ScraperTools {

    static final Pattern HREF = Pattern.compile("<a[^>]+href=[\"']([^\"'#]+)[\"']", Pattern.CASE_INSENSITIVE);
    static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    static final Pattern IMG = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    static final Pattern JS = Pattern.compile("<script[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    static final Pattern CSS = Pattern.compile("<link[^>]+href=[\"']([^\"']+\\.css[^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    static final Pattern PAG = Pattern.compile("href=[\"']([^\"']*(?:page|pg)[=/]\\d+[^\"']*)[\"']", Pattern.CASE_INSENSITIVE);

    static String host(String url) {
        try {
            return new java.net.URL(url).getHost();
        } catch (Exception e) {
            return "";
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

    /** Fetch robots.txt and return Disallow prefixes for User-agent: * */
    static Set<String> robotsDisallow(String root, Tool.Ctx c) {
        Set<String> dis = new HashSet<>();
        try {
            Net.Resp r = Net.get(root + "/robots.txt", c.userAgent);
            if (r.code == 200) {
                boolean star = false;
                for (String line : r.body.split("\n")) {
                    String l = line.trim().toLowerCase(Locale.US);
                    if (l.startsWith("user-agent:")) star = l.contains("*");
                    else if (star && l.startsWith("disallow:") && l.length() > 9) {
                        String p = line.substring(line.indexOf(':') + 1).trim();
                        if (!p.isEmpty()) dis.add(p);
                    }
                }
            }
            c.print("robots.txt: " + r.code + ", " + dis.size() + " Disallow rules for * (honored)");
        } catch (Exception e) {
            c.print("robots.txt: not reachable (" + e.getMessage() + ")");
        }
        return dis;
    }

    static boolean disallowed(String url, Set<String> dis) {
        try {
            java.net.URL u = new java.net.URL(url);
            for (String p : dis) if (u.getPath().startsWith(p)) return true;
        } catch (Exception ignored) { }
        return false;
    }

    public static void crawl(Tool.Ctx c) throws Exception {
        String start = WebTools.normUrl(c.target);
        int depth = 2, maxPages = 20;
        boolean includeSub = c.param != null && c.param.toLowerCase(Locale.US).contains("sub");
        if (c.param != null && c.param.matches(".*\\d+\\s*,\\s*\\d+.*")) {
            try {
                String[] pm = c.param.split(",");
                depth = Integer.parseInt(pm[0].trim().replaceAll("\\D", ""));
                maxPages = Integer.parseInt(pm[1].trim().replaceAll("\\D", ""));
            } catch (Exception ignored) { }
        } else if (c.param != null && c.param.trim().matches("\\d+")) {
            maxPages = Integer.parseInt(c.param.trim());
        }
        depth = Math.max(1, Math.min(depth, 5));
        maxPages = Math.max(1, Math.min(maxPages, 100));

        String root = Net.rootOf(start);
        String baseHost = host(start);
        String baseDomain = baseHost.replaceFirst("^www\\.", "");

        c.print("Crawl: " + start);
        c.print("Depth: " + depth + "  Max pages: " + maxPages + "  Scope: "
                + (includeSub ? "*." + baseDomain : baseHost) + "  Rate: " + c.rateMs + "ms");
        Set<String> dis = robotsDisallow(root, c);

        Set<String> visited = new HashSet<>();
        Deque<String[]> queue = new ArrayDeque<>();
        queue.add(new String[]{start, "0"});
        Set<String> emails = new LinkedHashSet<>();
        Set<String> js = new LinkedHashSet<>(), css = new LinkedHashSet<>(), images = new LinkedHashSet<>();
        Set<String> external = new LinkedHashSet<>(), pagination = new LinkedHashSet<>();
        int fetched = 0, failed = 0, queued = 1;

        while (!queue.isEmpty() && fetched < maxPages && !c.stop) {
            c.pauseGate();
            if (c.stop) break;
            String[] item = queue.poll();
            String url = item[0];
            int d = Integer.parseInt(item[1]);
            if (!visited.add(url)) continue;
            if (disallowed(url, dis)) {
                c.print("SKIP  (robots) " + url);
                continue;
            }
            try {
                c.sleep();
                Net.Resp r = Net.get(url, c.userAgent);
                fetched++;
                String title = "";
                Matcher t = TITLE.matcher(r.body);
                if (t.find()) title = t.group(1).trim();
                c.print(String.format(Locale.US, "[%d/%d] d%d HTTP %d %5dms %s %s",
                        fetched, maxPages, d, r.code, r.timeMs, url, title.isEmpty() ? "" : "— " + title));
                if (r.code >= 400) failed++;

                Matcher m = HREF.matcher(r.body);
                while (m.find() && queued < 600) {
                    String abs = resolve(url, m.group(1));
                    if (abs == null) continue;
                    String h = host(abs);
                    if (h.equalsIgnoreCase(baseHost) || (includeSub && h.endsWith("." + baseDomain))) {
                        if (abs.split("[?#]")[0].matches(".*\\.(jpg|jpeg|png|gif|pdf|zip|mp4|apk)(\\?.*)?$")) continue;
                        if (!visited.contains(abs)) {
                            queue.add(new String[]{abs.split("#")[0], String.valueOf(d + 1)});
                            queued++;
                        }
                    } else {
                        external.add(abs);
                    }
                }
                for (Matcher mm = EMAIL.matcher(r.body); mm.find() && emails.size() < 100; ) {
                    String e = mm.group().toLowerCase(Locale.US);
                    if (!e.contains(".png") && !e.contains(".jpg") && !e.contains("example.") && !e.contains("sentry"))
                        emails.add(e);
                }
                for (Matcher mm = JS.matcher(r.body); mm.find() && js.size() < 200; ) {
                    String a = resolve(url, mm.group(1));
                    if (a != null) js.add(a);
                }
                for (Matcher mm = CSS.matcher(r.body); mm.find() && css.size() < 200; ) {
                    String a = resolve(url, mm.group(1));
                    if (a != null) css.add(a);
                }
                for (Matcher mm = IMG.matcher(r.body); mm.find() && images.size() < 200; ) {
                    String a = resolve(url, mm.group(1));
                    if (a != null) images.add(a);
                }
                for (Matcher mm = PAG.matcher(r.body); mm.find() && pagination.size() < 50; ) {
                    String a = resolve(url, mm.group(1));
                    if (a != null) pagination.add(a);
                }
            } catch (Exception e) {
                failed++;
                c.print("FAIL  " + url + "  (" + e.getMessage() + ")");
            }
        }

        c.print("\n========== CRAWL SUMMARY ==========");
        c.print("Pages fetched: " + fetched + "   Failed: " + failed + "   Queued (not visited): " + Math.max(0, queued - fetched));
        c.print("Unique internal URLs seen: " + visited.size());
        c.print("Emails: " + emails.size());
        c.print("JS files: " + js.size() + "   CSS files: " + css.size() + "   Images: " + images.size());
        c.print("External links: " + external.size() + "   Pagination links: " + pagination.size());
        if (!emails.isEmpty()) {
            c.print("\n-- Emails --");
            int i = 0;
            for (String e : emails) {
                if (i++ >= 50) break;
                c.print(e);
            }
            c.finding("INFO", "0.0", "Email addresses collected by crawler",
                    emails.size() + " public emails found while crawling " + baseHost,
                    String.join(", ", emails), start, "Obfuscate public contact emails if unwanted.");
        }
        if (!js.isEmpty()) {
            c.print("\n-- JS files --");
            int i = 0;
            for (String e : js) {
                if (i++ >= 30) break;
                c.print(e);
            }
        }
        if (!pagination.isEmpty()) {
            c.print("\n-- Pagination links --");
            int i = 0;
            for (String e : pagination) {
                if (i++ >= 20) break;
                c.print(e);
            }
        }
        if (failed > 2)
            c.finding("LOW", "2.0", "Multiple crawling errors (" + failed + " pages)",
                    "Repeated 4xx/5xx during crawl may indicate broken links or WAF rate limiting.",
                    failed + " failed requests", start, "Review server logs; fix broken links if unintended.");
        c.print("\nCrawl finished (rate limited, robots.txt honored).");
    }
}
