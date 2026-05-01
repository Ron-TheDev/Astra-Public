package com.astra.lifeorganizer.widgets.providers;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

import com.astra.lifeorganizer.R;
import com.astra.lifeorganizer.widgets.WidgetConstants;
import com.astra.lifeorganizer.widgets.WidgetIntentUtils;

public abstract class BaseListWidgetProvider extends AppWidgetProvider {

    protected abstract String getListType();
    protected abstract String getTitle();
    protected abstract String getSubtitle();

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        updateAllWithProvider(
                context,
                appWidgetManager,
                appWidgetIds,
                getTitle(),
                getSubtitle(),
                getListType(),
                getClass()
        );
    }

    public static void bindListAdapter(RemoteViews views, Context context, int appWidgetId, String listType) {
        android.content.Intent serviceIntent = new android.content.Intent(context, com.astra.lifeorganizer.widgets.services.WidgetListService.class);
        serviceIntent.putExtra(WidgetConstants.EXTRA_WIDGET_LIST_TYPE, listType);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        serviceIntent.setData(android.net.Uri.parse(serviceIntent.toUri(android.content.Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.list_widget_items, serviceIntent);
        views.setEmptyView(R.id.list_widget_items, R.id.tv_widget_subtitle);
        android.content.Intent templateIntent = WidgetIntentUtils.buildPageIntent(context, listType);
        android.app.PendingIntent templatePendingIntent = android.app.PendingIntent.getActivity(
                context,
                appWidgetId,
                templateIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
        );
        views.setPendingIntentTemplate(R.id.list_widget_items, templatePendingIntent);
        views.setOnClickPendingIntent(R.id.btn_widget_open, templatePendingIntent);
    }

    public static void updateAllWithProvider(Context context, AppWidgetManager manager, int[] ids, String title, String subtitle, String listType, Class<? extends BaseListWidgetProvider> providerClass) {
        for (int appWidgetId : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_list);
            views.setTextViewText(R.id.tv_widget_title, title);
            views.setTextViewText(R.id.tv_widget_subtitle, subtitle);
            bindListAdapter(views, context, appWidgetId, listType);
            manager.updateAppWidget(appWidgetId, views);
            manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.list_widget_items);
        }
    }
}
