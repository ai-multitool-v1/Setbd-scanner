package com.setbd.scanner.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.setbd.scanner.R;
import com.setbd.scanner.core.Registry;

import java.util.ArrayList;
import java.util.List;

public class ToolsFragment extends Fragment {

    String currentCategory = "All";
    String query = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tools, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        RecyclerView list = v.findViewById(R.id.toolsList);
        list.setLayoutManager(new LinearLayoutManager(getContext()));

        ChipGroup chips = v.findViewById(R.id.chipGroup);
        String[] cats = Registry.categories();
        Chip all = mkChip("All");
        all.setId(View.generateViewId());
        all.setChecked(true);
        chips.addView(all);
        for (String c : cats) {
            Chip ch = mkChip(c);
            ch.setId(View.generateViewId());
            chips.addView(ch);
        }
        chips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            Chip sel = group.findViewById(checkedIds.get(0));
            if (sel != null) {
                currentCategory = sel.getText().toString();
                apply(list);
            }
        });

        EditText search = v.findViewById(R.id.searchBox);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().toLowerCase(java.util.Locale.US);
                apply(list);
            }
        });

        apply(list);
    }

    Chip mkChip(String text) {
        Chip ch = new Chip(requireContext());
        ch.setText(text);
        ch.setCheckable(true);
        ch.setChipBackgroundColorResource(R.color.chip);
        ch.setTextColor(requireContext().getColor(R.color.textMain));
        return ch;
    }

    void apply(RecyclerView list) {
        List<Registry.ToolInfo> src = "All".equals(currentCategory)
                ? Registry.all() : Registry.byCategory(currentCategory);
        List<Registry.ToolInfo> out = new ArrayList<>();
        for (Registry.ToolInfo t : src) {
            if (query.isEmpty()
                    || t.name.toLowerCase(java.util.Locale.US).contains(query)
                    || t.desc.toLowerCase(java.util.Locale.US).contains(query)
                    || t.category.toLowerCase(java.util.Locale.US).contains(query))
                out.add(t);
        }
        Adapters.ToolAdapter ad = new Adapters.ToolAdapter(requireContext(), out, t -> {
            Intent i = new Intent(getContext(), ToolDetailActivity.class);
            i.putExtra("id", t.id);
            startActivity(i);
        });
        list.setAdapter(ad);
    }
}
