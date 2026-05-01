package com.astra.lifeorganizer.widgets.providers;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.app.PendingIntent;
import android.widget.RemoteViews;

import com.astra.lifeorganizer.R;
import com.astra.lifeorganizer.data.database.AstraDatabase;
import com.astra.lifeorganizer.widgets.WidgetConstants;
import com.astra.lifeorganizer.widgets.WidgetDataHelper;
import com.astra.lifeorganizer.widgets.WidgetIntentUtils;
import com.astra.lifeorganizer.utils.DateTimeFormatUtils;

public class TodaySummaryWidgetProvider extends android.appwidget.AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        updateWidgets(context, appWidgetManager, appWidgetIds);
    }

    public static void updateWidgets(Context context, AppWidgetManager manager, int[] ids) {
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            WidgetDataHelper.SummaryData data = WidgetDataHelper.loadSummary(context);
            for (int appWidgetId : ids) {
                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_summary);
                views.setTextViewText(R.id.tv_widget_title, "Today's Summary");
                views.setTextViewText(R.id.tv_widget_date, data.dateText);
                views.setTextViewText(R.id.tv_widget_completed_value, String.valueOf(data.completedTasksToday));
                views.setTextViewText(R.id.tv_widget_due_value, String.valueOf(data.tasksDueToday));
                views.setTextViewText(R.id.tv_widget_events_value, String.valueOf(data.upcomingEvents));
                views.setTextViewText(R.id.tv_widget_next_event, "Next event");
                views.setTextViewText(R.id.tv_widget_next_event_subtitle, data.nextEventTitle);
                views.setTextViewText(R.id.tv_widget_next_event_time,
                        data.nextEventTime > 0 ? DateTimeFormatUtils.formatTime(context, data.nextEventTime) : "--");
                PendingIntent open = PendingIntent.getActivity(
                        context,
                        appWidgetId,
                        WidgetIntentUtils.buildPageIntent(context, WidgetConstants.PAGE_HOME),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                views.setOnClickPendingIntent(R.id.btn_widget_open, open);
                views.setOnClickPendingIntent(R.id.tv_widget_title, open);
                manager.updateAppWidget(appWidgetId, views);
            }
        });
    }
}
