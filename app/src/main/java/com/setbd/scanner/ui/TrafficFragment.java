package com.setbd.scanner.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.setbd.scanner.R;
import com.setbd.scanner.core.Net;
import com.setbd.scanner.core.Prefs;
import com.setbd.scanner.core.Reporter;
import com.setbd.scanner.data.Db;
import com.setbd.scanner.data.M;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Burp-style traffic workbench: request builder/repeater, history, decoders, HAR export. */
public class TrafficFragment extends Fragment {

    Spinner method;
    EditText url, headers, body;
    MaterialCardView respCard;
    TextView respStatus, respHeaders, respBody;
    MaterialSwitch pretty;

    View panelRequest, panelHistory, panelTools;
    RecyclerView historyList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_traffic, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        method = v.findViewById(R.id.methodSpinner);
        ArrayAdapter<String> ad = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                new String[]{"GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"});
        method.setAdapter(ad);

        url = v.findViewById(R.id.reqUrl);
        headers = v.findViewById(R.id.reqHeaders);
        body = v.findViewById(R.id.reqBody);
        respCard = v.findViewById(R.id.respCard);
        respStatus = v.findViewById(R.id.respStatus);
        respHeaders = v.findViewById(R.id.respHeaders);
        respBody = v.findViewById(R.id.respBody);
        pretty = v.findViewById(R.id.prettySwitch);

        panelRequest = v.findViewById(R.id.panelRequest);
        panelHistory = v.findViewById(R.id.panelHistory);
        panelTools = v.findViewById(R.id.panelTools);
        historyList = v.findViewById(R.id.historyList);

        v.findViewById(R.id.tabRequest).setOnClickListener(x -> switchTab(0));
        v.findViewById(R.id.tabHistory).setOnClickListener(x -> switchTab(1));
        v.findViewById(R.id.tabTools).setOnClickListener(x -> switchTab(2));

        v.findViewById(R.id.sendBtn).setOnClickListener(x -> send());
        v.findViewById(R.id.saveReqBtn).setOnClickListener(x -> {
            Db.get(requireContext()).addRequest("req-" + Reporter.stamp(),
                    method.getSelectedItem().toString(), url.getText().toString().trim(),
                    headers.getText().toString(), body.getText().toString());
            Toast.makeText(getContext(), "Request saved to history", Toast.LENGTH_SHORT).show();
        });

        v.findViewById(R.id.pB64).setOnClickListener(x -> openRun("b64"));
        v.findViewById(R.id.pJwt).setOnClickListener(x -> openRun("jwt"));
        v.findViewById(R.id.pWs).setOnClickListener(x -> openRun("wstest"));
        v.findViewById(R.id.pDiff).setOnClickListener(x -> openRun("textdiff"));
        v.findViewById(R.id.pHar).setOnClickListener(x -> exportHar());

        pretty.setOnCheckedChangeListener((b, checked) -> {
            if (respCard.getVisibility() == View.VISIBLE) renderBody(lastRaw);
        });

        switchTab(0);
    }

    void openRun(String id) {
        android.content.Intent i = new android.content.Intent(getContext(), ToolRunActivity.class);
        i.putExtra("id", id);
        startActivity(i);
    }

    void switchTab(int t) {
        panelRequest.setVisibility(t == 0 ? View.VISIBLE : View.GONE);
        panelHistory.setVisibility(t == 1 ? View.VISIBLE : View.GONE);
        panelTools.setVisibility(t == 2 ? View.VISIBLE : View.GONE);
        if (t == 1) refreshHistory();
    }

    void refreshHistory() {
        List<M.SavedRequest> reqs = Db.get(requireContext()).listRequests();
        historyList.setLayoutManager(new LinearLayoutManager(getContext()));
        historyList.setAdapter(new Adapters.HistoryAdapter(requireContext(), reqs, r -> {
            method.setSelection(indexOf(r.method));
            url.setText(r.url);
            headers.setText(r.headers);
            body.setText(r.body);
            switchTab(0);
        }, this::refreshHistory));
    }

    int indexOf(String m) {
        String[] all = {"GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"};
        for (int i = 0; i < all.length; i++) if (all[i].equals(m)) return i;
        return 0;
    }

    Map<String, String> parseHeaders(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        if (text == null) return out;
        for (String line : text.split("\n")) {
            int idx = line.indexOf(':');
            if (idx > 0) out.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
        }
        return out;
    }

    String lastRaw = "";

    void send() {
        String u = url.getText().toString().trim();
        if (u.isEmpty()) {
            Toast.makeText(getContext(), "Enter a URL", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!u.startsWith("http")) u = "https://" + u;
        final String target = u;
        final String m = method.getSelectedItem().toString();
        final Map<String, String> h = parseHeaders(headers.getText().toString());
        final String b = body.getText().toString();
        respCard.setVisibility(View.VISIBLE);
        respStatus.setText("Sending…");
        respStatus.setTextColor(requireContext().getColor(R.color.textSub));
        respHeaders.setText("");
        respBody.setText("");
        new Thread(() -> {
            try {
                Net.Resp r = Net.request(m, target, h, b, Prefs.get(requireContext(), "ua", null));
                lastRaw = r.body;
                StringBuilder hh = new StringBuilder();
                for (Map.Entry<String, List<String>> e : r.headers.entrySet())
                    hh.append(e.getKey()).append(": ").append(String.join(", ", e.getValue())).append('\n');
                final String hh2 = hh.toString();
                if (getActivity() == null || getActivity().isFinishing()) return;
                requireActivity().runOnUiThread(() -> {
                    respStatus.setText(r.code + " " + r.message + "   (" + r.timeMs + " ms, " + r.body.length() + " B)");
                    respStatus.setTextColor(requireContext().getColor(
                            r.code < 300 ? R.color.ok : r.code < 400 ? R.color.low : r.code < 500 ? R.color.warn : R.color.danger));
                    respHeaders.setText(hh2);
                    renderBody(r.body);
                });
            } catch (Exception e) {
                if (getActivity() == null || getActivity().isFinishing()) return;
                requireActivity().runOnUiThread(() -> {
                    respStatus.setText("FAILED");
                    respStatus.setTextColor(requireContext().getColor(R.color.danger));
                    respBody.setText("Error: " + e.getMessage());
                });
            }
        }, "setbd-http").start();
    }

    void renderBody(String raw) {
        if (raw == null) return;
        if (pretty.isChecked()) {
            String t = raw.trim();
            if (t.startsWith("{") || t.startsWith("[")) {
                try {
                    respBody.setText(t.startsWith("[") ? new JSONArray(t).toString(2) : new JSONObject(t).toString(2));
                    return;
                } catch (Exception ignored) { }
            }
        }
        respBody.setText(raw);
    }

    void exportHar() {
        try {
            JSONObject log = new JSONObject();
            log.put("version", "1.2");
            log.put("creator", new JSONObject().put("name", "SETBD Scanner").put("version", "1.0"));
            JSONArray entries = new JSONArray();
            for (M.SavedRequest r : Db.get(requireContext()).listRequests()) {
                JSONObject entry = new JSONObject();
                entry.put("startedDateTime", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                        .format(new java.util.Date(r.createdAt)));
                entry.put("time", 0);
                JSONObject req = new JSONObject();
                req.put("method", r.method);
                req.put("url", r.url);
                req.put("httpVersion", "HTTP/1.1");
                JSONArray hs = new JSONArray();
                for (Map.Entry<String, String> e : parseHeaders(r.headers).entrySet())
                    hs.put(new JSONObject().put("name", e.getKey()).put("value", e.getValue()));
                req.put("headers", hs);
                req.put("headersSize", -1);
                req.put("bodySize", r.body == null ? 0 : r.body.length());
                if (r.body != null && !r.body.isEmpty())
                    req.put("postData", new JSONObject().put("mimeType", "application/json").put("text", r.body));
                JSONObject resp = new JSONObject();
                resp.put("status", 0);
                resp.put("statusText", "");
                resp.put("httpVersion", "HTTP/1.1");
                resp.put("headers", new JSONArray());
                resp.put("content", new JSONObject().put("size", 0));
                entry.put("request", req);
                entry.put("response", resp);
                entry.put("cache", new JSONObject());
                entries.put(entry);
            }
            log.put("entries", entries);
            JSONObject har = new JSONObject().put("log", log);
            String name = "setbd-traffic-" + Reporter.stamp() + ".har";
            android.net.Uri uri = Reporter.saveText(requireContext(), name, har.toString(2), "application/json");
            Reporter.share(requireContext(), uri, "application/json", "Export HAR");
        } catch (Exception e) {
            Toast.makeText(getContext(), "HAR export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
