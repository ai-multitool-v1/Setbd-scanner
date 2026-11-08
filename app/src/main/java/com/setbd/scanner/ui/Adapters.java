package com.setbd.scanner.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.setbd.scanner.R;
import com.setbd.scanner.core.Registry;
import com.setbd.scanner.data.Db;
import com.setbd.scanner.data.M;

import java.util.List;
import java.util.Locale;

/** RecyclerView adapters for tools, scans, targets, findings and saved requests. */
public class Adapters {

    static int sevColor(Context ctx, String sev) {
        if (sev == null) return ContextCompat.getColor(ctx, R.color.textSub);
        switch (sev) {
            case "HIGH": return ContextCompat.getColor(ctx, R.color.danger);
            case "MEDIUM": return ContextCompat.getColor(ctx, R.color.warn);
            case "LOW": return ContextCompat.getColor(ctx, R.color.low);
            default: return ContextCompat.getColor(ctx, R.color.textSub);
        }
    }

    static void pill(TextView tv, int color) {
        tv.setBackgroundTintList(ColorStateList.valueOf(Color.argb(38, Color.red(color), Color.green(color), Color.blue(color))));
        tv.setTextColor(color);
    }

    // ---------------- TOOLS ----------------

    public static class ToolAdapter extends RecyclerView.Adapter<ToolAdapter.VH> {
        public interface Cb { void onTool(Registry.ToolInfo t); }

        final List<Registry.ToolInfo> items;
        final Cb cb;
        final Context ctx;

        public ToolAdapter(Context ctx, List<Registry.ToolInfo> items, Cb cb) {
            this.ctx = ctx;
            this.items = items;
            this.cb = cb;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tool, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            final Registry.ToolInfo t = items.get(pos);
            h.icon.setImageResource(t.icon);
            h.name.setText(t.name);
            h.desc.setText(t.desc);
            int c = t.status.equals("NATIVE") ? ContextCompat.getColor(ctx, R.color.ok)
                    : t.status.equals("TERMUX") ? ContextCompat.getColor(ctx, R.color.warn)
                    : ContextCompat.getColor(ctx, R.color.low);
            pill(h.status, c);
            h.status.setText(t.status.equals("NATIVE") ? "NATIVE — RUNS ON DEVICE" : t.status + " — SETUP REQUIRED");
            boolean fav = Db.get(ctx).isFavorite(t.id);
            h.fav.setImageResource(fav ? R.drawable.ic_star : R.drawable.ic_star);
            h.fav.setColorFilter(fav ? ContextCompat.getColor(ctx, R.color.warn) : ContextCompat.getColor(ctx, R.color.divider));
            h.fav.setOnClickListener(v -> {
                Db.get(ctx).toggleFavorite(t.id);
                notifyItemChanged(h.getAdapterPosition());
            });
            h.card.setOnClickListener(v -> cb.onTool(t));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            com.google.android.material.card.MaterialCardView card;
            ImageView icon, fav;
            TextView name, desc, status;

            VH(View v) {
                super(v);
                card = v.findViewById(R.id.card);
                icon = v.findViewById(R.id.toolIcon);
                fav = v.findViewById(R.id.favBtn);
                name = v.findViewById(R.id.toolName);
                desc = v.findViewById(R.id.toolDesc);
                status = v.findViewById(R.id.toolStatus);
            }
        }
    }

    // ---------------- SCANS ----------------

    public static class ScanAdapter extends RecyclerView.Adapter<ScanAdapter.VH> {
        public interface Cb { void onScan(M.ScanRecord s); }

        final List<M.ScanRecord> items;
        final Cb cb;
        final Context ctx;

        public ScanAdapter(Context ctx, List<M.ScanRecord> items, Cb cb) {
            this.ctx = ctx;
            this.items = items;
            this.cb = cb;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_scan, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            M.ScanRecord s = items.get(pos);
            h.target.setText(s.target);
            String when = new java.text.SimpleDateFormat("dd MMM HH:mm", Locale.US).format(new java.util.Date(s.finishedAt));
            h.meta.setText(s.type + "  •  " + when + "  •  " + s.findings.size() + " findings  •  " + s.summary);
            int c = "COMPLETED".equals(s.status) ? ContextCompat.getColor(ctx, R.color.ok)
                    : "FAILED".equals(s.status) ? ContextCompat.getColor(ctx, R.color.danger)
                    : ContextCompat.getColor(ctx, R.color.warn);
            pill(h.status, c);
            h.status.setText(s.status);
            h.card.setOnClickListener(v -> cb.onScan(s));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            com.google.android.material.card.MaterialCardView card;
            TextView target, meta, status;

            VH(View v) {
                super(v);
                card = v.findViewById(R.id.card);
                target = v.findViewById(R.id.scanTarget);
                meta = v.findViewById(R.id.scanMeta);
                status = v.findViewById(R.id.scanStatus);
            }
        }
    }

