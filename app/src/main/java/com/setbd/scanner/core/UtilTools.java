package com.setbd.scanner.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.CRC32;

/** Offline utility tools: crypto, encoding, JSON, regex, diff, wordlists, timestamps. */
public class UtilTools {

    static String text(Tool.Ctx c) {
        return (c.param == null || c.param.isEmpty()) ? c.target : c.param;
    }

    // ---------- Hashing ----------

    public static void hash(Tool.Ctx c) throws Exception {
        String in = text(c);
        if (in.isEmpty()) {
            c.print("Enter the text to hash in the input field.");
            return;
        }
        byte[] b = in.getBytes(StandardCharsets.UTF_8);
        c.print("Input: " + (in.length() > 120 ? in.substring(0, 120) + "... (" + in.length() + " chars)" : in));
        c.print("MD5:     " + hex(MessageDigest.getInstance("MD5").digest(b)));
        c.print("SHA-1:   " + hex(MessageDigest.getInstance("SHA-1").digest(b)));
        c.print("SHA-256: " + hex(MessageDigest.getInstance("SHA-256").digest(b)));
        c.print("SHA-512: " + hex(MessageDigest.getInstance("SHA-512").digest(b)));
        CRC32 crc = new CRC32();
        crc.update(b);
        c.print("CRC32:   " + String.format("%08x", crc.getValue()));
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    public static void hashId(Tool.Ctx c) throws Exception {
        String h = text(c).trim();
        c.print("Analyzing: " + (h.length() > 80 ? h.substring(0, 80) + "..." : h));
        String t = h.toLowerCase(Locale.US);
        List<String> hits = new ArrayList<>();
        if (t.matches("^[a-f0-9]{32}$")) {
            hits.add("MD5");
            hits.add("NTLM");
            hits.add("MD4 (rare)");
        }
        if (t.matches("^[a-f0-9]{40}$")) hits.add("SHA-1");
        if (t.matches("^[a-f0-9]{64}$")) {
            hits.add("SHA-256");
            hits.add("Keccak-256 / SHA3-256 variant");
        }
        if (t.matches("^[a-f0-9]{128}$")) hits.add("SHA-512");
        if (t.matches("^\\$2[aby]\\$.{56}$")) hits.add("bcrypt");
        if (t.startsWith("$argon2")) hits.add("Argon2");
        if (t.startsWith("$6$")) hits.add("SHA-512crypt (Unix)");
        if (t.startsWith("$5$")) hits.add("SHA-256crypt (Unix)");
        if (t.matches("^\\{ssha\\}.*")) hits.add("SSHA (LDAP)");
        if (t.matches("^[a-f0-9]{56}$")) hits.add("SHA-224 / SHA3-224");
        if (t.matches("^\\$apr1\\$.+")) hits.add("MD5crypt (apr1, Apache)");
        if (t.matches("^\\$p$8.+")) hits.add("Wordpress phpass");
        if (hits.isEmpty()) {
            c.print("No confident match from format/length heuristics.");
            c.print("Length: " + h.length() + " | charset: "
                    + (t.matches("^[a-f0-9]+$") ? "hex" : t.matches("^[a-z0-9+/=]+$") ? "base64-ish" : "mixed"));
        } else {
            for (String s : hits) c.print("→ " + s);
        }
        c.print("\nHeuristic identification only — verify with hashcat -m autocone or hash-identifier in Termux.");
    }

    // ---------- Encoding ----------

    public static void b64(Tool.Ctx c) throws Exception {
        String in = text(c);
        boolean decode = (c.target != null && c.target.toLowerCase(Locale.US).contains("decode"))
                || in.endsWith("==") && in.length() % 4 == 0 && !in.matches(".*[\\u0080-\\uffff].*");
        c.print("Mode: " + (decode ? "DECODE (auto)" : "ENCODE (auto)"));
        if (decode) {
            byte[] out;
            try {
                out = android.util.Base64.decode(in.trim(), android.util.Base64.DEFAULT);
            } catch (IllegalArgumentException e) {
                out = android.util.Base64.decode(in.trim(), android.util.Base64.URL_SAFE);
            }
            String s = new String(out, StandardCharsets.UTF_8);
            c.print(s.length() > 20000 ? s.substring(0, 20000) + "...[truncated]" : s);
        } else {
            c.print(android.util.Base64.encodeToString(in.getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP));
            c.print("\nURL-safe variant:");
            c.print(android.util.Base64.encodeToString(in.getBytes(StandardCharsets.UTF_8),
                    android.util.Base64.URL_SAFE + android.util.Base64.NO_WRAP));
        }
    }

    public static void urlCodec(Tool.Ctx c) throws Exception {
        String in = text(c);
        boolean decode = in.contains("%") || in.contains("+");
        c.print("Mode: " + (decode ? "DECODE (auto)" : "ENCODE (auto)"));
        if (decode) c.print(java.net.URLDecoder.decode(in, "UTF-8"));
        else c.print(java.net.URLEncoder.encode(in, "UTF-8"));
    }

    // ---------- JSON / HTML ----------

    public static void jsonFmt(Tool.Ctx c) throws Exception {
        String in = text(c).trim();
        try {
            if (in.startsWith("[")) {
                c.print(new JSONArray(in).toString(2));
            } else {
                c.print(new JSONObject(in).toString(2));
            }
        } catch (Exception e) {
            c.print("Invalid JSON: " + e.getMessage());
        }
    }

    public static void htmlFmt(Tool.Ctx c) throws Exception {
        String in = text(c);
        String out = in
                .replaceAll("(?i)\\s*<(div|p|br|ul|ol|li|table|thead|tbody|tr|td|th|h[1-6]|section|header|footer|nav|form|script|style|meta|link|title|html|head|body)([\\s>])", "\n<$1$2")
                .replaceAll("(?i)(</(?:div|p|ul|ol|li|table|tr|td|th|h[1-6]|section|header|footer|nav|form|script|style|title|html|head|body)>)", "$1\n")
                .replaceAll("\n{3,}", "\n\n");
        c.print(out.length() > 100_000 ? out.substring(0, 100_000) + "...[truncated]" : out);
    }

    // ---------- Regex tester ----------

    public static void regex(Tool.Ctx c) throws Exception {
        String pattern = c.param == null ? c.target : c.param;
        String text = c.param == null ? "" : c.target;
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(text);
            int found = 0;
            while (m.find() && found < 100) {
                found++;
                c.print("Match " + found + "  [" + m.start() + ".." + m.end() + "]  \"" + m.group() + "\"");
                for (int g = 1; g <= m.groupCount(); g++)
                    if (m.group(g) != null) c.print("   group " + g + ": \"" + m.group(g) + "\"");
            }
            c.print("\nTotal matches: " + found + (found >= 100 ? " (capped)" : ""));
        } catch (Exception e) {
            c.print("Invalid regex: " + e.getMessage());
        }
    }

