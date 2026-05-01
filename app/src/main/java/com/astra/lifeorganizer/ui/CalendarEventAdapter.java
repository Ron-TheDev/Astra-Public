package com.astra.lifeorganizer.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.PopupMenu;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.astra.lifeorganizer.R;
import com.astra.lifeorganizer.data.entities.Item;

import com.astra.lifeorganizer.utils.LabelUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CalendarEventAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private List<Object> items = new ArrayList<>();
    private final OnEventClickListener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    public interface OnEventClickListener {
        void onEventClicked(Item item);
        void onEventMenuClicked(Item item, View anchor);
        void onSelectionChanged(int count);
    }

    public CalendarEventAdapter(OnEventClickListener listener) {
        this.listener = listener;
    }

    public List<Object> getItems() {
        return items;
    }

    public void setItems(List<Object> newItems) {
        List<Object> oldItems = new ArrayList<>(this.items);
        this.items = newItems != null ? new ArrayList<>(newItems) : new ArrayList<>();
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size();
            }

            @Override
            public int getNewListSize() {
                return items.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                Object oldItem = oldItems.get(oldItemPosition);
                Object newItem = items.get(newItemPosition);
                if (oldItem instanceof String || newItem instanceof String) {
                    return oldItem instanceof String && newItem instanceof String && oldItem.equals(newItem);
                }
                Item oldEvent = (Item) oldItem;
                Item newEvent = (Item) newItem;
                return oldEvent.id == newEvent.id && getIdentityTime(oldEvent) == getIdentityTime(newEvent);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Object oldItem = oldItems.get(oldItemPosition);
                Object newItem = items.get(newItemPosition);
                if (oldItem instanceof String || newItem instanceof String) {
                    return oldItem != null && oldItem.equals(newItem);
                }
                Item oldEvent = (Item) oldItem;
                Item newEvent = (Item) newItem;
                return safeEquals(oldEvent.title, newEvent.title)
                        && safeEquals(oldEvent.description, newEvent.description)
                        && safeEquals(oldEvent.label, newEvent.label)
                        && safeEquals(oldEvent.status, newEvent.status)
                        && safeEquals(oldEvent.type, newEvent.type)
                        && safeEquals(oldEvent.startAt, newEvent.startAt)
                        && safeEquals(oldEvent.endAt, newEvent.endAt)
                        && safeEquals(oldEvent.dueAt, newEvent.dueAt)
                        && oldEvent.allDay == newEvent.allDay;
            }
        });
        diff.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemViewType(int position) {
        return (items.get(position) instanceof String) ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            // Customize style for header
            TextView tv = view.findViewById(android.R.id.text1);
            tv.setTextSize(14);
            tv.setAllCaps(true);
            tv.setPadding(32, 16, 0, 8);
            tv.setAlpha(0.6f);
            tv.setTextColor(parent.getContext().getResources().getColor(android.R.color.darker_gray));
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_calendar_event, parent, false);
            return new EventViewHolder(view);
        }
    }

    private final java.util.Set<Long> selectedIds = new java.util.HashSet<>();
    private boolean isSelectionMode = false;

    public boolean isSelectionMode() { return isSelectionMode; }
    public java.util.Set<Long> getSelectedIds() { return selectedIds; }
    public void clearSelection() {
        isSelectionMode = false;
        selectedIds.clear();
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionChanged(0);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_HEADER) {
            HeaderViewHolder headerHolder = (HeaderViewHolder) holder;
            headerHolder.tvDate.setText((String) items.get(position));
        } else {
            EventViewHolder itemHolder = (EventViewHolder) holder;
            Item item = (Item) items.get(position);
            itemHolder.tvTitle.setText(item.title);
            
            // Selection Background
            androidx.cardview.widget.CardView cardView = (androidx.cardview.widget.CardView) itemHolder.itemView;
            if (selectedIds.contains(item.id)) {
                cardView.setCardBackgroundColor(com.google.android.material.color.MaterialColors.getColor(cardView, com.google.android.material.R.attr.colorSecondaryContainer));
            } else {
                cardView.setCardBackgroundColor(com.google.android.material.color.MaterialColors.getColor(cardView, com.google.android.material.R.attr.colorSurfaceContainerLow));
            }

            boolean isCanceled = item.status != null && "canceled".equalsIgnoreCase(item.status);
            itemHolder.itemView.setAlpha(isCanceled ? 0.55f : 1f);
            
            // Populate Note
            if (item.description != null && !item.description.trim().isEmpty()) {
                itemHolder.tvNote.setVisibility(View.VISIBLE);
                itemHolder.tvNote.setText(item.description);
            } else {
                itemHolder.tvNote.setVisibility(View.GONE);
            }

            // Populate Label
            LabelUtils.applyLabelStyle(itemHolder.tvLabel, item.label);

            if (item.allDay) {
                itemHolder.tvTime.setVisibility(View.VISIBLE);
                itemHolder.tvTime.setText("All day");
            } else if (item.startAt != null && item.startAt > 0) {
                itemHolder.tvTime.setVisibility(View.VISIBLE);
                String timeStr = timeFormat.format(new Date(item.startAt));
                if (item.endAt != null && item.endAt > 0) {
                    timeStr += " - " + timeFormat.format(new Date(item.endAt));
                }
                itemHolder.tvTime.setText(timeStr);
            } else {
                itemHolder.tvTime.setVisibility(View.GONE);
            }

            itemHolder.itemView.setOnClickListener(v -> {
                if (isSelectionMode) {
                    toggleSelection(item.id);
                } else if (listener != null) {
                    listener.onEventClicked(item);
                }
            });
            
            itemHolder.itemView.setOnLongClickListener(v -> {
                if (!isSelectionMode) {
                    isSelectionMode = true;
                    toggleSelection(item.id);
                    return true;
                }
                return false;
            });

            itemHolder.btnOverflow.setOnClickListener(v -> {
                if (listener != null) listener.onEventMenuClicked(item, v);
            });
        }
    }

    private void toggleSelection(long id) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id);
        } else {
            selectedIds.add(id);
        }
        if (selectedIds.isEmpty()) isSelectionMode = false;
        notifyDataSetChanged();
        if (listener != null) {
            int count = 0;
            for (Object obj : items) {
                if (obj instanceof Item && selectedIds.contains(((Item) obj).id)) {
                    count++;
                }
            }
            // Note: Since multi-selection might include "virtual" occurrences in calendar, 
            // we use the simple selectedIds.size() for simplicity in this implementation.
            listener.onSelectionChanged(selectedIds.size());
        }
    }

    private static long getIdentityTime(Item item) {
        if (item == null) {
            return 0L;
        }
        if (item.startAt != null && item.startAt > 0L) {
            return item.startAt;
        }
        if (item.dueAt != null && item.dueAt > 0L) {
            return item.dueAt;
        }
        return 0L;
    }

    private static boolean safeEquals(Object a, Object b) {
        return a == b || (a != null && a.equals(b));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvNote, tvLabel, tvTime;
        ImageButton btnOverflow;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvNote = itemView.findViewById(R.id.tv_note);
            tvLabel = itemView.findViewById(R.id.tv_label);
            tvTime = itemView.findViewById(R.id.tv_time);
            btnOverflow = itemView.findViewById(R.id.btn_overflow);
        }
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(android.R.id.text1);
        }
    }
}
