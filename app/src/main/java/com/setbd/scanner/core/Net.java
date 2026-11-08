package com.setbd.scanner.core;

import com.setbd.scanner.data.M;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Shared HTTP engine for every network module. All requests flow through here so
 * user agent, timeouts and safety checks are applied consistently.
 */
public class Net {

    public static final String DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Mobile Safari/537.36 SETBDScanner/1.0";

    public static class Resp {
        public int code;
        public String message = "";
        public String url = "";
        public Map<String, List<String>> headers = new HashMap<>();
        public String body = "";
        public long timeMs;
        public long contentLength = -1;

        public String header(String name) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)
                        && e.getValue() != null && !e.getValue().isEmpty())
                    return e.getValue().get(0);
            }
            return null;
        }
    }

    private static OkHttpClient shared;

    public static synchronized OkHttpClient client() {
        return client(20);
    }

    public static synchronized OkHttpClient client(int timeoutSec) {
        if (shared == null) {
            shared = new OkHttpClient.Builder()
                    .connectTimeout(timeoutSec, TimeUnit.SECONDS)
                    .readTimeout(timeoutSec, TimeUnit.SECONDS)
                    .writeTimeout(timeoutSec, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build();
        }
        return shared;
    }

    public static Resp request(String method, String url, Map<String, String> headers,
                               String body, String userAgent) throws IOException {
        Request.Builder b = new Request.Builder().url(url);
        String ua = userAgent == null || userAgent.trim().isEmpty() ? DEFAULT_UA : userAgent;
        b.header("User-Agent", ua);
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() != null && !e.getKey().trim().isEmpty() && !"User-Agent".equalsIgnoreCase(e.getKey()))
                    b.header(e.getKey().trim(), e.getValue() == null ? "" : e.getValue());
            }
        }
        if ("GET".equalsIgnoreCase(method)) {
            b.get();
        } else if ("HEAD".equalsIgnoreCase(method)) {
            b.head();
        } else {
            String mt = "text/plain";
            if (body != null && body.trim().startsWith("{")) mt = "application/json";
            String safeBody = body == null ? "" : body;
            b.method(method.toUpperCase(Locale.US),
                    RequestBody.create(safeBody, MediaType.parse(mt)));
        }
        long t0 = System.currentTimeMillis();
        Resp r = new Resp();
        try (Response resp = client(20).newCall(b.build()).execute()) {
            r.code = resp.code();
            r.message = resp.message() == null ? "" : resp.message();
            r.url = resp.request().url().toString();
            r.headers = resp.headers().toMultimap();
            r.contentLength = resp.body() == null ? -1 : resp.body().contentLength();
            ResponseBody rb = resp.body();
            if (rb != null) {
                String ct = resp.header("Content-Type", "");
                if (ct.contains("image") || ct.contains("octet-stream") || ct.contains("font")) {
                    r.body = "[" + ct + " binary body " + rb.contentLength() + " bytes]";
                } else {
                    String s = rb.string();
                    r.body = s.length() > 400_000 ? s.substring(0, 400_000) + "\n...[truncated]" : s;
                }
            }
        }
        r.timeMs = System.currentTimeMillis() - t0;
        return r;
    }

    public static Resp get(String url) throws IOException {
        return request("GET", url, null, null, DEFAULT_UA);
    }

    public static Resp get(String url, String ua) throws IOException {
        return request("GET", url, null, null, ua);
    }

    /** Basic host sanity check. Returns error text or null when acceptable. */
    public static String checkTarget(String url, boolean allowPrivate) {
        try {
            java.net.URI u = java.net.URI.create(url);
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.US);
            String host = u.getHost();
            if (!scheme.equals("http") && !scheme.equals("https"))
                return "URL must start with http:// or https://";
            if (host == null || host.trim().isEmpty())
                return "URL has no host";
            if (!allowPrivate && isPrivateHost(host))
                return "Target resolves to a private/loopback address. Enable 'Allow local targets' to scan your own infrastructure.";
            return null;
        } catch (Exception e) {
            return "Invalid URL: " + e.getMessage();
        }
    }

    public static boolean isPrivateHost(String host) {
        if (host == null) return true;
        String h = host.toLowerCase(Locale.US);
        if (h.equals("localhost") || h.endsWith(".local") || h.equals("127.0.0.1") || h.equals("::1"))
            return true;
        if (h.matches("^10\\..*") || h.matches("^192\\.168\\..*") || h.matches("^172\\.(1[6-9]|2\\d|3[01])\\..*")
                || h.startsWith("169.254.") || h.startsWith("0."))
            return true;
        try {
            java.net.InetAddress[] all = java.net.InetAddress.getAllByName(h);
            for (java.net.InetAddress a : all) {
                if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress())
                    return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    public static String hostOf(String url) {
        try {
            return java.net.URI.create(url).getHost();
        } catch (Exception e) {
            return url;
        }
    }

    public static String rootOf(String url) {
        try {
            java.net.URI u = java.net.URI.create(url);
            int port = u.getPort();
            if (port == 80 || port == 443 || port == -1) return u.getScheme() + "://" + u.getHost();
            return u.getScheme() + "://" + u.getHost() + ":" + port;
        } catch (Exception e) {
            return url;
        }
    }

    /** Trailing summary line for a scan about all collected findings. */
    public static String summarize(List<M.Finding> findings) {
        int h = 0, m = 0, l = 0, i = 0;
        for (M.Finding f : findings) {
            if ("HIGH".equals(f.severity)) h++;
            else if ("MEDIUM".equals(f.severity)) m++;
            else if ("LOW".equals(f.severity)) l++;
            else i++;
        }
        return h + " high, " + m + " medium, " + l + " low, " + i + " info";
    }
}
