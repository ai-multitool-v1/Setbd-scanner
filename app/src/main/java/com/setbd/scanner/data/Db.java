package com.setbd.scanner.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Local secure storage: targets, scans, tool runs, saved requests, audit log, favorites. */
public class Db extends SQLiteOpenHelper {

    private static Db inst;

    public static synchronized Db get(Context c) {
        if (inst == null) inst = new Db(c.getApplicationContext());
        return inst;
    }

    private Db(Context c) {
        super(c, "setbd.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE targets(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, url TEXT," +
                "domains TEXT, excluded TEXT, authorized INTEGER DEFAULT 0, notes TEXT, created INTEGER)");
        db.execSQL("CREATE TABLE scans(id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT, target TEXT," +
                "status TEXT, summary TEXT, findings TEXT, tools TEXT, started INTEGER, finished INTEGER)");
        db.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT, toolId TEXT, toolName TEXT," +
                "target TEXT, summary TEXT, output TEXT, created INTEGER)");
        db.execSQL("CREATE TABLE requests(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, method TEXT," +
                "url TEXT, headers TEXT, body TEXT, created INTEGER)");
        db.execSQL("CREATE TABLE audit(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, action TEXT, detail TEXT)");
        db.execSQL("CREATE TABLE favorites(toolId TEXT PRIMARY KEY, created INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) { }

    // ---------- Targets ----------
    public long addTarget(String name, String url, String domains, String excluded, boolean authorized, String notes) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("url", url);
        v.put("domains", domains);
        v.put("excluded", excluded);
        v.put("authorized", authorized ? 1 : 0);
        v.put("notes", notes);
        v.put("created", System.currentTimeMillis());
        return getWritableDatabase().insert("targets", null, v);
    }

    public List<M.Target> listTargets() {
        List<M.Target> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM targets ORDER BY created DESC", null);
        while (c.moveToNext()) {
            M.Target t = new M.Target();
            t.id = c.getLong(0);
            t.name = c.getString(1);
            t.url = c.getString(2);
            t.allowedDomains = c.getString(3);
            t.excludedPaths = c.getString(4);
            t.authorized = c.getInt(5) == 1;
            t.notes = c.getString(6);
            t.createdAt = c.getLong(7);
            out.add(t);
        }
        c.close();
        return out;
    }

    public void deleteTarget(long id) {
        getWritableDatabase().delete("targets", "id=?", new String[]{String.valueOf(id)});
    }

    // ---------- Scans ----------
    public long addScan(String type, String target, String status, String summary,
                        List<M.Finding> findings, List<String> tools, long started, long finished) {
        try {
            JSONArray arr = new JSONArray();
            if (findings != null) {
                for (M.Finding f : findings) {
                    JSONObject o = new JSONObject();
                    o.put("severity", f.severity);
                    o.put("cvss", f.cvss);
                    o.put("title", f.title);
                    o.put("detail", f.detail);
                    o.put("evidence", f.evidence);
                    o.put("url", f.url);
                    o.put("recommendation", f.recommendation);
                    arr.put(o);
                }
            }
            JSONArray tl = new JSONArray();
            if (tools != null) for (String t : tools) tl.put(t);
            ContentValues v = new ContentValues();
            v.put("type", type);
            v.put("target", target);
            v.put("status", status);
            v.put("summary", summary);
            v.put("findings", arr.toString());
            v.put("tools", tl.toString());
            v.put("started", started);
            v.put("finished", finished);
            return getWritableDatabase().insert("scans", null, v);
        } catch (Exception e) {
            return -1;
        }
    }

