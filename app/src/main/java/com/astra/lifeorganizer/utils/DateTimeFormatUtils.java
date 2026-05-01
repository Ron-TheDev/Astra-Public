package com.astra.lifeorganizer.utils;

import android.content.Context;

import com.astra.lifeorganizer.data.repositories.SettingsRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DateTimeFormatUtils {
    private DateTimeFormatUtils() {}

    public static String formatDate(Context context, long timestamp) {
        String pattern = SettingsRepository.resolveDatePattern(context);
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(timestamp));
    }

    public static String formatTime(Context context, long timestamp) {
        String pattern = SettingsRepository.is24HourTime(context) ? "HH:mm" : "hh:mm a";
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(timestamp));
    }
}
