package com.astra.lifeorganizer.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.astra.lifeorganizer.R;
import com.astra.lifeorganizer.data.entities.CompletionHistory;
import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.utils.RecapBriefConstants;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecapPreviewFragment extends Fragment {

    private String mode = RecapBriefConstants.MODE_WEEKLY;
    private ItemViewModel itemViewModel;
    private TextView tvTitle, tvSubtitle;
    private android.widget.GridLayout gridStats;
    private LinearLayout layoutInsights;
    private ImageView ivHero;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mode = getArguments().getString("initial_mode", RecapBriefConstants.MODE_WEEKLY);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_recap_preview, container, false);
        tvTitle = root.findViewById(R.id.tv_recap_title);
        tvSubtitle = root.findViewById(R.id.tv_recap_subtitle);
        gridStats = root.findViewById(R.id.grid_stats);
        layoutInsights = root.findViewById(R.id.layout_insights);
        ivHero = root.findViewById(R.id.iv_recap_hero);

        root.findViewById(R.id.btn_done).setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());

        itemViewModel = new ViewModelProvider(requireActivity()).get(ItemViewModel.class);
        loadStats();

        return root;
    }

    private void loadStats() {
        itemViewModel.getAllItems().observe(getViewLifecycleOwner(), items -> {
            itemViewModel.getAllHistory().observe(getViewLifecycleOwner(), history -> {
                calculateAndDisplay(items, history);
            });
        });
    }

    private void calculateAndDisplay(List<Item> items, List<CompletionHistory> history) {
        long now = System.currentTimeMillis();
        long periodStart;
        Calendar cal = Calendar.getInstance();
        
        if (RecapBriefConstants.MODE_MONTHLY.equals(mode)) {
            cal.add(Calendar.DAY_OF_YEAR, -30);
            tvTitle.setText("Monthly Recap");
            ivHero.setImageResource(android.R.drawable.ic_menu_month);
        } else if (RecapBriefConstants.MODE_YEARLY.equals(mode)) {
            cal.add(Calendar.YEAR, -1);
            tvTitle.setText("Yearly Story");
            ivHero.setImageResource(android.R.drawable.btn_star_big_on);
        } else {
            cal.add(Calendar.DAY_OF_YEAR, -7);
            tvTitle.setText("Weekly Brief");
            ivHero.setImageResource(android.R.drawable.ic_menu_week);
        }
        periodStart = cal.getTimeInMillis();

        SimpleDateFormat sdf = new SimpleDateFormat("MMM d", Locale.getDefault());
        tvSubtitle.setText(sdf.format(new Date(periodStart)) + " - " + sdf.format(new Date(now)));

        int completed = 0;
        int ignored = 0;
        int events = 0;
        int activeHabits = 0;
        int habitCompletions = 0;

        for (CompletionHistory h : history) {
            if (h.timestamp >= periodStart && h.timestamp <= now) {
                if ("done".equalsIgnoreCase(h.action)) {
                    Item item = findItem(items, h.itemId);
                    if (item != null) {
                        if ("todo".equalsIgnoreCase(item.type)) completed++;
                        else if ("habit".equalsIgnoreCase(item.type)) habitCompletions++;
                    }
                }
            }
        }

        for (Item item : items) {
            if ("todo".equalsIgnoreCase(item.type)) {
                if (item.dueAt != null && item.dueAt >= periodStart && item.dueAt <= now) {
                    if (!"done".equalsIgnoreCase(item.status)) {
                        ignored++;
                    }
                }
            } else if ("event".equalsIgnoreCase(item.type)) {
                if (item.startAt != null && item.startAt >= periodStart && item.startAt <= now) {
                    events++;
                }
            } else if ("habit".equalsIgnoreCase(item.type)) {
                if (item.createdAt <= now && (item.status == null || !"archived".equalsIgnoreCase(item.status))) {
                    activeHabits++;
                }
            }
        }

        gridStats.removeAllViews();
        addStatCard("Completed", String.valueOf(completed));
        addStatCard("Ignored", String.valueOf(ignored));
        addStatCard("Events", String.valueOf(events));
        addStatCard("Habit Logs", String.valueOf(habitCompletions));

        updateInsights(completed, ignored, habitCompletions);
    }

    private void addStatCard(String label, String value) {
        View card = getLayoutInflater().inflate(R.layout.item_recap_stat_card, gridStats, false);
        ((TextView) card.findViewById(R.id.tv_stat_value)).setText(value);
        ((TextView) card.findViewById(R.id.tv_stat_label)).setText(label);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(8, 8, 8, 8);
        card.setLayoutParams(params);

        gridStats.addView(card);
    }

    private void updateInsights(int completed, int ignored, int habitLogs) {
        layoutInsights.removeAllViews();
        
        if (completed > ignored) {
            addInsight("You're on fire!", "You completed more tasks than you missed this period. Keep up the momentum!");
        } else if (ignored > 0) {
            addInsight("Room for focus", "A few tasks slipped through this period. Consider setting clearer priorities for next time.");
        }

        if (habitLogs > 10) {
            addInsight("Habit discipline", "Impressive consistency with your habits! Your daily logs show strong commitment.");
        }

        if (layoutInsights.getChildCount() == 0) {
            addInsight("Ready to grow", "Start tracking more tasks and habits to see deeper insights here.");
        }
    }

    private void addInsight(String title, String message) {
        View v = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, layoutInsights, false);
        TextView t1 = v.findViewById(android.R.id.text1);
        TextView t2 = v.findViewById(android.R.id.text2);
        t1.setText(title);
        t1.setTypeface(null, android.graphics.Typeface.BOLD);
        t2.setText(message);
        v.setPadding(0, 8, 0, 8);
        layoutInsights.addView(v);
    }

    private Item findItem(List<Item> items, long id) {
        for (Item i : items) if (i.id == id) return i;
        return null;
    }
}
