package com.setbd.scanner.ui;

import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.setbd.scanner.R;
import com.setbd.scanner.core.Reporter;
import com.setbd.scanner.data.Db;
import com.setbd.scanner.data.M;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReportActivity extends AppCompatActivity {

    M.ScanRecord scan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);
        setTitle("Security Report");

        long id = getIntent().getLongExtra("scanId", -1);
        for (M.ScanRecord s : Db.get(this).listScans(200)) {
            if (s.id == id) {
                scan = s;
                break;
            }
        }
        if (scan == null) {
            Toast.makeText(this, "Scan not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ((TextView) findViewById(R.id.reportTarget)).setText(scan.target);
        ((TextView) findViewById(R.id.reportMeta)).setText(
                scan.type + "  •  " + new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US).format(new Date(scan.finishedAt))
                        + "  •  status " + scan.status
                        + "\nTools used: " + String.join(", ", scan.toolsUsed));
        ((TextView) findViewById(R.id.reportSummary)).setText("Findings: " + scan.summary);

        RecyclerView list = findViewById(R.id.findingsList);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(new Adapters.FindingAdapter(this, scan.findings));

        findViewById(R.id.btnPdf).setOnClickListener(v -> export("pdf"));
        findViewById(R.id.btnHtml).setOnClickListener(v -> export("html"));
        findViewById(R.id.btnJson).setOnClickListener(v -> export("json"));
        findViewById(R.id.btnCsv).setOnClickListener(v -> export("csv"));
    }

    void export(String fmt) {
        String base = "setbd-report-" + sanitize(scan.target) + "-" + Reporter.stamp();
        try {
            Uri uri;
            String mime;
            String name;
            switch (fmt) {
                case "pdf":
                    name = base + ".pdf";
                    uri = Reporter.saveBytes(this, name, Reporter.toPdf(scan), "application/pdf");
                    mime = "application/pdf";
                    break;
                case "html":
                    name = base + ".html";
                    uri = Reporter.saveText(this, name, Reporter.toHtml(scan), "text/html");
                    mime = "text/html";
                    break;
                case "json":
                    name = base + ".json";
                    uri = Reporter.saveText(this, name, Reporter.toJson(scan), "application/json");
                    mime = "application/json";
                    break;
                default:
                    name = base + ".csv";
                    uri = Reporter.saveText(this, name, Reporter.toCsv(scan), "text/csv");
                    mime = "text/csv";
                    break;
            }
            Db.get(this).audit("REPORT_EXPORT", name);
            Reporter.share(this, uri, mime, "Share report (" + fmt.toUpperCase(Locale.US) + ")");
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    String sanitize(String s) {
        return (s == null ? "target" : s).replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
