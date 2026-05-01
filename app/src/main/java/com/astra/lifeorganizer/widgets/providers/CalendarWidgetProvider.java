package com.astra.lifeorganizer.widgets.providers;

import android.appwidget.AppWidgetManager;
import android.content.Context;

import com.astra.lifeorganizer.widgets.WidgetConstants;

public class CalendarWidgetProvider extends BaseListWidgetProvider {
    @Override
    protected String getListType() {
        return WidgetConstants.LIST_CALENDAR;
    }

    @Override
    protected String getTitle() {
        return "Calendar";
    }

    @Override
    protected String getSubtitle() {
        return "Next 7 days at a glance";
    }

    public static void updateWidgets(Context context, AppWidgetManager manager, int[] ids) {
        BaseListWidgetProvider.updateAllWithProvider(context, manager, ids, "Calendar", "Next 7 days at a glance", WidgetConstants.LIST_CALENDAR, CalendarWidgetProvider.class);
    }
}
