package com.astra.lifeorganizer.data.repositories;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.MutableLiveData;

import com.astra.lifeorganizer.R;
import com.astra.lifeorganizer.utils.LabelUtils;

public class SettingsRepository {

    private static SettingsRepository INSTANCE;
    private final SharedPreferences prefs;

    public static final String KEY_PAGE_HOME = "page_home";
    public static final String KEY_PAGE_TODO = "page_todo";
    public static final String KEY_PAGE_HABITS = "page_habits";
    public static final String KEY_PAGE_CALENDAR = "page_calendar";
    private static final String KEY_LABEL_COLOR_PREFIX = "label_color_";
    public static final String KEY_SHOW_WEEK_NUMBERS = "show_week_numbers";
    public static final String KEY_DYNAMIC_THEME = "dynamic_theme";
    public static final String KEY_ACCENT_COLOR = "accent_color";
    public static final String KEY_NIGHT_MODE = "night_mode";
    public static final String KEY_BLACK_THEME = "black_theme";
    public static final String KEY_DATE_FORMAT = "date_format";
    public static final String KEY_TIME_FORMAT = "time_format";
    public static final String KEY_DEFAULT_REMINDER = "default_reminder";
    public static final String KEY_DEFAULT_PRIORITY = "default_priority";
    public static final String KEY_DEFAULT_DURATION = "default_duration";
    public static final String KEY_MORNING_BRIEF_ENABLED = "morning_brief_enabled";
    public static final String KEY_MORNING_BRIEF_TIME = "morning_brief_time";
    public static final String KEY_EVENING_WRAP_ENABLED = "evening_wrap_enabled";
    public static final String KEY_EVENING_WRAP_TIME = "evening_wrap_time";
    public static final String KEY_BIOMETRIC_LOCK = "biometric_lock";
    public static final String KEY_PREVENT_SCREENSHOTS = "prevent_screenshots";
    public static final String KEY_HAPTICS_ENABLED = "haptics_enabled";
    public static final String KEY_IMPORT_BIRTHDAYS = "import_birthdays";
    public static final String KEY_IMPORT_LOCAL_CALENDAR = "import_local_calendar";
    public static final String KEY_AUTO_IMPORT_INTERVAL = "auto_import_interval";
    public static final String KEY_PURGE_ARCHIVED = "purge_archived";
    public static final String KEY_ALARM_RINGTONE = "alarm_ringtone";
    public static final String KEY_WEEK_START = "week_start";
    public static final String KEY_CALENDAR_SHOW_TASKS = "calendar_show_tasks";
    public static final String KEY_CALENDAR_SHOW_HABITS = "calendar_show_habits";
    public static final String KEY_AUTO_CONVERSION_ENABLED = "auto_conversion_enabled";
    public static final String KEY_DEFAULT_RECURRENCE = "default_recurrence";
    public static final String KEY_DEFAULT_CALENDAR_VIEW = "default_calendar_view";
    public static final String KEY_LAST_CALENDAR_VIEW = "last_calendar_view";

    public static final int WEEK_START_SUNDAY = 0;
    public static final int WEEK_START_MONDAY = 1;
    public static final int WEEK_START_SATURDAY = 2;

    public static final int THEME_STYLE_DEFAULT = R.style.Theme_AstraTodo;
    public static final int THEME_STYLE_BLUE = R.style.Theme_AstraTodo_Blue;
    public static final int THEME_STYLE_PURPLE = R.style.Theme_AstraTodo_Purple;
    public static final int THEME_STYLE_RED = R.style.Theme_AstraTodo_Red;
    public static final int THEME_STYLE_ORANGE = R.style.Theme_AstraTodo_Orange;
    public static final int THEME_STYLE_GREEN = R.style.Theme_AstraTodo_Green;
    public static final int THEME_STYLE_DEFAULT_AMOLED = R.style.Theme_AstraTodo_Amoled;
    public static final int THEME_STYLE_BLUE_AMOLED = R.style.Theme_AstraTodo_Blue_Amoled;
    public static final int THEME_STYLE_PURPLE_AMOLED = R.style.Theme_AstraTodo_Purple_Amoled;
    public static final int THEME_STYLE_RED_AMOLED = R.style.Theme_AstraTodo_Red_Amoled;
    public static final int THEME_STYLE_ORANGE_AMOLED = R.style.Theme_AstraTodo_Orange_Amoled;
    public static final int THEME_STYLE_GREEN_AMOLED = R.style.Theme_AstraTodo_Green_Amoled;

