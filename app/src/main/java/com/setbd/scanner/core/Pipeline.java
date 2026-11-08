package com.setbd.scanner.core;

import com.setbd.scanner.data.M;

/**
 * Full-scan orchestration: runs selected modules in order, aggregates findings
 * and tool usage, honors stop/rate/safe-mode from the shared Tool.Ctx.
 */
public class Pipeline {

    public interface Progress { void step(String what); }

    public static void run(Tool.Ctx c, java.util.Set<String> modules, Progress progress) {
        String url = WebTools.normUrl(c.target);
        String host = WebTools.host(url);

        if (modules.contains("recon")) {
            progress.step("Reconnaissance: DNS / WHOIS / IP");
            c.toolsUsed.add("DNS Lookup");
            c.toolsUsed.add("WHOIS");
            safe(c, () -> NetTools.dns(c), "DNS");
            safe(c, () -> NetTools.whois(c), "WHOIS");
            safe(c, () -> NetTools.ipInfo(c), "IP Info");
        }

        if (modules.contains("http")) {
            progress.step("HTTP & security headers");
            c.toolsUsed.add("HTTP Header Analyzer");
            safe(c, () -> WebTools.secHeaders(c), "Headers");
            c.toolsUsed.add("Cookie Security Analyzer");
            safe(c, () -> WebTools.cookies(c), "Cookies");
            c.toolsUsed.add("CORS Checker");
            safe(c, () -> WebTools.cors(c), "CORS");
            c.toolsUsed.add("CSP Analyzer");
            safe(c, () -> WebTools.csp(c), "CSP");
            c.toolsUsed.add("Technology Fingerprinting");
            safe(c, () -> WebTools.tech(c), "Tech");
        }

        if (modules.contains("tls")) {
            progress.step("TLS/SSL analysis");
            c.toolsUsed.add("SSL/TLS Certificate Analyzer");
            safe(c, () -> WebTools.tlsInfo(c), "TLS Info");
            c.toolsUsed.add("SSL/TLS Protocol Scanner");
            safe(c, () -> WebTools.tlsScan(c), "TLS Scan");
        }

        if (modules.contains("ports")) {
            progress.step("TCP port scan (top ports)");
            c.toolsUsed.add("Port Scanner");
            c.param = "21,22,25,53,80,110,143,443,445,3306,3389,5432,6379,8080,8443";
            safe(c, () -> NetTools.ports(c), "Ports");
        }

        if (modules.contains("sqli") || modules.contains("xss")) {
            progress.step("Light injection probes (passive)");
            if (modules.contains("sqli")) {
                c.toolsUsed.add("SQLi Parameter Analyzer");
                safe(c, () -> WebTools.sqliCheck(c), "SQLi");
            }
            if (modules.contains("xss")) {
                c.toolsUsed.add("Reflected XSS Tester");
                safe(c, () -> WebTools.xssReflected(c), "XSS");
                c.toolsUsed.add("DOM XSS Analysis");
                safe(c, () -> WebTools.domXss(c), "DOM XSS");
            }
        }

        if (modules.contains("crawl")) {
            progress.step("Crawl (depth 1, max 10 pages)");
            c.toolsUsed.add("Website Crawler");
            c.param = "1,10";
            safe(c, () -> ScraperTools.crawl(c), "Crawler");
        }

        if (modules.contains("osint")) {
            progress.step("OSINT: CT subdomains + Wayback");
            c.toolsUsed.add("Subdomain Finder (CT logs)");
            safe(c, () -> Registry.subCt(c), "CT Subdomains");
            c.toolsUsed.add("Wayback URL Discovery");
            safe(c, () -> WebTools.wayback(c), "Wayback");
        }

        progress.step("Finalizing");
        c.print("\n========== SCAN SUMMARY ==========");
        c.print("Target:     " + url + "  (" + host + ")");
        c.print("Modules:    " + modules);
        c.print("Findings:   " + Net.summarize(c.findings));
    }

    interface Thunk { void run() throws Exception; }

    static void safe(Tool.Ctx c, Thunk t, String label) {
        if (c.stop) return;
        c.print("\n────────── " + label + " ──────────");
        try {
            t.run();
        } catch (Exception e) {
            c.print("[" + label + "] error: " + e.getMessage());
        }
    }
}
