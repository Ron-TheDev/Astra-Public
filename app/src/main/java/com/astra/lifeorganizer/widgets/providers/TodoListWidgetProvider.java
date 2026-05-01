package com.astra.lifeorganizer.widgets.providers;

import android.appwidget.AppWidgetManager;
import android.content.Context;

import com.astra.lifeorganizer.widgets.WidgetConstants;

public class TodoListWidgetProvider extends BaseListWidgetProvider {
    @Override
    protected String getListType() {
        return WidgetConstants.LIST_TODO;
    }

    @Override
    protected String getTitle() {
        return "Todo List";
    }

    @Override
    protected String getSubtitle() {
        return "Open tasks and due items";
    }

    public static void updateWidgets(Context context, AppWidgetManager manager, int[] ids) {
        BaseListWidgetProvider.updateAllWithProvider(context, manager, ids, "Todo List", "Open tasks and due items", WidgetConstants.LIST_TODO, TodoListWidgetProvider.class);
    }
}
