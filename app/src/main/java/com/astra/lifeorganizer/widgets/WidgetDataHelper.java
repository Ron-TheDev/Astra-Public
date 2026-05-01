package com.astra.lifeorganizer.widgets;

import android.content.Context;

import com.astra.lifeorganizer.data.entities.CompletionHistory;
import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.data.repositories.ItemRepository;
import com.astra.lifeorganizer.utils.LabelUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class WidgetDataHelper {
    private WidgetDataHelper() {}

    public static SummaryData loadSummary(Context context) {
        ItemRepository repository = new ItemRepository((android.app.Application) context.getApplicationContext());
        List<Item> items = repository.getAllItemsSync();
        List<CompletionHistory> history = repository.getAllHistorySync();
        long now = System.currentTimeMillis();
        long todayStart = startOfDay(now);
        long todayEnd = endOfDay(now);
        long nextSevenDays = now + 7L * dayMs();

        SummaryData data = new SummaryData();
        for (Item item : items) {
            if (item == null || item.status != null && "archived".equalsIgnoreCase(item.status)) {
                continue;
            }
            if ("todo".equalsIgnoreCase(item.type)) {
                if (item.status == null || !"done".equalsIgnoreCase(item.status)) {
                    if (item.dueAt != null) {
                        if (item.dueAt >= todayStart && item.dueAt <= todayEnd) {
                            data.tasksDueToday++;
                        } else if (item.dueAt < todayStart) {
                            data.overdueTasks++;
                        }
                    }
                }
            } else if ("event".equalsIgnoreCase(item.type)) {
                if (item.startAt != null && item.startAt >= now) {
                    data.upcomingEvents++;
                    if (data.nextEventTitle == null || item.startAt < data.nextEventTime) {
                        data.nextEventTime = item.startAt;
                    data.nextEventTitle = displayTitle(item.title);
                    }
                }
            }
        }

        for (CompletionHistory h : history) {
            if (h == null || h.timestamp < todayStart || h.timestamp > todayEnd || !"done".equalsIgnoreCase(h.action)) {
                continue;
            }
            Item item = findItem(items, h.itemId);
            if (item != null && "todo".equalsIgnoreCase(item.type)) {
                data.completedTasksToday++;
            }
        }

        if (data.nextEventTitle == null) {
            data.nextEventTitle = "No upcoming event";
        }
        data.dateText = new java.text.SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(new Date(now));
        data.weekText = new java.text.SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(new Date(now));
        data.todayText = new java.text.SimpleDateFormat("MMM d", Locale.getDefault()).format(new Date(now));
        return data;
    }

    public static List<ListRow> loadRows(Context context, String listType) {
        ItemRepository repository = new ItemRepository((android.app.Application) context.getApplicationContext());
        List<Item> items = repository.getAllItemsSync();
        List<ListRow> rows = new ArrayList<>();
        long now = System.currentTimeMillis();
        long nextSevenDays = now + 7L * dayMs();

        for (Item item : items) {
            if (item == null || item.status != null && ("archived".equalsIgnoreCase(item.status) || "done".equalsIgnoreCase(item.status))) {
                continue;
            }
            if (WidgetConstants.LIST_TODO.equals(listType)) {
                if (!"todo".equalsIgnoreCase(item.type)) continue;
                long due = item.dueAt != null ? item.dueAt : 0L;
                if (due <= 0L) continue;
                rows.add(new ListRow(item.id, displayTitle(item.title), "Due " + formatDateTime(context, due), item.type, due));
            } else if (WidgetConstants.LIST_UPCOMING.equals(listType)) {
                if (!"event".equalsIgnoreCase(item.type)) continue;
                long start = item.startAt != null ? item.startAt : 0L;
                if (start < now) continue;
                rows.add(new ListRow(item.id, displayTitle(item.title), item.allDay ? "All day" : formatDateTime(context, start), item.type, start));
            } else if (WidgetConstants.LIST_CALENDAR.equals(listType)) {
                long time = item.startAt != null ? item.startAt : (item.dueAt != null ? item.dueAt : 0L);
                if (time <= 0L || time > nextSevenDays || time < now - dayMs()) continue;
                String subtitle = "event".equalsIgnoreCase(item.type)
                        ? (item.allDay ? "All day" : formatDateTime(context, time))
                        : "Due " + formatDateTime(context, time);
                rows.add(new ListRow(item.id, displayTitle(item.title), subtitle, item.type, time));
            }
        }

        rows.sort(Comparator.comparingLong(row -> row.primaryTime));
        return rows;
    }

    private static Item findItem(List<Item> items, long itemId) {
        if (items == null) {
            return null;
        }
        for (Item item : items) {
            if (item != null && item.id == itemId) {
                return item;
            }
        }
        return null;
    }

    private static String displayTitle(String title) {
        String normalized = LabelUtils.displayLabel(title);
        return normalized != null ? normalized : "Untitled";
    }

    private static String formatDateTime(Context context, long timestamp) {
        return com.astra.lifeorganizer.utils.DateTimeFormatUtils.formatDate(context, timestamp) + " • " +
                com.astra.lifeorganizer.utils.DateTimeFormatUtils.formatTime(context, timestamp);
    }

    private static long startOfDay(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static long endOfDay(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTimeInMillis();
    }

    private static long dayMs() {
        return 24L * 60L * 60L * 1000L;
    }

    public static final class SummaryData {
        public int completedTasksToday;
        public int tasksDueToday;
        public int overdueTasks;
        public int upcomingEvents;
        public String nextEventTitle;
        public long nextEventTime;
        public String dateText;
        public String weekText;
        public String todayText;
    }

    public static final class ListRow {
        public final long itemId;
        public final String title;
        public final String subtitle;
        public final String itemType;
        public final long primaryTime;

        public ListRow(long itemId, String title, String subtitle, String itemType, long primaryTime) {
            this.itemId = itemId;
            this.title = title;
            this.subtitle = subtitle;
            this.itemType = itemType;
            this.primaryTime = primaryTime;
        }
    }
}
