package com.astra.lifeorganizer.widgets;

import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;

import com.astra.lifeorganizer.widgets.providers.CalendarWidgetProvider;
import com.astra.lifeorganizer.widgets.providers.UpcomingWidgetProvider;
import com.astra.lifeorganizer.widgets.providers.TodaySummaryWidgetProvider;
import com.astra.lifeorganizer.widgets.providers.TodoListWidgetProvider;

public final class WidgetUpdater {
    private WidgetUpdater() {}

    public static void updateAll(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
        if (manager == null) {
            return;
        }

        updateProvider(appContext, manager, TodaySummaryWidgetProvider.class);
        updateProvider(appContext, manager, TodoListWidgetProvider.class);
        updateProvider(appContext, manager, UpcomingWidgetProvider.class);
        updateProvider(appContext, manager, CalendarWidgetProvider.class);
    }

    private static void updateProvider(Context context, AppWidgetManager manager, Class<?> providerClass) {
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, providerClass));
        if (ids != null && ids.length > 0) {
            if (providerClass == TodaySummaryWidgetProvider.class) {
                TodaySummaryWidgetProvider.updateWidgets(context, manager, ids);
            } else if (providerClass == TodoListWidgetProvider.class) {
                TodoListWidgetProvider.updateWidgets(context, manager, ids);
            } else if (providerClass == UpcomingWidgetProvider.class) {
                UpcomingWidgetProvider.updateWidgets(context, manager, ids);
            } else if (providerClass == CalendarWidgetProvider.class) {
                CalendarWidgetProvider.updateWidgets(context, manager, ids);
            }
        }
    }
}
