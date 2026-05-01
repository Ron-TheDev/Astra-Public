package com.astra.lifeorganizer.ui;

import android.graphics.Color;
import android.graphics.Paint;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.astra.lifeorganizer.R;
import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.data.entities.Subtask;

import com.astra.lifeorganizer.utils.LabelUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ItemAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_PLAIN    = 0;
    private static final int TYPE_SUBTASKS = 1;

    private List<Item> items = new ArrayList<>();
    // Map from itemId → its subtasks (populated externally via setSubtasks)
    private Map<Long, List<Subtask>> subtaskMap = new HashMap<>();

    private final OnItemClickListener listener;
    private final Set<Long> selectedIds = new HashSet<>();
    private boolean isSelectionMode = false;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    public interface OnItemClickListener {
        void onCompleteToggled(Item item, boolean isChecked);
        void onItemClicked(Item item);
        void onEditItem(Item item);
        void onDeleteItem(Item item);
        void onCompleteForever(Item item);
        void onViewCompletionHistory(Item item);
        void onSelectionChanged(int count);
        /** Called when a single subtask checkbox is toggled. */
        void onSubtaskToggled(Subtask subtask, boolean isDone);
    }

    public ItemAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<Item> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    /** Provide a fresh subtask map (itemId → list of Subtask). Call this whenever subtask data changes. */
    public void setSubtasks(Map<Long, List<Subtask>> subtasks) {
        this.subtaskMap = subtasks != null ? subtasks : new HashMap<>();
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() { return isSelectionMode; }
    public Set<Long> getSelectedIds() { return selectedIds; }
    public void clearSelection() {
        isSelectionMode = false;
        selectedIds.clear();
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionChanged(0);
        }
    }

    @Override
    public int getItemViewType(int position) {
        Item item = items.get(position);
        List<Subtask> subs = subtaskMap.get(item.id);
        return (subs != null && !subs.isEmpty()) ? TYPE_SUBTASKS : TYPE_PLAIN;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SUBTASKS) {
            View view = inflater.inflate(R.layout.item_todo_with_subtasks, parent, false);
            return new SubtaskViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_todo, parent, false);
            return new PlainViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Item item = items.get(position);

        if (holder instanceof SubtaskViewHolder) {
            bindSubtaskHolder((SubtaskViewHolder) holder, item);
        } else {
            bindPlainHolder((PlainViewHolder) holder, item);
        }
    }

    // ── Plain ViewHolder binding ──────────────────────────────────────────────

    private void bindPlainHolder(PlainViewHolder h, Item item) {
        h.tvTitle.setText(item.title);
        boolean isDone = "done".equals(item.status);
        if (isDone) {
            h.tvTitle.setPaintFlags(h.tvTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            h.tvTitle.setAlpha(0.6f);
        } else {
            h.tvTitle.setPaintFlags(h.tvTitle.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            h.tvTitle.setAlpha(1.0f);
        }

        applySelectionBackground(h.itemView.findViewById(R.id.item_root_layout), item.id);
        bindCommonMeta(h.tvNote, h.tvLabel, h.tvDueDate, item);

        h.cbComplete.setOnCheckedChangeListener(null);
        h.cbComplete.setChecked("done".equals(item.status));
        h.cbComplete.setOnCheckedChangeListener((btn, isChecked) -> {
            if (listener != null) {
                if (isRecurring(item) && isChecked) {
                    animateRecurringTransition(h.itemView, () -> listener.onCompleteToggled(item, true));
                } else {
                    listener.onCompleteToggled(item, isChecked);
                }
            }
        });

        h.itemView.setOnClickListener(v -> handleClick(v, item));
        h.itemView.setOnLongClickListener(v -> handleLongClick(v, item));
        h.btnOverflow.setOnClickListener(v -> showOverflow(v, item));
    }

    // ── Subtask ViewHolder binding ────────────────────────────────────────────

    private void bindSubtaskHolder(SubtaskViewHolder h, Item item) {
        h.tvTitle.setText(item.title);
        boolean isDone = "done".equals(item.status);
        if (isDone) {
            h.tvTitle.setPaintFlags(h.tvTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            h.tvTitle.setAlpha(0.6f);
        } else {
            h.tvTitle.setPaintFlags(h.tvTitle.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            h.tvTitle.setAlpha(1.0f);
        }

        applySelectionBackground(h.itemView.findViewById(R.id.item_root_layout), item.id);
        bindCommonMeta(h.tvNote, h.tvLabel, h.tvDueDate, item);

        List<Subtask> subs = subtaskMap.getOrDefault(item.id, new ArrayList<>());
        int total = subs.size();
        int done  = 0;
        for (Subtask s : subs) if (s.isDone) done++;

        // Fraction label
        h.tvSubtaskFraction.setText(done + " / " + total + " subtasks");

        // Vertical progress bar (0–100), inverted to match the visual fill direction.
        int progress = total > 0 ? (int) ((done / (float) total) * 100) : 0;
        h.pbSubtasks.setProgress(100 - progress);

        // Main checkbox: completes / un-completes ALL subtasks
        h.cbComplete.setOnCheckedChangeListener(null);
        h.cbComplete.setChecked(done == total && total > 0);
        h.cbComplete.setOnCheckedChangeListener((btn, isChecked) -> {
            if (listener != null) {
                // Toggle every subtask
                for (Subtask s : subs) {
                    s.isDone = isChecked;
                    listener.onSubtaskToggled(s, isChecked);
                }
                if (isRecurring(item) && isChecked) {
                    animateRecurringTransition(h.itemView, () -> listener.onCompleteToggled(item, true));
                } else {
                    listener.onCompleteToggled(item, isChecked);
                }
            }
        });

        // Populate subtask rows
        h.layoutSubtaskList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(h.itemView.getContext());
        for (Subtask sub : subs) {
            View row = inflater.inflate(R.layout.item_subtask_row, h.layoutSubtaskList, false);
            CheckBox cb = row.findViewById(R.id.cb_subtask);
            TextView tv = row.findViewById(R.id.tv_subtask_title);
            tv.setText(sub.title);
            cb.setOnCheckedChangeListener(null);
            cb.setChecked(sub.isDone);
            cb.setOnCheckedChangeListener((btn, checked) -> {
                sub.isDone = checked;
                if (listener != null) {
                    listener.onSubtaskToggled(sub, checked);

                    int completed = 0;
                    for (Subtask s : subs) {
                        if (s.isDone) completed++;
                    }
                    boolean nowComplete = completed == total && total > 0;
                    if (isRecurring(item) && nowComplete) {
                        animateRecurringTransition(h.itemView, () -> listener.onCompleteToggled(item, true));
                    } else {
                        listener.onCompleteToggled(item, nowComplete);
                    }
                }
            });
            h.layoutSubtaskList.addView(row);
        }

        h.itemView.setOnClickListener(v -> handleClick(v, item));
        h.itemView.setOnLongClickListener(v -> handleLongClick(v, item));
        h.btnOverflow.setOnClickListener(v -> showOverflow(v, item));
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private void bindCommonMeta(TextView tvNote, TextView tvLabel, TextView tvDueDate, Item item) {
        if (item.description != null && !item.description.trim().isEmpty()) {
            tvNote.setVisibility(View.VISIBLE);
            tvNote.setText(item.description);
        } else {
            tvNote.setVisibility(View.GONE);
        }
        LabelUtils.applyLabelStyle(tvLabel, item.label);
        if (item.dueAt != null && item.dueAt > 0) {
            tvDueDate.setVisibility(View.VISIBLE);
            tvDueDate.setText("Due: " + dateFormat.format(new Date(item.dueAt)));
            
            if (item.dueAt < System.currentTimeMillis() && !"done".equalsIgnoreCase(item.status)) {
                tvDueDate.setTextColor(com.google.android.material.color.MaterialColors.getColor(tvDueDate, androidx.appcompat.R.attr.colorError, android.graphics.Color.RED));
                tvDueDate.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                tvDueDate.setTextColor(com.google.android.material.color.MaterialColors.getColor(tvDueDate, com.google.android.material.R.attr.colorOnSurfaceVariant, android.graphics.Color.GRAY));
                tvDueDate.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        } else {
            tvDueDate.setVisibility(View.GONE);
        }
    }

    private void applySelectionBackground(View root, long itemId) {
        if (root == null) return;
        if (selectedIds.contains(itemId)) {
            root.setBackgroundColor(Color.parseColor("#E3F2FD"));
        } else {
            root.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private void handleClick(View v, Item item) {
        if (isSelectionMode) {
            toggleSelection(item.id);
        } else if (listener != null) {
            listener.onItemClicked(item);
        }
    }

    private boolean handleLongClick(View v, Item item) {
        if (!isSelectionMode) {
            isSelectionMode = true;
            toggleSelection(item.id);
            return true;
        }
        return false;
    }

    private void showOverflow(View v, Item item) {
        PopupMenu popup = new PopupMenu(v.getContext(), v);
        popup.getMenu().add("Edit");
        popup.getMenu().add("Delete");
        if (item.recurrenceRule != null && !item.recurrenceRule.isEmpty()) {
            popup.getMenu().add("Complete Forever");
        }
        if ("todo".equalsIgnoreCase(item.type) && isRecurring(item)) {
            popup.getMenu().add("Task completion history");
        }
        popup.getMenu().add("Share as Text");
        if ("event".equals(item.type)) {
            popup.getMenu().add("Share as .ics");
        }

        popup.setOnMenuItemClickListener(menuItem -> {
            if (listener == null) return false;
            String title = menuItem.getTitle().toString();
            switch (title) {
                case "Edit":            listener.onEditItem(item);      return true;
                case "Delete":          listener.onDeleteItem(item);    return true;
                case "Complete Forever":listener.onCompleteForever(item);return true;
                case "Task completion history": listener.onViewCompletionHistory(item); return true;
                case "Share as Text":
                    com.astra.lifeorganizer.utils.ShareUtils.shareItemsAsText(v.getContext(), java.util.Collections.singletonList(item));
                    return true;
                case "Share as .ics":
                    com.astra.lifeorganizer.utils.ShareUtils.shareItemsAsIcs(v.getContext(), java.util.Collections.singletonList(item));
                    return true;
            }
            return false;
        });
        popup.show();
    }

    private boolean isRecurring(Item item) {
        return item != null && item.recurrenceRule != null && !item.recurrenceRule.trim().isEmpty() && !"NONE".equalsIgnoreCase(item.recurrenceRule);
    }

    private void animateRecurringTransition(View view, Runnable afterAnimation) {
        if (view == null) {
            if (afterAnimation != null) {
                afterAnimation.run();
            }
            return;
        }
        view.animate()
                .alpha(0.35f)
                .setDuration(140)
                .withEndAction(() -> view.animate()
                        .alpha(1f)
                        .setDuration(140)
                        .withEndAction(() -> {
                            if (afterAnimation != null) {
                                afterAnimation.run();
                            }
                        })
                        .start())
                .start();
    }

    private void toggleSelection(long id) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id);
        } else {
            selectedIds.add(id);
        }
        if (selectedIds.isEmpty()) isSelectionMode = false;
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged(selectedIds.size());
    }

    @Override
    public int getItemCount() { return items.size(); }

    // ── ViewHolders ───────────────────────────────────────────────────────────

    static class PlainViewHolder extends RecyclerView.ViewHolder {
        CheckBox cbComplete;
        TextView tvTitle, tvNote, tvLabel, tvDueDate;
        ImageButton btnOverflow;

        PlainViewHolder(@NonNull View itemView) {
            super(itemView);
            cbComplete  = itemView.findViewById(R.id.cb_complete);
            tvTitle     = itemView.findViewById(R.id.tv_title);
            tvNote      = itemView.findViewById(R.id.tv_note);
            tvLabel     = itemView.findViewById(R.id.tv_label);
            tvDueDate   = itemView.findViewById(R.id.tv_due_date);
            btnOverflow = itemView.findViewById(R.id.btn_overflow);
        }
    }

    static class SubtaskViewHolder extends RecyclerView.ViewHolder {
        CheckBox cbComplete;
        TextView tvTitle, tvNote, tvLabel, tvDueDate, tvSubtaskFraction;
        ProgressBar pbSubtasks;
        LinearLayout layoutSubtaskList;
        ImageButton btnOverflow;

        SubtaskViewHolder(@NonNull View itemView) {
            super(itemView);
            cbComplete         = itemView.findViewById(R.id.cb_complete);
            tvTitle            = itemView.findViewById(R.id.tv_title);
            tvNote             = itemView.findViewById(R.id.tv_note);
            tvLabel            = itemView.findViewById(R.id.tv_label);
            tvDueDate          = itemView.findViewById(R.id.tv_due_date);
            tvSubtaskFraction  = itemView.findViewById(R.id.tv_subtask_fraction);
            pbSubtasks         = itemView.findViewById(R.id.pb_subtasks);
            layoutSubtaskList  = itemView.findViewById(R.id.layout_subtask_list);
            btnOverflow        = itemView.findViewById(R.id.btn_overflow);
        }
    }
}
