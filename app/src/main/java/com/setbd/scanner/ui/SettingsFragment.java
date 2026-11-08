package com.setbd.scanner.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.setbd.scanner.App;
import com.setbd.scanner.R;
import com.setbd.scanner.core.Prefs;
import com.setbd.scanner.data.Db;

import java.util.List;

public class SettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        String theme = Prefs.get(requireContext(), "theme", "system");
        ((RadioButton) v.findViewById("dark".equals(theme) ? R.id.rbDark : "light".equals(theme) ? R.id.rbLight : R.id.rbSystem)).setChecked(true);
        RadioGroup rg = v.findViewById(R.id.themeRadio);
        rg.setOnCheckedChangeListener((g, id) -> {
            String mode = id == R.id.rbDark ? "dark" : id == R.id.rbLight ? "light" : "system";
            Prefs.put(requireContext(), "theme", mode);
            App.applyTheme(mode);
        });

        MaterialSwitch safe = v.findViewById(R.id.safeSwitch);
        safe.setChecked(Prefs.bool(requireContext(), "safeDefault", true));
        safe.setOnCheckedChangeListener((b, checked) -> Prefs.putBool(requireContext(), "safeDefault", checked));

        MaterialSwitch local = v.findViewById(R.id.localSwitch);
        local.setChecked(Prefs.bool(requireContext(), "localDefault", false));
        local.setOnCheckedChangeListener((b, checked) -> Prefs.putBool(requireContext(), "localDefault", checked));

        SeekBar rate = v.findViewById(R.id.rateBar);
        rate.setProgress(Prefs.getInt(requireContext(), "rate", 300));
        TextView rateLabel = v.findViewById(R.id.rateLabel);
        rateLabel.setText("Default rate limit: " + rate.getProgress() + " ms between requests");
        rate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                rateLabel.setText("Default rate limit: " + p + " ms between requests");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                Prefs.putInt(requireContext(), "rate", sb.getProgress());
            }
        });

        EditText ua = v.findViewById(R.id.uaInput);
        ua.setText(Prefs.get(requireContext(), "ua", ""));
        ua.setOnFocusChangeListener((vv, hasFocus) -> {
            if (!hasFocus) Prefs.put(requireContext(), "ua", ua.getText().toString().trim());
        });

        v.findViewById(R.id.auditBtn).setOnClickListener(x -> showAudit());
        v.findViewById(R.id.clearBtn).setOnClickListener(x ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Clear all local data")
                        .setMessage("Deletes targets, scan history, saved requests, favorites and the audit log. This cannot be undone.")
                        .setPositiveButton("Delete everything", (d, w) -> {
                            Db.get(requireContext()).wipeAll();
                            Db.get(requireContext()).audit("WIPE", "all local data cleared by user");
                            Toast.makeText(getContext(), "Local data cleared", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show());
    }

    void showAudit() {
        List<com.setbd.scanner.data.M.AuditEntry> log = Db.get(requireContext()).listAudit(200);
        StringBuilder sb = new StringBuilder();
        if (log.isEmpty()) sb.append("Audit log is empty.");
        for (com.setbd.scanner.data.M.AuditEntry a : log) {
            sb.append(new java.text.SimpleDateFormat("dd MMM HH:mm:ss", java.util.Locale.US).format(new java.util.Date(a.ts)))
                    .append("  ").append(a.action).append("  ").append(a.detail).append("\n");
        }
        TextView tv = new TextView(requireContext());
        tv.setText(sb.toString());
        tv.setTextIsSelectable(true);
        tv.setTextColor(requireContext().getColor(R.color.textMain));
        tv.setTextSize(11);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Audit log (last 200 events)")
                .setView(tv)
                .setPositiveButton("Close", null)
                .show();
    }

    @Override
    public void onPause() {
        super.onPause();
        EditText ua = getView() == null ? null : getView().findViewById(R.id.uaInput);
        if (ua != null) Prefs.put(requireContext(), "ua", ua.getText().toString().trim());
    }
}
