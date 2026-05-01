package com.astra.lifeorganizer.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.astra.lifeorganizer.data.repositories.SettingsRepository;
import com.google.android.material.chip.Chip;

import java.util.Locale;

public final class LabelUtils {

    private LabelUtils() {
    }

    public static String normalizeLabel(String label) {
        if (label == null) {
            return null;
        }
        String trimmed = label.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String[] words = trimmed.toLowerCase(Locale.getDefault()).split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word == null || word.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                builder.append(word.substring(1));
            }
        }
        return builder.toString();
    }

    public static String displayLabel(String label) {
        return normalizeLabel(label);
    }

    public static String labelKey(String label) {
        String normalized = normalizeLabel(label);
        return normalized == null ? "" : normalized.toLowerCase(Locale.getDefault());
    }

    public static int fallbackColor(String label) {
        int[] palette = new int[] {
                0xFF2563EB, 0xFF059669, 0xFFDB2777, 0xFFD97706,
                0xFF7C3AED, 0xFF0EA5E9, 0xFFDC2626, 0xFF16A34A
        };
        String normalized = labelKey(label);
        int index = Math.abs(normalized.hashCode()) % palette.length;
        return palette[index];
    }

    public static int resolveLabelColor(Context context, String label) {
        String normalized = normalizeLabel(label);
        if (context == null || normalized == null) {
            return fallbackColor(label);
        }
        SettingsRepository repository = SettingsRepository.getInstance(context);
        return repository.getLabelColor(normalized, fallbackColor(normalized));
    }

    public static void applyLabelStyle(TextView view, String label) {
        if (view == null) {
            return;
        }
        String normalized = normalizeLabel(label);
        if (normalized == null) {
            view.setVisibility(android.view.View.GONE);
            return;
        }

        view.setVisibility(android.view.View.VISIBLE);
        view.setText(normalized);

        int baseColor = resolveLabelColor(view.getContext(), normalized);
        if (view instanceof Chip) {
            Chip chip = (Chip) view;
            chip.setChipBackgroundColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(baseColor, 40)));
            chip.setTextColor(baseColor);
            chip.setChipStrokeColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(baseColor, 120)));
            chip.setChipStrokeWidth(2f);
        } else {
            view.setBackgroundColor(ColorUtils.setAlphaComponent(baseColor, 36));
            view.setTextColor(baseColor);
        }

        view.setEllipsize(TextUtils.TruncateAt.END);
    }
}
