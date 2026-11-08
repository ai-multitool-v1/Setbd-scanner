package com.setbd.scanner.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.setbd.scanner.R;
import com.setbd.scanner.data.Db;
import com.setbd.scanner.data.M;

public class ScansActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scans);
        setTitle("Scan History");

        RecyclerView list = findViewById(R.id.scansList);
        list.setLayoutManager(new LinearLayoutManager(this));
        java.util.List<M.ScanRecord> scans = Db.get(this).listScans(200);
        if (scans.isEmpty()) {
            android.widget.TextView empty = new android.widget.TextView(this);
            empty.setText("  No scans yet. Run one from the Scanner tab.");
            empty.setTextColor(getColor(R.color.textSub));
            empty.setPadding(24, 40, 24, 24);
            setContentView(empty);
            return;
        }
        list.setAdapter(new Adapters.ScanAdapter(this, scans, s -> {
            Intent i = new Intent(this, ReportActivity.class);
            i.putExtra("scanId", s.id);
            startActivity(i);
        }));
    }
}
