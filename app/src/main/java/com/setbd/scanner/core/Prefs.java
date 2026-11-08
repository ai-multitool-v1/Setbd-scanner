package com.setbd.scanner.core;

import android.content.Context;
import android.content.SharedPreferences;

/** Small typed wrapper around the app settings store. */
public class Prefs {

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("setbd_settings", Context.MODE_PRIVATE);
    }

    public static String get(Context c, String k, String d) { return p(c).getString(k, d); }

    public static void put(Context c, String k, String v) { p(c).edit().putString(k, v).apply(); }

    public static boolean bool(Context c, String k, boolean d) { return p(c).getBoolean(k, d); }

    public static void putBool(Context c, String k, boolean v) { p(c).edit().putBoolean(k, v).apply(); }

    public static int getInt(Context c, String k, int d) { return p(c).getInt(k, d); }

    public static void putInt(Context c, String k, int v) { p(c).edit().putInt(k, v).apply(); }
}
