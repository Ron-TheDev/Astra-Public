package com.astra.lifeorganizer.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.astra.lifeorganizer.data.repositories.SettingsRepository;
import com.astra.lifeorganizer.receivers.RecapBriefReceiver;

import java.util.Calendar;

public final class RecapBriefScheduler {
    private static final int REQUEST_MORNING = 81001;
    private static final int REQUEST_EVENING = 81002;

    private RecapBriefScheduler() {}

    public static void rescheduleAll(Context context) {
        scheduleMorning(context);
        scheduleEvening(context);
    }

    public static void scheduleMorning(Context context) {
        SettingsRepository settings = SettingsRepository.getInstance(context);
        if (!settings.getBoolean(SettingsRepository.KEY_MORNING_BRIEF_ENABLED, false)) {
            cancel(context, RecapBriefConstants.KIND_MORNING);
            return;
        }
        long triggerAt = nextTriggerAt(settings.getLong(SettingsRepository.KEY_MORNING_BRIEF_TIME, defaultTime(8, 0)));
        if (triggerAt <= 0L) {
            cancel(context, RecapBriefConstants.KIND_MORNING);
            return;
        }
        schedule(context, triggerAt, RecapBriefConstants.KIND_MORNING, computeMorningMode(triggerAt), REQUEST_MORNING);
    }

    public static void scheduleEvening(Context context) {
        SettingsRepository settings = SettingsRepository.getInstance(context);
        if (!settings.getBoolean(SettingsRepository.KEY_EVENING_WRAP_ENABLED, false)) {
            cancel(context, RecapBriefConstants.KIND_EVENING);
            return;
        }
        long triggerAt = nextTriggerAt(settings.getLong(SettingsRepository.KEY_EVENING_WRAP_TIME, defaultTime(18, 0)));
        if (triggerAt <= 0L) {
            cancel(context, RecapBriefConstants.KIND_EVENING);
            return;
        }
        schedule(context, triggerAt, RecapBriefConstants.KIND_EVENING, RecapBriefConstants.MODE_DAILY, REQUEST_EVENING);
    }

    public static void cancel(Context context, String kind) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        PendingIntent pendingIntent = buildPendingIntent(context, kind, null, kind.equals(RecapBriefConstants.KIND_MORNING) ? REQUEST_MORNING : REQUEST_EVENING, true);
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
        }
    }

    public static long nextTriggerAt(long timeOfDayMillis) {
        Calendar target = Calendar.getInstance();
        Calendar source = Calendar.getInstance();
        source.setTimeInMillis(timeOfDayMillis);
        target.set(Calendar.HOUR_OF_DAY, source.get(Calendar.HOUR_OF_DAY));
        target.set(Calendar.MINUTE, source.get(Calendar.MINUTE));
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        if (target.getTimeInMillis() <= System.currentTimeMillis()) {
            target.add(Calendar.DAY_OF_YEAR, 1);
        }
        return target.getTimeInMillis();
    }

    public static String computeMorningMode(long triggerAt) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(triggerAt);
        int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
        int lastDayOfMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        int month = calendar.get(Calendar.MONTH);
        int year = calendar.get(Calendar.YEAR);

        if (month == Calendar.DECEMBER && dayOfMonth == 31) {
            return RecapBriefConstants.MODE_YEARLY;
        }
        if (dayOfMonth == lastDayOfMonth) {
            return RecapBriefConstants.MODE_MONTHLY;
        }
        if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            return RecapBriefConstants.MODE_WEEKLY;
        }
        return RecapBriefConstants.MODE_DAILY;
    }

    private static void schedule(Context context, long triggerAt, String kind, String mode, int requestCode) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        Intent intent = new Intent(context, RecapBriefReceiver.class);
        intent.setAction(RecapBriefConstants.KIND_MORNING.equals(kind)
                ? RecapBriefConstants.ACTION_MORNING_BRIEF
                : RecapBriefConstants.ACTION_EVENING_WRAP);
        intent.putExtra(RecapBriefConstants.EXTRA_BRIEF_KIND, kind);
        intent.putExtra(RecapBriefConstants.EXTRA_BRIEF_MODE, mode);
        intent.putExtra("EXTRA_TRIGGER_AT", triggerAt);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (pendingIntent == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }

    private static PendingIntent buildPendingIntent(Context context, String kind, String mode, int requestCode, boolean noCreate) {
        Intent intent = new Intent(context, RecapBriefReceiver.class);
        intent.setAction(RecapBriefConstants.KIND_MORNING.equals(kind)
                ? RecapBriefConstants.ACTION_MORNING_BRIEF
                : RecapBriefConstants.ACTION_EVENING_WRAP);
        if (mode != null) {
            intent.putExtra(RecapBriefConstants.EXTRA_BRIEF_MODE, mode);
        }
        int flags = PendingIntent.FLAG_IMMUTABLE | (noCreate ? PendingIntent.FLAG_NO_CREATE : PendingIntent.FLAG_UPDATE_CURRENT);
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }

    private static long defaultTime(int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
