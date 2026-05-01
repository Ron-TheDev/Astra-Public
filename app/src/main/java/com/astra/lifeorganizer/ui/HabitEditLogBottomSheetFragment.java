package com.astra.lifeorganizer.ui;

import android.os.Bundle;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.astra.lifeorganizer.R;
import com.astra.lifeorganizer.data.entities.CompletionHistory;
import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.data.repositories.SettingsRepository;
import com.astra.lifeorganizer.utils.BottomSheetUtils;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HabitEditLogBottomSheetFragment extends BottomSheetDialogFragment {

    private ItemViewModel itemViewModel;
    private RecyclerView rvMiniCalendar, rvHabitToggles;
    private TextView tvSelectionCount;
    private Button btnApplyChanges;

    private CalendarDayAdapter dayAdapter;
    private HabitToggleAdapter habitAdapter;
    
    // Multi-select dates
    private Set<Date> selectedDates = new HashSet<>();
    // Map to track existing history records for deletion
    private Map<String, CompletionHistory> habitHistoryMap = new HashMap<>();
    private boolean isInitialHistoryLoaded = false;

    private long filterHabitId = -1;

    public static HabitEditLogBottomSheetFragment newInstance() {
        return new HabitEditLogBottomSheetFragment();
    }

    public static HabitEditLogBottomSheetFragment newInstance(long habitId) {
        HabitEditLogBottomSheetFragment fragment = new HabitEditLogBottomSheetFragment();
        Bundle args = new Bundle();
        args.putLong("habit_id", habitId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            filterHabitId = getArguments().getLong("habit_id", -1);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_habit_edit_log, container, false);
        itemViewModel = new ViewModelProvider(requireActivity()).get(ItemViewModel.class);

        rvMiniCalendar = root.findViewById(R.id.rv_mini_calendar);
        rvHabitToggles = root.findViewById(R.id.rv_habit_toggles);
        tvSelectionCount = root.findViewById(R.id.tv_selection_count);
        btnApplyChanges = root.findViewById(R.id.btn_apply_changes);

        setupCalendar();
        setupHabitList();

        btnApplyChanges.setOnClickListener(v -> {
            applyChanges();
        });

        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        BottomSheetUtils.expandToViewport(getDialog());
    }

    private void setupCalendar() {
        List<Date> dates = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 7);
        rvMiniCalendar.setLayoutManager(layoutManager);
        int weekStartDay = SettingsRepository.resolveWeekStartDay(requireContext());
        
        // Match 5-year range from main view (2 back, 3 future)
        cal.add(Calendar.YEAR, -2);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);

        // Start on the configured first day of the week
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int startOffset = (dayOfWeek - weekStartDay + 7) % 7;
        cal.add(Calendar.DAY_OF_YEAR, -startOffset);
        
        Calendar endCal = Calendar.getInstance();
        endCal.add(Calendar.YEAR, 3);
        endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH));
        
        // End on the configured last day of the week
        int endDayOfWeek = endCal.get(Calendar.DAY_OF_WEEK);
        int weekEndDay = weekStartDay == Calendar.SUNDAY ? Calendar.SATURDAY : weekStartDay - 1;
        int endOffset = (weekEndDay - endDayOfWeek + 7) % 7;
        endCal.add(Calendar.DAY_OF_YEAR, endOffset);

        while (!cal.after(endCal)) {
            dates.add(cal.getTime());
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        dayAdapter = new CalendarDayAdapter(dates, null, new CalendarDayAdapter.OnDateClickListener() {
            @Override public void onDateClicked(Date date) {
                if (date != null) {
                    toggleDateSelection(date);
                }
            }
            @Override public void onDateDoubleClicked(Date date) {}
            @Override public void onDateLongPressed(Date date) {}
        }, false, weekStartDay);
        rvMiniCalendar.setAdapter(dayAdapter);

        // Majority-rule scroll listener for title & highlight sync
        rvMiniCalendar.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                int first = layoutManager.findFirstVisibleItemPosition();
                int last = layoutManager.findLastVisibleItemPosition();
                if (first != RecyclerView.NO_POSITION && last != RecyclerView.NO_POSITION) {
                    int mid = first + (last - first) / 2;
                    if (mid < dates.size()) {
                        Calendar midCal = Calendar.getInstance();
                        midCal.setTime(dates.get(mid));
                        updateDialogTitle(midCal);
                        dayAdapter.setDominantMonth(midCal.get(Calendar.MONTH), midCal.get(Calendar.YEAR));
                    }
                }
            }
        });

        // Auto-scroll to today
        Date target = new Date();
        for (int i = 0; i < dates.size(); i++) {
            Calendar d = Calendar.getInstance(); d.setTime(dates.get(i));
            Calendar today = Calendar.getInstance(); today.setTime(target);
            if (d.get(Calendar.YEAR) == today.get(Calendar.YEAR) && d.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
                layoutManager.scrollToPositionWithOffset(i, 100); 
                break;
            }
        }
    }

    private void updateDialogTitle(Calendar cal) {
        TextView tvTitle = getView().findViewById(R.id.tv_dialog_title);
        if (tvTitle != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
            String habitTitle = "";
            if (habitAdapter.getHabits() != null && !habitAdapter.getHabits().isEmpty()) {
                habitTitle = habitAdapter.getHabits().get(0).title + " - ";
            }
            tvTitle.setText(habitTitle + sdf.format(cal.getTime()));
        }
    }

    private void toggleDateSelection(Date date) {
        boolean removed = false;
        Calendar target = Calendar.getInstance();
        target.setTime(date);
        
        for (Date d : selectedDates) {
            Calendar c = Calendar.getInstance();
            c.setTime(d);
            if (c.get(Calendar.YEAR) == target.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) {
                selectedDates.remove(d);
                removed = true;
                break;
            }
        }
        if (!removed) {
            selectedDates.add(date);
        }
        
        tvSelectionCount.setText(selectedDates.size() + " dates selected");
        updateCalendarVisuals();
    }

    private void setupHabitList() {
        rvHabitToggles.setLayoutManager(new LinearLayoutManager(getContext()));
        habitAdapter = new HabitToggleAdapter();
        rvHabitToggles.setAdapter(habitAdapter);

        itemViewModel.getItemsByType("habit").observe(getViewLifecycleOwner(), items -> {
            if (items != null) {
                if (filterHabitId != -1) {
                    List<Item> single = new ArrayList<>();
                    for (Item item : items) {
                        if (item.id == filterHabitId) {
                            single.add(item);
                            TextView tvTitle = getView().findViewById(R.id.tv_dialog_title);
                            if (tvTitle != null) tvTitle.setText("Edit Log: " + item.title);
                            habitAdapter.getCompletedIds().add(item.id);
                            
                            // LOAD HISTORY
                            loadHabitHistory(item.id);
                        }
                    }
                    habitAdapter.setHabits(single);
                    // Force title update with content
                    updateDialogTitle(Calendar.getInstance());
                } else {
                    habitAdapter.setHabits(items);
                }
            }
        });
    }

    private void loadHabitHistory(long habitId) {
        itemViewModel.getHistoryForItemLive(habitId).observe(getViewLifecycleOwner(), history -> {
            if (history != null && !isInitialHistoryLoaded) {
                selectedDates.clear();
                habitHistoryMap.clear();
                for (CompletionHistory record : history) {
                    Calendar c = Calendar.getInstance();
                    c.setTimeInMillis(record.timestamp);
                    // Standardize to noon to match calendar toggle logic
                    c.set(Calendar.HOUR_OF_DAY, 12);
                    c.set(Calendar.MINUTE, 0);
                    c.set(Calendar.SECOND, 0);
                    c.set(Calendar.MILLISECOND, 0);
                    
                    Date date = c.getTime();
                    selectedDates.add(date);
                    habitHistoryMap.put(getDateKey(c), record);
                }
                isInitialHistoryLoaded = true;
                updateCalendarVisuals();
                tvSelectionCount.setText(selectedDates.size() + " dates selected");
            }
        });
    }

    private String getDateKey(Calendar c) {
        return c.get(Calendar.YEAR) + "-" + c.get(Calendar.DAY_OF_YEAR);
    }

    private void updateCalendarVisuals() {
        java.util.Map<String, java.util.List<Integer>> dotColors = new java.util.HashMap<>();
        for (Date d : selectedDates) {
            Calendar c = Calendar.getInstance();
            c.setTime(d);
            dotColors.put(getDateKey(c), java.util.Collections.singletonList(0xFF2563EB));
        }
        dayAdapter.setEventDotColors(dotColors);
    }

    private void applyChanges() {
        if (selectedDates.isEmpty()) {
            Toast.makeText(getContext(), "Select at least one date", Toast.LENGTH_SHORT).show();
            return;
        }

        Set<Long> completedIds = habitAdapter.getCompletedIds();
        if (completedIds.isEmpty()) {
            Toast.makeText(getContext(), "No habits selected to mark as done", Toast.LENGTH_SHORT).show();
            return;
        }

        Set<Long> habitIds = habitAdapter.getCompletedIds();
        
        // Standardize current selection keys
        Set<String> currentSelections = new HashSet<>();
        for (Date d : selectedDates) {
            Calendar c = Calendar.getInstance();
            c.setTime(d);
            currentSelections.add(getDateKey(c));
        }

        // 1. REMOVE: If it's in history map but NOT in current selection
        for (String key : new HashSet<>(habitHistoryMap.keySet())) {
            if (!currentSelections.contains(key)) {
                itemViewModel.deleteHistory(habitHistoryMap.get(key));
                habitHistoryMap.remove(key);
            }
        }

        // 2. ADD: For every selected date and every focus habit
        for (Date d : selectedDates) {
            Calendar c = Calendar.getInstance();
            c.setTime(d);
            String key = getDateKey(c);

            // Only insert if it doesn't already exist in the history map
            if (!habitHistoryMap.containsKey(key)) {
                c.set(Calendar.HOUR_OF_DAY, 12); // Standard noon timestamp
                for (Long habitId : habitIds) {
                    CompletionHistory history = new CompletionHistory();
                    history.itemId = habitId;
                    history.action = "done";
                    history.timestamp = c.getTimeInMillis();
                    itemViewModel.recordHistory(history);
                }
            }
        }

        Toast.makeText(getContext(), "Habits updated for " + selectedDates.size() + " day(s)", Toast.LENGTH_SHORT).show();
        dismiss();
    }
}
