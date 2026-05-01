package com.astra.lifeorganizer.utils;

import android.app.Application;
import android.content.Context;
import android.content.Intent;

import com.astra.lifeorganizer.MainActivity;
import com.astra.lifeorganizer.data.database.AstraDatabase;
import com.astra.lifeorganizer.data.entities.CompletionHistory;
import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.data.repositories.ItemRepository;
import com.astra.lifeorganizer.services.AlarmAlertService;
import com.astra.lifeorganizer.ui.AlarmFullScreenActivity;

import java.util.Calendar;

public final class AlarmActionHandler {
    private AlarmActionHandler() {}

    public static void snooze(Context context, long itemId) {
        Item item = loadItem(context, itemId);
        if (item == null) {
            stopAlarmService(context);
            releaseCurrentAndLaunchNext(context, itemId);
            return;
        }

        AlarmScheduler.snooze(context, item);
        stopAlarmService(context);
        releaseCurrentAndLaunchNext(context, itemId);
    }

    public static void markDoneOrDismiss(Context context, long itemId) {
        Item item = loadItem(context, itemId);
        if (item == null) {
            stopAlarmService(context);
            releaseCurrentAndLaunchNext(context, itemId);
            return;
        }

        if ("todo".equalsIgnoreCase(item.type)) {
            if (item.recurrenceRule != null && !item.recurrenceRule.trim().isEmpty() && !"NONE".equalsIgnoreCase(item.recurrenceRule)) {
                long baseTime = item.dueAt != null ? item.dueAt : System.currentTimeMillis();
                long next = com.astra.lifeorganizer.utils.RecurrenceRuleParser.getNextOccurrence(item.recurrenceRule, baseTime);
                if (next > 0) {
                    item.dueAt = next;
                    item.status = "pending";
                } else {
                    item.status = "done";
                }
            } else {
                item.status = "done";
            }

            recordDoneHistory(context, item.id);
            saveItem(context, item);
        } else {
            AlarmScheduler.cancelForItem(context, item.id);
        }
        stopAlarmService(context);

        releaseCurrentAndLaunchNext(context, item.id);
    }

    public static Intent createOpenIntent(Context context, long itemId) {
        return createOpenIntent(context, itemId, null);
    }

    public static Intent createOpenIntent(Context context, long itemId, String type) {
        if (type == null || type.isEmpty()) {
            type = "todo";
        }
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(AlarmConstants.EXTRA_TARGET_ITEM_ID, itemId);
        intent.putExtra(AlarmConstants.EXTRA_ITEM_TYPE, type);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    public static Intent createFullscreenIntent(Context context, long itemId, boolean previewMode) {
        Intent intent = new Intent(context, AlarmFullScreenActivity.class);
        intent.setAction(AlarmConstants.ACTION_ALARM_OPEN);
        intent.putExtra(AlarmConstants.EXTRA_ITEM_ID, itemId);
        intent.putExtra(AlarmConstants.EXTRA_FORCE_FULLSCREEN_PREVIEW, previewMode);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        return intent;
    }

    public static void launchFullscreenAlarm(Context context, long itemId, boolean previewMode) {
        context.startActivity(createFullscreenIntent(context, itemId, previewMode));
    }

    public static void markDoneFromAlarmUi(Context context, long itemId) {
        markDoneOrDismiss(context, itemId);
    }

    public static void dismissFromAlarmUi(Context context, long itemId) {
        Item item = loadItem(context, itemId);
        if (item != null) {
            if ("todo".equalsIgnoreCase(item.type)) {
                // If recurring, advance to next occurrence so it schedules for tomorrow/next cycle
                if (item.recurrenceRule != null && !item.recurrenceRule.trim().isEmpty() && !"NONE".equalsIgnoreCase(item.recurrenceRule)) {
                    long baseTime = item.dueAt != null ? item.dueAt : System.currentTimeMillis();
                    long next = com.astra.lifeorganizer.utils.RecurrenceRuleParser.getNextOccurrence(item.recurrenceRule, baseTime);
                    if (next > 0) {
                        item.dueAt = next;
                        item.status = "pending";
                    } else {
                        item.status = "done";
                    }
                } else {
                    // Non-recurring: Clear priority so it's no longer 'High Priority' alarm candidate
                    item.priority = 1; // PRIORITY_NORMAL (assuming 3 is high)
                }
                saveItem(context, item);
            } else {
                AlarmScheduler.cancelForItem(context, item.id);
            }
        }

        stopAlarmService(context);
        releaseCurrentAndLaunchNext(context, itemId);
    }

    private static Item loadItem(Context context, long itemId) {
        ItemRepository repository = new ItemRepository((Application) context.getApplicationContext());
        return repository.getItemByIdSync(itemId);
    }

    private static void saveItem(Context context, Item item) {
        ItemRepository repository = new ItemRepository((Application) context.getApplicationContext());
        repository.update(item);
    }

    private static void recordDoneHistory(Context context, long itemId) {
        ItemRepository repository = new ItemRepository((Application) context.getApplicationContext());
        CompletionHistory history = new CompletionHistory();
        history.itemId = itemId;
        history.action = "done";
        history.timestamp = System.currentTimeMillis();
        repository.recordHistory(history);
    }

    private static void releaseCurrentAndLaunchNext(Context context, long itemId) {
        Long nextId = AlarmQueueManager.acknowledgeAlarm(context, itemId);
        if (nextId == null) {
            return;
        }

        AlarmScheduler.cancelForItem(context, nextId);
        Intent serviceIntent = new Intent(context, AlarmAlertService.class);
        serviceIntent.putExtra(AlarmConstants.EXTRA_ITEM_ID, nextId);
        androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent);
    }

    private static void stopAlarmService(Context context) {
        context.stopService(new Intent(context, AlarmAlertService.class));
    }
}
