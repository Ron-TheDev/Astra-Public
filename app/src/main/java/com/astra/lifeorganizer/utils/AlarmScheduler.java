package com.astra.lifeorganizer.utils;

import android.app.AlarmManager;
import android.app.AlarmManager.AlarmClockInfo;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.receivers.AlarmCountdownReceiver;
import com.astra.lifeorganizer.receivers.AlarmTriggerReceiver;

public final class AlarmScheduler {
    private AlarmScheduler() {}

    public static void scheduleForItem(Context context, Item item) {
        if (item == null || item.id <= 0) {
            return;
        }
        cancelForItem(context, item.id);
        if (!shouldSchedule(item)) {
            return;
        }

        long scheduledTime = getAlarmTime(item);

        boolean exactScheduled = scheduleTriggerExact(context, item, scheduledTime);
        if (!exactScheduled) {
            scheduleTriggerInexact(context, item, scheduledTime);
        }
        scheduleCountdown(context, item, scheduledTime, exactScheduled);
    }

    public static void cancelForItem(Context context, long itemId) {
        if (itemId <= 0) {
            return;
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        alarmManager.cancel(buildTriggerPendingIntent(context, itemId, 0));
        alarmManager.cancel(buildCountdownPendingIntent(context, itemId, 0));
        NotificationHelper.cancelNotification(context, getAlarmNotificationId(itemId));
        NotificationHelper.cancelNotification(context, getCountdownNotificationId(itemId));
    }

    public static void snooze(Context context, Item item) {
        cancelForItem(context, item.id);
        long snoozedAt = System.currentTimeMillis() + AlarmConstants.SNOOZE_WINDOW_MS;
        boolean exactScheduled = scheduleTriggerExact(context, item, snoozedAt);
        if (!exactScheduled) {
            scheduleTriggerInexact(context, item, snoozedAt);
        }
        scheduleCountdown(context, item, snoozedAt, exactScheduled);
    }

    public static long getScheduledTime(Item item) {
        if ("event".equalsIgnoreCase(item.type)) {
            return item.startAt != null ? item.startAt : 0L;
        }
        return item.dueAt != null ? item.dueAt : 0L;
    }

    public static long getAlarmTime(Item item) {
        if (item == null) {
            return 0L;
        }
        if (item.reminderAt != null && item.reminderAt > 0L) {
            return item.reminderAt;
        }
        return getScheduledTime(item);
    }

    public static boolean shouldSchedule(Item item) {
        if (item == null) {
            return false;
        }
        if (!"todo".equalsIgnoreCase(item.type) && !"event".equalsIgnoreCase(item.type)) {
            return false;
        }
        if (item.priority != AlarmConstants.PRIORITY_HIGH) {
            return false;
        }
        if (item.status != null && ("done".equalsIgnoreCase(item.status) || "archived".equalsIgnoreCase(item.status) || "canceled".equalsIgnoreCase(item.status))) {
            return false;
        }
        long alarmTime = getAlarmTime(item);
        if (alarmTime <= 0) {
            return false;
        }

        // Only schedule if the alarm is in the future.
        // We allow a small buffer (e.g. 5 minutes) to account for time spent in boot-up/rescheduling.
        // This prevents 'notifications for past alarms' after an app update.
        return alarmTime > (System.currentTimeMillis() - (5 * 60 * 1000L));
    }

    public static int getAlarmNotificationId(long itemId) {
        return Long.hashCode(itemId) & 0x7fffffff;
    }

    public static int getCountdownNotificationId(long itemId) {
        return Long.hashCode(itemId ^ AlarmConstants.NOTIFICATION_ID_OFFSET) & 0x7fffffff;
    }

    private static boolean scheduleTriggerExact(Context context, Item item, long triggerAt) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return false;
        }

        try {
            PendingIntent operationIntent = buildTriggerPendingIntent(context, item.id, triggerAt);
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operationIntent);
            return true;
        } catch (SecurityException exactDenied) {
            return false;
        }
    }

    private static void scheduleTriggerInexact(Context context, Item item, long triggerAt) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        PendingIntent pendingIntent = buildTriggerPendingIntent(context, item.id, triggerAt);
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
    }

    private static void scheduleCountdown(Context context, Item item, long triggerAt, boolean exactScheduled) {
        long countdownAt = triggerAt - AlarmConstants.COUNTDOWN_WINDOW_MS;
        if (countdownAt <= System.currentTimeMillis()) {
            NotificationHelper.showCountdownNotification(
                    context,
                    item,
                    triggerAt,
                    true
            );
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        PendingIntent pendingIntent = buildCountdownPendingIntent(context, item.id, triggerAt);
        try {
            if (exactScheduled) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, countdownAt, pendingIntent);
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, countdownAt, pendingIntent);
            }
        } catch (SecurityException denied) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, countdownAt, pendingIntent);
        }
    }

    private static PendingIntent buildTriggerPendingIntent(Context context, long itemId, long targetTime) {
        Intent intent = new Intent(context, AlarmTriggerReceiver.class);
        intent.setAction(AlarmConstants.ACTION_ALARM_TRIGGER);
        intent.putExtra(AlarmConstants.EXTRA_ITEM_ID, itemId);
        intent.putExtra(AlarmConstants.EXTRA_ITEM_TIME, targetTime);
        return PendingIntent.getBroadcast(
                context,
                requestCode(itemId, 1),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent buildCountdownPendingIntent(Context context, long itemId, long targetTime) {
        Intent intent = new Intent(context, AlarmCountdownReceiver.class);
        intent.setAction(AlarmConstants.ACTION_ALARM_COUNTDOWN);
        intent.putExtra(AlarmConstants.EXTRA_ITEM_ID, itemId);
        intent.putExtra(AlarmConstants.EXTRA_ITEM_TIME, targetTime);
        return PendingIntent.getBroadcast(
                context,
                requestCode(itemId, 2),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent buildFullScreenPendingIntent(Context context, long itemId) {
        Intent intent = new Intent(context, com.astra.lifeorganizer.ui.AlarmFullScreenActivity.class);
        intent.setAction(AlarmConstants.ACTION_ALARM_OPEN);
        intent.putExtra(AlarmConstants.EXTRA_ITEM_ID, itemId);
        return PendingIntent.getActivity(
                context,
                requestCode(itemId, 3),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static int requestCode(long itemId, int salt) {
        long mixed = itemId ^ ((long) salt << 48);
        return Long.valueOf(mixed).hashCode();
    }

}
