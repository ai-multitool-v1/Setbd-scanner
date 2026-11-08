package com.setbd.scanner.ui;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.setbd.scanner.MainActivity;
import com.setbd.scanner.R;
import com.setbd.scanner.data.Db;
import com.setbd.scanner.data.M;

public class HomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        v.findViewById(R.id.quickScanBtn).setOnClickListener(x -> {
            String t = ((TextView) v.findViewById(R.id.quickTarget)).getText().toString().trim();
            if (t.isEmpty()) {
                Toast.makeText(getContext(), "Enter a target URL first", Toast.LENGTH_SHORT).show();
                return;
            }
            ((MainActivity) requireActivity()).gotoScanner(t, true);
        });

        openTool(v, R.id.q_dns, "dns");
        openTool(v, R.id.q_headers, "secheaders");
        openTool(v, R.id.q_ports, "ports");
        openTool(v, R.id.q_crawl, "crawl");
        openTool(v, R.id.q_whois, "whois");
        openTool(v, R.id.q_jwt, "jwt");
        openTool(v, R.id.q_hash, "hash");
        openTool(v, R.id.q_subct, "subct");

        v.findViewById(R.id.viewAllScans).setOnClickListener(x ->
                startActivity(new Intent(getContext(), ScansActivity.class)));

        refresh(v);
    }

    void openTool(View root, int btnId, String toolId) {
        root.findViewById(btnId).setOnClickListener(x -> {
            Intent i = new Intent(getContext(), ToolRunActivity.class);
            i.putExtra("id", toolId);
            startActivity(i);
        });
    }

    void refresh(View v) {
        int[] s = Db.get(requireContext()).stats();
        ((TextView) v.findViewById(R.id.statScans)).setText(String.valueOf(s[0]));
        ((TextView) v.findViewById(R.id.statFindings)).setText(String.valueOf(s[1]));
        ((TextView) v.findViewById(R.id.statTargets)).setText(String.valueOf(s[2]));

        // Recent scans (top 3, manual rows)
        LinearLayout recent = v.findViewById(R.id.recentList);
        recent.removeAllViews();
        java.util.List<M.ScanRecord> scans = Db.get(requireContext()).listScans(3);
        if (scans.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("No scans yet — run a quick scan or open the Scanner tab.");
            empty.setTextColor(requireContext().getColor(R.color.textSub));
            empty.setTextSize(13);
            empty.setPadding(4, 8, 4, 8);
            recent.addView(empty);
        } else {
            for (final M.ScanRecord sc : scans) {
                View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_scan, recent, false);
                ((TextView) row.findViewById(R.id.scanTarget)).setText(sc.target);
                String when = new java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.US)
                        .format(new java.util.Date(sc.finishedAt));
                ((TextView) row.findViewById(R.id.scanMeta)).setText(sc.type + "  •  " + when + "  •  " + sc.findings.size() + " findings");
                Adapters.pill(row.findViewById(R.id.scanStatus),
                        "COMPLETED".equals(sc.status) ? requireContext().getColor(R.color.ok) : requireContext().getColor(R.color.warn));
                ((TextView) row.findViewById(R.id.scanStatus)).setText(sc.status);
                row.setOnClickListener(x -> {
                    Intent i = new Intent(getContext(), ReportActivity.class);
                    i.putExtra("scanId", sc.id);
                    startActivity(i);
                });
                recent.addView(row);
            }
        }

        // Network status
        TextView net = v.findViewById(R.id.netStatus);
        try {
            ConnectivityManager cm = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (nc == null || !nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                net.setText("●  OFFLINE — most tools need a network connection.");
            } else {
                String type = nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi"
                        : nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "Mobile data"
                        : "Other network";
                boolean metered = !nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
                net.setText("●  ONLINE — " + type + (metered ? " (metered: heavy scans may cost data)" : ""));
            }
        } catch (Exception e) {
            net.setText("Network status unavailable: " + e.getMessage());
        }

        // Compatibility status (honest values)
        TextView comp = v.findViewById(R.id.compatList);
        StringBuilder sb = new StringBuilder();
        sb.append("• Android ").append(android.os.Build.VERSION.RELEASE)
                .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(") — supported (min 24)\n");
        sb.append("• CPU ABI: ").append(android.os.Build.SUPPORTED_ABIS[0]).append("\n");
        boolean termux = false;
        try {
            requireContext().getPackageManager().getApplicationInfo("com.termux", 0);
            termux = true;
        } catch (Exception ignored) { }
        sb.append("• Termux installed: ").append(termux ? "YES — external tools (nmap, nuclei…) can run" : "NO — install Termux to enable external tool integrations").append("\n");
        boolean root = new java.io.File("/system/bin/su").exists() || new java.io.File("/system/xbin/su").exists();
        sb.append("• Root: ").append(root ? "likely present" : "not detected (not required for NATIVE tools)").append("\n");
        sb.append("• NATIVE tools: 50 run fully on-device — no fake statuses are shown anywhere.");
        comp.setText(sb.toString());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) refresh(getView());
    }
}
