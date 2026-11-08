package com.setbd.scanner;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.setbd.scanner.core.Prefs;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        applyTheme(Prefs.get(this, "theme", "system"));
    }

    /** mode: "dark" | "light" | "system" */
    public static void applyTheme(String mode) {
        if ("dark".equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else if ("light".equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }
}
