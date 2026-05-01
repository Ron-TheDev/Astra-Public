package com.astra.lifeorganizer.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.astra.lifeorganizer.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

public class WidgetPickerFragment extends Fragment {

    private LinearLayout root;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_widget_picker, container, false);
        root = view.findViewById(R.id.widget_picker_root);

        addHeader();
        addWidgetPreviewCard(
                getString(R.string.widget_today_summary_title),
                getString(R.string.widget_today_summary_desc),
                R.layout.widget_summary,
                this::bindSummaryPreview
        );
        addWidgetPreviewCard(
                getString(R.string.widget_calendar_title),
                getString(R.string.widget_calendar_desc),
                R.layout.widget_list_preview,
                preview -> bindListPreview(preview, "Calendar", "Next 7 days at a glance",
                        new String[]{"Team stand-up", "Design review", "Doctor appointment"},
                        new String[]{"Today, 9:00 AM", "Today, 1:30 PM", "Tomorrow, 4:00 PM"})
        );
        addWidgetPreviewCard(
                getString(R.string.widget_todo_list_title),
                getString(R.string.widget_todo_list_desc),
                R.layout.widget_list_preview,
                preview -> bindListPreview(preview, "Todo List", "Open tasks and due items",
                        new String[]{"Finish report", "Pay utilities", "Update habit log"},
                        new String[]{"Due today", "Due tomorrow", "High priority"})
        );
        addWidgetPreviewCard(
                getString(R.string.widget_upcoming_title),
                getString(R.string.widget_upcoming_desc),
                R.layout.widget_list_preview,
                preview -> bindListPreview(preview, "Upcoming", "Next scheduled events",
                        new String[]{"Lunch with Maya", "Gym session", "Planning sprint"},
                        new String[]{"In 2 hours", "Tonight", "Friday"})
        );
        return view;
    }

    private void addHeader() {
        TextView title = new TextView(requireContext());
        title.setText(getString(R.string.widget_picker_title));
        title.setTextSize(28f);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurface));
        title.setPadding(4, 4, 4, 8);
        root.addView(title);

        TextView subtitle = new TextView(requireContext());
        subtitle.setText(getString(R.string.widget_picker_subtitle));
        subtitle.setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        subtitle.setPadding(4, 0, 4, 4);
        root.addView(subtitle);

        TextView hint = new TextView(requireContext());
        hint.setText(getString(R.string.widget_picker_hint));
        hint.setTextColor(resolveThemeColor(androidx.appcompat.R.attr.colorPrimary));
        hint.setPadding(4, 0, 4, dp(16));
        root.addView(hint);
    }

    private void addWidgetPreviewCard(String title, String subtitle, int previewLayoutRes, PreviewBinder binder) {
        MaterialCardView card = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(16));
        card.setLayoutParams(params);
        card.setRadius(dp(18));
        card.setCardElevation(dp(1));
        card.setUseCompatPadding(true);
        card.setStrokeWidth(dp(1));
        card.setCardBackgroundColor(MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurfaceContainerLow, MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF)));
        card.setStrokeColor(MaterialColors.getColor(card, com.google.android.material.R.attr.colorOutlineVariant, MaterialColors.getColor(card, com.google.android.material.R.attr.colorOutline, 0x22000000)));

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView cardTitle = new TextView(requireContext());
        cardTitle.setText(title);
        cardTitle.setTextSize(18f);
        cardTitle.setTypeface(cardTitle.getTypeface(), android.graphics.Typeface.BOLD);
        cardTitle.setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurface));
        content.addView(cardTitle);

        TextView cardSubtitle = new TextView(requireContext());
        cardSubtitle.setText(subtitle);
        cardSubtitle.setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        cardSubtitle.setPadding(0, dp(2), 0, dp(12));
        content.addView(cardSubtitle);

        View preview = LayoutInflater.from(requireContext()).inflate(previewLayoutRes, content, false);
        if (binder != null) {
            binder.bind(preview);
        }
        preview.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(preview);

        MaterialButton previewButton = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        previewButton.setText("Preview");
        previewButton.setOnClickListener(v -> Toast.makeText(requireContext(), "Previewing " + title, Toast.LENGTH_SHORT).show());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = dp(12);
        previewButton.setLayoutParams(buttonParams);
        content.addView(previewButton);

        card.addView(content);
        root.addView(card);
    }

    private void bindSummaryPreview(View preview) {
        setText(preview, R.id.tv_widget_title, getString(R.string.widget_today_summary_title));
        setText(preview, R.id.tv_widget_date, "Today");
        setText(preview, R.id.tv_widget_completed_value, "8");
        setText(preview, R.id.tv_widget_due_value, "3");
        setText(preview, R.id.tv_widget_events_value, "2");
        setText(preview, R.id.tv_widget_next_event, "Next event");
        setText(preview, R.id.tv_widget_next_event_subtitle, "Design review");
        setText(preview, R.id.tv_widget_next_event_time, "Today, 1:30 PM");
    }

    private void bindListPreview(View preview, String title, String subtitle, String[] rowTitles, String[] rowSubtitles) {
        setText(preview, R.id.tv_widget_title, title);
        setText(preview, R.id.tv_widget_subtitle, subtitle);
        LinearLayout container = preview.findViewById(R.id.preview_list_container);
        if (container == null) {
            return;
        }
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < rowTitles.length; i++) {
            View row = inflater.inflate(R.layout.widget_list_item, container, false);
            setText(row, R.id.tv_item_title, rowTitles[i]);
            setText(row, R.id.tv_item_subtitle, rowSubtitles[i]);
            container.addView(row);
        }
    }

    private void setText(View root, int id, CharSequence text) {
        View view = root.findViewById(id);
        if (view instanceof TextView) {
            ((TextView) view).setText(text);
        }
    }

    private int resolveThemeColor(int attr) {
        return MaterialColors.getColor(requireContext(), attr, 0xFF111111);
    }

    private int dp(int value) {
        return (int) (value * requireContext().getResources().getDisplayMetrics().density);
    }

    private interface PreviewBinder {
        void bind(View previewRoot);
    }
}
