package com.setbd.scanner.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.setbd.scanner.R;
import com.setbd.scanner.data.Db;
import com.setbd.scanner.data.M;

import java.util.List;

public class TargetsActivity extends AppCompatActivity {

    List<M.Target> items;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_targets);
        setTitle("Target Manager");
        findViewById(R.id.fab).setOnClickListener(v -> showAddDialog());
        RecyclerView list = findViewById(R.id.targetsList);
        list.setLayoutManager(new LinearLayoutManager(this));
        refresh();
    }

    void refresh() {
        items = Db.get(this).listTargets();
        RecyclerView list = findViewById(R.id.targetsList);
        list.setAdapter(new Adapters.TargetAdapter(this, items, t -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(t.name)
                    .setMessage("URL: " + t.url
                            + "\nAllowed domains: " + t.allowedDomains
                            + "\nExcluded paths: " + t.excludedPaths
                            + "\nAuthorized: " + (t.authorized ? "YES" : "NO")
                            + "\nNotes: " + (t.notes == null || t.notes.isEmpty() ? "—" : t.notes))
                    .setPositiveButton("Close", null)
                    .show();
        }, this::refresh));
    }

    void showAddDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_target, null, false);
        EditText name = v.findViewById(R.id.dName);
        EditText url = v.findViewById(R.id.dUrl);
        EditText domains = v.findViewById(R.id.dDomains);
        EditText excluded = v.findViewById(R.id.dExcluded);
        EditText notes = v.findViewById(R.id.dNotes);
        CheckBox auth = v.findViewById(R.id.dAuth);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Add target")
                .setView(v)
                .setPositiveButton("Save", (d, w) -> {
                    String u = url.getText().toString().trim();
                    if (u.isEmpty()) return;
                    if (!u.startsWith("http")) u = "https://" + u;
                    Db.get(this).addTarget(
                            name.getText().toString().trim().isEmpty() ? u : name.getText().toString().trim(),
                            u, domains.getText().toString().trim(), excluded.getText().toString().trim(),
                            auth.isChecked(), notes.getText().toString().trim());
                    Db.get(this).audit("TARGET_ADD", u);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