    // ---------- JWT ----------

    public static void jwt(Tool.Ctx c) throws Exception {
        String token = text(c).trim().replaceAll("^Bearer ", "");
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            c.print("Not a JWT (expected header.payload.signature).");
            return;
        }
        String header = new String(android.util.Base64.decode(parts[0], android.util.Base64.URL_SAFE), StandardCharsets.UTF_8);
        String payload = new String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE), StandardCharsets.UTF_8);
        c.print("== HEADER ==");
        c.print(new JSONObject(header).toString(2));
        c.print("\n== PAYLOAD ==");
        JSONObject pl = new JSONObject(payload);
        c.print(pl.toString(2));
        c.print("\n== SIGNATURE ==");
        c.print(parts.length > 2 ? parts[2] : "(missing — alg 'none' style token)");
        String alg = new JSONObject(header).optString("alg", "?");
        c.print("\nAlgorithm: " + alg);
        if (alg.equalsIgnoreCase("none"))
            c.finding("HIGH", "8.1", "JWT with alg=none",
                    "The token declares 'none' — unsigned tokens must be rejected by the server.",
                    header, c.target, "Reject alg=none on the server; pin expected algorithms.");
        long now = System.currentTimeMillis() / 1000;
        if (pl.has("exp")) {
            long exp = pl.getLong("exp");
            c.print("exp: " + exp + " → " + (exp > now ? "valid for " + (exp - now) / 60 + " more minutes" : "EXPIRED " + (now - exp) / 60 + " minutes ago"));
            if (exp < now)
                c.finding("MEDIUM", "4.0", "Expired JWT", "The token's exp claim is in the past — verify the server rejects it.",
                        "exp=" + exp, c.target, "Ensure the API rejects expired tokens.");
        }
        if (pl.has("iat")) c.print("iat: " + pl.getLong("iat"));
        c.print("\nNote: decoded locally — no network request was made.");
    }

    // ---------- Diff ----------

    public static void diff(Tool.Ctx c) throws Exception {
        List<String> a = Arrays.asList((c.target == null ? "" : c.target).split("\n"));
        List<String> b = Arrays.asList((c.param == null ? "" : c.param).split("\n"));
        int n = Math.min(a.size(), 400), m = Math.min(b.size(), 400);
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--)
            for (int j = m - 1; j >= 0; j--)
                dp[i][j] = a.get(i).equals(b.get(j)) ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
        int i = 0, j = 0, changed = 0;
        while (i < n && j < m) {
            if (a.get(i).equals(b.get(j))) {
                c.print("  " + a.get(i));
                i++; j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                c.print("- " + a.get(i));
                changed++;
                i++;
            } else {
                c.print("+ " + b.get(j));
                changed++;
                j++;
            }
        }
        while (i < n) {
            c.print("- " + a.get(i++));
            changed++;
        }
        while (j < m) {
            c.print("+ " + b.get(j++));
            changed++;
        }
        c.print("\nChanged/removed/added lines: " + changed);
    }

    // ---------- Timestamps ----------

    public static void timestamp(Tool.Ctx c) throws Exception {
        String in = text(c).trim();
        if (in.matches("\\d{10,13}")) {
            long ms = in.length() == 13 ? Long.parseLong(in) : Long.parseLong(in) * 1000;
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US);
            f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            c.print("Epoch:     " + in);
            c.print("UTC:       " + f.format(new java.util.Date(ms)));
            f.setTimeZone(java.util.TimeZone.getDefault());
            c.print("Local:     " + f.format(new java.util.Date(ms)));
        } else {
            try {
                java.time.Instant t = java.time.Instant.parse(in.contains("T") ? in : in.replace(" ", "T") + "Z");
                c.print("Epoch ms:  " + t.toEpochMilli());
                c.print("Epoch sec: " + t.getEpochSecond());
            } catch (Exception e) {
                c.print("Provide a unix timestamp (seconds or millis) or ISO-8601 like 2026-01-31T10:00:00Z");
            }
        }
        c.print("Now: " + System.currentTimeMillis() / 1000);
    }

    // ---------- Wordlist generator ----------

    public static void wordlist(Tool.Ctx c) throws Exception {
        String[] words = text(c).toLowerCase(Locale.US).split("[,\\s]+");
        if (words.length == 0 || words[0].isEmpty()) {
            c.print("Provide base words, e.g.: admin, login, backup");
            return;
        }
        Set<String> out = new TreeSet<>();
        for (String w : words) {
            if (w.isEmpty()) continue;
            out.add(w);
            out.add(w.toUpperCase(Locale.US));
            out.add(Character.toUpperCase(w.charAt(0)) + w.substring(1));
            for (int y = 2020; y <= 2026; y++) {
                out.add(w + y);
                out.add(w + y + "!");
                out.add(Character.toUpperCase(w.charAt(0)) + w.substring(1) + y);
            }
            for (int d = 1; d <= 12; d++) out.add(w + d);
            out.add(w + "_backup");
            out.add(w + "-old");
            out.add(w + "123");
            out.add(w + "!");
        }
        c.print("Generated entries: " + out.size());
        int i = 0;
        for (String w : out) {
            if (i++ >= 400) {
                c.print("... full list kept in saved output (use Export)");
                break;
            }
            c.print(w);
        }
        if (out.size() <= 400) {
            StringBuilder sb = new StringBuilder();
            for (String w : out) sb.append(w).append('\n');
            c.print("\n----FULL----\n" + sb);
        }
    }

    // ---------- Payload / marker reference (benign probes) ----------

    public static void payloads(Tool.Ctx c) throws Exception {
        String cat = text(c).toLowerCase(Locale.US).trim();
        c.print("Authorized-use reference markers. These are detection canaries — not weaponized exploits.");
        c.print("Only use against targets you are authorized to test.\n");
        if (cat.contains("sql")) {
            c.print("== SQLi error-detection probes (single request, passive) ==");
            c.print("'");
            c.print("\"");
            c.print("' OR '1'='1");
            c.print("1' ORDER BY 1--");
            c.print("1 AND SLEEP(0)  (time-based check with 0 delay = harmless)");
        } else if (cat.contains("xss")) {
            c.print("== XSS reflection canaries (non-executing) ==");
            c.print("setbd7x9qz");
            c.print("<svg/x>setbd  (checks output encoding; no event handler)");
            c.print("\"><setbd>");
            c.print("javascript:void(0)  (filter check only)");
        } else if (cat.contains("trav") || cat.contains("path")) {
            c.print("== Path traversal probes (read of /etc/hosts style canaries) ==");
            c.print("../../etc/hosts");
            c.print("..%2f..%2fetc%2fhosts");
            c.print("....//....//etc/hosts");
        } else if (cat.contains("cmd")) {
            c.print("== Command injection canaries (harmless echo markers) ==");
            c.print("; echo setbdcanary");
            c.print("| echo setbdcanary");
            c.print("`echo setbdcanary`");
            c.print("$(echo setbdcanary)");
        } else {
            c.print("Categories: sql | xss | traversal | cmd");
            c.print("Example: type 'xss' in the input field.");
        }
    }
}
