package com.astra.lifeorganizer.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.astra.lifeorganizer.R;

import java.util.Calendar;

public class TimelineIndicatorView extends View {

    private Paint mainPaint;
    private Paint outlinePaint;
    private float hourHeightPx;
    private float leftMarginPx;
    private float topOffsetPx;

    public TimelineIndicatorView(Context context) {
        super(context);
        init();
    }

    public TimelineIndicatorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        Context ctx = getContext();
        hourHeightPx = dpToPx(ctx, 80);
        leftMarginPx = dpToPx(ctx, 64);
        topOffsetPx = dpToPx(ctx, 40);

        outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outlinePaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, ContextCompat.getColor(ctx, android.R.color.white)));
        outlinePaint.setStrokeWidth(dpToPx(ctx, 4));
        outlinePaint.setStyle(Paint.Style.STROKE);

        mainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mainPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondary, ContextCompat.getColor(ctx, android.R.color.holo_red_dark)));
        mainPaint.setStrokeWidth(dpToPx(ctx, 2));
    }

    private float dpToPx(Context context, float dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = (int) (24 * hourHeightPx + topOffsetPx + dpToPx(getContext(), 40));
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();

        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minute = now.get(Calendar.MINUTE);
        float currentTimeY = topOffsetPx + (hour + (minute / 60f)) * hourHeightPx;

        float rightMarginPx = width - dpToPx(getContext(), 8);

        // Draw Outline (for better visibility over events)
        canvas.drawLine(leftMarginPx, currentTimeY, rightMarginPx, currentTimeY, outlinePaint);
        
        outlinePaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(leftMarginPx, currentTimeY, dpToPx(getContext(), 6), outlinePaint); // Solid outline circle

        // Draw Main Indicator
        canvas.drawLine(leftMarginPx, currentTimeY, rightMarginPx, currentTimeY, mainPaint);
        mainPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(leftMarginPx, currentTimeY, dpToPx(getContext(), 4), mainPaint); // Actual indicator circle

        postInvalidateDelayed(60000);
    }
}
