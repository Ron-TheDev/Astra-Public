package com.astra.lifeorganizer.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.data.repositories.ItemRepository;
import com.astra.lifeorganizer.services.AlarmAlertService;
import com.astra.lifeorganizer.utils.AlarmConstants;
import com.astra.lifeorganizer.utils.AlarmQueueManager;
import com.astra.lifeorganizer.utils.AlarmScheduler;
import com.astra.lifeorganizer.utils.AlarmTaskExecutor;
import com.astra.lifeorganizer.utils.NotificationHelper;

import androidx.core.content.ContextCompat;

public class AlarmTriggerReceiver extends BroadcastReceiver {
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
                if (item == null) {
                    AlarmScheduler.cancelForItem(context, itemId);
                    Long nextId = AlarmQueueManager.acknowledgeAlarm(context, itemId);
                    if (nextId != null) {
                        Intent serviceIntent = new Intent(context, AlarmAlertService.class);
                        serviceIntent.putExtra(AlarmConstants.EXTRA_ITEM_ID, nextId);
                        ContextCompat.startForegroundService(context, serviceIntent);
                    }
                    return;
                }

                if (!AlarmScheduler.shouldSchedule(item)) {
                    AlarmScheduler.cancelForItem(context, itemId);
                    Long nextId = AlarmQueueManager.acknowledgeAlarm(context, itemId);
                    if (nextId != null) {
                        Intent serviceIntent = new Intent(context, AlarmAlertService.class);
                        serviceIntent.putExtra(AlarmConstants.EXTRA_ITEM_ID, nextId);
                        ContextCompat.startForegroundService(context, serviceIntent);
                    }
                    return;
                }

                AlarmTaskExecutor.postToMain(() -> {
                    NotificationHelper.cancelNotification(context, AlarmScheduler.getCountdownNotificationId(itemId));
                    boolean activated = AlarmQueueManager.registerIncoming(context, itemId);
                    if (activated) {
                        Intent serviceIntent = new Intent(context, AlarmAlertService.class);
                        serviceIntent.putExtra(AlarmConstants.EXTRA_ITEM_ID, itemId);
                        ContextCompat.startForegroundService(context, serviceIntent);
                    } else if (AlarmQueueManager.getActiveAlarmId(context) != itemId) {
                        com.astra.lifeorganizer.utils.NotificationHelper.showQueuedAlarmNotification(context, item);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                pendingResult.finish();
            }
        });
    }
}
