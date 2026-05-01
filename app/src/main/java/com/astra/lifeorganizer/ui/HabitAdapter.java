package com.astra.lifeorganizer.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.astra.lifeorganizer.R;
import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.data.entities.ItemOccurrence;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HabitAdapter extends RecyclerView.Adapter<HabitAdapter.HabitViewHolder> {

    private List<Item> items = new ArrayList<>();
    private List<ItemOccurrence> occurrences = new ArrayList<>();
    private final OnItemClickListener listener;

    private final Set<Long> selectedIds = new HashSet<>();
    private boolean isSelectionMode = false;

    public interface OnItemClickListener {
        void onDoneClicked(Item item);
        void onItemClicked(Item item);
        void onEditClicked(Item item);
        void onSelectionChanged(int count);
    }

    public HabitAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<Item> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    public void setOccurrences(List<ItemOccurrence> newOccurrences) {
        this.occurrences = newOccurrences;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HabitViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_habit, parent, false);
        return new HabitViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HabitViewHolder holder, int position) {
        Item item = items.get(position);

        ItemOccurrence occ = findOccurrence(item.id);
        int current = occ != null ? occ.currentCount : 0;
        int target = item.dailyTargetCount > 0 ? item.dailyTargetCount : 1;

        holder.tvTitle.setText(item.title);
        holder.tvProgressValue.setText(current + " / " + target);
        holder.progressBar.setMax(target);
        holder.progressBar.setProgress(current);
        long now = System.currentTimeMillis();
        boolean isValidToday = com.astra.lifeorganizer.utils.RecurrenceRuleParser.isValidOccurrence(now, item);

        if (!isValidToday) {
            holder.btnComplete.setVisibility(View.GONE);
            holder.itemView.setAlpha(0.5f);
            holder.tvStreak.setText("Scheduled for " + com.astra.lifeorganizer.utils.RecurrenceRuleParser.getHumanReadableSummary(item.recurrenceRule).replace("Repeats ", ""));
        } else {
            holder.itemView.setAlpha(1.0f);
            holder.tvStreak.setText("Streak Active (tracked)"); 
            if (current >= target) {
                holder.btnComplete.setVisibility(View.GONE);
            } else {
                holder.btnComplete.setVisibility(View.VISIBLE);
                if (current == target - 1) {
                    holder.btnComplete.setText("Done");
                } else {
                    holder.btnComplete.setText("+1");
                }
            }
        }

        holder.btnComplete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDoneClicked(item);
            }
        });

        holder.btnEditHabit.setOnClickListener(v -> {
            if (listener != null) listener.onEditClicked(item);
        });

        // ✅ CARD BACKGROUND HANDLING (FIXED)
        CardView card = (CardView) holder.itemView;

        if (selectedIds.contains(item.id)) {
            card.setCardBackgroundColor(
                    android.graphics.Color.parseColor("#E3F2FD")
            );
        } else {
            card.setCardBackgroundColor(
                    MaterialColors.getColor(
                            card,
                            com.google.android.material.R.attr.colorSurfaceContainerLow
                    )
            );
        }

        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                toggleSelection(item.id);
            } else if (listener != null) {
                listener.onItemClicked(item);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (!isSelectionMode) {
                isSelectionMode = true;
                toggleSelection(item.id);
                return true;
            }
            return false;
        });
    }

    public void clearSelection() {
        isSelectionMode = false;
        selectedIds.clear();
        notifyDataSetChanged();

        if (listener != null) {
            listener.onSelectionChanged(0);
        }
    }

    public Set<Long> getSelectedIds() {
        return selectedIds;
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
            listener.onSelectionChanged(selectedIds.size());
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private ItemOccurrence findOccurrence(long itemId) {
        for (ItemOccurrence o : occurrences) {
            if (o.itemId == itemId) return o;
        }
        return null;
    }

    static class HabitViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvStreak, tvProgressValue;
        ProgressBar progressBar;
        Button btnComplete;
        ImageButton btnEditHabit;

        public HabitViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvStreak = itemView.findViewById(R.id.tv_streak);
            tvProgressValue = itemView.findViewById(R.id.tv_progress_value);
            progressBar = itemView.findViewById(R.id.progress_habit);
            btnComplete = itemView.findViewById(R.id.btn_complete);
            btnEditHabit = itemView.findViewById(R.id.btn_edit_habit);
        }
    }
}