    public static final int NIGHT_MODE_SYSTEM = 0;
    public static final int NIGHT_MODE_DARK = 1;
    @Deprecated
    public static final int NIGHT_MODE_BLACK = NIGHT_MODE_DARK;
    public static final int NIGHT_MODE_LIGHT = 2;

    public final MutableLiveData<Boolean> isHomeEnabled = new MutableLiveData<>();
    public final MutableLiveData<Boolean> isTodoEnabled = new MutableLiveData<>();
    public final MutableLiveData<Boolean> isHabitsEnabled = new MutableLiveData<>();
    public final MutableLiveData<Boolean> isCalendarEnabled = new MutableLiveData<>();
    public final MutableLiveData<Boolean> isCalendarShowTasksEnabled = new MutableLiveData<>();
    public final MutableLiveData<Boolean> isCalendarShowHabitsEnabled = new MutableLiveData<>();
    public final MutableLiveData<Boolean> isDynamicThemeEnabled = new MutableLiveData<>();
    public final MutableLiveData<Boolean> isAutoConversionEnabled = new MutableLiveData<>();

    private SettingsRepository(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("astra_datastore_prefs", Context.MODE_PRIVATE);
        refreshLiveData();
    }

    public static SettingsRepository getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = new SettingsRepository(context);
        }
        return INSTANCE;
    }

    public static int resolveThemeStyle(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences("astra_datastore_prefs", Context.MODE_PRIVATE);
        boolean dynamic = prefs.getBoolean(KEY_DYNAMIC_THEME, true);
        int nightMode = prefs.getInt(KEY_NIGHT_MODE, NIGHT_MODE_SYSTEM);
        boolean blackTheme = prefs.getBoolean(KEY_BLACK_THEME, nightMode == NIGHT_MODE_DARK);
        if (nightMode == NIGHT_MODE_LIGHT) {
            blackTheme = false;
        }

        if (dynamic) {
            return blackTheme ? THEME_STYLE_DEFAULT_AMOLED : THEME_STYLE_DEFAULT;
        }

        String accent = prefs.getString(KEY_ACCENT_COLOR, "blue");
        if ("purple".equalsIgnoreCase(accent)) {
            return blackTheme ? THEME_STYLE_PURPLE_AMOLED : THEME_STYLE_PURPLE;
        }
        if ("red".equalsIgnoreCase(accent)) {
            return blackTheme ? THEME_STYLE_RED_AMOLED : THEME_STYLE_RED;
        }
        if ("orange".equalsIgnoreCase(accent)) {
            return blackTheme ? THEME_STYLE_ORANGE_AMOLED : THEME_STYLE_ORANGE;
        }
        if ("green".equalsIgnoreCase(accent)) {
            return blackTheme ? THEME_STYLE_GREEN_AMOLED : THEME_STYLE_GREEN;
        }
        return blackTheme ? THEME_STYLE_BLUE_AMOLED : THEME_STYLE_BLUE;
    }

    public static int resolveNightMode(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences("astra_datastore_prefs", Context.MODE_PRIVATE);
        switch (prefs.getInt(KEY_NIGHT_MODE, NIGHT_MODE_SYSTEM)) {
            case NIGHT_MODE_DARK:
                return androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;
            case NIGHT_MODE_LIGHT:
                return androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
            case NIGHT_MODE_SYSTEM:
            default:
                return androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
    }

    public static String resolveDatePattern(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences("astra_datastore_prefs", Context.MODE_PRIVATE);
        switch (prefs.getString(KEY_DATE_FORMAT, "medium")) {
            case "short":
                return "MM/dd/yy";
            case "iso":
                return "yyyy-MM-dd";
            case "long":
                return "EEEE, MMMM d, yyyy";
            case "medium":
            default:
                return "MMM d, yyyy";
        }
    }

    public static boolean is24HourTime(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences("astra_datastore_prefs", Context.MODE_PRIVATE);
        return "24h".equalsIgnoreCase(prefs.getString(KEY_TIME_FORMAT, "12h"));
    }

    public void refreshLiveData() {
        isHomeEnabled.postValue(prefs.getBoolean(KEY_PAGE_HOME, true));
        isTodoEnabled.postValue(prefs.getBoolean(KEY_PAGE_TODO, true));
        isHabitsEnabled.postValue(prefs.getBoolean(KEY_PAGE_HABITS, true));
        isCalendarEnabled.postValue(prefs.getBoolean(KEY_PAGE_CALENDAR, true));
        isCalendarShowTasksEnabled.postValue(prefs.getBoolean(KEY_CALENDAR_SHOW_TASKS, false));
        isCalendarShowHabitsEnabled.postValue(prefs.getBoolean(KEY_CALENDAR_SHOW_HABITS, false));
        isDynamicThemeEnabled.postValue(prefs.getBoolean(KEY_DYNAMIC_THEME, true));
        isAutoConversionEnabled.postValue(prefs.getBoolean(KEY_AUTO_CONVERSION_ENABLED, false));
    }

    public static int resolveWeekStartDay(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences("astra_datastore_prefs", Context.MODE_PRIVATE);
        switch (prefs.getInt(KEY_WEEK_START, WEEK_START_SUNDAY)) {
            case WEEK_START_MONDAY:
                return java.util.Calendar.MONDAY;
            case WEEK_START_SATURDAY:
                return java.util.Calendar.SATURDAY;
            case WEEK_START_SUNDAY:
            default:
                return java.util.Calendar.SUNDAY;
        }
    }

    public boolean setPageEnabled(String key, boolean enabled, MutableLiveData<Boolean> liveData) {
        if (enabled) {
            prefs.edit().putBoolean(key, true).apply();
            liveData.setValue(true);
            return true;
        }

        // CR-004: Limit disabled tabs (only one of Todo List, Habit Tracker, or Calendar can be disabled at a time)
        if (KEY_PAGE_TODO.equals(key) || KEY_PAGE_HABITS.equals(key) || KEY_PAGE_CALENDAR.equals(key)) {
            int disabledCoreCount = 0;
            if (!prefs.getBoolean(KEY_PAGE_TODO, true)) disabledCoreCount++;
            if (!prefs.getBoolean(KEY_PAGE_HABITS, true)) disabledCoreCount++;
            if (!prefs.getBoolean(KEY_PAGE_CALENDAR, true)) disabledCoreCount++;
            
            if (disabledCoreCount >= 1) {
                return false;
            }
        }

        if (countFunctionalPagesEnabled(key) <= 1) {
            return false;
        }

        prefs.edit().putBoolean(key, false).apply();
        liveData.setValue(false);
        return true;
    }

    public void setBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
        if (KEY_AUTO_CONVERSION_ENABLED.equals(key)) {
            isAutoConversionEnabled.postValue(value);
        } else if (KEY_CALENDAR_SHOW_TASKS.equals(key)) {
            isCalendarShowTasksEnabled.postValue(value);
        } else if (KEY_CALENDAR_SHOW_HABITS.equals(key)) {
            isCalendarShowHabitsEnabled.postValue(value);
        } else if (KEY_DYNAMIC_THEME.equals(key)) {
            isDynamicThemeEnabled.postValue(value);
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }

    public void setString(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    public String getString(String key, String defaultValue) {
        return prefs.getString(key, defaultValue);
    }

    public void setInt(String key, int value) {
        prefs.edit().putInt(key, value).apply();
    }

    public int getInt(String key, int defaultValue) {
        return prefs.getInt(key, defaultValue);
    }

    public void setLong(String key, long value) {
        prefs.edit().putLong(key, value).apply();
    }

    public void setLabelColor(String label, int color) {
        String normalized = LabelUtils.normalizeLabel(label);
        if (normalized == null) {
            return;
        }
        prefs.edit().putInt(labelColorKey(normalized), color).apply();
    }

    public int getLabelColor(String label, int defaultColor) {
        String normalized = LabelUtils.normalizeLabel(label);
        if (normalized == null) {
            return defaultColor;
        }
        return prefs.getInt(labelColorKey(normalized), defaultColor);
    }

    public void renameLabelColor(String oldLabel, String newLabel) {
        String normalizedOld = LabelUtils.normalizeLabel(oldLabel);
        String normalizedNew = LabelUtils.normalizeLabel(newLabel);
        if (normalizedOld == null || normalizedNew == null) {
            return;
        }
        String oldKey = labelColorKey(normalizedOld);
        String newKey = labelColorKey(normalizedNew);
        if (oldKey.equals(newKey)) {
            return;
        }
        synchronized (this) {
            if (!prefs.contains(oldKey)) {
                return;
            }
            int color = prefs.getInt(oldKey, 0);
            prefs.edit().remove(oldKey).putInt(newKey, color).apply();
        }
    }

    public void mergeLabelColor(String fromLabel, String toLabel) {
        String normalizedFrom = LabelUtils.normalizeLabel(fromLabel);
        String normalizedTo = LabelUtils.normalizeLabel(toLabel);
        if (normalizedFrom == null || normalizedTo == null) {
            return;
        }
        String fromKey = labelColorKey(normalizedFrom);
        String toKey = labelColorKey(normalizedTo);
        if (fromKey.equals(toKey)) {
            return;
        }
        synchronized (this) {
            SharedPreferences.Editor editor = prefs.edit();
            if (!prefs.contains(toKey) && prefs.contains(fromKey)) {
                editor.putInt(toKey, prefs.getInt(fromKey, 0));
            }
            editor.remove(fromKey).apply();
        }
    }

    public void deleteLabelColor(String label) {
        String normalized = LabelUtils.normalizeLabel(label);
        if (normalized == null) {
            return;
        }
        prefs.edit().remove(labelColorKey(normalized)).apply();
    }

    public long getLong(String key, long defaultValue) {
        return prefs.getLong(key, defaultValue);
    }

    public void clearAll() {
        prefs.edit().clear().apply();
        refreshLiveData();
    }

    public int countFunctionalPagesEnabled(String keyToIgnore) {
        int count = 0;
        if (!KEY_PAGE_HOME.equals(keyToIgnore) && prefs.getBoolean(KEY_PAGE_HOME, true)) count++;
        if (!KEY_PAGE_TODO.equals(keyToIgnore) && prefs.getBoolean(KEY_PAGE_TODO, true)) count++;
        if (!KEY_PAGE_HABITS.equals(keyToIgnore) && prefs.getBoolean(KEY_PAGE_HABITS, true)) count++;
        if (!KEY_PAGE_CALENDAR.equals(keyToIgnore) && prefs.getBoolean(KEY_PAGE_CALENDAR, true)) count++;
        return count;
    }

    public int countFunctionalPagesEnabled() {
        return countFunctionalPagesEnabled(null);
    }

    public String getVersionName() {
        return "1.0";
    }

    private String labelColorKey(String label) {
        return KEY_LABEL_COLOR_PREFIX + LabelUtils.labelKey(label);
    }
}
