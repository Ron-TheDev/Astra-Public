package com.astra.lifeorganizer.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.astra.lifeorganizer.R;
import com.astra.lifeorganizer.data.entities.CompletionHistory;
import com.astra.lifeorganizer.data.entities.Item;
import android.util.TypedValue;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;

public class HomepageFragment extends Fragment {

    private ItemViewModel itemViewModel;
    private TextView tvTasksValue, tvTasksSubtitle;
    private TextView tvHabitsValue, tvHabitsSubtitle;
    private TextView tvEventsValue, tvEventsSubtitle;
    private TextView tvStreakValue, tvStreakSubtitle;
    private TextView tvRemindersEmpty;
    private RecyclerView rvReminders;
    private HomeReminderAdapter reminderAdapter;

    private final List<Item> currentHabits = new ArrayList<>();
    private final List<CompletionHistory> allHistory = new ArrayList<>();
    private final List<Item> allTodos = new ArrayList<>();
    private final List<Item> allEvents = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_homepage, container, false);
        itemViewModel = new ViewModelProvider(this).get(ItemViewModel.class);

        tvTasksValue = root.findViewById(R.id.tv_tasks_value);
        tvTasksSubtitle = root.findViewById(R.id.tv_tasks_subtitle);
        tvHabitsValue = root.findViewById(R.id.tv_habits_value);
        tvHabitsSubtitle = root.findViewById(R.id.tv_habits_subtitle);
        tvEventsValue = root.findViewById(R.id.tv_events_value);
        tvEventsSubtitle = root.findViewById(R.id.tv_events_subtitle);
        tvStreakValue = root.findViewById(R.id.tv_streak_value);
        tvStreakSubtitle = root.findViewById(R.id.tv_streak_subtitle);
        tvRemindersEmpty = root.findViewById(R.id.tv_reminders_empty);
        rvReminders = root.findViewById(R.id.rv_home_reminders);
        if (rvReminders != null) {
            rvReminders.setLayoutManager(new LinearLayoutManager(getContext()));
        }
        reminderAdapter = new HomeReminderAdapter(item -> ItemPreviewBottomSheetFragment
                .newInstance(item.id)
                .show(getParentFragmentManager(), "HomeReminderPreview"));
        if (rvReminders != null) {
            rvReminders.setAdapter(reminderAdapter);
        }

        itemViewModel.getItemsByType("habit").observe(getViewLifecycleOwner(), habits -> {
            currentHabits.clear();
            if (habits != null) {
                currentHabits.addAll(habits);
            }
            refreshStats();
        });

        itemViewModel.getItemsByType("todo").observe(getViewLifecycleOwner(), todos -> {
            allTodos.clear();
            if (todos != null) {
                allTodos.addAll(todos);
            }
            refreshStats();
        });

        itemViewModel.getItemsByType("event").observe(getViewLifecycleOwner(), events -> {
            allEvents.clear();
            if (events != null) {
                allEvents.addAll(events);
            }
            refreshStats();
        });

        itemViewModel.getAllHistory().observe(getViewLifecycleOwner(), histories -> {
            allHistory.clear();
            if (histories != null) {
                allHistory.addAll(histories);
            }
            refreshStats();
        });
        refreshStats();

        return root;
    }

    private void refreshStats() {
        if (!isAdded()) {
            return;
        }

        long nowMs = System.currentTimeMillis();
        long todayStart = startOfDay(nowMs);
        long todayEnd = endOfDay(nowMs);
        long monthStart = startOfMonth(nowMs);
        long monthEnd = endOfMonth(nowMs);

        int overdueTasks = 0;
        int dueTodayTasks = 0;
        int tasksCompletedToday = 0;
        for (Item item : allTodos) {
            if (!isCountableHomeItem(item)) {
                continue;
            }
            long due = item.dueAt != null ? item.dueAt : 0L;
            if (due > 0 && due < todayStart) {
                overdueTasks++;
            } else if (due >= todayStart && due <= todayEnd) {
                dueTodayTasks++;
            }
        }
        for (CompletionHistory history : allHistory) {
            if (history == null || history.timestamp < todayStart || history.timestamp > todayEnd || !"done".equalsIgnoreCase(history.action)) {
                continue;
            }
            Item matched = findItemById(history.itemId);
            if (matched != null && "todo".equalsIgnoreCase(matched.type)) {
                tasksCompletedToday++;
            }
        }

        int taskDenominator = overdueTasks + dueTodayTasks;
        int taskPercent = taskDenominator > 0 ? Math.round((tasksCompletedToday * 100f) / taskDenominator) : 0;
        if (tvTasksValue != null) {
            tvTasksValue.setText(tasksCompletedToday + "/" + taskDenominator);
        }
        if (tvTasksSubtitle != null) {
            tvTasksSubtitle.setText(taskDenominator > 0 ? taskPercent + "% complete" : "All caught up");
        }

        int activeHabitCount = currentHabits.size();
        int maxCurrentStreak = 0;
        for (Item habit : currentHabits) {
            if (habit == null || habit.id <= 0) {
                continue;
            }
            List<Long> habitDays = new ArrayList<>();
            for (CompletionHistory history : allHistory) {
                if (history != null && history.itemId == habit.id && "done".equalsIgnoreCase(history.action)) {
                    habitDays.add(history.timestamp);
                }
            }
            int current = calculateCurrentStreak(habitDays);
            if (current > maxCurrentStreak) {
                maxCurrentStreak = current;
            }
        }
        if (tvHabitsValue != null) {
            tvHabitsValue.setText(String.valueOf(activeHabitCount));
        }
        if (tvHabitsSubtitle != null) {
            tvHabitsSubtitle.setText(activeHabitCount == 1 ? "active habit" : "active habits");
        }
        if (tvStreakValue != null) {
            tvStreakValue.setText(String.valueOf(maxCurrentStreak));
        }
        if (tvStreakSubtitle != null) {
            tvStreakSubtitle.setText("days active");
        }

        int eventsThisMonth = 0;
        for (Item item : allEvents) {
            if (!isCountableHomeItem(item)) {
                continue;
            }
            long start = item.startAt != null ? item.startAt : 0L;
            if (start >= monthStart && start <= monthEnd) {
                eventsThisMonth++;
            }
        }
        if (tvEventsValue != null) {
            tvEventsValue.setText(String.valueOf(eventsThisMonth));
        }
        if (tvEventsSubtitle != null) {
            tvEventsSubtitle.setText("This month");
        }

        refreshReminderList(nowMs);
    }

    private void refreshReminderList(long nowMs) {
        if (reminderAdapter == null) {
            return;
        }

        List<HomeReminderAdapter.ReminderEntry> reminders = new ArrayList<>();
        for (Item item : allTodos) {
            if (isReminderCandidate(item, nowMs)) {
                reminders.add(new HomeReminderAdapter.ReminderEntry(item, formatReminderSubtitle(item.reminderAt, nowMs), item.reminderAt));
            }
        }
        for (Item item : allEvents) {
            if (isReminderCandidate(item, nowMs)) {
                reminders.add(new HomeReminderAdapter.ReminderEntry(item, formatReminderSubtitle(item.reminderAt, nowMs), item.reminderAt));
            }
        }

        reminders.sort((a, b) -> Long.compare(a.reminderTime, b.reminderTime));
        reminderAdapter.setItems(reminders);
        if (tvRemindersEmpty != null) {
            tvRemindersEmpty.setVisibility(reminders.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (rvReminders != null) {
            rvReminders.setVisibility(reminders.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private boolean isReminderCandidate(Item item, long nowMs) {
        if (item == null || item.reminderAt == null || item.reminderAt <= nowMs) {
            return false;
        }
        if (item.status != null && ("done".equalsIgnoreCase(item.status) || "archived".equalsIgnoreCase(item.status) || "canceled".equalsIgnoreCase(item.status))) {
            return false;
        }
        long windowEnd = reminderWindowEnd(item, nowMs);
        if (windowEnd <= nowMs || item.reminderAt > windowEnd) {
            return false;
        }
        return "todo".equalsIgnoreCase(item.type) || "event".equalsIgnoreCase(item.type);
    }

    private boolean isVisibleReminderItem(Item item) {
        return item != null && (item.status == null
                || (!"archived".equalsIgnoreCase(item.status)
                && !"done".equalsIgnoreCase(item.status)
                && !"canceled".equalsIgnoreCase(item.status)));
    }

    private boolean isCountableHomeItem(Item item) {
        return item != null && (item.status == null
                || (!"archived".equalsIgnoreCase(item.status)
                && !"canceled".equalsIgnoreCase(item.status)));
    }

    private long reminderWindowEnd(Item item, long nowMs) {
        long sevenDays = nowMs + (7L * 24L * 60L * 60L * 1000L);
        long thirtyDays = nowMs + (30L * 24L * 60L * 60L * 1000L);
        if (item != null && item.priority == 3) {
            return thirtyDays;
        }
        return sevenDays;
    }

    private Item findItemById(long itemId) {
        for (Item item : allTodos) {
            if (item != null && item.id == itemId) {
                return item;
            }
        }
        for (Item item : allEvents) {
            if (item != null && item.id == itemId) {
                return item;
            }
        }
        for (Item item : currentHabits) {
            if (item != null && item.id == itemId) {
                return item;
            }
        }
        return null;
    }

    private String formatReminderSubtitle(Long reminderAt, long nowMs) {
        if (reminderAt == null || reminderAt <= 0L) {
            return "Reminder set";
        }
        if (startOfDay(reminderAt) == startOfDay(nowMs)) {
            return com.astra.lifeorganizer.utils.DateTimeFormatUtils.formatTime(requireContext(), reminderAt);
        }
        return com.astra.lifeorganizer.utils.DateTimeFormatUtils.formatDate(requireContext(), reminderAt) + " \u2022 " +
                com.astra.lifeorganizer.utils.DateTimeFormatUtils.formatTime(requireContext(), reminderAt);
    }

    private int getThemeColor(int attr) {
        TypedValue typedValue = new TypedValue();
        if (getContext() != null && getContext().getTheme().resolveAttribute(attr, typedValue, true)) {
            return typedValue.data;
        }
        return Color.parseColor("#3F51B5");
    }

    private long startOfDay(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private long endOfDay(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTimeInMillis();
    }

    private long startOfMonth(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private long endOfMonth(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTimeInMillis();
    }
//
//    private void drawOverallHeatmap() {
//        if (gridHeatmap == null) {
//            return;
//        }
//        gridHeatmap.removeAllViews();
//        Set<Long> habitIds = new HashSet<>();
//        for (Item h : currentHabits) {
//            if (h != null) {
//                habitIds.add(h.id);
//            }
//        }
//        int[] completionsPerDay = new int[28];
//        long now = System.currentTimeMillis();
//        long oneDayMs = 24 * 60 * 60 * 1000L;
//        for (CompletionHistory history : allHistory) {
//            if (history == null || history.timestamp <= 0 || !"done".equalsIgnoreCase(history.action)) {
//                continue;
//            }
//            if (!habitIds.contains(history.itemId)) {
//                continue;
//            }
//            long diff = now - history.timestamp;
//            int dayIndex = (int) (diff / oneDayMs);
//            if (dayIndex >= 0 && dayIndex < 28) {
//                completionsPerDay[27 - dayIndex]++;
//            }
//        }
//        gridHeatmap.post(() -> {
//            int gridWidth = gridHeatmap.getWidth();
//            int squareSize = Math.max(10, (gridWidth / 7) - 8);
//            int primaryColor = getThemeColor(androidx.appcompat.R.attr.colorPrimary);
//            for (int i = 0; i < 28; i++) {
//                View square = new View(getContext());
//                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
//                params.width = squareSize;
//                params.height = squareSize;
//                params.setMargins(4, 4, 4, 4);
//                square.setLayoutParams(params);
//
//                int count = completionsPerDay[i];
//                if (count == 0) {
//                    square.setBackgroundColor(Color.parseColor("#E0E0E0"));
//                    square.setAlpha(0.3f);
//                } else if (count == 1 || count == 2) {
//                    square.setBackgroundColor(ColorUtils.setAlphaComponent(primaryColor, 64));
//                } else if (count == 3 || count == 4) {
//                    square.setBackgroundColor(ColorUtils.setAlphaComponent(primaryColor, 128));
//                } else if (count <= 6) {
//                    square.setBackgroundColor(ColorUtils.setAlphaComponent(primaryColor, 192));
//                } else {
//                    square.setBackgroundColor(primaryColor);
//                }
//
//                final int dayOffset = 27 - i;
//                square.setOnClickListener(v -> {
//                    Toast.makeText(getContext(), "Editing day " + dayOffset + " days ago", Toast.LENGTH_SHORT).show();
//                });
//
//                gridHeatmap.addView(square);
//            }
//        });
//    }
//
//    private void drawOverallChart(int rangeType) {
//        if (layoutFrequencyChart == null) {
//            return;
//        }
//        layoutFrequencyChart.removeAllViews();
//        int bars = 7;
//        if (rangeType == 0) {
//            bars = 30;
//        } else if (rangeType == 2) {
//            bars = 12;
//        }
//
//        Set<Long> habitIds = new HashSet<>();
//        for (Item h : currentHabits) {
//            if (h != null) {
//                habitIds.add(h.id);
//            }
//        }
//
//        int[] completionsPerBar = new int[bars];
//        long now = System.currentTimeMillis();
//        long barDurationMs = 24 * 60 * 60 * 1000L;
//        if (rangeType == 2) {
//            barDurationMs = 30L * 24 * 60 * 60 * 1000L;
//        }
//
//        for (CompletionHistory h : allHistory) {
//            if (h == null || h.timestamp <= 0 || !"done".equalsIgnoreCase(h.action) || !habitIds.contains(h.itemId)) {
//                continue;
//            }
//            long diff = now - h.timestamp;
//            int barIndex = (int) (diff / barDurationMs);
//            if (barIndex >= 0 && barIndex < bars) {
//                completionsPerBar[(bars - 1) - barIndex]++;
//            }
//        }
//
//        int max = 1;
//        for (int c : completionsPerBar) {
//            if (c > max) {
//                max = c;
//            }
//        }
//        for (int i = 0; i < completionsPerBar.length; i++) {
//            View bar = new View(requireContext());
//            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 0, 1f);
//            params.setMargins(4, 0, 4, 0);
//            params.height = (int) (Math.max(10, (completionsPerBar[i] / (float) max) * dp(64)));
//            bar.setLayoutParams(params);
//            bar.setBackgroundColor(completionsPerBar[i] == 0 ? 0x332F3E46 : Color.parseColor("#4F46E5"));
//            layoutFrequencyChart.addView(bar);
//        }
//    }

    private int calculateCurrentStreak(List<Long> timestamps) {
        if (timestamps == null || timestamps.isEmpty()) {
            return 0;
        }
        Set<String> days = new HashSet<>();
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
        for (Long ts : timestamps) {
            if (ts != null) {
                days.add(fmt.format(new java.util.Date(ts)));
            }
        }
        long dayMs = 24 * 60 * 60 * 1000L;
        long now = System.currentTimeMillis();
        String today = fmt.format(new java.util.Date(now));
        String yesterday = fmt.format(new java.util.Date(now - dayMs));
        int streak = 0;
        if (days.contains(today) || days.contains(yesterday)) {
            int offset = days.contains(today) ? 0 : 1;
            while (days.contains(fmt.format(new java.util.Date(now - (long) offset * dayMs)))) {
                streak++;
                offset++;
            }
        }
        return streak;
    }

    private int calculateBestStreak(List<Long> timestamps) {
        if (timestamps == null || timestamps.isEmpty()) {
            return 0;
        }
        Set<String> days = new HashSet<>();
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
        for (Long ts : timestamps) {
            if (ts != null) {
                days.add(fmt.format(new java.util.Date(ts)));
            }
        }
        int best = 0;
        int current = 0;
        long dayMs = 24 * 60 * 60 * 1000L;
        long now = System.currentTimeMillis();
        for (int i = 0; i < 365; i++) {
            if (days.contains(fmt.format(new java.util.Date(now - (long) i * dayMs)))) {
                current++;
                best = Math.max(best, current);
            } else {
                current = 0;
            }
        }
        return best;
    }

}
