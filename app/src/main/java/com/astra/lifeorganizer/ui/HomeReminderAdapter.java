package com.astra.lifeorganizer.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.astra.lifeorganizer.R;
import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.utils.LabelUtils;

import java.util.ArrayList;
import java.util.List;

public class HomeReminderAdapter extends RecyclerView.Adapter<HomeReminderAdapter.ViewHolder> {

    public interface OnReminderClickListener {
        void onReminderClicked(Item item);
    }

    public static final class ReminderEntry {
        public final Item item;
        public final String subtitle;
        public final long reminderTime;

        public ReminderEntry(Item item, String subtitle, long reminderTime) {
            this.item = item;
            this.subtitle = subtitle;
            this.reminderTime = reminderTime;
        }
    }

    private final OnReminderClickListener listener;
    private final List<ReminderEntry> items = new ArrayList<>();

    public HomeReminderAdapter(OnReminderClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<ReminderEntry> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_reminder, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ReminderEntry entry = items.get(position);
        Item item = entry.item;
        String title = LabelUtils.displayLabel(item.title);
        holder.tvTitle.setText(title != null ? title : "Untitled");
        holder.tvSubtitle.setText(entry.subtitle);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null && item != null) {
                listener.onReminderClicked(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvSubtitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_reminder_title);
            tvSubtitle = itemView.findViewById(R.id.tv_reminder_subtitle);
        }
    }
}
