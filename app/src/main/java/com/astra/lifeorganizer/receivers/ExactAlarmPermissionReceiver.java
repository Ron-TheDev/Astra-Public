package com.astra.lifeorganizer.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.data.repositories.ItemRepository;
import com.astra.lifeorganizer.utils.AlarmScheduler;
import com.astra.lifeorganizer.utils.AlarmTaskExecutor;
import com.astra.lifeorganizer.utils.RecapBriefScheduler;

public class ExactAlarmPermissionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }

        if (!android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(intent.getAction())) {
            return;
        }

        BroadcastReceiver.PendingResult result = goAsync();
        AlarmTaskExecutor.execute(() -> {
            try {
                ItemRepository repository = new ItemRepository((android.app.Application) context.getApplicationContext());
                for (Item item : repository.getAllExportableItems()) {
                    AlarmScheduler.scheduleForItem(context, item);
                }
                RecapBriefScheduler.rescheduleAll(context);
                com.astra.lifeorganizer.widgets.WidgetUpdater.updateAll(context);
            } finally {
                result.finish();
            }
        });
    }
}
