package com.astra.lifeorganizer.widgets;

import android.content.Context;
import android.content.Intent;

import com.astra.lifeorganizer.MainActivity;

public final class WidgetIntentUtils {
    private WidgetIntentUtils() {}

    public static Intent buildPageIntent(Context context, String page) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(WidgetConstants.EXTRA_WIDGET_PAGE, page);
        return intent;
    }

    public static Intent buildItemIntent(Context context, long itemId, String itemType) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(WidgetConstants.EXTRA_WIDGET_ITEM_ID, itemId);
        intent.putExtra(WidgetConstants.EXTRA_WIDGET_ITEM_TYPE, itemType);
        return intent;
    }
}
