package com.setbd.scanner.core;

import com.setbd.scanner.data.M;

import java.util.ArrayList;
import java.util.List;

/** Tool execution contract shared by the registry, native tools and the UI. */
public class Tool {

    /** Output sink — the UI streams these lines into the result view. */
    public interface Sink { void print(String s); }

    /** A native tool implementation. */
    public interface Impl { void run(Ctx c) throws Exception; }

    public static class Ctx {
        public String target = "";
        public String param = "";
        public boolean safeMode = true;
        public boolean allowLocal = false;
        public boolean authorized = false;
        public int rateMs = 300;
        public int timeoutSec = 20;
        public String userAgent;
        public Sink out = s -> { };
        public volatile boolean stop = false;
        public volatile boolean paused = false;
        public List<M.Finding> findings = new ArrayList<>();
        public List<String> toolsUsed = new ArrayList<>();

        public void print(String s) { out.print(s); }

        public synchronized void finding(String sev, String cvss, String title, String detail,
                                         String evidence, String url, String reco) {
            findings.add(new M.Finding(sev, cvss, title, detail, evidence, url, reco));
        }

        public void sleep() {
            try {
                if (rateMs > 0) Thread.sleep(rateMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /** Blocks while the user paused the job. */
        public void pauseGate() {
            while (paused && !stop) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
