package com.astra.lifeorganizer.ui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.drawable.GradientDrawable;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.astra.lifeorganizer.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashSet;

public class CalendarDayAdapter extends RecyclerView.Adapter<CalendarDayAdapter.DayViewHolder> {

    private final List<Date> dates;
    private Date selectedDate;
    private final OnDateClickListener listener;
    private final Calendar today = Calendar.getInstance();
    private Map<String, List<Integer>> eventDotColors = new HashMap<>();
    private final boolean showWeekNumbers;
    private final int weekStartDay;

    private int clickCount = 0;
    private final Handler doubleClickEventHandler = new Handler(Looper.getMainLooper());

    private int dominantMonth = -1;
    private int dominantYear = -1;
    private int prevDominantMonth = -1;
    private int prevDominantYear = -1;
    
    private final Calendar bindCal = Calendar.getInstance();
    private final Calendar selCal = Calendar.getInstance();

    public interface OnDateClickListener {
        void onDateClicked(Date date);
        void onDateDoubleClicked(Date date);
        void onDateLongPressed(Date date);
    }

    public CalendarDayAdapter(List<Date> dates, Date selectedDate, OnDateClickListener listener) {
        this(dates, selectedDate, listener, false);
    }

    public CalendarDayAdapter(List<Date> dates, Date selectedDate, OnDateClickListener listener, boolean showWeekNumbers) {
        this(dates, selectedDate, listener, showWeekNumbers, Calendar.SUNDAY);
    }

    public CalendarDayAdapter(List<Date> dates, Date selectedDate, OnDateClickListener listener, boolean showWeekNumbers, int weekStartDay) {
        this.dates = dates;
        this.selectedDate = selectedDate;
        this.listener = listener;
        this.showWeekNumbers = showWeekNumbers;
        this.weekStartDay = weekStartDay;
    }

    public void setEventDotColors(Map<String, List<Integer>> eventDotColors) {
        this.eventDotColors = eventDotColors != null ? eventDotColors : new HashMap<>();
        notifyDataSetChanged();
    }

    public void setSelectedDate(Date date) {
        this.selectedDate = date;
        notifyDataSetChanged();
    }

