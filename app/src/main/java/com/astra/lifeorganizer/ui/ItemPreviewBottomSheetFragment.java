package com.astra.lifeorganizer.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.astra.lifeorganizer.R;
import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.data.entities.Subtask;
import com.astra.lifeorganizer.utils.LabelUtils;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ItemPreviewBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String ARG_ITEM_ID = "item_id";
    private static final String ARG_READ_ONLY = "read_only";
    private long itemId;
    private ItemViewModel itemViewModel;
    private Item currentItem;
    private boolean readOnly;

    public static ItemPreviewBottomSheetFragment newInstance(long itemId) {
        return newInstance(itemId, false);
    }

    public static ItemPreviewBottomSheetFragment newInstance(long itemId, boolean readOnly) {
        ItemPreviewBottomSheetFragment fragment = new ItemPreviewBottomSheetFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_ITEM_ID, itemId);
        args.putBoolean(ARG_READ_ONLY, readOnly);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            itemId = getArguments().getLong(ARG_ITEM_ID);
            readOnly = getArguments().getBoolean(ARG_READ_ONLY, false);
        }
        itemViewModel = new ViewModelProvider(requireActivity()).get(ItemViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_item_preview_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        itemViewModel.getItemById(itemId).observe(getViewLifecycleOwner(), item -> {
            if (item != null) {
                currentItem = item;
                populateData(view, item);
            } else {
                dismiss();
            }
        });

        itemViewModel.getSubtasksForItem(itemId).observe(getViewLifecycleOwner(), subtasks -> {
            populateSubtasks(view, subtasks);
        });

        view.findViewById(R.id.btn_preview_edit).setOnClickListener(v -> {
            if (readOnly) {
                return;
            }
            dismiss();
            if (currentItem != null) {
                AddTaskBottomSheetFragment.newInstance(currentItem.type, currentItem.id)
                        .show(getParentFragmentManager(), "EditTaskBottomSheet");
            }
        });

        view.findViewById(R.id.btn_preview_delete).setOnClickListener(v -> {
            if (readOnly) {
                return;
            }
            if (currentItem != null) {
                itemViewModel.delete(currentItem);
                dismiss();
            }
        });

        view.findViewById(R.id.btn_preview_complete).setOnClickListener(v -> {
            if (readOnly) {
                return;
            }
            if (currentItem != null) {
                currentItem.status = "done";
                itemViewModel.update(currentItem);
                dismiss();
            }
        });
    }

    private void populateData(View v, Item item) {
        TextView tvTitle = v.findViewById(R.id.tv_preview_title);
        TextView tvDescription = v.findViewById(R.id.tv_preview_description);
        TextView tvDateTime = v.findViewById(R.id.tv_preview_date_time);
        Chip chipType = v.findViewById(R.id.chip_preview_type);
        Chip chipPriority = v.findViewById(R.id.chip_preview_priority);
        LinearLayout layoutLabel = v.findViewById(R.id.layout_preview_label);
        TextView tvLabel = v.findViewById(R.id.tv_preview_label);
        LinearLayout layoutRecurrence = v.findViewById(R.id.layout_preview_recurrence);
        TextView tvRecurrence = v.findViewById(R.id.tv_preview_recurrence);
        MaterialButton btnComplete = v.findViewById(R.id.btn_preview_complete);
        MaterialButton btnEdit = v.findViewById(R.id.btn_preview_edit);
        MaterialButton btnDelete = v.findViewById(R.id.btn_preview_delete);

        tvTitle.setText(item.title);
        tvDescription.setText(item.description != null && !item.description.isEmpty() ? item.description : "No description provided.");
        tvDescription.setAlpha(item.description != null && !item.description.isEmpty() ? 1f : 0.5f);

        chipType.setText(item.type.substring(0, 1).toUpperCase() + item.type.substring(1));
        chipType.setChipIconResource("event".equalsIgnoreCase(item.type) ? R.drawable.ic_calendar_event : R.drawable.ic_task);

        String priorityText;
        switch (item.priority) {
            case 3:
                priorityText = "High Priority";
                break;
            case 2:
                priorityText = "Medium Priority";
                break;
            case 1:
                priorityText = "Low Priority";
                break;
            default:
                priorityText = "No Priority";
                break;
        }
        chipPriority.setText(priorityText);
        // We could tint the chip but M3 chips have specific styling. Default is fine.

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault());
        if (item.dueAt != null && item.dueAt > 0) {
            tvDateTime.setText(sdf.format(new Date(item.dueAt)));
        } else if (item.startAt != null && item.startAt > 0) {
            tvDateTime.setText(sdf.format(new Date(item.startAt)));
        } else {
            tvDateTime.setText("No date set");
        }

        if (item.label != null && !item.label.isEmpty()) {
            layoutLabel.setVisibility(View.VISIBLE);
            tvLabel.setText(LabelUtils.displayLabel(item.label));
        } else {
            layoutLabel.setVisibility(View.GONE);
        }

        if (item.recurrenceRule != null && !item.recurrenceRule.isEmpty() && !"NONE".equalsIgnoreCase(item.recurrenceRule)) {
            layoutRecurrence.setVisibility(View.VISIBLE);
            tvRecurrence.setText("Repeat: " + item.recurrenceRule);
        } else {
            layoutRecurrence.setVisibility(View.GONE);
        }

        boolean completed = "done".equalsIgnoreCase(item.status);
        btnComplete.setVisibility(readOnly || completed ? View.GONE : View.VISIBLE);
        btnEdit.setVisibility(readOnly ? View.GONE : View.VISIBLE);
        btnDelete.setVisibility(readOnly ? View.GONE : View.VISIBLE);
    }

    private void populateSubtasks(View v, List<Subtask> subtasks) {
        LinearLayout section = v.findViewById(R.id.layout_preview_subtasks_section);
        LinearLayout list = v.findViewById(R.id.layout_preview_subtasks_list);
        list.removeAllViews();

        if (subtasks != null && !subtasks.isEmpty()) {
            section.setVisibility(View.VISIBLE);
            LayoutInflater inflater = LayoutInflater.from(getContext());
            for (Subtask sub : subtasks) {
                View row = inflater.inflate(R.layout.item_subtask_row, list, false);
                CheckBox cb = row.findViewById(R.id.cb_subtask);
                TextView tv = row.findViewById(R.id.tv_subtask_title);
                
                tv.setText(sub.title);
                cb.setChecked(sub.isDone);
                cb.setEnabled(!readOnly);
                cb.setClickable(!readOnly);
                if (!readOnly) {
                    cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        sub.isDone = isChecked;
                        itemViewModel.updateSubtask(sub);
                    });
                } else {
                    cb.setOnCheckedChangeListener(null);
                }
                list.addView(row);
            }
        } else {
            section.setVisibility(View.GONE);
        }
    }

}