    // ---------------- TARGETS ----------------

    public static class TargetAdapter extends RecyclerView.Adapter<TargetAdapter.VH> {
        public interface Cb { void onTarget(M.Target t); }

        final List<M.Target> items;
        final Cb cb;
        final Context ctx;
        final Runnable refresh;

        public TargetAdapter(Context ctx, List<M.Target> items, Cb cb, Runnable refresh) {
            this.ctx = ctx;
            this.items = items;
            this.cb = cb;
            this.refresh = refresh;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_target, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            M.Target t = items.get(pos);
            h.name.setText(t.name);
            h.url.setText(t.url);
            h.notes.setText(t.notes == null || t.notes.isEmpty() ? t.allowedDomains : t.notes);
            int c = t.authorized ? ContextCompat.getColor(ctx, R.color.ok) : ContextCompat.getColor(ctx, R.color.warn);
            pill(h.auth, c);
            h.auth.setText(t.authorized ? "AUTHORIZED" : "UNAUTHORIZED");
            h.card.setOnClickListener(v -> cb.onTarget(t));
            h.del.setOnClickListener(v -> {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                        .setTitle("Delete target")
                        .setMessage("Remove " + t.name + " from the target manager?")
                        .setPositiveButton("Delete", (d, w) -> {
                            Db.get(ctx).deleteTarget(t.id);
                            if (refresh != null) refresh.run();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            com.google.android.material.card.MaterialCardView card;
            TextView name, url, notes, auth;
            ImageView del;

            VH(View v) {
                super(v);
                card = v.findViewById(R.id.card);
                name = v.findViewById(R.id.tName);
                url = v.findViewById(R.id.tUrl);
                notes = v.findViewById(R.id.tNotes);
                auth = v.findViewById(R.id.tAuth);
                del = v.findViewById(R.id.tDelete);
            }
        }
    }

    // ---------------- FINDINGS ----------------

    public static class FindingAdapter extends RecyclerView.Adapter<FindingAdapter.VH> {
        final List<M.Finding> items;
        final Context ctx;

        public FindingAdapter(Context ctx, List<M.Finding> items) {
            this.ctx = ctx;
            this.items = items;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_finding, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            M.Finding f = items.get(pos);
            int c = sevColor(ctx, f.severity);
            pill(h.sev, c);
            h.sev.setText(f.severity);
            h.cvss.setText(f.cvss != null && !"0.0".equals(f.cvss) && !f.cvss.isEmpty() ? "CVSS ~" + f.cvss : "");
            h.title.setText(f.title);
            h.detail.setText(f.detail);
            h.url.setText(f.url == null ? "" : f.url);
            h.evidence.setText(f.evidence == null || f.evidence.isEmpty() ? "" : f.evidence);
            h.evidence.setVisibility(f.evidence == null || f.evidence.isEmpty() ? View.GONE : View.VISIBLE);
            h.reco.setText(f.recommendation == null ? "" : "✔ " + f.recommendation);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView sev, cvss, title, detail, url, evidence, reco;

            VH(View v) {
                super(v);
                sev = v.findViewById(R.id.fSeverity);
                cvss = v.findViewById(R.id.fCvss);
                title = v.findViewById(R.id.fTitle);
                detail = v.findViewById(R.id.fDetail);
                url = v.findViewById(R.id.fUrl);
                evidence = v.findViewById(R.id.fEvidence);
                reco = v.findViewById(R.id.fReco);
            }
        }
    }

    // ---------------- SAVED REQUESTS ----------------

    public static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {
        public interface Cb { void onLoad(M.SavedRequest r); }

        final List<M.SavedRequest> items;
        final Cb cb;
        final Context ctx;
        final Runnable refresh;

        public HistoryAdapter(Context ctx, List<M.SavedRequest> items, Cb cb, Runnable refresh) {
            this.ctx = ctx;
            this.items = items;
            this.cb = cb;
            this.refresh = refresh;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            M.SavedRequest r = items.get(pos);
            h.title.setText(r.method + "  " + (r.name == null || r.name.isEmpty() ? "(unsaved name)" : r.name));
            h.sub.setText(r.url);
            h.load.setOnClickListener(v -> cb.onLoad(r));
            h.del.setOnClickListener(v -> {
                Db.get(ctx).deleteRequest(r.id);
                if (refresh != null) refresh.run();
            });
            h.card.setOnClickListener(v -> cb.onLoad(r));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            com.google.android.material.card.MaterialCardView card;
            TextView title, sub;
            ImageView load, del;

            VH(View v) {
                super(v);
                card = v.findViewById(R.id.card);
                title = v.findViewById(R.id.hTitle);
                sub = v.findViewById(R.id.hSub);
                load = v.findViewById(R.id.hLoad);
                del = v.findViewById(R.id.hDelete);
            }
        }
    }
}
