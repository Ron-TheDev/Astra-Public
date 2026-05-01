package com.astra.lifeorganizer.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Draws the hourly timeline grid background.
 * Automatically detects and annotates DST transition hours for all IANA timezones.
 */
public class TimelineBackgroundView extends View {

    private Paint linePaint;
    private Paint textPaint;
    private Paint bgPaint;
    private Paint dstBandPaint;
    private Paint dstLabelPaint;

    private float hourHeightPx;
    private float leftMarginPx;
    private float topOffsetPx;
    private boolean is24Hour;

    /**
     * DST state for the currently displayed day.
     *  dstAffectedHour  — the grid hour index (0-23) where the transition occurs.
     *  dstLabel         — human-readable label, empty string if no transition today.
     *  dstBandRows      — number of grid rows the tinted band should span.
     */
    private int    dstAffectedHour = -1;
    private String dstLabel        = "";
    private int    dstBandRows     = 0; // negative = skipped rows, positive = extra rows

    public TimelineBackgroundView(Context context) {
        super(context);
        init();
    }

    public TimelineBackgroundView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        Context ctx = getContext();

        hourHeightPx = dpToPx(ctx, 80);
        leftMarginPx = dpToPx(ctx, 64);
        topOffsetPx  = dpToPx(ctx, 40);
        is24Hour = com.astra.lifeorganizer.data.repositories.SettingsRepository.is24HourTime(ctx);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOutlineVariant,
                ContextCompat.getColor(ctx, android.R.color.darker_gray)));
        linePaint.setStrokeWidth(dpToPx(ctx, 1));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                ContextCompat.getColor(ctx, android.R.color.darker_gray)));
        textPaint.setTextSize(dpToPx(ctx, 12));
        textPaint.setTextAlign(Paint.Align.RIGHT);

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int surfaceColor = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurfaceVariant,
                ContextCompat.getColor(ctx, android.R.color.darker_gray));
        bgPaint.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(surfaceColor, 80));
        bgPaint.setStyle(Paint.Style.FILL);

        // Amber DST band
        dstBandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dstBandPaint.setColor(0x33FFA500);
        dstBandPaint.setStyle(Paint.Style.FILL);

        dstLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dstLabelPaint.setColor(0xDDDD8800);
        dstLabelPaint.setTextSize(dpToPx(ctx, 10));
        dstLabelPaint.setTextAlign(Paint.Align.LEFT);
        dstLabelPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    }

    /**
     * Must be called whenever the displayed date changes.
     * Uses the device's IANA timezone (TimeZone.getDefault()) so all countries with DST
     * — including half-hour zones like Lord Howe Island — are handled automatically.
     */
    public void setDisplayedDate(long dayStartMillis) {
        dstAffectedHour = -1;
        dstLabel        = "";
        dstBandRows     = 0;

        TimeZone tz = TimeZone.getDefault();

        // Skip timezones that never observe DST (saves pointless iteration)
        if (!tz.useDaylightTime() && tz.getDSTSavings() == 0) {
            invalidate();
            return;
        }

        // Snap to local midnight (Calendar respects the TZ so this is always correct,
        // even when midnight itself is during a DST transition).
        Calendar cal = Calendar.getInstance(tz);
        cal.setTimeInMillis(dayStartMillis);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE,      0);
        cal.set(Calendar.SECOND,      0);
        cal.set(Calendar.MILLISECOND, 0);
        final long midnightMs = cal.getTimeInMillis();

        int prevOffset = tz.getOffset(midnightMs);

        // Walk through up to 25 real-time hours (covers 25-hour fall-back days).
        for (int h = 1; h <= 25; h++) {
            long checkMs   = midnightMs + (long) h * 3600_000L;
            int  currOffset = tz.getOffset(checkMs);
            int  deltaMs   = currOffset - prevOffset; // positive = spring-forward, negative = fall-back

            if (deltaMs == 0) {
                prevOffset = currOffset;
                continue;
            }

            // Only annotate hours that fall within the visible 24-hour grid
            if (h <= 24) {
                int savingsMins = Math.abs(deltaMs) / 60_000;

                if (deltaMs > 0) {
                    // SPRING FORWARD — offset increased (e.g. UTC-5 → UTC-4)
                    // The clock jumps forward so hour h is SKIPPED in local time.
                    dstAffectedHour = h;
                    dstBandRows     = -(savingsMins / 60); // negative = slots removed from grid
                    dstLabel = savingsMins == 60
                            ? "⏩ Clocks spring forward — 1 hour skipped"
                            : String.format(java.util.Locale.getDefault(),
                                    "⏩ Clocks spring forward — %d min skipped", savingsMins);
                } else {
                    // FALL BACK — offset decreased (e.g. UTC-4 → UTC-5)
                    // The clock repeats, so hour h-1 appears TWICE in local time.
                    dstAffectedHour = h - 1;
                    dstBandRows     = (savingsMins / 60);  // positive = extra slots added
                    dstLabel = savingsMins == 60
                            ? "⏪ Clocks fall back — 1 hour repeated"
                            : String.format(java.util.Locale.getDefault(),
                                    "⏪ Clocks fall back — %d min repeated", savingsMins);
                }
            }
            break; // only one DST transition can happen per day
        }

        invalidate();
    }

    private float dpToPx(Context context, float dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width  = MeasureSpec.getSize(widthMeasureSpec);
        int height = (int) (24 * hourHeightPx + topOffsetPx + dpToPx(getContext(), 40));
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int   width       = getWidth();
        float rightMargin = width - dpToPx(getContext(), 8);
        float bottomPx    = topOffsetPx + (24 * hourHeightPx);

        // Opaque card background
        canvas.drawRoundRect(leftMarginPx, topOffsetPx, rightMargin, bottomPx,
                dpToPx(getContext(), 8), dpToPx(getContext(), 8), bgPaint);

        // DST highlight band
        if (dstAffectedHour >= 0 && dstAffectedHour < 24 && !dstLabel.isEmpty()) {
            float bandTop    = topOffsetPx + dstAffectedHour * hourHeightPx;
            float bandHeight = Math.max(hourHeightPx * 0.5f, hourHeightPx);
            float bandBottom = bandTop + bandHeight;
            canvas.drawRect(leftMarginPx, bandTop, rightMargin, bandBottom, dstBandPaint);
            // Label centred vertically in the band
            float labelY = bandTop + bandHeight / 2f + dstLabelPaint.getTextSize() / 2f;
            canvas.drawText(dstLabel, leftMarginPx + dpToPx(getContext(), 6), labelY, dstLabelPaint);
        }

        // Hour lines and time labels
        for (int i = 0; i < 24; i++) {
            float y = topOffsetPx + (i * hourHeightPx);

            // Dim the line & label for the DST-affected hour
            boolean isDst = (i == dstAffectedHour);
            linePaint.setAlpha(isDst ? 80 : 255);
            textPaint.setAlpha(isDst ? 80 : 255);

            canvas.drawLine(leftMarginPx, y, rightMargin, y, linePaint);

            String label;
            if (is24Hour) {
                label = String.format(java.util.Locale.getDefault(), "%02d:00", i);
            } else {
                if (i == 0)       label = "12 AM";
                else if (i == 12) label = "12 PM";
                else if (i < 12)  label = i + " AM";
                else               label = (i - 12) + " PM";
            }

            float textY = y + textPaint.getTextSize() / 2.5f;
            canvas.drawText(label, leftMarginPx - dpToPx(getContext(), 12), textY, textPaint);
        }

        // Restore alpha
        linePaint.setAlpha(255);
        textPaint.setAlpha(255);
    }
}
