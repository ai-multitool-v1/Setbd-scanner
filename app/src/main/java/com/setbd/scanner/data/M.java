package com.setbd.scanner.data;

import java.util.ArrayList;
import java.util.List;

/** Plain data models shared between the DB layer, tools and UI. */
public class M {

    public static class Target {
        public long id;
        public String name;
        public String url;
        public String allowedDomains;   // comma separated
        public String excludedPaths;    // comma separated
        public boolean authorized;
        public String notes;
        public long createdAt;
    }

    public static class Finding {
        public String severity;        // HIGH / MEDIUM / LOW / INFO
        public String cvss;            // approximate CVSS-style score label
        public String title;
        public String detail;
        public String evidence;
        public String url;
        public String recommendation;

        public Finding() { }

        public Finding(String severity, String cvss, String title, String detail,
                       String evidence, String url, String recommendation) {
            this.severity = severity;
            this.cvss = cvss;
            this.title = title;
            this.detail = detail;
            this.evidence = evidence;
            this.url = url;
            this.recommendation = recommendation;
        }
    }

    public static class ScanRecord {
        public long id;
        public String type;
        public String target;
        public String status;      // RUNNING / COMPLETED / FAILED / CANCELLED
        public String summary;
        public long startedAt;
        public long finishedAt;
        public List<Finding> findings = new ArrayList<>();
        public List<String> toolsUsed = new ArrayList<>();
    }

    public static class SavedRequest {
        public long id;
        public String name;
        public String method;
        public String url;
        public String headers;
        public String body;
        public long createdAt;
    }

    public static class ToolRun {
        public long id;
        public String toolId;
        public String toolName;
        public String target;
        public String summary;
        public String output;
        public long createdAt;
    }

    public static class AuditEntry {
        public long id;
        public long ts;
        public String action;
        public String detail;
    }
}
