package com.astra.lifeorganizer.ui;

import android.graphics.Color;
import android.os.Bundle;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.util.TypedValue;
import androidx.core.graphics.ColorUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.astra.lifeorganizer.R;
import com.astra.lifeorganizer.data.entities.CompletionHistory;
import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.data.entities.ItemOccurrence;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HabitTrackerFragment extends Fragment {
    
    private ItemViewModel itemViewModel;
    private HabitAdapter adapter;
    private GridLayout gridHeatmap;
    private LinearLayout layoutFrequencyChart;
    private Spinner spinnerChartRange;
    private Button btnEditMode;
    
    private List<Item> activeHabits = new ArrayList<>();
    private List<Item> allHabits = new ArrayList<>();
    private List<CompletionHistory> allHistory = new ArrayList<>();
    private java.util.Map<Long, ItemOccurrence> todayOccurrences = new java.util.HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_habit_tracker, container, false);
        
        gridHeatmap = root.findViewById(R.id.grid_overall_heatmap);
        layoutFrequencyChart = root.findViewById(R.id.layout_overall_chart);
        spinnerChartRange = root.findViewById(R.id.spinner_chart_range);
        btnEditMode = root.findViewById(R.id.btn_edit_mode);
        
        RecyclerView recyclerView = root.findViewById(R.id.rv_habits);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        adapter = new HabitAdapter(new HabitAdapter.OnItemClickListener() {
            @Override
            public void onDoneClicked(Item item) {
                itemViewModel.incrementHabitCount(item.id, System.currentTimeMillis());
            }

            @Override
            public void onItemClicked(Item item) {
                HabitDetailBottomSheetFragment.newInstance(item.id)
                    .show(getParentFragmentManager(), "HabitDetailBottomSheet");
            }
            
            @Override
            public void onEditClicked(Item item) {
                HabitEditLogBottomSheetFragment.newInstance(item.id)
                    .show(getParentFragmentManager(), "SingleHabitEditLog");
            }

            @Override
            public void onSelectionChanged(int count) {
                View root = getView();
                if (root == null) return;
                View toolbar = root.findViewById(R.id.card_selection_toolbar);
                if (toolbar != null) {
                    toolbar.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
                    TextView tvCount = toolbar.findViewById(R.id.tv_selection_count);
                    if (tvCount != null) tvCount.setText(count + " selected");
                }
            }
        });
        recyclerView.setAdapter(adapter);

        itemViewModel = new ViewModelProvider(this).get(ItemViewModel.class);

        itemViewModel.getItemsByType("habit").observe(getViewLifecycleOwner(), items -> {
            if (items != null) {
                allHabits = items;
                activeHabits = items; // Show all habits, sorting will handle "active" vs "pending"
                sortAndSetHabits();
                updateVisuals();
            }
        });

        itemViewModel.getAllHistory().observe(getViewLifecycleOwner(), histories -> {
            if (histories != null) {
                allHistory = histories;
                updateVisuals();
            }
        });

        // Track occurrences for today to show progress
        long now = System.currentTimeMillis();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(now);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        long startOfDay = cal.getTimeInMillis();
        long endOfDay = startOfDay + (24 * 60 * 60 * 1000L) - 1;

        itemViewModel.getOccurrencesForDay(startOfDay, endOfDay).observe(getViewLifecycleOwner(), occurrences -> {
            if (occurrences != null) {
                todayOccurrences.clear();
                for (ItemOccurrence o : occurrences) todayOccurrences.put(o.itemId, o);
                adapter.setOccurrences(occurrences);
                sortAndSetHabits();
            }
        });

        spinnerChartRange.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                drawOverallChart(pos);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        
        btnEditMode.setOnClickListener(v -> {
            HabitEditLogBottomSheetFragment.newInstance()
                .show(getParentFragmentManager(), "HabitEditLogBottomSheet");
        });

        setupSelectionToolbar(root);
        return root;
    }

    private void setupSelectionToolbar(View root) {
        View toolbar = root.findViewById(R.id.card_selection_toolbar);
        if (toolbar == null) return;

        // In HabitTracker, ICS share might be less common but IcsUtils handles them as VTODO if we want.
        // However, user specifically asked for Events for ICS. For habits, we'll hide ICS share to keep it clean.
        root.findViewById(R.id.btn_share_ics).setVisibility(View.GONE);

        root.findViewById(R.id.btn_clear_selection).setOnClickListener(v -> {
            adapter.clearSelection();
        });

        root.findViewById(R.id.btn_delete_selected).setOnClickListener(v -> {
            int count = adapter.getSelectedIds().size();
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete " + count + " habits?")
                    .setMessage("This will remove all logs and history for the selected habits.")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        for (Long id : adapter.getSelectedIds()) {
                            Item item = findHabitById(id);
                            if (item != null) itemViewModel.delete(item);
                        }
                        adapter.clearSelection();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        root.findViewById(R.id.btn_share_text).setOnClickListener(v -> {
            com.astra.lifeorganizer.utils.ShareUtils.shareItemsAsText(requireContext(), getSelectedItems());
        });
    }

    private Item findHabitById(long id) {
        for (Item h : activeHabits) if (h.id == id) return h;
        return null;
    }

    private void sortAndSetHabits() {
        if (activeHabits == null) return;
        ArrayList<Item> sorted = new ArrayList<>(activeHabits);
        long now = System.currentTimeMillis();
        sorted.sort((a, b) -> {
            boolean aValid = com.astra.lifeorganizer.utils.RecurrenceRuleParser.isValidOccurrence(now, a);
            boolean bValid = com.astra.lifeorganizer.utils.RecurrenceRuleParser.isValidOccurrence(now, b);

            if (aValid != bValid) {
                return aValid ? -1 : 1; // Valid for today (Active) comes first
            }

            boolean aDone = isHabitCompletedForToday(a);
            boolean bDone = isHabitCompletedForToday(b);
            if (aDone != bDone) {
                return aDone ? 1 : -1; // Not done comes first
            }
            String aTitle = a != null && a.title != null ? a.title : "";
            String bTitle = b != null && b.title != null ? b.title : "";
            return aTitle.compareToIgnoreCase(bTitle);
        });
        adapter.setItems(sorted);
    }

    private boolean isHabitCompletedForToday(Item item) {
        if (item == null) return false;
        ItemOccurrence today = todayOccurrences.get(item.id);
        if (today != null) return today.isComplete;
        return false;
    }

    private List<Item> getSelectedItems() {
        List<Item> selected = new ArrayList<>();
        if (adapter == null) return selected;
        for (Long id : adapter.getSelectedIds()) {
            Item item = findHabitById(id);
            if (item != null) selected.add(item);
        }
        return selected;
    }

    private int getThemeColor(int attr) {
        TypedValue typedValue = new TypedValue();
        if (getContext() != null && getContext().getTheme().resolveAttribute(attr, typedValue, true)) {
            return typedValue.data;
        }
        return Color.parseColor("#3F51B5"); // Fallback to original brand purple
    }
    
    private void updateVisuals() {
        if (allHabits.isEmpty() && allHistory.isEmpty()) {
             // Still draw empty
             drawOverallHeatmap();
             drawOverallChart(spinnerChartRange.getSelectedItemPosition());
             return;
        }
        drawOverallHeatmap();
        drawOverallChart(spinnerChartRange.getSelectedItemPosition());
    }

    private void drawOverallHeatmap() {
        gridHeatmap.removeAllViews();
        Set<Long> habitIds = new HashSet<>();
        for (Item h : allHabits) habitIds.add(h.id);
        
        int[] completionsPerDay = new int[28];
        long now = System.currentTimeMillis();
        long oneDayMs = 24 * 60 * 60 * 1000L;
        
        for (CompletionHistory h : allHistory) {
            if (habitIds.contains(h.itemId) && "done".equals(h.action)) {
                long diff = now - h.timestamp;
                int dayIndex = (int) (diff / oneDayMs);
                if (dayIndex >= 0 && dayIndex < 28) {
                    completionsPerDay[27 - dayIndex]++;
                }
            }
        }
        
        gridHeatmap.post(() -> {
            int gridWidth = gridHeatmap.getWidth();
            int squareSize = Math.max(10, (gridWidth / 7) - 8);
            for (int i = 0; i < 28; i++) {
                View square = new View(getContext());
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = squareSize;
                params.height = squareSize;
                params.setMargins(4, 4, 4, 4);
                square.setLayoutParams(params);
                
                int count = completionsPerDay[i];
                int primaryColorAttr = getResources().getIdentifier("colorPrimary", "attr", getContext().getPackageName());
                int primaryColor = getThemeColor(primaryColorAttr);
                
                if (count == 0) {
                    square.setBackgroundColor(Color.parseColor("#E0E0E0"));
                    square.setAlpha(0.3f);
                } else if (count == 1 || count == 2) {
                    square.setBackgroundColor(ColorUtils.setAlphaComponent(primaryColor, 64)); // 25%
                } else if (count == 3 || count == 4) {
                    square.setBackgroundColor(ColorUtils.setAlphaComponent(primaryColor, 128)); // 50%
                } else if (count <= 6) {
                    square.setBackgroundColor(ColorUtils.setAlphaComponent(primaryColor, 192)); // 75%
                } else {
                    square.setBackgroundColor(primaryColor); // 100%
                }
                
                final int dayOffset = 27 - i; // Days ago
                square.setOnClickListener(v -> {
                    Toast.makeText(getContext(), "Editing day " + dayOffset + " days ago", Toast.LENGTH_SHORT).show();
                    // Future: invoke HabitEditLogBottomSheetFragment
                });
                
                gridHeatmap.addView(square);
            }
        });
    }

    private void drawOverallChart(int rangeType) {
        layoutFrequencyChart.removeAllViews();
        int bars = 7; 
        if (rangeType == 0) bars = 30; 
        else if (rangeType == 2) bars = 12;

        Set<Long> habitIds = new HashSet<>();
        for (Item h : allHabits) habitIds.add(h.id);
        
        int[] completionsPerBar = new int[bars];
        long now = System.currentTimeMillis();
        long barDurationMs = 24 * 60 * 60 * 1000L;
        if (rangeType == 2) barDurationMs = 30L * 24 * 60 * 60 * 1000L; // approximate month
        
        for (CompletionHistory h : allHistory) {
            if (habitIds.contains(h.itemId) && "done".equals(h.action)) {
                long diff = now - h.timestamp;
                int barIndex = (int) (diff / barDurationMs);
                if (barIndex >= 0 && barIndex < bars) {
                    completionsPerBar[(bars - 1) - barIndex]++; 
                }
            }
        }
        
        int max = 1;
        for (int c : completionsPerBar) if (c > max) max = c;

        for (int i = 0; i < bars; i++) {
            View bar = new View(getContext());
            float percentage = (float) completionsPerBar[i] / max;
            int height = (int) (percentage * 180) + 10; // Ensure some visibility
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, height, 1);
            params.setMargins(4, 0, 4, 0); // 4dp padding
            bar.setLayoutParams(params);
            int primaryColorAttr = getResources().getIdentifier("colorPrimary", "attr", getContext().getPackageName());
            bar.setBackgroundColor(getThemeColor(primaryColorAttr));
            layoutFrequencyChart.addView(bar);
        }
    }
}
