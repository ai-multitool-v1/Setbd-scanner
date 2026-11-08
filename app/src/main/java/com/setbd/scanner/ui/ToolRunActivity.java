package com.setbd.scanner.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.setbd.scanner.R;
import com.setbd.scanner.core.Net;
import com.setbd.scanner.core.Prefs;
import com.setbd.scanner.core.Registry;
import com.setbd.scanner.core.Reporter;
import com.setbd.scanner.core.Tool;
import com.setbd.scanner.data.Db;

public class ToolRunActivity extends AppCompatActivity {

    TextView console, statusLabel, inputLabel, paramLabel;
    EditText targetInput, paramInput;
    ScrollView consoleScroll;
    StringBuilder buffer = new StringBuilder();
    Tool.Ctx running;
    Registry.ToolInfo tool;
    String lastOutput = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tool_run);

        tool = Registry.byId(getIntent().getStringExtra("id"));
        if (tool == null) {
            finish();
            return;
        }
        setTitle(tool.name);

        console = findViewById(R.id.console);
        consoleScroll = findViewById(R.id.consoleScroll);
        statusLabel = findViewById(R.id.statusLabel);
        inputLabel = findViewById(R.id.inputLabel);
        paramLabel = findViewById(R.id.paramLabel);
        targetInput = findViewById(R.id.targetInput);
        paramInput = findViewById(R.id.paramInput);

        if (tool.targetLabel != null) {
            inputLabel.setText(tool.targetLabel.replace("\n", " "));
        } else {
            inputLabel.setVisibility(View.GONE);
            targetInput.setVisibility(View.GONE);
        }
        if (tool.paramLabel != null) {
            paramLabel.setVisibility(View.VISIBLE);
            paramLabel.setText(tool.paramLabel.replace("\n", " "));
            paramInput.setVisibility(View.VISIBLE);
        }

        CheckBox auth = findViewById(R.id.authCheck);
        auth.setVisibility(tool.needsAuth ? View.VISIBLE : View.GONE);
        ((MaterialSwitch) findViewById(R.id.safeSwitch)).setChecked(Prefs.bool(this, "safeDefault", true));
        ((MaterialSwitch) findViewById(R.id.localSwitch)).setChecked(Prefs.bool(this, "localDefault", false));

        findViewById(R.id.runBtn).setOnClickListener(v -> run());
        findViewById(R.id.stopBtn).setOnClickListener(v -> {
            if (running != null) running.stop = true;
        });
        findViewById(R.id.pauseBtn).setOnClickListener(v -> {
            if (running != null) {
                running.paused = !running.paused;
                ((android.widget.Button) v).setText(running.paused ? "Resume" : "Pause");
            }
        });
        findViewById(R.id.exportTxtBtn).setOnClickListener(v -> export());
        findViewById(R.id.shareBtn).setOnClickListener(v -> {
            if (lastOutput.isEmpty()) {
                Toast.makeText(this, "Nothing to share yet", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                String name = "setbd-" + tool.id + "-" + Reporter.stamp() + ".txt";
                Uri uri = Reporter.saveText(this, name, lastOutput, "text/plain");
                Reporter.share(this, uri, "text/plain", "Share output");
            } catch (Exception e) {
                Toast.makeText(this, "Share failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    void run() {
        if (running != null && !running.stop) {
            Toast.makeText(this, "Already running — stop first", Toast.LENGTH_SHORT).show();
            return;
        }
        String target = targetInput.getText().toString().trim();
        boolean needsTarget = tool.targetLabel != null && tool.impl != null;
        if (needsTarget && target.isEmpty()) {
            Toast.makeText(this, "Fill the input field", Toast.LENGTH_SHORT).show();
            return;
        }
        if (tool.needsAuth && !((CheckBox) findViewById(R.id.authCheck)).isChecked()) {
            Toast.makeText(this, "Confirm target authorization first", Toast.LENGTH_LONG).show();
            return;
        }
        boolean allowLocal = ((MaterialSwitch) findViewById(R.id.localSwitch)).isChecked();
        if (needsTarget && target.contains("://")) {
            String err = Net.checkTarget(target, allowLocal);
            if (err != null) {
                Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                return;
            }
        }

        final Tool.Ctx c = new Tool.Ctx();
        c.target = target;
        c.param = paramInput.getText().toString().trim();
        c.safeMode = ((MaterialSwitch) findViewById(R.id.safeSwitch)).isChecked();
        c.allowLocal = allowLocal;
        c.authorized = !tool.needsAuth || ((CheckBox) findViewById(R.id.authCheck)).isChecked();
        c.rateMs = Prefs.getInt(this, "rate", 300);
        c.userAgent = Prefs.get(this, "ua", null);
        running = c;

        buffer.setLength(0);
        console.setText(buffer);
        statusLabel.setText("Running…");
        lastOutput = "";

        c.out = s -> {
            if (isFinishing() || isDestroyed()) return;
            runOnUiThread(() -> {
                buffer.append(s).append('\n');
                if (buffer.length() > 200_000) buffer.delete(0, buffer.length() - 200_000);
                console.setText(buffer);
                consoleScroll.post(() -> consoleScroll.fullScroll(View.FOCUS_DOWN));
            });
        };

        new Thread(() -> {
            try {
                c.print("$ " + tool.name + (c.safeMode ? "  [safe mode]" : "") + "  rate=" + c.rateMs + "ms");
                c.print("");
                tool.impl.run(c);
                c.print("\n[exit] completed with " + c.findings.size() + " finding(s).");
            } catch (Throwable e) {
                c.print("\n[error] " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            final String out = buffer.toString();
            final int findings = c.findings.size();
            Db.get(this).addHistory(tool.id, tool.name, c.target, findings + " findings", out);
            Db.get(this).audit("TOOL_RUN", tool.id + " → " + c.target);
            if (isFinishing() || isDestroyed()) return;
            runOnUiThread(() -> {
                running = null;
                lastOutput = out;
                statusLabel.setText("Done — " + findings + " finding(s). Output kept in history.");
            });
        }, "setbd-tool-" + tool.id).start();
    }

    void export() {
        if (lastOutput.isEmpty()) {
            Toast.makeText(this, "Run the tool first", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String name = "setbd-" + tool.id + "-" + Reporter.stamp() + ".txt";
            Uri uri = Reporter.saveText(this, name, lastOutput, "text/plain");
            Toast.makeText(this, "Saved to Downloads/SETBD Scanner/" + name, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (running != null) running.stop = true;
        super.onDestroy();
    }
}
