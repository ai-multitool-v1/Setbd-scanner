package com.setbd.scanner.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.setbd.scanner.R;
import com.setbd.scanner.core.Registry;

public class ToolDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tool_detail);
        setTitle("Tool details");

        String id = getIntent().getStringExtra("id");
        Registry.ToolInfo t = Registry.byId(id);
        if (t == null) {
            finish();
            return;
        }

        ((android.widget.ImageView) findViewById(R.id.toolIcon)).setImageResource(t.icon);
        ((TextView) findViewById(R.id.toolName)).setText(t.name);
        ((TextView) findViewById(R.id.toolCat)).setText(t.category + "  •  version " + t.version);
        ((TextView) findViewById(R.id.toolDesc)).setText(t.desc);
        ((TextView) findViewById(R.id.toolMeta)).setText(
                "Status: " + t.status + "\nDependencies: " + t.deps);

        TextView status = findViewById(R.id.toolStatus);
        int c;
        if ("NATIVE".equals(t.status)) {
            c = getColor(R.color.ok);
        } else if ("TERMUX".equals(t.status)) {
            c = getColor(R.color.warn);
        } else {
            c = getColor(R.color.low);
        }
        Adapters.pill(status, c);
        status.setText(t.status);

        View warn = findViewById(R.id.authWarn);
        warn.setVisibility(t.needsAuth ? View.VISIBLE : View.GONE);

        String doc;
        if (t.doc != null && !t.doc.isEmpty()) {
            doc = t.doc;
        } else {
            doc = "NATIVE TOOL — runs fully on-device inside SETBD Scanner.\n\n"
                    + "Input: " + (t.targetLabel == null ? "(offline tool)" : t.targetLabel)
                    + (t.paramLabel != null ? "\nOptions: " + t.paramLabel.replace("\n", " / ") : "") + "\n\n"
                    + (t.needsAuth
                    ? "ACTIVE TEST: only run this against systems you are authorized to assess. "
                    + "Requests are rate-limited and safe mode keeps all probes benign.\n"
                    : "PASSIVE/OFFLINE: this tool makes no intrusive requests.\n")
                    + "\nAll results are real tool output — SETBD Scanner never fabricates scan results.";
        }
        ((TextView) findViewById(R.id.toolDoc)).setText(doc);

        findViewById(R.id.launchBtn).setOnClickListener(v -> {
            if (t.impl != null) {
                startActivity(new android.content.Intent(this, ToolRunActivity.class).putExtra("id", t.id));
            } else {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(t.name + " — setup guide")
                        .setMessage(t.doc)
                        .setPositiveButton("Got it", null)
                        .show();
            }
        });

        findViewById(R.id.docBtn).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle(t.name + " — documentation")
                        .setMessage(doc)
                        .setPositiveButton("Close", null)
                        .show());
    }
}
