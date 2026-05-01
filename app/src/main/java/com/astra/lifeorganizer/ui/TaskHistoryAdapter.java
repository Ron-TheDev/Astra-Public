package com.astra.lifeorganizer.ui;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.astra.lifeorganizer.R;
import com.astra.lifeorganizer.data.entities.CompletionHistory;
import com.astra.lifeorganizer.data.entities.Item;

import com.astra.lifeorganizer.utils.DateTimeFormatUtils;
import com.astra.lifeorganizer.utils.LabelUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskHistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private final SimpleDateFormat completionTimeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat headerTimeFormat = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault());
    private final OnHistoryClickListener listener;
    private List<Object> items = new ArrayList<>();

    public interface OnHistoryClickListener {
        void onHistoryClicked(HistoryEntry entry);
    }

    public static class HistoryEntry {
        public final Item item;
        public final CompletionHistory history;

        public HistoryEntry(Item item, CompletionHistory history) {
            this.item = item;
            this.history = history;
        }
    }

    public TaskHistoryAdapter(OnHistoryClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<Object> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof String ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            TextView tv = view.findViewById(android.R.id.text1);
            tv.setTextSize(14f);
            tv.setAllCaps(true);
            tv.setPadding(32, 16, 0, 8);
            tv.setAlpha(0.65f);
            tv.setTextColor(parent.getContext().getResources().getColor(android.R.color.darker_gray));
            return new HeaderViewHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_HEADER) {
            ((HeaderViewHolder) holder).tvDate.setText((String) items.get(position));
            return;
        }

        HistoryEntry entry = (HistoryEntry) items.get(position);
        HistoryViewHolder vh = (HistoryViewHolder) holder;
        Item item = entry.item;
        CompletionHistory history = entry.history;

        vh.tvTitle.setText(item != null ? item.title : "Deleted item");

        if (item != null && item.description != null && !item.description.trim().isEmpty()) {
            vh.tvNote.setVisibility(View.VISIBLE);
            vh.tvNote.setText(item.description);
        } else {
            vh.tvNote.setVisibility(View.GONE);
        }

        LabelUtils.applyLabelStyle(vh.tvLabel, item != null ? item.label : null);

        if (item != null && item.dueAt != null && item.dueAt > 0) {
            vh.tvDueDate.setVisibility(View.VISIBLE);
            vh.tvDueDate.setText("Due: " + DateTimeFormatUtils.formatDate(vh.itemView.getContext(), item.dueAt));
        } else {
            vh.tvDueDate.setVisibility(View.GONE);
        }

        vh.tvCompletedAt.setText("Completed: " + completionTimeFormat.format(new Date(history.timestamp)));

        vh.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onHistoryClicked(entry);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        final TextView tvDate;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(android.R.id.text1);
        }
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvNote;
        final TextView tvLabel;
        final TextView tvDueDate;
        final TextView tvCompletedAt;

        HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvNote = itemView.findViewById(R.id.tv_note);
            tvLabel = itemView.findViewById(R.id.tv_label);
            tvDueDate = itemView.findViewById(R.id.tv_due_date);
            tvCompletedAt = itemView.findViewById(R.id.tv_completed_at);
        }
    }
}
