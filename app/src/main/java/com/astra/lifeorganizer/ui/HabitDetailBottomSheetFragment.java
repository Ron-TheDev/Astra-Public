package com.astra.lifeorganizer.ui;

import android.graphics.Color;
import android.os.Bundle;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.TypedValue;
import androidx.core.graphics.ColorUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import com.astra.lifeorganizer.R;
import com.astra.lifeorganizer.data.entities.CompletionHistory;
import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.utils.BottomSheetUtils;
import com.astra.lifeorganizer.utils.DateTimeFormatUtils;
import com.astra.lifeorganizer.utils.LabelUtils;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HabitDetailBottomSheetFragment extends BottomSheetDialogFragment {

    private long habitId;
    private ItemViewModel itemViewModel;
    private Item currentItem;
    private TextView tvTitle, tvLabel, tvDetailType, tvDetailFreq, tvDetailStart, tvDetailEnd, tvDetailNotes;
    private GridLayout gridHeatmap;
    private LinearLayout layoutFrequencyChart;
    private MaterialAutoCompleteTextView actvChartRange;
    private TextView tvChartRangeLabel;
    private View btnChartPrev;
    private View btnChartNext;
    private Button btnEdit;
    private TextView tvCurrentStreak, tvBestStreak;

    private List<CompletionHistory> habitHistory = new ArrayList<>();
    private int selectedChartRange = 1;
    private int chartWindowOffset = 0;

    private static final int RANGE_WEEK = 0;
    private static final int RANGE_MONTH = 1;
    private static final int RANGE_YEAR = 2;

    public static HabitDetailBottomSheetFragment newInstance(long habitId) {
        HabitDetailBottomSheetFragment fragment = new HabitDetailBottomSheetFragment();
        Bundle args = new Bundle();
        args.putLong("habit_id", habitId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            habitId = getArguments().getLong("habit_id");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_habit_detail, container, false);
        itemViewModel = new ViewModelProvider(requireActivity()).get(ItemViewModel.class);

        tvTitle = root.findViewById(R.id.tv_habit_title);
        tvLabel = root.findViewById(R.id.tv_habit_label);
        tvDetailType = root.findViewById(R.id.tv_detail_type);
        tvDetailFreq = root.findViewById(R.id.tv_detail_frequency);
        tvDetailStart = root.findViewById(R.id.tv_detail_start);
        tvDetailEnd = root.findViewById(R.id.tv_detail_end);
        tvDetailNotes = root.findViewById(R.id.tv_detail_notes);
        
        gridHeatmap = root.findViewById(R.id.grid_heatmap);
        layoutFrequencyChart = root.findViewById(R.id.layout_frequency_chart);
        actvChartRange = root.findViewById(R.id.actv_chart_range);
        tvChartRangeLabel = root.findViewById(R.id.tv_chart_range_label);
        btnChartPrev = root.findViewById(R.id.btn_chart_prev);
        btnChartNext = root.findViewById(R.id.btn_chart_next);
        btnEdit = root.findViewById(R.id.btn_edit_habit);
        tvCurrentStreak = root.findViewById(R.id.tv_current_streak);
        tvBestStreak = root.findViewById(R.id.tv_best_streak);

        loadHabitData();

        ArrayAdapter<CharSequence> rangeAdapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.habit_chart_options,
                android.R.layout.simple_dropdown_item_1line
        );
        actvChartRange.setAdapter(rangeAdapter);
        actvChartRange.setText(rangeAdapter.getItem(selectedChartRange), false);
        actvChartRange.setOnItemClickListener((parent, view, position, id) -> {
            selectedChartRange = position;
            chartWindowOffset = 0;
            renderFrequencyChart();
        });

        btnChartPrev.setOnClickListener(v -> {
            chartWindowOffset++;
            renderFrequencyChart();
        });

        btnChartNext.setOnClickListener(v -> {
            if (chartWindowOffset > 0) {
                chartWindowOffset--;
                renderFrequencyChart();
            }
        });

        btnEdit.setOnClickListener(v -> {
            AddTaskBottomSheetFragment.newInstance("habit", habitId)
                .show(getParentFragmentManager(), "EditHabitFromDetail");
            dismiss();
        });

        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        BottomSheetUtils.expandToViewport(getDialog());
    }

    private void loadHabitData() {
        itemViewModel.getItemById(habitId).observe(getViewLifecycleOwner(), item -> {
            if (item != null) {
                currentItem = item;
                tvTitle.setText(item.title);
                tvLabel.setText(item.label != null && !item.label.isEmpty() ? "#" + LabelUtils.displayLabel(item.label) : "");
                tvDetailType.setText("Type: " + (item.isPositive ? "Positive" : "Negative"));
                tvDetailFreq.setText("Goal: " + item.dailyTargetCount + " times");
                tvDetailStart.setText("Started: " + DateTimeFormatUtils.formatDate(requireContext(), item.createdAt));
                tvDetailEnd.setText("Ends: " + (item.endAt != null ? DateTimeFormatUtils.formatDate(requireContext(), item.endAt) : "Forever"));
                tvDetailNotes.setText("Notes: " + (item.description != null ? item.description : "None"));
                
                // Redraw heatmap if item changed (e.g. target change)
                drawHeatmap();
            }
        });

        itemViewModel.getAllHistory().observe(getViewLifecycleOwner(), allHistory -> {
            if (allHistory != null) {
                habitHistory.clear();
                for (CompletionHistory h : allHistory) {
                    if (h.itemId == habitId && "done".equals(h.action)) {
                        habitHistory.add(h);
                    }
                }
                // Sort history by timestamp descending for streak calculation
                habitHistory.sort((h1, h2) -> Long.compare(h2.timestamp, h1.timestamp));
                
                updateStreakDisplay();
                drawHeatmap();
                renderFrequencyChart();
            }
        });
    }

    private int getThemeColor(int attr) {
        TypedValue typedValue = new TypedValue();
        if (getContext() != null && getContext().getTheme().resolveAttribute(attr, typedValue, true)) {
            return typedValue.data;
        }
        return Color.parseColor("#3F51B5");
    }

    private void updateStreakDisplay() {
        if (habitHistory.isEmpty()) {
            tvCurrentStreak.setText("0");
            tvBestStreak.setText("0");
            return;
        }

        long now = System.currentTimeMillis();
        long oneDayMs = 24 * 60 * 60 * 1000L;
        
        java.util.Set<String> uniqueDays = new java.util.HashSet<>();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        for (CompletionHistory h : habitHistory) {
            uniqueDays.add(fmt.format(new Date(h.timestamp)));
        }

        // Current Streak
        int currentStreak = 0;
        String todayStr = fmt.format(new Date(now));
        String yesterdayStr = fmt.format(new Date(now - oneDayMs));
        
        boolean hasToday = uniqueDays.contains(todayStr);
        boolean hasYesterday = uniqueDays.contains(yesterdayStr);
        
        if (hasToday || hasYesterday) {
            int offset = hasToday ? 0 : 1;
            while (uniqueDays.contains(fmt.format(new Date(now - (long)offset * oneDayMs)))) {
                currentStreak++;
                offset++;
            }
        }

        // Best Streak
        int bestStreak = 0;
        int tempStreak = 0;
        // Simplified best streak: check the last 365 days
        for (int i = 0; i < 365; i++) {
            if (uniqueDays.contains(fmt.format(new Date(now - (long)i * oneDayMs)))) {
                tempStreak++;
                if (tempStreak > bestStreak) bestStreak = tempStreak;
            } else {
                tempStreak = 0;
            }
        }

        tvCurrentStreak.setText(String.valueOf(currentStreak));
        tvBestStreak.setText(String.valueOf(bestStreak));
    }

    private void drawHeatmap() {
        if (currentItem == null) return;
        gridHeatmap.removeAllViews();
        
        int[] completionsPerDay = new int[28];
        long now = System.currentTimeMillis();
        long oneDayMs = 24 * 60 * 60 * 1000L;
        
        // Find start of day for 'now'
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(now);
        startOfDay(cal);
        long todayStart = cal.getTimeInMillis();

        for (CompletionHistory h : habitHistory) {
            long diff = todayStart - h.timestamp;
            int dayIndex = (int) (diff / oneDayMs);
            if (dayIndex >= 0 && dayIndex < 28) {
                completionsPerDay[27 - dayIndex]++;
            }
        }

        gridHeatmap.post(() -> {
            int gridWidth = gridHeatmap.getWidth();
            int squareSize = Math.max(10, (gridWidth / 7) - 8);
            int primaryColorAttr = getResources().getIdentifier("colorPrimary", "attr", getContext().getPackageName());
            int primaryColor = getThemeColor(primaryColorAttr);

            for (int i = 0; i < 28; i++) {
                int daysAgo = 27 - i;
                long dayTimestamp = todayStart - (long)daysAgo * oneDayMs;
                
                boolean isValid = com.astra.lifeorganizer.utils.RecurrenceRuleParser.isValidOccurrence(dayTimestamp, currentItem);
                
                View square = new View(getContext());
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = squareSize;
                params.height = squareSize;
                params.setMargins(4, 4, 4, 4);
                square.setLayoutParams(params);
                
                if (!isValid) {
                    square.setBackgroundColor(Color.TRANSPARENT);
                } else {
                    int count = completionsPerDay[i];
                    int target = currentItem.dailyTargetCount > 0 ? currentItem.dailyTargetCount : 1;

                    if (count == 0) {
                        square.setBackgroundColor(Color.parseColor("#E0E0E0"));
                        square.setAlpha(0.3f);
                    } else if (count < target) {
                        // Partial shading
                        float ratio = (float) count / target;
                        int alpha = (int) (64 + (128 * ratio)); // 64 to 192 alpha
                        square.setBackgroundColor(ColorUtils.setAlphaComponent(primaryColor, alpha));
                    } else {
                        // Full completion
                        square.setBackgroundColor(primaryColor);
                    }
                }
                
                gridHeatmap.addView(square);
            }
        });
    }

    private void renderFrequencyChart() {
        layoutFrequencyChart.removeAllViews();
        List<FrequencyBucket> buckets = buildBuckets();
        if (buckets.isEmpty()) {
            tvChartRangeLabel.setText("No data");
            btnChartNext.setEnabled(false);
            return;
        }

        int max = 1;
        for (FrequencyBucket bucket : buckets) {
            if (bucket.count > max) {
                max = bucket.count;
            }
        }

        for (FrequencyBucket bucket : buckets) {
            layoutFrequencyChart.addView(createFrequencyBar(bucket, max));
        }

        tvChartRangeLabel.setText(buildRangeLabel(buckets));
        btnChartNext.setEnabled(chartWindowOffset > 0);
    }

    private List<FrequencyBucket> buildBuckets() {
        List<FrequencyBucket> buckets = new ArrayList<>();
        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        int completionCount = 0;

        if (selectedChartRange == RANGE_WEEK) {
            int days = 7;
            for (int i = 0; i < days; i++) {
                Calendar start = Calendar.getInstance();
                start.setTimeInMillis(now);
                start.add(Calendar.DAY_OF_YEAR, -((chartWindowOffset * days) + (days - 1 - i)));
                startOfDay(start);

                Calendar end = Calendar.getInstance();
                end.setTimeInMillis(start.getTimeInMillis());
                end.add(Calendar.DAY_OF_YEAR, 1);

                boolean isCurrentBucket = chartWindowOffset == 0 && i == days - 1;
                long bucketEnd = isCurrentBucket ? now : end.getTimeInMillis();
                completionCount = countCompletionsBetween(start.getTimeInMillis(), bucketEnd);
                buckets.add(new FrequencyBucket(
                        completionCount,
                        new SimpleDateFormat("EEE", Locale.getDefault()).format(new Date(start.getTimeInMillis())),
                        new SimpleDateFormat("d", Locale.getDefault()).format(new Date(start.getTimeInMillis())),
                        start.getTimeInMillis(),
                        bucketEnd
                ));
            }
            return buckets;
        }

        int months = selectedChartRange == RANGE_YEAR ? 12 : 6;
        cal.setTimeInMillis(now);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        startOfDay(cal);

        for (int i = 0; i < months; i++) {
            Calendar start = (Calendar) cal.clone();
            start.add(Calendar.MONTH, -((chartWindowOffset * months) + (months - 1 - i)));

            Calendar end = (Calendar) start.clone();
            end.add(Calendar.MONTH, 1);

            boolean isCurrentBucket = chartWindowOffset == 0 && i == months - 1;
            long bucketEnd = isCurrentBucket ? now : end.getTimeInMillis();
            completionCount = countCompletionsBetween(start.getTimeInMillis(), bucketEnd);
            buckets.add(new FrequencyBucket(
                    completionCount,
                    new SimpleDateFormat("MMM", Locale.getDefault()).format(new Date(start.getTimeInMillis())),
                    new SimpleDateFormat("yyyy", Locale.getDefault()).format(new Date(start.getTimeInMillis())),
                    start.getTimeInMillis(),
                    bucketEnd
            ));
        }
        return buckets;
    }

    private int countCompletionsBetween(long startMs, long endMs) {
        int count = 0;
        for (CompletionHistory history : habitHistory) {
            if (history == null || history.timestamp < startMs || history.timestamp >= endMs) {
                continue;
            }
            if ("done".equalsIgnoreCase(history.action)) {
                count++;
            }
        }
        return count;
    }

    private View createFrequencyBar(FrequencyBucket bucket, int max) {
        int primaryColor = getThemeColor(com.google.android.material.R.attr.colorPrimaryVariant);
        int onSurface = getThemeColor(com.google.android.material.R.attr.colorOnSurface);
        int onSurfaceVariant = getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant);

        LinearLayout column = new LinearLayout(requireContext());
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        columnParams.setMargins(dp(4), 0, dp(4), 0);
        column.setLayoutParams(columnParams);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);

        TextView value = new TextView(requireContext());
        value.setText(String.valueOf(bucket.count));
        value.setTextColor(ColorUtils.setAlphaComponent(onSurface, 240));
        value.setTextSize(18f);
        value.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        value.setPadding(0, 0, 0, dp(8));
        column.addView(value);

        LinearLayout barArea = new LinearLayout(requireContext());
        LinearLayout.LayoutParams barAreaParams = new LinearLayout.LayoutParams(dp(22), dp(150));
        barArea.setLayoutParams(barAreaParams);
        barArea.setOrientation(LinearLayout.VERTICAL);
        barArea.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);

        View bar = new View(requireContext());
        int barHeight = Math.max(dp(8), (int) (dp(150) * (bucket.count / (float) max)));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(dp(22), barHeight);
        bar.setLayoutParams(barParams);
        bar.setBackgroundResource(R.drawable.bg_frequency_bar);
        if (bucket.count > 0) {
            bar.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ColorUtils.setAlphaComponent(primaryColor, 176)));
        } else {
            bar.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ColorUtils.setAlphaComponent(onSurfaceVariant, 48)));
        }
        barArea.addView(bar);
        column.addView(barArea);

        TextView labelTop = new TextView(requireContext());
        labelTop.setText(bucket.labelTop);
        labelTop.setTextColor(onSurfaceVariant);
        labelTop.setTextSize(16f);
        labelTop.setPadding(0, dp(12), 0, 0);
        labelTop.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        column.addView(labelTop);

        TextView labelBottom = new TextView(requireContext());
        labelBottom.setText(bucket.labelBottom);
        labelBottom.setTextColor(onSurfaceVariant);
        labelBottom.setTextSize(12f);
        labelBottom.setPadding(0, dp(6), 0, 0);
        labelBottom.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        column.addView(labelBottom);

        return column;
    }

    private String buildRangeLabel(List<FrequencyBucket> buckets) {
        if (buckets.isEmpty()) {
            return "";
        }
        FrequencyBucket first = buckets.get(0);
        FrequencyBucket last = buckets.get(buckets.size() - 1);
        if (chartWindowOffset == 0) {
            return formatRangeStart(first.startMs) + " ~ Now";
        }
        return formatRangeStart(first.startMs) + " ~ " + formatRangeEnd(last.startMs);
    }

    private String formatRangeStart(long ms) {
        if (selectedChartRange == RANGE_WEEK) {
            return new SimpleDateFormat("MMM d", Locale.getDefault()).format(new Date(ms));
        }
        return new SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(new Date(ms));
    }

    private String formatRangeEnd(long ms) {
        if (selectedChartRange == RANGE_WEEK) {
            return new SimpleDateFormat("MMM d", Locale.getDefault()).format(new Date(ms));
        }
        return new SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(new Date(ms));
    }

    private void startOfDay(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    private int dp(int value) {
        return (int) (value * requireContext().getResources().getDisplayMetrics().density);
    }


    private static class FrequencyBucket {
        final int count;
        final String labelTop;
        final String labelBottom;
        final long startMs;
        final long endMs;

        FrequencyBucket(int count, String labelTop, String labelBottom, long startMs, long endMs) {
            this.count = count;
            this.labelTop = labelTop;
            this.labelBottom = labelBottom;
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }
}
