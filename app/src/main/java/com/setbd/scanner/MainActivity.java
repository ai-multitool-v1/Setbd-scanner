package com.setbd.scanner;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.setbd.scanner.data.Db;
import com.setbd.scanner.ui.HomeFragment;
import com.setbd.scanner.ui.ScannerFragment;
import com.setbd.scanner.ui.SettingsFragment;
import com.setbd.scanner.ui.ToolsFragment;
import com.setbd.scanner.ui.TrafficFragment;

public class MainActivity extends AppCompatActivity {

    BottomNavigationView nav;

    /** Pre-filled target handed over from Home quick scan. */
    public static String pendingScannerTarget;
    public static boolean pendingQuickScan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        nav = findViewById(R.id.bottom_nav);

        nav.setOnItemSelectedListener(item -> {
            Fragment f;
            int id = item.getItemId();
            if (id == R.id.nav_home) f = new HomeFragment();
            else if (id == R.id.nav_tools) f = new ToolsFragment();
            else if (id == R.id.nav_scanner) f = new ScannerFragment();
            else if (id == R.id.nav_traffic) f = new TrafficFragment();
            else if (id == R.id.nav_settings) f = new SettingsFragment();
            else f = new HomeFragment();
            getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.container, f)
                    .commit();
            return true;
        });

        nav.setSelectedItemId(R.id.nav_home);
        Db.get(this).audit("APP_OPEN", "session started");
    }

    public void gotoScanner(String target, boolean quick) {
        pendingScannerTarget = target;
        pendingQuickScan = quick;
        nav.setSelectedItemId(R.id.nav_scanner);
    }
}
