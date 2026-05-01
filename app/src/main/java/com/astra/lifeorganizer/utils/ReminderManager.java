package com.astra.lifeorganizer.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.receivers.ReminderReceiver;

public class ReminderManager {

    public static void scheduleReminder(Context context, Item item, long reminderTime) {
        if (reminderTime <= System.currentTimeMillis()) return;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra(ReminderReceiver.EXTRA_ITEM_TITLE, item.title);
        intent.putExtra(ReminderReceiver.EXTRA_ITEM_TYPE, item.type);
        intent.putExtra(ReminderReceiver.EXTRA_ITEM_ID, item.id);
        
        // Pass the original event/task time to calculate relative time in receiver
        long baseTime = (item.type.equals("event")) ? (item.startAt != null ? item.startAt : 0) : (item.dueAt != null ? item.dueAt : 0);
        intent.putExtra(ReminderReceiver.EXTRA_BASE_TIME, baseTime);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) item.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTime, pendingIntent);
        }
    }

    public static void cancelReminder(Context context, long itemId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) itemId,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null && pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
        }
    }
}
