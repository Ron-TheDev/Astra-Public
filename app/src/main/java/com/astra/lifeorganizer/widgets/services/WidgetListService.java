package com.astra.lifeorganizer.widgets.services;

import android.content.Intent;
import android.widget.RemoteViewsService;

public class WidgetListService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new WidgetListFactory(getApplicationContext(), intent);
    }
}
