package com.astra.lifeorganizer.widgets.providers;

import android.appwidget.AppWidgetManager;
import android.content.Context;

import com.astra.lifeorganizer.widgets.WidgetConstants;

public class UpcomingWidgetProvider extends BaseListWidgetProvider {
    @Override
    protected String getListType() {
        return WidgetConstants.LIST_UPCOMING;
    }

    @Override
    protected String getTitle() {
        return "Upcoming";
    }

    @Override
    protected String getSubtitle() {
        return "Next scheduled events";
    }

    public static void updateWidgets(Context context, AppWidgetManager manager, int[] ids) {
        BaseListWidgetProvider.updateAllWithProvider(context, manager, ids, "Upcoming", "Next scheduled events", WidgetConstants.LIST_UPCOMING, UpcomingWidgetProvider.class);
    }
}
