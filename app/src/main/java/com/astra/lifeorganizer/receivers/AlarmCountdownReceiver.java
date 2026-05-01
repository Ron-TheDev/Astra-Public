package com.astra.lifeorganizer.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.data.repositories.ItemRepository;
import com.astra.lifeorganizer.services.AlarmAlertService;
import com.astra.lifeorganizer.utils.AlarmConstants;
import com.astra.lifeorganizer.utils.AlarmQueueManager;
import com.astra.lifeorganizer.utils.AlarmScheduler;
import com.astra.lifeorganizer.utils.AlarmTaskExecutor;
import com.astra.lifeorganizer.utils.NotificationHelper;

public class AlarmCountdownReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pendingResult = goAsync();
        AlarmTaskExecutor.execute(() -> {
            try {
                if (intent == null) {
                    return;
                }

                long itemId = intent.getLongExtra(AlarmConstants.EXTRA_ITEM_ID, 0L);
                if (itemId <= 0) {
                    return;
                }

                Item item = new ItemRepository((android.app.Application) context.getApplicationContext()).getItemByIdSync(itemId);
                if (item == null || !AlarmScheduler.shouldSchedule(item)) {
                    AlarmScheduler.cancelForItem(context, itemId);
                    Long nextId = AlarmQueueManager.acknowledgeAlarm(context, itemId);
                    if (nextId != null) {
                        Intent serviceIntent = new Intent(context, AlarmAlertService.class);
                        serviceIntent.putExtra(AlarmConstants.EXTRA_ITEM_ID, nextId);
                        ContextCompat.startForegroundService(context, serviceIntent);
                    }
                    return;
                }

                long alarmTime = AlarmScheduler.getScheduledTime(item);
                if (alarmTime <= System.currentTimeMillis()) {
                    NotificationHelper.cancelNotification(context, AlarmScheduler.getCountdownNotificationId(itemId));
                    AlarmTaskExecutor.postToMain(() -> {
                        boolean activated = AlarmQueueManager.registerIncoming(context, itemId);
                        if (activated) {
                            Intent serviceIntent = new Intent(context, AlarmAlertService.class);
                            serviceIntent.putExtra(AlarmConstants.EXTRA_ITEM_ID, itemId);
                            ContextCompat.startForegroundService(context, serviceIntent);
                        } else if (AlarmQueueManager.getActiveAlarmId(context) != itemId) {
                            NotificationHelper.showQueuedAlarmNotification(context, item);
                        }
                    });
                    return;
                }

                NotificationHelper.showCountdownNotification(context, item, alarmTime, false);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                pendingResult.finish();
            }
        });
    }
}
