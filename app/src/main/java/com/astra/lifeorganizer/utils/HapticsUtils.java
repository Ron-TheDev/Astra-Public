package com.astra.lifeorganizer.utils;

import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.View;

import com.astra.lifeorganizer.data.repositories.SettingsRepository;

public final class HapticsUtils {

    private HapticsUtils() {}

    public static void click(View view) {
        perform(view, HapticFeedbackConstants.VIRTUAL_KEY);
    }

    public static void contextClick(View view) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            perform(view, HapticFeedbackConstants.CONTEXT_CLICK);
        } else {
            perform(view, HapticFeedbackConstants.LONG_PRESS); // fallback
        }
    }

    public static void longPress(View view) {
        perform(view, HapticFeedbackConstants.LONG_PRESS);
    }

    public static void perform(View view, int feedbackConstant) {
        if (view == null) return;

        Context context = view.getContext();

        // Central single source of truth for app-level haptics
        if (!SettingsRepository.getInstance(context)
                .getBoolean(SettingsRepository.KEY_HAPTICS_ENABLED, true)) {
            return;
        }

        // To ensure consistency, we force haptic feedback enabled for our custom taps
        if (!view.isHapticFeedbackEnabled()) {
            view.setHapticFeedbackEnabled(true);
        }

        // FLAG_IGNORE_VIEW_SETTING ensures the event fires even if the view disabled it.
        // FLAG_IGNORE_GLOBAL_SETTING ensures the event fires even if the Android OS system haptics are toggled off.
        view.performHapticFeedback(feedbackConstant, 
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING | HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
    }
}
