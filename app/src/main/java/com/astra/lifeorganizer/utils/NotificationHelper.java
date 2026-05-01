package com.astra.lifeorganizer.utils;

import android.app.NotificationChannel;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.astra.lifeorganizer.R;
import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.receivers.AlarmActionReceiver;
import com.astra.lifeorganizer.receivers.RecapBriefActionReceiver;
import com.astra.lifeorganizer.ui.AlarmFullScreenActivity;
import com.astra.lifeorganizer.utils.DateTimeFormatUtils;

import java.util.concurrent.TimeUnit;

public class NotificationHelper {
    public static final String CHANNEL_ID = "REMINDERS_CHANNEL";
    public static final String ALARM_CHANNEL_ID = "URGENT_ALARMS_CHANNEL_V3";
    public static final String COUNTDOWN_CHANNEL_ID = "ALARM_COUNTDOWN_CHANNEL";

    private static final String CHANNEL_NAME = "Life Organizer Reminders";
    private static final String ALARM_CHANNEL_NAME = "Life Organizer Alarms";
    private static final String COUNTDOWN_CHANNEL_NAME = "Alarm Countdowns";

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        NotificationChannel reminderChannel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
        );
        reminderChannel.setDescription("Notifications for tasks and habits");
        manager.createNotificationChannel(reminderChannel);

        NotificationChannel countdownChannel = new NotificationChannel(
                COUNTDOWN_CHANNEL_ID,
                COUNTDOWN_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
        );
        countdownChannel.setDescription("Pre-alarm countdown notifications");
        manager.createNotificationChannel(countdownChannel);

        NotificationChannel alarmChannel = new NotificationChannel(
                ALARM_CHANNEL_ID,
                ALARM_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_MAX
        );
        alarmChannel.setDescription("Full-screen alarms for urgent items");
        alarmChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        alarmChannel.setBypassDnd(true);
        alarmChannel.setSound(null, null);
        alarmChannel.enableVibration(false);
        alarmChannel.enableLights(false);
        manager.createNotificationChannel(alarmChannel);
    }

    public static void showNotification(Context context, String title, String message, int id) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(id, builder.build());
        }
    }

    public static void showNotification(
            Context context,
            String title,
            String message,
            int id,
            PendingIntent pendingIntent,   // 👈 added
            PendingIntent snoozeIntent,
            PendingIntent dismissIntent
    ) {
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent); // 👈 critical line

        if (snoozeIntent != null) {
            builder.addAction(new NotificationCompat.Action(0, "Snooze 10 min", snoozeIntent));
        }
        if (dismissIntent != null) {
            builder.addAction(new NotificationCompat.Action(0, "Dismiss", dismissIntent));
        }

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager != null) {
            manager.notify(id, builder.build());
        }
    }

    public static void showCountdownNotification(Context context, Item item, long alarmTime, boolean immediate) {
        int notificationId = AlarmScheduler.getCountdownNotificationId(item.id);
        String typeLabel = "event".equalsIgnoreCase(item.type) ? "Event" : "Task";
        String title = item.title != null ? item.title : "Alarm";
        String body = immediate ? "Snoozed for 10 minutes" : "Alarm coming soon";
        long remainingMs = Math.max(0L, alarmTime - System.currentTimeMillis());
        String countdownText = remainingMs > 0 ? "Alarm in " + formatCountdown(remainingMs) : "Alarm now";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, COUNTDOWN_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setSubText(typeLabel + " - " + countdownText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setWhen(Math.max(alarmTime, System.currentTimeMillis()))
                .setShowWhen(true)
                .setUsesChronometer(remainingMs > 0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && remainingMs > 0) {
            builder.setChronometerCountDown(true);
        }

        if (item.description != null && !item.description.trim().isEmpty()) {
            builder.setStyle(new NotificationCompat.BigTextStyle()
                    .bigText("Alarm in progress\n\n" + item.description.trim()));
        }

        addActionButtons(context, builder, item);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(notificationId, builder.build());
        }
    }

    public static void showFullScreenAlarm(Context context, Item item) {
        int notificationId = AlarmScheduler.getAlarmNotificationId(item.id);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(notificationId, buildFullScreenAlarmNotification(context, item).build());
        }
    }

    public static NotificationCompat.Builder buildPlaceholderAlarmNotification(Context context, long itemId) {
        Intent fullScreenIntent = new Intent(context, AlarmFullScreenActivity.class);
        fullScreenIntent.putExtra(AlarmConstants.EXTRA_ITEM_ID, itemId);
        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                (int) (itemId % Integer.MAX_VALUE),
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Alarm")
                .setContentText("Incoming alarm...")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setSilent(true)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent);
    }

    public static NotificationCompat.Builder buildFullScreenAlarmNotification(Context context, Item item) {
        String typeLabel = "event".equalsIgnoreCase(item.type) ? "Event" : "Task";
        long when = AlarmScheduler.getAlarmTime(item);
        String timeText = DateTimeFormatUtils.formatTime(context, when);

        Intent fullScreenIntent = new Intent(context, AlarmFullScreenActivity.class);
        fullScreenIntent.putExtra(AlarmConstants.EXTRA_ITEM_ID, item.id);
        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                AlarmScheduler.getAlarmNotificationId(item.id),
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(item.title != null ? item.title : "Alarm")
                .setContentText(typeLabel + " - " + timeText)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setSilent(true)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent);

        if (item.description != null && !item.description.trim().isEmpty()) {
            builder.setStyle(new NotificationCompat.BigTextStyle()
                    .bigText(item.description.trim()));
        }

        addActionButtons(context, builder, item);
        return builder;
    }

    public static void showQueuedAlarmNotification(Context context, Item item) {
        int notificationId = AlarmScheduler.getAlarmNotificationId(item.id);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(item.title != null ? item.title : "Alarm queued")
                .setContentText("Queued alarm")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setSilent(true);

        addActionButtons(context, builder, item);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(notificationId, builder.build());
        }
    }

    public static void showRecapBriefNotification(Context context, String title, String message, String mode, String kind) {
        int notificationId = (RecapBriefConstants.KIND_EVENING.equals(kind) ? 82002 : 82001);
        Intent openIntent = new Intent(context, RecapBriefActionReceiver.class);
        openIntent.putExtra(RecapBriefConstants.EXTRA_BRIEF_MODE, mode);
        openIntent.putExtra(RecapBriefConstants.EXTRA_BRIEF_KIND, kind);

        PendingIntent openPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(openPendingIntent)
                .setDeleteIntent(openPendingIntent)
                .setWhen(System.currentTimeMillis());

        builder.addAction(new NotificationCompat.Action(0, "Open App", openPendingIntent));

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(notificationId, builder.build());
        }
    }

    public static void showRecapBriefNotification(
            Context context,
            String title,
            String message,
            String mode,
            String kind,
            PendingIntent pendingIntent   // 👈 accept this
    ) {
        int notificationId =
                (RecapBriefConstants.KIND_EVENING.equals(kind) ? 82002 : 82001);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)   // 👈 use it
                        .setDeleteIntent(pendingIntent)
                        .setWhen(System.currentTimeMillis());

        // Action button
        builder.addAction(
                new NotificationCompat.Action(0, "Open App", pendingIntent)
        );

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager != null) {
            manager.notify(notificationId, builder.build());
        }
    }

    private static void addActionButtons(Context context, NotificationCompat.Builder builder, Item item) {
        PendingIntent snoozeIntent = buildActionPendingIntent(context, AlarmConstants.ACTION_ALARM_SNOOZE, item.id);
        PendingIntent doneIntent = buildActionPendingIntent(context,
                "event".equalsIgnoreCase(item.type) ? AlarmConstants.ACTION_ALARM_DISMISS : AlarmConstants.ACTION_ALARM_DONE,
                item.id);

        builder.addAction(new NotificationCompat.Action(0, "Snooze 10 min", snoozeIntent));
        builder.addAction(new NotificationCompat.Action(0, "event".equalsIgnoreCase(item.type) ? "Dismiss" : "Mark Done", doneIntent));
        builder.addAction(new NotificationCompat.Action(0, "Open", buildActionPendingIntent(context, AlarmConstants.ACTION_ALARM_OPEN, item.id, item.type)));
    }

    private static PendingIntent buildActionPendingIntent(Context context, String action, long itemId) {
        return buildActionPendingIntent(context, action, itemId, null);
    }

    private static PendingIntent buildActionPendingIntent(Context context, String action, long itemId, String itemType) {
        Intent intent = new Intent(context, AlarmActionReceiver.class);
        intent.setAction(action);
        intent.putExtra(AlarmConstants.EXTRA_ITEM_ID, itemId);
        if (itemType != null) {
            intent.putExtra(AlarmConstants.EXTRA_ITEM_TYPE, itemType);
        }
        return PendingIntent.getBroadcast(
                context,
                (AlarmScheduler.getAlarmNotificationId(itemId) * 31) + action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static String formatCountdown(long remainingMs) {
        long totalSeconds = Math.max(0L, TimeUnit.MILLISECONDS.toSeconds(remainingMs));
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public static void cancelNotification(Context context, int id) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(id);
        }
    }

    public static void cancelAll(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancelAll();
        }
    }
}
