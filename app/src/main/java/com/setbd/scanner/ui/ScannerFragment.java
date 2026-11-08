package com.setbd.scanner.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.setbd.scanner.MainActivity;
import com.setbd.scanner.R;
import com.setbd.scanner.core.Net;
import com.setbd.scanner.core.Pipeline;
import com.setbd.scanner.core.Prefs;
import com.setbd.scanner.core.Tool;
import com.setbd.scanner.data.Db;

import java.util.HashSet;
import java.util.Set;

public class ScannerFragment extends Fragment {

    TextView console, progress, rateLabel;
    ScrollView consoleScroll;
    StringBuilder buffer = new StringBuilder();

    Tool.Ctx running;
    long lastScanId = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scanner, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        console = v.findViewById(R.id.console);
        consoleScroll = v.findViewById(R.id.consoleScroll);
        progress = v.findViewById(R.id.progressLabel);
        rateLabel = v.findViewById(R.id.rateLabel);

        SeekBar rate = v.findViewById(R.id.rateBar);
        rate.setProgress(Prefs.getInt(requireContext(), "rate", 300));
        rateLabel.setText("Request rate limit: " + rate.getProgress() + " ms");
        rate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean b) {
                rateLabel.setText("Request rate limit: " + p + " ms");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                Prefs.putInt(requireContext(), "rate", sb.getProgress());
            }
        });

        ((MaterialSwitch) v.findViewById(R.id.safeSwitch)).setChecked(Prefs.bool(requireContext(), "safeDefault", true));
        ((MaterialSwitch) v.findViewById(R.id.localSwitch)).setChecked(Prefs.bool(requireContext(), "localDefault", false));

        v.findViewById(R.id.startBtn).setOnClickListener(x -> startScan(v, false));
        v.findViewById(R.id.stopBtn).setOnClickListener(x -> {
            if (running != null) running.stop = true;
            progress.setText("Stopping…");
        });
        v.findViewById(R.id.openReportBtn).setOnClickListener(x -> {
            if (lastScanId > 0) {
                Intent i = new Intent(getContext(), ReportActivity.class);
                i.putExtra("scanId", lastScanId);
                startActivity(i);
            } else {
                Toast.makeText(getContext(), "No report yet — run a scan first", Toast.LENGTH_SHORT).show();
            }
        });

        // Hand-off from Home quick scan
        if (MainActivity.pendingScannerTarget != null) {
            ((TextView) v.findViewById(R.id.targetInput)).setText(MainActivity.pendingScannerTarget);
            boolean quick = MainActivity.pendingQuickScan;
            MainActivity.pendingScannerTarget = null;
            MainActivity.pendingQuickScan = false;
            if (quick) {
                ((CheckBox) v.findViewById(R.id.modRecon)).setChecked(false);
                ((CheckBox) v.findViewById(R.id.modPorts)).setChecked(false);
                ((CheckBox) v.findViewById(R.id.modCrawl)).setChecked(false);
                ((CheckBox) v.findViewById(R.id.chkAuth)).setChecked(true);
                v.postDelayed(() -> startScan(v, true), 350);
            }
        }
    }

    void startScan(View v, boolean quick) {
        if (running != null && !running.stop) {
            Toast.makeText(getContext(), "A scan is already running — stop it first", Toast.LENGTH_SHORT).show();
            return;
        }
        String target = ((TextView) v.findViewById(R.id.targetInput)).getText().toString().trim();
        if (target.isEmpty()) {
            Toast.makeText(getContext(), "Enter a target URL", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean allowLocal = ((MaterialSwitch) v.findViewById(R.id.localSwitch)).isChecked();
        String err = Net.checkTarget(target.startsWith("http") ? target : "https://" + target, allowLocal);
        if (err != null) {
            Toast.makeText(getContext(), err, Toast.LENGTH_LONG).show();
            return;
        }
        if (!((CheckBox) v.findViewById(R.id.chkAuth)).isChecked()) {
            Toast.makeText(getContext(), "Confirm you are authorized to test this target", Toast.LENGTH_LONG).show();
            return;
        }

        Set<String> modules = new HashSet<>();
        if (((CheckBox) v.findViewById(R.id.modRecon)).isChecked()) modules.add("recon");
        if (((CheckBox) v.findViewById(R.id.modHttp)).isChecked()) modules.add("http");
        if (((CheckBox) v.findViewById(R.id.modTls)).isChecked()) modules.add("tls");
        if (((CheckBox) v.findViewById(R.id.modPorts)).isChecked()) modules.add("ports");
        if (((CheckBox) v.findViewById(R.id.modInject)).isChecked()) {
            modules.add("sqli");
            modules.add("xss");
        }
        if (((CheckBox) v.findViewById(R.id.modCrawl)).isChecked()) modules.add("crawl");
        if (((CheckBox) v.findViewById(R.id.modOsint)).isChecked()) modules.add("osint");
        if (modules.isEmpty()) {
            Toast.makeText(getContext(), "Select at least one module", Toast.LENGTH_SHORT).show();
            return;
        }

        final Tool.Ctx c = new Tool.Ctx();
        c.target = target;
        c.safeMode = ((MaterialSwitch) v.findViewById(R.id.safeSwitch)).isChecked();
        c.allowLocal = allowLocal;
        c.authorized = true;
        c.rateMs = Prefs.getInt(requireContext(), "rate", 300);
        c.userAgent = Prefs.get(requireContext(), "ua", null);
        running = c;

        buffer.setLength(0);
        console.setText("SETBD Scanner — full scan\nTarget: " + target + "\nModules: " + modules + "\n\n");
        progress.setText("Starting scan…");

        c.out = s -> {
            if (getActivity() == null || getActivity().isFinishing()) return;
            requireActivity().runOnUiThread(() -> {
                buffer.append(s).append('\n');
                if (buffer.length() > 160_000) buffer.delete(0, buffer.length() - 160_000);
                console.setText(buffer);
                consoleScroll.post(() -> consoleScroll.fullScroll(View.FOCUS_DOWN));
            });
        };

        final long started = System.currentTimeMillis();
        new Thread(() -> {
            try {
                Pipeline.run(c, modules, what -> {
                    if (getActivity() != null && !getActivity().isFinishing())
                        requireActivity().runOnUiThread(() -> progress.setText("Scanning: " + what));
                });
            } catch (Exception e) {
                c.print("FATAL: " + e.getMessage());
            }
            final String status = c.stop ? "CANCELLED" : "COMPLETED";
            final String summary = Net.summarize(c.findings);
            final long id = Db.get(requireContext()).addScan(quick ? "QUICK" : "FULL", target, status, summary,
                    c.findings, c.toolsUsed, started, System.currentTimeMillis());
            Db.get(requireContext()).audit("SCAN", status + " " + target + " → " + summary);
            if (getActivity() == null || getActivity().isFinishing()) return;
            requireActivity().runOnUiThread(() -> {
                lastScanId = id;
                running = null;
                progress.setText("Scan " + status.toLowerCase(java.util.Locale.US) + " — " + summary
                        + (id > 0 ? "  •  report saved (id " + id + ")" : ""));
                c.print("\n[REPORT] Open it via 'Open last report' — exportable as PDF / HTML / JSON / CSV.");
            });
        }, "setbd-scan").start();
    }
}
