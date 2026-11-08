package com.setbd.scanner.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONObject;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** Network recon modules (DNS, WHOIS, ports, ping, subnet, discovery). */
public class NetTools {

    static void exec(Tool.Ctx c, String[] argv) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(argv).redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                c.print(line);
                sb.append(line).append('\n');
                if (c.stop) {
                    p.destroy();
                    break;
                }
            }
        }
        p.waitFor(30, TimeUnit.SECONDS);
    }

    // ---------- DNS ----------

    public static void dns(Tool.Ctx c) throws Exception {
        String host = c.target.trim().replaceFirst("^https?://", "").split("[:/]")[0];
        c.print("Resolving " + host + " ...");
        InetAddress[] all = InetAddress.getAllByName(host);
        for (InetAddress a : all) {
            c.print("A/AAAA:    " + a.getHostAddress());
            c.print("Hostname:  " + a.getCanonicalHostName());
            c.print("Reachable(3s): " + a.isReachable(3000));
            c.print("");
        }
        try {
            c.print("Reverse of first address: " + InetAddress.getByName(all[0].getHostAddress()).getCanonicalHostName());
        } catch (Exception ignored) { }
        c.print("Note: Android's resolver returns A/AAAA records. Full record types (MX/TXT/NS) need a DNS library or Termux dig.");
    }

    // ---------- WHOIS ----------

    static String whoisQuery(String server, String query) throws IOException {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(server, 43), 10_000);
            s.setSoTimeout(10_000);
            OutputStream os = s.getOutputStream();
            os.write((query + "\r\n").getBytes());
            os.flush();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                    if (sb.length() > 60_000) break;
                }
            }
            return sb.toString();
        }
    }

    public static void whois(Tool.Ctx c) throws Exception {
        String domain = c.target.trim().replaceFirst("^https?://", "").split("[:/]")[0];
        c.print("IANA lookup for " + domain + " ...");
        String first = whoisQuery("whois.iana.org", domain);
        String refer = null;
        for (String line : first.split("\n")) {
            String l = line.trim().toLowerCase(Locale.US);
            if (l.startsWith("refer:") || l.startsWith("whois:")) {
                String v = line.substring(line.indexOf(':') + 1).trim();
                if (!v.isEmpty()) refer = v;
            }
        }
        if (refer == null) {
            c.print(first);
            c.print("(registrar referral not found — IANA data shown)");
            return;
        }
        c.print("Registrar WHOIS server: " + refer);
        c.print("----------------------------------------");
        String full = whoisQuery(refer, domain);
        c.print(full.length() > 8000 ? full.substring(0, 8000) + "\n...[truncated]" : full);
    }

    // ---------- Port scanner ----------

    static final String DEFAULT_PORTS = "21,22,23,25,53,80,110,143,443,445,873,1433,3306,3389,5432,5900,6379,8080,8443,9200,27017";

    public static void ports(Tool.Ctx c) throws Exception {
        String host = c.target.trim().replaceFirst("^https?://", "").split("[:/]")[0];
        String spec = (c.param == null || c.param.trim().isEmpty()) ? DEFAULT_PORTS : c.param.trim();
        List<Integer> ports = new ArrayList<>();
        for (String part : spec.split(",")) {
            part = part.trim();
            if (part.contains("-")) {
                String[] ab = part.split("-");
                int a = Integer.parseInt(ab[0].trim()), b = Integer.parseInt(ab[1].trim());
                if (b - a > 2000) throw new IllegalArgumentException("Range too large (max 2000 ports per scan).");
                for (int i = a; i <= b; i++) ports.add(i);
            } else if (!part.isEmpty()) ports.add(Integer.parseInt(part));
        }
        if (ports.size() > 2000) ports = ports.subList(0, 2000);
        final int totalPorts = ports.size();
        c.print("TCP connect scan on " + host + " — " + totalPorts + " ports, 1.5s timeout, "
                + (c.safeMode ? "safe mode" : "fast mode"));
        final List<Integer> open = java.util.Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger done = new AtomicInteger();
        ExecutorService ex = Executors.newFixedThreadPool(32);
        for (int p : new ArrayList<>(ports)) {
            ex.submit(() -> {
                if (c.stop) return;
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(host, p), 1500);
                    open.add(p);
                    c.print("OPEN  " + p);
                } catch (Exception ignored) {
                } finally {
                    int d = done.incrementAndGet();
                    if (d % 50 == 0) c.print("... " + d + "/" + totalPorts + " scanned");
                }
            });
        }
        ex.shutdown();
        ex.awaitTermination(5, TimeUnit.MINUTES);
        c.print("\nOpen ports: " + open.size());
        java.util.Collections.sort(open);

        for (int p : open) {
            String svc = bannerFor(c, host, p);
            c.print(String.format(Locale.US, "%-6d %s", p, svc == null ? "(no banner)" : svc));
        }
        String[][] risky = {
                {"23", "Telnet", "Telnet transmits credentials in cleartext.", "HIGH"},
                {"21", "FTP", "FTP is cleartext; anonymous access is common misconfiguration.", "MEDIUM"},
                {"445", "SMB", "SMB exposed to a network you scan may expose shares ( EternalBlue family).", "HIGH"},
                {"6379", "Redis", "Unauthenticated Redis instances allow full data access and often RCE.", "HIGH"},
                {"27017", "MongoDB", "MongoDB without auth exposes all databases.", "HIGH"},
                {"9200", "Elasticsearch", "Elasticsearch HTTP API without auth exposes all indices.", "HIGH"},
                {"3389", "RDP", "RDP exposed to the internet is a common entry point (BlueKeep family).", "MEDIUM"},
                {"1433", "MSSQL", "Database service exposed; brute force and CVEs apply.", "MEDIUM"},
                {"3306", "MySQL", "Database service exposed; brute force and CVEs apply.", "MEDIUM"},
                {"5432", "PostgreSQL", "Database service exposed; brute force and CVEs apply.", "MEDIUM"}
        };
        for (String[] rk : risky) {
            if (open.contains(Integer.parseInt(rk[0])))
                c.finding(rk[3], rk[3].equals("HIGH") ? "7.5" : "5.0",
                        rk[1] + " service reachable (" + rk[0] + "/tcp)",
                        rk[2], "TCP " + rk[0] + " open on " + host, host,
                        "Restrict with a firewall / bind to localhost / add authentication.");
        }
        c.print("\nScan complete.");
    }

    static String bannerFor(Tool.Ctx c, String host, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 2000);
            s.setSoTimeout(2500);
            if (port == 443 || port == 8443) {
                SSLSocketFactory f = (SSLSocketFactory) SSLSocketFactory.getDefault();
                try (SSLSocket tls = (SSLSocket) f.createSocket(s, host, port, true)) {
                    tls.startHandshake();
                    OutputStream os = tls.getOutputStream();
                    os.write(("HEAD / HTTP/1.0\r\nHost: " + host + "\r\n\r\n").getBytes());
                    os.flush();
                    byte[] buf = new byte[600];
                    int n = tls.getInputStream().read(buf);
                    return n > 0 ? new String(buf, 0, n).split("\r\n")[0] + " | TLS OK" : null;
                }
            }
            OutputStream os = s.getOutputStream();
            if (port == 80 || port == 8080 || port == 8000 || port == 8888) {
                os.write(("HEAD / HTTP/1.0\r\nHost: " + host + "\r\n\r\n").getBytes());
                os.flush();
            }
            byte[] buf = new byte[600];
            int n = s.getInputStream().read(buf);
            if (n <= 0) return null;
            String all = new String(buf, 0, n);
            return all.split("\r\n")[0].split("\n")[0] + (all.length() > 40 ? " | " + all.substring(0, Math.min(all.length(), 40)).replaceAll("[\r\n]+", " ") : "");
        } catch (Exception e) {
            return null;
        }
    }

    public static void banner(Tool.Ctx c) throws Exception {
        String[] hp = (c.param == null || c.param.trim().isEmpty() ? c.target : c.param).trim().split(":");
        String host = hp[0];
        int port = hp.length > 1 ? Integer.parseInt(hp[1].replaceAll("\\D", "")) : 80;
        c.print("Grabbing banner from " + host + ":" + port);
        String b = bannerFor(c, host, port);
        if (b == null) c.print("No banner received (port closed, filtered, or silent service).");
        else c.print(b);
    }

    // ---------- ping / traceroute ----------

    public static void ping(Tool.Ctx c) throws Exception {
        String host = c.target.trim().replaceFirst("^https?://", "").split("[:/]")[0];
        c.print("$ ping -c 4 -W 3 " + host);
        exec(c, new String[]{"ping", "-c", "4", "-W", "3", host});
    }

    public static void traceroute(Tool.Ctx c) throws Exception {
        String host = c.target.trim().replaceFirst("^https?://", "").split("[:/]")[0];
        try {
            c.print("$ traceroute " + host);
            exec(c, new String[]{"traceroute", "-w", "2", "-q", "1", "-m", "15", host});
        } catch (IOException e) {
            c.print("The traceroute binary is not available on this Android system (stock ROMs do not ship it).");
            c.print("Working alternatives:");
            c.print("  1) Install Termux and run:  pkg install traceroute && traceroute " + host);
            c.print("  2) Use the Ping tool with the Ping sweep / port tools for path info.");
            c.print("SETBD Scanner does not fake results — no synthetic hop list is generated.");
        }
    }

    // ---------- Subnet calculator ----------

    public static void subnet(Tool.Ctx c) throws Exception {
        String cidr = (c.param == null || c.param.trim().isEmpty() ? c.target : c.param).trim();
        if (!cidr.contains("/")) cidr = cidr + "/24";
        String[] parts = cidr.split("/");
        int prefix = Integer.parseInt(parts[1].trim());
        if (prefix < 8 || prefix > 32) throw new IllegalArgumentException("Prefix must be 8..32");
        byte[] ip = InetAddress.getByName(parts[0].trim()).getAddress();
        if (ip.length != 4) throw new IllegalArgumentException("IPv4 required");
        int ipInt = 0;
        for (byte b : ip) ipInt = (ipInt << 8) | (b & 0xFF);
        int mask = prefix == 0 ? 0 : (0xFFFFFFFF << (32 - prefix));
        int net = ipInt & mask, bcast = net | (~mask);
        c.print("CIDR:            " + parts[0] + "/" + prefix);
        c.print("Netmask:         " + intToIp(mask));
        c.print("Wildcard:        " + intToIp(~mask));
        c.print("Network:         " + intToIp(net));
        c.print("Broadcast:       " + intToIp(bcast));
        c.print("First host:      " + intToIp(prefix >= 31 ? net : net + 1));
        c.print("Last host:       " + intToIp(prefix >= 31 ? bcast : bcast - 1));
        long usable = prefix >= 31 ? (1L << (32 - prefix)) : (1L << (32 - prefix)) - 2;
        c.print("Usable hosts:    " + usable);
        c.print("Total addresses: " + (1L << (32 - prefix)));
        c.print("Binary mask:     " + String.format("%32s", Integer.toBinaryString(mask)).replace(' ', '0'));
    }

    static String intToIp(int v) {
        return (v >> 24 & 0xFF) + "." + (v >> 16 & 0xFF) + "." + (v >> 8 & 0xFF) + "." + (v & 0xFF);
    }

    // ---------- Device discovery (ping sweep) ----------

    public static void discover(Tool.Ctx c) throws Exception {
        String cidr = (c.param == null || c.param.trim().isEmpty() ? c.target : c.param).trim();
        if (!cidr.contains("/")) throw new IllegalArgumentException("Provide a CIDR range, e.g. 192.168.1.0/24");
        String[] parts = cidr.split("/");
        int prefix = Integer.parseInt(parts[1].trim());
        if (prefix < 22) throw new IllegalArgumentException("Max range is /22 (1024 hosts) — narrow the scope.");
        byte[] ip = InetAddress.getByName(parts[0]).getAddress();
        int ipInt = 0;
        for (byte b : ip) ipInt = (ipInt << 8) | (b & 0xFF);
        int mask = (0xFFFFFFFF << (32 - prefix));
        int net = ipInt & mask;
        int total = 1 << (32 - prefix);
        c.print("Ping sweep " + intToIp(net) + "/" + prefix + " — " + total + " hosts (1 probe each)");
        final Set<String> alive = java.util.Collections.synchronizedSet(new LinkedHashSet<>());
        ExecutorService ex = Executors.newFixedThreadPool(24);
        for (int i = 0; i < total; i++) {
            final String h = intToIp(net + i);
            ex.submit(() -> {
                if (c.stop) return;
                try {
                    Process p = new ProcessBuilder("ping", "-c", "1", "-W", "1", h).redirectErrorStream(true).start();
                    BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line;
                    StringBuilder sb = new StringBuilder();
                    while ((line = r.readLine()) != null) sb.append(line);
                    p.waitFor();
                    if (sb.toString().contains("1 received") || sb.toString().contains("time=")) {
                        alive.add(h);
                        c.print("UP    " + h);
                    }
                } catch (Exception ignored) {
                }
            });
        }
        ex.shutdown();
        ex.awaitTermination(10, TimeUnit.MINUTES);
        c.print("\nAlive hosts: " + alive.size() + " / " + total);
        for (String h : alive) {
            try {
                String cn = InetAddress.getByName(h).getCanonicalHostName();
                if (!cn.equals(h)) c.print(h + "  (" + cn + ")");
            } catch (Exception ignored) { }
        }
    }

    // ---------- IP info ----------

    public static void ipInfo(Tool.Ctx c) throws Exception {
        String q = c.target.trim().replaceFirst("^https?://", "").split("[:/]")[0];
        String api = "https://ipinfo.io/" + (q.isEmpty() ? "json" : java.net.URLEncoder.encode(q, "UTF-8") + "/json");
        c.print("GET " + api);
        Net.Resp r = Net.get(api);
        try {
            JSONObject o = new JSONObject(r.body);
            for (String k : new String[]{"ip", "hostname", "city", "region", "country", "loc", "org", "postal", "timezone"})
                if (o.has(k)) c.print(String.format("%-10s %s", k, o.getString(k)));
        } catch (Exception e) {
            c.print(r.body);
        }
        c.print("\nData source: ipinfo.io public API (rate limited).");
    }
}
