package com.astra.lifeorganizer.receivers;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.astra.lifeorganizer.MainActivity;
import com.astra.lifeorganizer.utils.NotificationHelper;
import com.astra.lifeorganizer.utils.RecapBriefConstants;
import com.astra.lifeorganizer.utils.RecapBriefScheduler;
//
//public class RecapBriefReceiver extends BroadcastReceiver {
//    @Override
//    public void onReceive(Context context, Intent intent) {
//        if (intent == null) {
//            return;
//        }
//
//        String kind = intent.getStringExtra(RecapBriefConstants.EXTRA_BRIEF_KIND);
//        String mode = intent.getStringExtra(RecapBriefConstants.EXTRA_BRIEF_MODE);
//        long triggerAt = intent.getLongExtra("EXTRA_TRIGGER_AT", System.currentTimeMillis());
//
//        if (RecapBriefConstants.KIND_MORNING.equals(kind)) {
//            mode = RecapBriefScheduler.computeMorningMode(triggerAt);
//        } else {
//            mode = RecapBriefConstants.MODE_DAILY;
//        }
//
//        String title;
//        String message;
//        if (RecapBriefConstants.MODE_YEARLY.equals(mode)) {
//            title = "Here's your yearly recap";
//            message = "Tap to open your yearly recap.";
//        } else if (RecapBriefConstants.MODE_MONTHLY.equals(mode)) {
//            title = "Here's your monthly recap";
//            message = "Tap to open your monthly recap.";
//        } else if (RecapBriefConstants.MODE_WEEKLY.equals(mode)) {
//            title = "Here's your weekly recap";
//            message = "Tap to open your weekly recap.";
//        } else if (RecapBriefConstants.KIND_EVENING.equals(kind)) {
//            title = "It's time to unwind";
//            message = "Tap to open your daily recap.";
//        } else {
//            title = "Let's get your day started";
//            message = "Tap to open your daily brief.";
//        }
//
//        NotificationHelper.showRecapBriefNotification(context, title, message, mode, kind);
//        RecapBriefScheduler.rescheduleAll(context);
//    }
//}


public class RecapBriefReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String kind = intent.getStringExtra(RecapBriefConstants.EXTRA_BRIEF_KIND);
        String mode = intent.getStringExtra(RecapBriefConstants.EXTRA_BRIEF_MODE);
        long triggerAt = intent.getLongExtra("EXTRA_TRIGGER_AT", System.currentTimeMillis());

        // Determine mode
        if (RecapBriefConstants.KIND_MORNING.equals(kind)) {
            mode = RecapBriefScheduler.computeMorningMode(triggerAt);
        } else {
            mode = RecapBriefConstants.MODE_DAILY;
        }

        // Build notification content
        String title;
        String message;

        if (RecapBriefConstants.MODE_YEARLY.equals(mode)) {
            title = "Here's your yearly recap";
            message = "Tap to open your yearly recap.";
        } else if (RecapBriefConstants.MODE_MONTHLY.equals(mode)) {
            title = "Here's your monthly recap";
            message = "Tap to open your monthly recap.";
        } else if (RecapBriefConstants.MODE_WEEKLY.equals(mode)) {
            title = "Here's your weekly recap";
            message = "Tap to open your weekly recap.";
        } else if (RecapBriefConstants.KIND_EVENING.equals(kind)) {
            title = "Review your day";
            message = "Finish remaining tasks and plan for tomorrow.";
        } else {
            title = "Start your day";
            message = "Tap to get started on your tasks and events.";
        }

        // 🔑 Create intent to bring app to foreground (standard launch)
        Intent openIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (openIntent != null) {
            openIntent.putExtra("notification_id", (RecapBriefConstants.KIND_EVENING.equals(kind) ? 82002 : 82001));
            openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (mode + kind).hashCode(), // unique request code
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 🔔 Show notification with correct intent
        NotificationHelper.showRecapBriefNotification(
                context,
                title,
                message,
                mode,
                kind,
                pendingIntent   // 👈 new param
        );

        // Reschedule next brief
        RecapBriefScheduler.rescheduleAll(context);
    }
}