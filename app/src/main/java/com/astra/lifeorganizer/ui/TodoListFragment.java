package com.astra.lifeorganizer.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.astra.lifeorganizer.R;
import com.astra.lifeorganizer.data.entities.CompletionHistory;
import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.data.entities.Subtask;

import com.astra.lifeorganizer.utils.LabelUtils;
import com.astra.lifeorganizer.utils.RecurrenceRuleParser;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TodoListFragment extends Fragment {

    private static final int MODE_REGULAR = 0;
    private static final int MODE_HISTORY = 1;

    private final SimpleDateFormat historyHeaderFormat = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault());
    private ItemViewModel itemViewModel;
    private RecyclerView recyclerView;
    private TextView tvEmptyState;
    private TextInputEditText etSearch;
    private View btnTodoMode;
    private Chip chipAll;
    private Chip chipSort;
    private Chip chipTags;
    private Chip chipDue;
    private Chip chipPriority;
    private ChipGroup chipGroupFilters;

    private ItemAdapter taskAdapter;
    private TaskHistoryAdapter historyAdapter;

    private final List<Item> allTasks = new ArrayList<>();
    private final List<CompletionHistory> allHistory = new ArrayList<>();
    private final Map<Long, Item> itemIndex = new HashMap<>();
    private Map<Long, List<Subtask>> currentSubtaskMap = new HashMap<>();
    private List<String> distinctLabels = new ArrayList<>();

    private int currentMode = MODE_REGULAR;
    private Long historyFilterItemId = null;
    private String searchQuery = "";
    private boolean sortRecentFirst = true;
    private String selectedTag = null;
    private DueFilter selectedDueFilter = DueFilter.ANY;
    private PriorityFilter selectedPriorityFilter = PriorityFilter.ANY;

    private enum DueFilter {
        ANY, TODAY, OVERDUE, NO_DATE, UPCOMING
    }

    private enum PriorityFilter {
        ANY, NONE, LOW, MEDIUM, HIGH
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_todo_list, container, false);

        recyclerView = root.findViewById(R.id.rv_todos);
        tvEmptyState = root.findViewById(R.id.tv_empty_state);
        etSearch = root.findViewById(R.id.et_search);
        btnTodoMode = root.findViewById(R.id.btn_todo_mode);
        chipGroupFilters = root.findViewById(R.id.chip_group_filters);
        chipAll = root.findViewById(R.id.chip_filter_all);
        chipSort = root.findViewById(R.id.chip_filter_sort);
        chipTags = root.findViewById(R.id.chip_filter_tags);
        chipDue = root.findViewById(R.id.chip_filter_due);
        chipPriority = root.findViewById(R.id.chip_filter_priority);

        itemViewModel = new ViewModelProvider(this).get(ItemViewModel.class);

        setupModeMenu();
        setupSearch();
        setupFilterChips();
        setupTaskAdapter();
        setupHistoryAdapter();
        observeData();

        setMode(MODE_REGULAR);
        setupSelectionToolbar(root);
        return root;
    }

    private void setupSelectionToolbar(View root) {
        View toolbar = root.findViewById(R.id.card_selection_toolbar);
        if (toolbar == null) return;

        root.findViewById(R.id.btn_clear_selection).setOnClickListener(v -> {
            taskAdapter.clearSelection();
        });

        root.findViewById(R.id.btn_delete_selected).setOnClickListener(v -> {
            int count = taskAdapter.getSelectedIds().size();
            new AlertDialog.Builder(requireContext())
                    .setTitle("Delete " + count + " items?")
                    .setMessage("This action cannot be undone.")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        for (Long id : taskAdapter.getSelectedIds()) {
                            Item item = itemIndex.get(id);
                            if (item != null) itemViewModel.delete(item);
                        }
                        taskAdapter.clearSelection();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        root.findViewById(R.id.btn_share_text).setOnClickListener(v -> {
            com.astra.lifeorganizer.utils.ShareUtils.shareItemsAsText(requireContext(), getSelectedItems());
        });

        root.findViewById(R.id.btn_share_ics).setOnClickListener(v -> {
            com.astra.lifeorganizer.utils.ShareUtils.shareItemsAsIcs(requireContext(), getSelectedItems());
        });
    }

    private List<Item> getSelectedItems() {
        List<Item> selected = new ArrayList<>();
        if (taskAdapter == null) return selected;
        for (Long id : taskAdapter.getSelectedIds()) {
            Item item = itemIndex.get(id);
            if (item != null) selected.add(item);
        }
        return selected;
    }

    private void setupModeMenu() {
        if (btnTodoMode != null) {
            btnTodoMode.setOnClickListener(v -> {
                android.widget.PopupMenu popup = new android.widget.PopupMenu(requireContext(), v);
                popup.getMenu().add(0, MODE_REGULAR, 0, "Regular View");
                popup.getMenu().add(0, MODE_HISTORY, 1, "Tasks Completion History");
                
                popup.setOnMenuItemClickListener(item -> {
                    setMode(item.getItemId());
                    return true;
                });
                popup.show();
            });
        }
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s == null ? "" : s.toString().trim();
                refreshVisibleList();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupFilterChips() {
        chipAll.setOnClickListener(v -> {
            searchQuery = "";
            if (etSearch != null) {
                etSearch.setText("");
            }
            selectedTag = null;
            selectedDueFilter = DueFilter.ANY;
            selectedPriorityFilter = PriorityFilter.ANY;
            refreshFilterChipLabels();
            refreshVisibleList();
        });

        chipSort.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Sort order")
                    .setSingleChoiceItems(new CharSequence[]{"Recent to oldest", "Oldest to recent"}, sortRecentFirst ? 0 : 1, (dialog, which) -> {
                        sortRecentFirst = which == 0;
                        refreshFilterChipLabels();
                        refreshVisibleList();
                        dialog.dismiss();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        chipTags.setOnClickListener(v -> {
            showTagFilterDialog();
        });
        chipDue.setOnClickListener(v -> {
            showDueFilterDialog();
        });
        chipPriority.setOnClickListener(v -> {
            showPriorityFilterDialog();
        });
        refreshFilterChipLabels();
    }

    private void setupTaskAdapter() {
        taskAdapter = new ItemAdapter(new ItemAdapter.OnItemClickListener() {
            @Override
            public void onCompleteToggled(Item item, boolean isChecked) {
                if (isChecked && item.recurrenceRule != null && !item.recurrenceRule.equalsIgnoreCase("NONE")) {
                    long baseTime = item.dueAt != null ? item.dueAt : System.currentTimeMillis();
                    long next = RecurrenceRuleParser.getNextOccurrence(item.recurrenceRule, baseTime);
                    if (next > 0) {
                        if (item.startAt != null && item.dueAt != null) {
                            long delta = next - item.dueAt;
                            item.startAt = item.startAt + delta;
                            if (item.endAt != null) item.endAt = item.endAt + delta;
                        }
                        item.dueAt = next;
                        item.status = "pending";
                        itemViewModel.update(item);
                        resetSubtasksForRecurringItem(item.id);

                        CompletionHistory history = new CompletionHistory();
                        history.itemId = item.id;
                        history.action = "done";
                        history.timestamp = System.currentTimeMillis();
                        itemViewModel.recordHistory(history);
                        return;
                    }
                }
                item.status = isChecked ? "done" : "pending";
                itemViewModel.update(item);

                CompletionHistory history = new CompletionHistory();
                history.itemId = item.id;
                history.action = isChecked ? "done" : "uncompleted";
                history.timestamp = System.currentTimeMillis();
                itemViewModel.recordHistory(history);
            }

            @Override public void onItemClicked(Item item) {
                ItemPreviewBottomSheetFragment.newInstance(item.id).show(getParentFragmentManager(), "ItemPreviewBottomSheet");
            }

            @Override public void onEditItem(Item item) {
                AddTaskBottomSheetFragment.newInstance(item.type, item.id).show(getParentFragmentManager(), "EditTaskBottomSheet");
            }

            @Override public void onDeleteItem(Item item) { itemViewModel.delete(item); }

            @Override public void onCompleteForever(Item item) {
                item.recurrenceRule = null;
                item.status = "done";
                itemViewModel.update(item);
                recordHistory(item, "done");
            }

            @Override public void onViewCompletionHistory(Item item) {
                if (item == null) {
                    return;
                }
                historyFilterItemId = item.id;
                setMode(MODE_HISTORY);
            }

            @Override public void onSelectionChanged(int count) {
                View root = getView();
                if (root == null) return;
                View toolbar = root.findViewById(R.id.card_selection_toolbar);
                if (toolbar != null) {
                    toolbar.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
                    TextView tvCount = toolbar.findViewById(R.id.tv_selection_count);
                    if (tvCount != null) tvCount.setText(count + " selected");
                }
            }

            @Override public void onSubtaskToggled(Subtask subtask, boolean isDone) {
                itemViewModel.updateSubtask(subtask);
            }
        });
    }

    private void setupHistoryAdapter() {
        historyAdapter = new TaskHistoryAdapter(entry -> {
            if (entry != null && entry.item != null) {
                ItemPreviewBottomSheetFragment.newInstance(entry.item.id, true)
                        .show(getParentFragmentManager(), "PreviewTaskHistoryItem");
            }
        });
    }

    private void observeData() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(historyAdapter);

        itemViewModel.getAllSubtasksLive().observe(getViewLifecycleOwner(), subtasks -> {
            currentSubtaskMap = groupSubtasksByItem(subtasks);
            taskAdapter.setSubtasks(currentSubtaskMap);
        });

        itemViewModel.getItemsByType("todo").observe(getViewLifecycleOwner(), items -> {
            allTasks.clear();
            itemIndex.clear();
            if (items != null) {
                for (Item item : items) {
                    itemIndex.put(item.id, item);
                    if (!"done".equalsIgnoreCase(item.status) && !"archived".equalsIgnoreCase(item.status)) {
                        allTasks.add(item);
                    }
                }
            }
            refreshVisibleList();
        });

        itemViewModel.getAllHistory().observe(getViewLifecycleOwner(), histories -> {
            allHistory.clear();
            if (histories != null) {
                allHistory.addAll(histories);
            }
            refreshVisibleList();
        });

        itemViewModel.getDistinctLabels().observe(getViewLifecycleOwner(), labels -> {
            distinctLabels = labels != null ? labels : new ArrayList<>();
        });
    }

    private void setMode(int mode) {
        currentMode = mode;
        if (mode == MODE_REGULAR) {
            historyFilterItemId = null;
        }
        chipGroupFilters.setVisibility(View.VISIBLE);
        if (recyclerView != null && taskAdapter != null && historyAdapter != null) {
            recyclerView.setAdapter(currentMode == MODE_HISTORY ? historyAdapter : taskAdapter);
        }
        refreshVisibleList();
    }

    private void refreshVisibleList() {
        if (!isAdded()) {
            return;
        }

        if (currentMode == MODE_HISTORY) {
            if (historyAdapter == null) {
                return;
            }
            List<Object> display = buildHistoryDisplayItems(historyFilterItemId);
            historyAdapter.setItems(display);
            boolean empty = display.isEmpty();
            tvEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            tvEmptyState.setText(empty ? "No matching completion history" : "");
        } else {
            if (taskAdapter == null) {
                return;
            }
            List<Item> display = buildFilteredTasks();
            taskAdapter.setItems(display);
            boolean empty = display.isEmpty();
            tvEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            tvEmptyState.setText(empty ? "No matching tasks" : "");
        }
    }

    private List<Item> buildFilteredTasks() {
        List<Item> filtered = new ArrayList<>();
        for (Item item : allTasks) {
            if (!matchesSearch(item)) {
                continue;
            }
            if (!matchesTag(item)) {
                continue;
            }
            if (!matchesDueFilter(item)) {
                continue;
            }
            if (!matchesPriority(item)) {
                continue;
            }
            filtered.add(item);
        }
        filtered.sort((a, b) -> {
            long aTime = getComparableTime(a);
            long bTime = getComparableTime(b);
            return sortRecentFirst ? Long.compare(bTime, aTime) : Long.compare(aTime, bTime);
        });
        return filtered;
    }

    private List<Object> buildHistoryDisplayItems(@Nullable Long itemFilterId) {
        List<TaskHistoryAdapter.HistoryEntry> entries = new ArrayList<>();
        for (CompletionHistory history : allHistory) {
            if (history == null || history.timestamp <= 0) {
                continue;
            }
            if (!"done".equalsIgnoreCase(history.action)) {
                continue;
            }
            Item item = itemIndex.get(history.itemId);
            if (item == null || !"todo".equalsIgnoreCase(item.type)) {
                continue;
            }
            if (itemFilterId != null && item.id != itemFilterId) {
                continue;
            }
            if (!matchesSearch(item) || !matchesTag(item) || !matchesDueFilter(item) || !matchesPriority(item)) {
                continue;
            }
            entries.add(new TaskHistoryAdapter.HistoryEntry(item, history));
        }

        entries.sort((a, b) -> sortRecentFirst
                ? Long.compare(b.history.timestamp, a.history.timestamp)
                : Long.compare(a.history.timestamp, b.history.timestamp));

        List<Object> display = new ArrayList<>();
        String currentHeader = null;
        for (TaskHistoryAdapter.HistoryEntry entry : entries) {
            String header = historyHeaderFormat.format(entry.history.timestamp);
            if (!header.equals(currentHeader)) {
                display.add(header);
                currentHeader = header;
            }
            display.add(entry);
        }
        return display;
    }

    private boolean matchesSearch(Item item) {
        if (searchQuery == null || searchQuery.isEmpty()) {
            return true;
        }
        String haystack = ((item.title != null ? item.title : "") + " " +
                (item.description != null ? item.description : "") + " " +
                (item.label != null ? item.label : "")).toLowerCase(Locale.getDefault());
        return haystack.contains(searchQuery.toLowerCase(Locale.getDefault()));
    }

    private boolean matchesTag(Item item) {
        if (selectedTag == null || selectedTag.trim().isEmpty()) {
            return true;
        }
        if ("__no_label__".equals(selectedTag)) {
            return item.label == null || item.label.trim().isEmpty();
        }
        return item.label != null && item.label.trim().equalsIgnoreCase(selectedTag.trim());
    }

    private boolean matchesDueFilter(Item item) {
        if (selectedDueFilter == DueFilter.ANY) {
            return true;
        }
        long now = System.currentTimeMillis();
        long todayStart = startOfDay(now);
        long todayEnd = endOfDay(now);
        long due = item.dueAt != null ? item.dueAt : 0L;
        switch (selectedDueFilter) {
            case TODAY:
                return due >= todayStart && due <= todayEnd;
            case OVERDUE:
                return due > 0 && due < todayStart;
            case NO_DATE:
                return due <= 0;
            case UPCOMING:
                return due > todayEnd;
            default:
                return true;
        }
    }

    private boolean matchesPriority(Item item) {
        int priority = item.priority;
        switch (selectedPriorityFilter) {
            case NONE:
                return priority == 0;
            case LOW:
                return priority == 1;
            case MEDIUM:
                return priority == 2;
            case HIGH:
                return priority == 3;
            case ANY:
            default:
                return true;
        }
    }

    private long getComparableTime(Item item) {

        long due = item.dueAt != null ? item.dueAt : 0L;
        if (due > 0) {
            return due;
        }
        long start = item.startAt != null ? item.startAt : 0L;
        return start > 0 ? start : item.createdAt;
    }

    private long startOfDay(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private long endOfDay(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTimeInMillis();
    }

    private void showTagFilterDialog() {
        List<CharSequence> options = new ArrayList<>();
        options.add("Any");
        options.add("No label");
        for (String label : distinctLabels) {
            options.add(LabelUtils.displayLabel(label));
        }
        int selectedIndex = 0;
        if (selectedTag != null) {
            if ("__no_label__".equals(selectedTag)) {
                selectedIndex = 1;
            } else {
                for (int i = 0; i < distinctLabels.size(); i++) {
                    if (distinctLabels.get(i) != null && distinctLabels.get(i).equalsIgnoreCase(selectedTag)) {
                        selectedIndex = i + 2;
                        break;
                    }
                }
            }
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Tags")
                .setSingleChoiceItems(options.toArray(new CharSequence[0]), selectedIndex, (dialog, which) -> {
                    if (which == 0) {
                        selectedTag = null;
                    } else if (which == 1) {
                        selectedTag = "__no_label__";
                    } else {
                        selectedTag = LabelUtils.normalizeLabel(options.get(which).toString());
                    }
                    refreshFilterChipLabels();
                    refreshVisibleList();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDueFilterDialog() {
        DueFilter[] values = {DueFilter.ANY, DueFilter.TODAY, DueFilter.OVERDUE, DueFilter.NO_DATE, DueFilter.UPCOMING};
        CharSequence[] labels = {"Any", "Today", "Overdue", "No date", "Upcoming"};
        int selectedIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == selectedDueFilter) {
                selectedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Due status")
                .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
                    selectedDueFilter = values[which];
                    refreshFilterChipLabels();
                    refreshVisibleList();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPriorityFilterDialog() {
        PriorityFilter[] values = {PriorityFilter.ANY, PriorityFilter.NONE, PriorityFilter.LOW, PriorityFilter.MEDIUM, PriorityFilter.HIGH};
        CharSequence[] labels = {"Any", "None", "Low", "Medium", "High"};
        int selectedIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == selectedPriorityFilter) {
                selectedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Priority")
                .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
                    selectedPriorityFilter = values[which];
                    refreshFilterChipLabels();
                    refreshVisibleList();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void refreshFilterChipLabels() {
        chipSort.setText("Sort: " + (sortRecentFirst ? "Recent" : "Oldest"));
        chipTags.setText("Tags: " + tagLabel());
        chipDue.setText("Due: " + dueLabel());
        chipPriority.setText("Priority: " + priorityLabel());
    }

    private String tagLabel() {
        if (selectedTag == null || selectedTag.trim().isEmpty()) {
            return "Any";
        }
        if ("__no_label__".equals(selectedTag)) {
            return "No label";
        }
        return LabelUtils.displayLabel(selectedTag);
    }

    private String dueLabel() {
        switch (selectedDueFilter) {
            case TODAY: return "Today";
            case OVERDUE: return "Overdue";
            case NO_DATE: return "No date";
            case UPCOMING: return "Upcoming";
            case ANY:
            default: return "Any";
        }
    }

    private String priorityLabel() {
        switch (selectedPriorityFilter) {
            case NONE: return "None";
            case LOW: return "Low";
            case MEDIUM: return "Medium";
            case HIGH: return "High";
            case ANY:
            default: return "Any";
        }
    }

    private Map<Long, List<Subtask>> groupSubtasksByItem(List<Subtask> subtasks) {
        Map<Long, List<Subtask>> grouped = new HashMap<>();
        if (subtasks == null) {
            return grouped;
        }
        for (Subtask subtask : subtasks) {
            grouped.computeIfAbsent(subtask.itemId, key -> new ArrayList<>()).add(subtask);
        }
        return grouped;
    }

    private void resetSubtasksForRecurringItem(long itemId) {
        List<Subtask> subtasks = currentSubtaskMap.get(itemId);
        if (subtasks == null || subtasks.isEmpty()) {
            return;
        }
        for (Subtask subtask : subtasks) {
            if (subtask == null) {
                continue;
            }
            subtask.isDone = false;
            itemViewModel.updateSubtask(subtask);
        }
    }

    private void recordHistory(Item item, String action) {
        if (item == null) {
            return;
        }
        CompletionHistory history = new CompletionHistory();
        history.itemId = item.id;
        history.action = action;
        history.timestamp = System.currentTimeMillis();
        itemViewModel.recordHistory(history);
    }
}
