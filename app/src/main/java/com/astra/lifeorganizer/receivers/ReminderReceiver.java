package com.astra.lifeorganizer.receivers;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.astra.lifeorganizer.MainActivity;
import com.astra.lifeorganizer.utils.NotificationHelper;
import java.util.concurrent.TimeUnit;

public class ReminderReceiver extends BroadcastReceiver {
    public static final String EXTRA_ITEM_TITLE = "EXTRA_ITEM_TITLE";
    public static final String EXTRA_ITEM_TYPE = "EXTRA_ITEM_TYPE";
    public static final String EXTRA_ITEM_ID = "EXTRA_ITEM_ID";
    public static final String EXTRA_BASE_TIME = "EXTRA_BASE_TIME";

//    @Override
//    public void onReceive(Context context, Intent intent) {
//        String title = intent.getStringExtra(EXTRA_ITEM_TITLE);
//        String type = intent.getStringExtra(EXTRA_ITEM_TYPE);
//        long id = intent.getLongExtra(EXTRA_ITEM_ID, 0);
//        long baseTime = intent.getLongExtra(EXTRA_BASE_TIME, 0);
//
//        String relativeTimeMsg = calculateRelativeTime(baseTime);
//
//        String actionLabel = "event".equals(type) ? "Starts" : "Due";
//        String message = actionLabel + " " + relativeTimeMsg;
//
//
//        NotificationHelper.showNotification(context, title != null ? title : "Astra", message, (int) id);
//    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        long id = intent.getLongExtra(EXTRA_ITEM_ID, 0);

        if ("ACTION_SNOOZE".equals(action)) {
            NotificationHelper.cancelNotification(context, (int) id);
            com.astra.lifeorganizer.utils.AlarmActionHandler.snooze(context, id);
            return;
        } else if ("ACTION_DISMISS".equals(action)) {
            NotificationHelper.cancelNotification(context, (int) id);
            return;
        }

        String title = intent.getStringExtra(EXTRA_ITEM_TITLE);
        String type = intent.getStringExtra(EXTRA_ITEM_TYPE);
        long baseTime = intent.getLongExtra(EXTRA_BASE_TIME, 0);

        String relativeTimeMsg = calculateRelativeTime(baseTime);

        String actionLabel = "event".equals(type) ? "Starts" : "Due";
        String message = actionLabel + " " + relativeTimeMsg;

        // 🔑 Create intent to open app in foreground
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setAction("OPEN_ITEM_" + id); // ensures uniqueness

        openIntent.putExtra("item_id", id);
        openIntent.putExtra("item_type", type);
        openIntent.putExtra("notification_id", (int) id);

        // 🚨 REQUIRED for BroadcastReceiver → Activity launch
        openIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (int) id, // unique request code
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent dismissIntent = new Intent(context, ReminderReceiver.class);
        dismissIntent.setAction("ACTION_DISMISS");
        dismissIntent.putExtra(EXTRA_ITEM_ID, id);
        PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(
                context, (int) id + 10000, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent snoozeIntent = new Intent(context, ReminderReceiver.class);
        snoozeIntent.setAction("ACTION_SNOOZE");
        snoozeIntent.putExtra(EXTRA_ITEM_ID, id);
        PendingIntent snoozePendingIntent = PendingIntent.getBroadcast(
                context, (int) id + 20000, snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 🔔 Pass PendingIntent into your notification
        NotificationHelper.showNotification(
                context,
                title != null ? title : "Astra",
                message,
                (int) id,
                pendingIntent,   // 👈 important change
                snoozePendingIntent,
                dismissPendingIntent
        );
    }



    private String calculateRelativeTime(long targetTime) {
        long diffMillis = targetTime - System.currentTimeMillis();
        if (diffMillis <= 0) return "now";
        
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis);
        if (minutes < 60) {
            return "in " + minutes + " mins";
        }
        
        long hours = TimeUnit.MILLISECONDS.toHours(diffMillis);
        if (hours < 24) {
            return "in " + hours + " hours";
        }
        
        long days = TimeUnit.MILLISECONDS.toDays(diffMillis);
        return "in " + days + " days";
    }
}

