package com.astra.lifeorganizer.ui.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.astra.lifeorganizer.R;

public class SettingsRowView extends LinearLayout {

    private TextView tvTitle;
    private TextView tvSubtitle;
    private FrameLayout controlContainer;

    public SettingsRowView(@NonNull Context context) {
        this(context, null);
    }

    public SettingsRowView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SettingsRowView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        setOrientation(VERTICAL);
        int padding = (int) (12 * context.getResources().getDisplayMetrics().density);
        setPadding(0, padding, 0, padding);

        LayoutInflater.from(context).inflate(R.layout.view_settings_row_internal, this, true);

        tvTitle = findViewById(R.id.tv_row_title);
        tvSubtitle = findViewById(R.id.tv_row_subtitle);
        controlContainer = findViewById(R.id.control_container);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SettingsRowView);
            String title = a.getString(R.styleable.SettingsRowView_rowTitle);
            String subtitle = a.getString(R.styleable.SettingsRowView_rowSubtitle);
            
            if (title != null) tvTitle.setText(title);
            if (subtitle != null) tvSubtitle.setText(subtitle);
            else tvSubtitle.setVisibility(GONE);
            
            a.recycle();
        }
    }

    public void setTitle(String title) {
        tvTitle.setText(title);
    }

    public void setSubtitle(String subtitle) {
        if (subtitle == null || subtitle.isEmpty()) {
            tvSubtitle.setVisibility(GONE);
        } else {
            tvSubtitle.setVisibility(VISIBLE);
            tvSubtitle.setText(subtitle);
        }
    }

    public FrameLayout getControlContainer() {
        return controlContainer;
    }

    @Override
    public void addView(View child, int index, android.view.ViewGroup.LayoutParams params) {
        if (controlContainer == null) {
            super.addView(child, index, params);
        } else {
            controlContainer.addView(child, index, params);
        }
    }
}
