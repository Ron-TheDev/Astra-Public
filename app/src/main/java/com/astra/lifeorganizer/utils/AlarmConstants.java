package com.astra.lifeorganizer.utils;

public final class AlarmConstants {
    private AlarmConstants() {}

    public static final long COUNTDOWN_WINDOW_MS = 10 * 60 * 1000L;
    public static final long SNOOZE_WINDOW_MS = 10 * 60 * 1000L;

    public static final int PRIORITY_HIGH = 3;

    public static final String EXTRA_ITEM_ID = "extra_item_id";
    public static final String EXTRA_ITEM_TYPE = "extra_item_type";
    public static final String EXTRA_ITEM_TITLE = "extra_item_title";
    public static final String EXTRA_ITEM_NOTE = "extra_item_note";
    public static final String EXTRA_ITEM_TIME = "extra_item_time";
    public static final String EXTRA_FORCE_FULLSCREEN_PREVIEW = "extra_force_fullscreen_preview";

    public static final String ACTION_ALARM_TRIGGER = "com.astra.lifeorganizer.ACTION_ALARM_TRIGGER";
    public static final String ACTION_ALARM_COUNTDOWN = "com.astra.lifeorganizer.ACTION_ALARM_COUNTDOWN";
    public static final String ACTION_ALARM_SNOOZE = "com.astra.lifeorganizer.ACTION_ALARM_SNOOZE";
    public static final String ACTION_ALARM_DONE = "com.astra.lifeorganizer.ACTION_ALARM_DONE";
    public static final String ACTION_ALARM_DISMISS = "com.astra.lifeorganizer.ACTION_ALARM_DISMISS";
    public static final String ACTION_ALARM_OPEN = "com.astra.lifeorganizer.ACTION_ALARM_OPEN";

    public static final String EXTRA_ALARM_ACTION = "extra_alarm_action";
    public static final String EXTRA_TARGET_ITEM_ID = "extra_target_item_id";

    public static final int NOTIFICATION_ID_OFFSET = 100000;
}