    public List<M.ScanRecord> listScans(int limit) {
        List<M.ScanRecord> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM scans ORDER BY finished DESC LIMIT " + limit, null);
        while (c.moveToNext()) {
            M.ScanRecord s = new M.ScanRecord();
            s.id = c.getLong(0);
            s.type = c.getString(1);
            s.target = c.getString(2);
            s.status = c.getString(3);
            s.summary = c.getString(4);
            s.startedAt = c.getLong(7);
            s.finishedAt = c.getLong(8);
            try {
                JSONArray arr = new JSONArray(c.getString(5));
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    s.findings.add(new M.Finding(o.optString("severity"), o.optString("cvss"),
                            o.optString("title"), o.optString("detail"), o.optString("evidence"),
                            o.optString("url"), o.optString("recommendation")));
                }
                JSONArray tl = new JSONArray(c.getString(6));
                for (int i = 0; i < tl.length(); i++) s.toolsUsed.add(tl.getString(i));
            } catch (Exception ignored) { }
            out.add(s);
        }
        c.close();
        return out;
    }

    public void deleteScan(long id) {
        getWritableDatabase().delete("scans", "id=?", new String[]{String.valueOf(id)});
    }

    // ---------- Tool run history ----------
    public void addHistory(String toolId, String toolName, String target, String summary, String output) {
        ContentValues v = new ContentValues();
        v.put("toolId", toolId);
        v.put("toolName", toolName);
        v.put("target", target);
        v.put("summary", summary);
        v.put("output", output);
        v.put("created", System.currentTimeMillis());
        getWritableDatabase().insert("history", null, v);
    }

    public List<M.ToolRun> listHistory(int limit) {
        List<M.ToolRun> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM history ORDER BY created DESC LIMIT " + limit, null);
        while (c.moveToNext()) {
            M.ToolRun r = new M.ToolRun();
            r.id = c.getLong(0);
            r.toolId = c.getString(1);
            r.toolName = c.getString(2);
            r.target = c.getString(3);
            r.summary = c.getString(4);
            r.output = c.getString(5);
            r.createdAt = c.getLong(6);
            out.add(r);
        }
        c.close();
        return out;
    }

    public void deleteHistory(long id) {
        getWritableDatabase().delete("history", "id=?", new String[]{String.valueOf(id)});
    }

    // ---------- Saved requests ----------
    public void addRequest(String name, String method, String url, String headers, String body) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("method", method);
        v.put("url", url);
        v.put("headers", headers);
        v.put("body", body);
        v.put("created", System.currentTimeMillis());
        getWritableDatabase().insert("requests", null, v);
    }

    public List<M.SavedRequest> listRequests() {
        List<M.SavedRequest> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM requests ORDER BY created DESC", null);
        while (c.moveToNext()) {
            M.SavedRequest r = new M.SavedRequest();
            r.id = c.getLong(0);
            r.name = c.getString(1);
            r.method = c.getString(2);
            r.url = c.getString(3);
            r.headers = c.getString(4);
            r.body = c.getString(5);
            r.createdAt = c.getLong(6);
            out.add(r);
        }
        c.close();
        return out;
    }

    public void deleteRequest(long id) {
        getWritableDatabase().delete("requests", "id=?", new String[]{String.valueOf(id)});
    }

    // ---------- Audit log ----------
    public void audit(String action, String detail) {
        ContentValues v = new ContentValues();
        v.put("ts", System.currentTimeMillis());
        v.put("action", action);
        v.put("detail", detail);
        try {
            getWritableDatabase().insert("audit", null, v);
        } catch (Exception ignored) { }
    }

    public List<M.AuditEntry> listAudit(int limit) {
        List<M.AuditEntry> out = new ArrayList<>();
        try {
            Cursor c = getReadableDatabase().rawQuery("SELECT * FROM audit ORDER BY ts DESC LIMIT " + limit, null);
            while (c.moveToNext()) {
                M.AuditEntry a = new M.AuditEntry();
                a.id = c.getLong(0);
                a.ts = c.getLong(1);
                a.action = c.getString(2);
                a.detail = c.getString(3);
                out.add(a);
            }
            c.close();
        } catch (Exception ignored) { }
        return out;
    }

    // ---------- Favorites ----------
    public boolean isFavorite(String toolId) {
        Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM favorites WHERE toolId=?", new String[]{toolId});
        boolean r = c.moveToFirst();
        c.close();
        return r;
    }

    public void toggleFavorite(String toolId) {
        SQLiteDatabase db = getWritableDatabase();
        if (isFavorite(toolId)) db.delete("favorites", "toolId=?", new String[]{toolId});
        else {
            ContentValues v = new ContentValues();
            v.put("toolId", toolId);
            v.put("created", System.currentTimeMillis());
            db.insert("favorites", null, v);
        }
    }

    public List<String> favoriteIds() {
        List<String> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT toolId FROM favorites ORDER BY created DESC", null);
        while (c.moveToNext()) out.add(c.getString(0));
        c.close();
        return out;
    }

    // ---------- Stats ----------
    public int[] stats() {
        int[] s = new int[3]; // scans, findings, targets
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM scans", null);
        if (c.moveToFirst()) s[0] = c.getInt(0);
        c.close();
        c = getReadableDatabase().rawQuery("SELECT findings FROM scans", null);
        while (c.moveToNext()) {
            try {
                s[1] += new JSONArray(c.getString(0)).length();
            } catch (Exception ignored) { }
        }
        c.close();
        c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM targets", null);
        if (c.moveToFirst()) s[2] = c.getInt(0);
        c.close();
        return s;
    }

    public void wipeAll() {
        SQLiteDatabase db = getWritableDatabase();
        for (String t : new String[]{"targets", "scans", "history", "requests", "audit", "favorites"})
            db.delete(t, null, null);
    }
}