    public void setDominantMonth(int month, int year) {
        if (this.dominantMonth != month || this.dominantYear != year) {
            this.prevDominantMonth = this.dominantMonth;
            this.prevDominantYear = this.dominantYear;
            this.dominantMonth = month;
            this.dominantYear = year;
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_calendar_day, parent, false);
        return new DayViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        Date date = dates.get(position);
        
        if (date == null) {
            holder.tvDayNumber.setText("");
            holder.tvWeekNumber.setVisibility(View.GONE);
            holder.layoutDotContainer.setVisibility(View.GONE);
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
            holder.viewMonthHighlight.animate().cancel();
            holder.viewMonthHighlight.setAlpha(0f);
            holder.itemView.setOnClickListener(null);
            return;
        }

        bindCal.setTime(date);
        bindCal.setFirstDayOfWeek(weekStartDay);
        
        // Show month abbreviation on the 1st of each month for "Timeline" context
        if (bindCal.get(Calendar.DAY_OF_MONTH) == 1) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM", Locale.getDefault());
            holder.tvDayNumber.setText(sdf.format(date) + " " + bindCal.get(Calendar.DAY_OF_MONTH));
            holder.tvDayNumber.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10);
        } else {
            holder.tvDayNumber.setText(String.valueOf(bindCal.get(Calendar.DAY_OF_MONTH)));
            holder.tvDayNumber.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        }

        if (showWeekNumbers && bindCal.get(Calendar.DAY_OF_WEEK) == weekStartDay) {
            holder.tvWeekNumber.setVisibility(View.VISIBLE);
            holder.tvWeekNumber.setText(String.valueOf(bindCal.get(Calendar.WEEK_OF_YEAR)));
        } else {
            holder.tvWeekNumber.setVisibility(View.GONE);
        }

        String dateKey = bindCal.get(Calendar.YEAR) + "-" + bindCal.get(Calendar.DAY_OF_YEAR);
        List<Integer> colors = eventDotColors.get(dateKey);
        boolean isToday = isSameDay(bindCal, today);
        bindDots(holder, colors, isToday);
        boolean isSelected = false;
        if (selectedDate != null) {
            selCal.setTime(selectedDate);
            isSelected = isSameDay(bindCal, selCal);
        }

        if (isToday) {
            holder.itemView.setBackgroundResource(R.drawable.bg_today_tile);
//            holder.tvDayNumber.setBackgroundResource(R.drawable.bg_today_tile);
            int onPrimary = com.google.android.material.color.MaterialColors.getColor(holder.tvDayNumber, com.google.android.material.R.attr.colorOnPrimary);
            holder.tvDayNumber.setTextColor(onPrimary);
        } else if (isSelected) {
            holder.itemView.setBackgroundResource(R.drawable.bg_selected_tile);
            int onPrimaryContainer = com.google.android.material.color.MaterialColors.getColor(holder.tvDayNumber, com.google.android.material.R.attr.colorOnPrimaryContainer);
            holder.tvDayNumber.setTextColor(onPrimaryContainer);
        } else {
            holder.itemView.setBackgroundResource(R.drawable.bg_calendar_tile);
            holder.tvDayNumber.setBackgroundColor(Color.TRANSPARENT);
            holder.tvDayNumber.setTextColor(holder.tvDayNumber.getContext().getColor(android.R.color.tab_indicator_text));
            
            // Apply dominant month accent shade with alpha fade
            boolean isFocused = bindCal.get(Calendar.MONTH) == dominantMonth && bindCal.get(Calendar.YEAR) == dominantYear;
            boolean wasFocused = bindCal.get(Calendar.MONTH) == prevDominantMonth && bindCal.get(Calendar.YEAR) == prevDominantYear;

            float targetAlpha = isFocused ? 0.08f : 0f; // Subtle 8% focus
            
            holder.viewMonthHighlight.animate().cancel();

            // Only animate if the focus status just changed while this view is active
            if (isFocused != wasFocused) {
                holder.viewMonthHighlight.animate()
                    .alpha(targetAlpha)
                    .setDuration(400)
                    .start();
            } else {
                holder.viewMonthHighlight.setAlpha(targetAlpha);
            }
        }

        holder.itemView.setOnClickListener(v -> {
            clickCount++;
            if (clickCount == 1) {
                doubleClickEventHandler.postDelayed(() -> {
                    if (clickCount == 1) {
                        if (listener != null) listener.onDateClicked(date);
                    }
                    clickCount = 0;
                }, 300); // 300ms window for double tap
            } else if (clickCount == 2) {
                clickCount = 0;
                if (listener != null) listener.onDateDoubleClicked(date);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onDateLongPressed(date);
            return true;
        });
    }

    private void bindDots(DayViewHolder holder, List<Integer> colors, boolean outline) {
        if (holder == null || holder.layoutDotContainer == null) {
            return;
        }
        if (colors == null || colors.isEmpty()) {
            holder.layoutDotContainer.removeAllViews();
            holder.layoutDotContainer.setVisibility(View.GONE);
            return;
        }

        holder.layoutDotContainer.removeAllViews();
        holder.layoutDotContainer.setVisibility(View.VISIBLE);
        int maxDots = outline ? colors.size() : Math.min(colors.size(), 4);
        int dotSize = dp(holder.itemView, 5);
        int dotSpacing = dp(holder.itemView, 2);
        int strokeWidth = dp(holder.itemView, 1);

        for (int i = 0; i < maxDots; i++) {
            View dot = new View(holder.itemView.getContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dotSize, dotSize);
            if (i < maxDots - 1) {
                params.rightMargin = dotSpacing;
            }
            dot.setLayoutParams(params);
            dot.setBackground(makeDotDrawable(colors.get(i), outline, strokeWidth));
            holder.layoutDotContainer.addView(dot);
        }
    }

    private int dp(View view, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                view.getResources().getDisplayMetrics()
        );
    }

    private GradientDrawable makeDotDrawable(int color, boolean outline, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        if (outline) {
            drawable.setColor(Color.TRANSPARENT);
            drawable.setStroke(strokeWidth, color);
        } else {
            drawable.setColor(color);
        }
        return drawable;
    }

    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    @Override
    public int getItemCount() {
        return dates.size();
    }

    static class DayViewHolder extends RecyclerView.ViewHolder {
        TextView tvDayNumber;
        TextView tvWeekNumber;
        LinearLayout layoutDotContainer;
        View viewMonthHighlight;

        public DayViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDayNumber = itemView.findViewById(R.id.tv_day_number);
            tvWeekNumber = itemView.findViewById(R.id.tv_week_number);
            layoutDotContainer = itemView.findViewById(R.id.layout_dot_container);
            viewMonthHighlight = itemView.findViewById(R.id.view_month_highlight);
        }
    }
}
