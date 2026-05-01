package com.astra.lifeorganizer.widgets.services;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.astra.lifeorganizer.R;
import com.astra.lifeorganizer.widgets.WidgetConstants;
import com.astra.lifeorganizer.widgets.WidgetDataHelper;
import com.astra.lifeorganizer.widgets.WidgetIntentUtils;

import java.util.ArrayList;
import java.util.List;

public class WidgetListFactory implements RemoteViewsService.RemoteViewsFactory {
    private final Context context;
    private final int appWidgetId;
    private final String listType;
    private final List<WidgetDataHelper.ListRow> rows = new ArrayList<>();

    public WidgetListFactory(Context context, Intent intent) {
        this.context = context;
        this.appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        this.listType = intent.getStringExtra(WidgetConstants.EXTRA_WIDGET_LIST_TYPE);
    }

    @Override
    public void onCreate() {}

    @Override
    public void onDataSetChanged() {
        rows.clear();
        rows.addAll(WidgetDataHelper.loadRows(context, listType));
    }

    @Override
    public void onDestroy() {
        rows.clear();
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public RemoteViews getViewAt(int position) {
        if (position < 0 || position >= rows.size()) {
            return null;
        }

        WidgetDataHelper.ListRow row = rows.get(position);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_list_item);
        views.setTextViewText(R.id.tv_item_title, row.title);
        views.setTextViewText(R.id.tv_item_subtitle, row.subtitle);

        Intent fillIn = WidgetIntentUtils.buildItemIntent(context, row.itemId, row.itemType);
        views.setOnClickFillInIntent(R.id.widget_item_root, fillIn);
        return views;
    }

    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= rows.size()) {
            return position;
        }
        return rows.get(position).itemId;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }
}
