package com.setbd.scanner.core;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.core.content.FileProvider;

import com.setbd.scanner.data.M;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Security report builder: HTML / JSON / CSV / PDF (native PdfDocument). */
public class Reporter {

    static SimpleDateFormat iso() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    }

    // ---------- JSON ----------

    public static String toJson(M.ScanRecord s) {
        try {
            JSONObject o = new JSONObject();
            o.put("tool", "SETBD Scanner 1.0");
            o.put("generated", iso().format(new Date()));
            o.put("scan_type", s.type);
            o.put("target", s.target);
            o.put("status", s.status);
            o.put("scan_date", iso().format(new Date(s.finishedAt > 0 ? s.finishedAt : s.startedAt)));
            o.put("summary", s.summary);
            JSONArray tools = new JSONArray();
            for (String t : s.toolsUsed) tools.put(t);
            o.put("tools_used", tools);
            JSONArray fs = new JSONArray();
            for (M.Finding f : s.findings) {
                JSONObject fo = new JSONObject();
                fo.put("severity", f.severity);
                fo.put("cvss_estimate", f.cvss);
                fo.put("title", f.title);
                fo.put("detail", f.detail);
                fo.put("evidence", f.evidence);
                fo.put("affected_url", f.url);
                fo.put("recommendation", f.recommendation);
                fs.put(fo);
            }
            o.put("findings", fs);
            return o.toString(2);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    // ---------- CSV ----------

    static String csvEsc(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"").replace("\n", " ").replace("\r", "") + "\"";
    }

    public static String toCsv(M.ScanRecord s) {
        StringBuilder sb = new StringBuilder();
        sb.append("severity,cvss_estimate,title,affected_url,detail,recommendation\n");
        for (M.Finding f : s.findings) {
            sb.append(csvEsc(f.severity)).append(',').append(csvEsc(f.cvss)).append(',')
                    .append(csvEsc(f.title)).append(',').append(csvEsc(f.url)).append(',')
                    .append(csvEsc(f.detail)).append(',').append(csvEsc(f.recommendation)).append('\n');
        }
        sb.append("\ntarget,").append(csvEsc(s.target)).append("\nscan_type,").append(csvEsc(s.type))
                .append("\ndate,").append(csvEsc(iso().format(new Date(s.finishedAt)))).append('\n');
        return sb.toString();
    }

    // ---------- HTML ----------

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static String sevColor(String sev) {
        switch (sev == null ? "" : sev) {
            case "HIGH": return "#dc2626";
            case "MEDIUM": return "#d97706";
            case "LOW": return "#2563eb";
            default: return "#64748b";
        }
    }

    public static String toHtml(M.ScanRecord s) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>")
                .append("<title>SETBD Scanner Report — ").append(esc(s.target)).append("</title><style>")
                .append("body{font-family:-apple-system,Segoe UI,Roboto,sans-serif;margin:0;background:#f1f5f9;color:#0f172a}")
                .append(".wrap{max-width:860px;margin:24px auto;padding:0 16px}")
                .append(".hdr{background:linear-gradient(135deg,#0b1220,#13203a);color:#e6f1ff;border-radius:18px;padding:28px}")
                .append(".hdr h1{margin:0 0 4px;font-size:26px}.hdr p{margin:2px 0;color:#8fa3bf}")
                .append(".grid{display:flex;gap:12px;flex-wrap:wrap;margin:18px 0}")
                .append(".kpi{background:#fff;border-radius:14px;padding:14px 18px;box-shadow:0 1px 3px rgba(2,6,23,.08);min-width:110px}")
                .append(".kpi b{font-size:22px;display:block}.fnd{background:#fff;border-radius:16px;padding:18px;margin:14px 0;box-shadow:0 1px 3px rgba(2,6,23,.08);border-left:5px solid #64748b}")
                .append(".fnd h3{margin:0 0 6px;font-size:16px}.badge{display:inline-block;padding:2px 10px;border-radius:999px;color:#fff;font-size:12px;font-weight:700;margin-right:8px}")
                .append("code,pre{background:#f1f5f9;border-radius:8px;padding:2px 6px;font-size:13px}pre{padding:12px;overflow:auto}")
                .append(".kv{color:#475569;font-size:13px;margin:3px 0}")
                .append("</style></head><body><div class='wrap'>");
        sb.append("<div class='hdr'><h1>SETBD SCANNER</h1><p>Discover. Analyze. Secure.</p>")
                .append("<p style='margin-top:14px'>Security Assessment Report</p>")
                .append("<p>Target: <b>").append(esc(s.target)).append("</b></p>")
                .append("<p>Scan type: ").append(esc(s.type)).append(" &nbsp;|&nbsp; Date: ").append(iso().format(new Date(s.finishedAt > 0 ? s.finishedAt : System.currentTimeMillis())))
                .append(" &nbsp;|&nbsp; Status: ").append(esc(s.status)).append("</p></div>");
        int h = 0, m = 0, l = 0, i = 0;
        for (M.Finding f : s.findings) {
            if ("HIGH".equals(f.severity)) h++;
            else if ("MEDIUM".equals(f.severity)) m++;
            else if ("LOW".equals(f.severity)) l++;
            else i++;
        }
        sb.append("<div class='grid'>")
                .append("<div class='kpi'><b style='color:#dc2626'>").append(h).append("</b>High</div>")
                .append("<div class='kpi'><b style='color:#d97706'>").append(m).append("</b>Medium</div>")
                .append("<div class='kpi'><b style='color:#2563eb'>").append(l).append("</b>Low</div>")
                .append("<div class='kpi'><b style='color:#64748b'>").append(i).append("</b>Info</div>")
                .append("</div>");
        sb.append("<div class='fnd'><h3>Scope &amp; Tools</h3><p class='kv'>Target: ").append(esc(s.target))
                .append("</p><p class='kv'>Tools used: ").append(esc(String.join(", ", s.toolsUsed)))
                .append("</p><p class='kv'>Summary: ").append(esc(s.summary)).append("</p></div>");
        int n = 0;
        for (M.Finding f : s.findings) {
            n++;
            sb.append("<div class='fnd' style='border-left-color:").append(sevColor(f.severity)).append("'>")
                    .append("<span class='badge' style='background:").append(sevColor(f.severity)).append("'>").append(esc(f.severity)).append("</span>")
                    .append(f.cvss != null && !f.cvss.isEmpty() && !"0.0".equals(f.cvss) ? "<span class='badge' style='background:#334155'>CVSS ~" + esc(f.cvss) + "</span>" : "")
                    .append("<h3>#").append(n).append(" ").append(esc(f.title)).append("</h3>")
                    .append("<p>").append(esc(f.detail)).append("</p>")
                    .append("<p class='kv'><b>Affected URL:</b> ").append(esc(f.url)).append("</p>")
                    .append(f.evidence != null && !f.evidence.isEmpty() ? "<p class='kv'><b>Evidence:</b></p><pre>" + esc(f.evidence) + "</pre>" : "")
                    .append("<p class='kv'><b>Recommendation:</b> ").append(esc(f.recommendation)).append("</p></div>");
        }
        sb.append("<p style='color:#64748b;font-size:12px'>Generated on-device by SETBD Scanner v1.0. Results are indicative — verify manually before remediation decisions. Only test targets you are authorized to assess.</p>");
        sb.append("</div></body></html>");
        return sb.toString();
    }

    // ---------- PDF ----------

    public static byte[] toPdf(M.ScanRecord s) {
        PdfDocument doc = new PdfDocument();
        TextPaint tpTitle = new TextPaint();
        tpTitle.setTextSize(20);
        tpTitle.setFakeBoldText(true);
        tpTitle.setColor(0xFF0B1220);
        TextPaint tpSub = new TextPaint();
        tpSub.setTextSize(10);
        tpSub.setColor(0xFF475569);
        TextPaint tpBody = new TextPaint();
        tpBody.setTextSize(10);
        tpBody.setColor(0xFF0F172A);
        TextPaint tpBold = new TextPaint(tpBody);
        tpBold.setFakeBoldText(true);
        final float W = 595, X = 40, CW = 515, MAXY = 810;

        final PdfDocument.Page[] page = {null};
        final Canvas[] cv = {null};
        final float[] y = {0};
        final int[] idx = {0};

        class Helper {
            void newPage() {
                if (page[0] != null) doc.finishPage(page[0]);
                idx[0]++;
                page[0] = doc.startPage(new PdfDocument.PageInfo.Builder((int) W, 842, idx[0]).create());
                cv[0] = page[0].getCanvas();
                y[0] = 42;
                cv[0].drawText("SETBD Scanner — Security Assessment", X, 26, tpSub);
            }

            void para(String text, TextPaint tp, float gap) {
                if (text == null || text.isEmpty()) return;
                StaticLayout sl = StaticLayout.Builder.obtain(text, 0, text.length(), tp, (int) CW)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(1.5f, 1f).build();
                if (y[0] + sl.getHeight() > MAXY) newPage();
                cv[0].save();
                cv[0].translate(X, y[0]);
                sl.draw(cv[0]);
                cv[0].restore();
                y[0] += sl.getHeight() + gap;
            }

            void gap(float h) {
                if (y[0] + h > MAXY) newPage();
                y[0] += h;
            }

            void rule() {
                if (y[0] + 10 > MAXY) newPage();
                Paint p = new Paint();
                p.setColor(0xFFE2E8F0);
                cv[0].drawRect(X, y[0], X + CW, y[0] + 1, p);
                y[0] += 8;
            }
        }
        Helper hlp = new Helper();
        hlp.newPage();

        hlp.para("SETBD SCANNER", tpTitle, 2);
        hlp.para("Discover. Analyze. Secure.", tpSub, 10);
        hlp.para("Security Assessment Report", tpBold, 8);
        hlp.rule();
        hlp.para("Target: " + s.target, tpBody, 3);
        hlp.para("Scan type: " + s.type + "    Status: " + s.status, tpBody, 3);
        hlp.para("Scan date: " + iso().format(new Date(s.finishedAt > 0 ? s.finishedAt : System.currentTimeMillis())), tpBody, 3);
        hlp.para("Summary: " + s.summary, tpBody, 3);
        hlp.para("Tools used: " + String.join(", ", s.toolsUsed), tpBody, 8);

        int[] sev = new int[4];
        for (M.Finding f : s.findings) {
            int k = f.severity == null ? 3 : ("HIGH".equals(f.severity) ? 0 : "MEDIUM".equals(f.severity) ? 1 : "LOW".equals(f.severity) ? 2 : 3);
            sev[k]++;
        }
        hlp.para("Findings: " + sev[0] + " high, " + sev[1] + " medium, " + sev[2] + " low, " + sev[3] + " info", tpBold, 8);

        int n = 0;
        for (M.Finding f : s.findings) {
            n++;
            hlp.gap(6);
            hlp.rule();
            hlp.para("#" + n + "  [" + f.severity + (f.cvss != null && !"0.0".equals(f.cvss) ? " | CVSS ~" + f.cvss : "") + "]  " + f.title, tpBold, 4);
            hlp.para(f.detail, tpBody, 3);
            hlp.para("Affected URL: " + f.url, tpSub, 3);
            if (f.evidence != null && !f.evidence.isEmpty()) hlp.para("Evidence: " + f.evidence, tpSub, 3);
            hlp.para("Recommendation: " + f.recommendation, tpBody, 4);
        }
        hlp.gap(10);
        hlp.para("Generated on-device by SETBD Scanner v1.0. Findings are indicative; verify manually before remediation. Only test authorized targets.", tpSub, 0);
        if (page[0] != null) doc.finishPage(page[0]);

        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try {
            doc.writeTo(bo);
        } catch (java.io.IOException e) {
            throw new RuntimeException("PDF generation failed: " + e.getMessage(), e);
        }
        doc.close();
        return bo.toByteArray();
    }

    // ---------- Save / share ----------

    public static Uri saveBytes(Context ctx, String name, byte[] data, String mime) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SETBD Scanner");
            Uri uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IllegalStateException("MediaStore insert failed");
            try (OutputStream os = ctx.getContentResolver().openOutputStream(uri)) {
                os.write(data);
            }
            return uri;
        } else {
            File dir = new File(ctx.getExternalFilesDir(null), "exports");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, name);
            try (FileOutputStream fo = new FileOutputStream(f)) {
                fo.write(data);
            }
            return FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".files", f);
        }
    }

    public static Uri saveText(Context ctx, String name, String content, String mime) throws Exception {
        return saveBytes(ctx, name, content.getBytes("UTF-8"), mime);
    }

    public static void share(Context ctx, Uri uri, String mime, String title) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(Intent.createChooser(i, title));
    }

    public static String stamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
    }
}
