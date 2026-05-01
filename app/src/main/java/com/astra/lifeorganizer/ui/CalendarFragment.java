package com.astra.lifeorganizer.ui;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.astra.lifeorganizer.R;
import com.astra.lifeorganizer.data.database.AstraDatabase;
import com.astra.lifeorganizer.data.entities.CompletionHistory;
import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.data.entities.ItemOccurrence;
import com.astra.lifeorganizer.data.repositories.SettingsRepository;

import com.astra.lifeorganizer.utils.DateTimeFormatUtils;
import com.astra.lifeorganizer.utils.AlarmScheduler;
import com.astra.lifeorganizer.utils.LabelUtils;
import com.astra.lifeorganizer.utils.RecurrenceRuleParser;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CalendarFragment extends Fragment {

    private ItemViewModel itemViewModel;
    private RecyclerView rvCalendarGrid, rvEvents;
    private CalendarEventAdapter eventAdapter;
    private CalendarDayAdapter dayAdapter;
    private TextView tvSelectedDateLabel;
    private Button btnToday;
    private Button btnLabelFilter;
    private View cardCalendarContainer;
    private BottomSheetBehavior bottomSheetBehavior;
    private com.google.android.material.button.MaterialButton btnViewSelector;
    private TextView tvSelectionLabel;
    private LinearLayout layoutCalendarHeaders;
    
    // Timeline variables
    private View layoutTimelineSchedule;
    private android.widget.FrameLayout layoutTimelineEvents;
    private LinearLayout layoutAllDayEvents;
    private View layoutAllDayEventsContainer;
    private TextView tvTimelineDate;

    private boolean isProgrammaticChange = false;
    private boolean isDraggingSheet = false;
    private final List<String> availableLabels = new ArrayList<>();
    private final Set<String> selectedLabels = new HashSet<>();
    private boolean labelFilterInitialized = false;
    private final Set<Long> timelineSelectedIds = new HashSet<>();

    private Calendar selectedDate = Calendar.getInstance();
    
    public static final String VIEW_SCHEDULE = "Schedule";
    public static final String VIEW_LIST = "Events List";
    public static final String VIEW_AGENDA = "Event Agenda";
    
    private String currentViewType = VIEW_SCHEDULE;
    private View root;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_calendar, container, false);
        itemViewModel = new ViewModelProvider(requireActivity()).get(ItemViewModel.class);

        rvCalendarGrid = root.findViewById(R.id.rv_calendar_grid);
        rvEvents = root.findViewById(R.id.rv_calendar_events);
        tvSelectedDateLabel = root.findViewById(R.id.tv_selected_date_label);
        btnToday = root.findViewById(R.id.btn_today);
        btnLabelFilter = root.findViewById(R.id.btn_label_filter);
        cardCalendarContainer = root.findViewById(R.id.card_calendar_container);
        btnViewSelector = root.findViewById(R.id.btn_view_selector);
        layoutCalendarHeaders = root.findViewById(R.id.layout_calendar_headers);
        
        layoutTimelineSchedule = root.findViewById(R.id.layout_timeline_schedule);
        layoutTimelineEvents = root.findViewById(R.id.layout_timeline_events);
        layoutAllDayEvents = root.findViewById(R.id.layout_all_day_events);
        layoutAllDayEventsContainer = root.findViewById(R.id.layout_all_day_events_container);
        tvTimelineDate = root.findViewById(R.id.tv_timeline_date);
        
        View bottomSheet = root.findViewById(R.id.bottom_sheet_tasks);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setFitToContents(false);
        bottomSheetBehavior.setHideable(false);
        bottomSheetBehavior.setPeekHeight(dpToPx(80));
        bottomSheetBehavior.setHalfExpandedRatio(0.45f); // Sit almost in the middle (55% space)

        setupBottomSheetSnapping();
        initViews();
        
        // Load default view state
        SettingsRepository settings = SettingsRepository.getInstance(requireContext());
        int defaultMode = settings.getInt(SettingsRepository.KEY_DEFAULT_CALENDAR_VIEW, 0);
        String targetView = VIEW_SCHEDULE;
        if (defaultMode == 0) {
            String lastView = settings.getString(SettingsRepository.KEY_LAST_CALENDAR_VIEW, VIEW_SCHEDULE);
            // Safety check to ensure the string matches a known view
            if (VIEW_SCHEDULE.equals(lastView) || VIEW_LIST.equals(lastView) || VIEW_AGENDA.equals(lastView)) {
                targetView = lastView;
            }
        } else if (defaultMode == 1) {
            targetView = VIEW_SCHEDULE;
        } else if (defaultMode == 2) {
            targetView = VIEW_AGENDA;
        } else if (defaultMode == 3) {
            targetView = VIEW_LIST;
        }
        
        updateWeekdayHeaders();
        setupLabelFilter();
        
        // Temporarily clear state to force switchViewType to execute completely
        currentViewType = ""; 
        switchViewType(targetView);

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isAdded()) {
            return;
        }
        if (VIEW_AGENDA.equals(currentViewType)) {
            loadAllEventsAgenda();
        } else if (VIEW_LIST.equals(currentViewType)) {
            loadAllEventsList();
        } else {
            setupCalendar(currentViewType);
            loadEventsForSelectedDate();
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void setupBottomSheetSnapping() {
        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_DRAGGING) {
                    isDraggingSheet = true;
                }
                
                if (newState == BottomSheetBehavior.STATE_HALF_EXPANDED || newState == BottomSheetBehavior.STATE_EXPANDED || newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    isDraggingSheet = false;
                    // Force a shading update now that we've settled
                    updateTitleForDate(selectedDate.getTime());
                    
                    if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                        // Reset to single day view on collapse unless we're in agenda mode
                        if (!VIEW_AGENDA.equals(currentViewType) && !VIEW_LIST.equals(currentViewType)) {
                            loadEventsForSelectedDate();
                        }
                    }
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                // If we are in Agenda or List mode, we don't automatically toggle Monthly/Weekly grid
                // because we want the view to stay stable as selected by the user.
                if (VIEW_AGENDA.equals(currentViewType) || VIEW_LIST.equals(currentViewType)) {
                    return;
                }
                
                // slideOffset: 1.0 (Expanded) to 0.0 (Collapsed)
                // Weekly view logic: trigger when list is focused in top half (> 0.5% offset)
                String targetGrid = (slideOffset > 0.55f) ? "Weekly" : "Monthly";
                // Only switch if we are in "Schedule" mode or want to allow auto-toggle
                if (VIEW_SCHEDULE.equals(currentViewType) || "Weekly".equals(currentViewType) || "Monthly".equals(currentViewType)) {
                    if (!targetGrid.equals(currentViewType)) {
                        currentViewType = targetGrid;
                        // Smooth transition fade
                        cardCalendarContainer.animate().alpha(0.5f).setDuration(100).withEndAction(() -> {
                            setupCalendar(currentViewType);
                            cardCalendarContainer.animate().alpha(1.0f).setDuration(100).start();
                        }).start();
                    }
                }
            }
        });
    }

    private void initViews() {
        eventAdapter = new CalendarEventAdapter(new CalendarEventAdapter.OnEventClickListener() {
            @Override
            public void onEventClicked(Item item) {
                ItemPreviewBottomSheetFragment.newInstance(item.id).show(getParentFragmentManager(), "ItemPreviewBottomSheet");
            }

            @Override
            public void onEventMenuClicked(Item item, View anchor) {
                showEventActionsMenu(item, anchor);
            }

            @Override
            public void onSelectionChanged(int count) {
                View toolbar = root.findViewById(R.id.card_selection_toolbar);
                if (toolbar != null) {
                    toolbar.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
                    TextView tvCount = toolbar.findViewById(R.id.tv_selection_count);
                    if (tvCount != null) tvCount.setText(count + " selected");
                }
            }
        });
        rvEvents.setLayoutManager(new LinearLayoutManager(getContext()));
        rvEvents.setAdapter(eventAdapter);

        setupSelectionToolbar(root);

        btnToday.setOnClickListener(v -> {
            selectedDate = Calendar.getInstance();
            setupCalendar(currentViewType);
            if (VIEW_AGENDA.equals(currentViewType)) {
                loadAgendaData();
            } else {
                loadEventsForSelectedDate();
            }
        });

        btnViewSelector.setOnClickListener(v -> showViewSelectorMenu());

        View bgView = root.findViewById(R.id.timeline_background);
        if (bgView != null) {
            android.view.GestureDetector tapDetector = new android.view.GestureDetector(getContext(), new android.view.GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapUp(android.view.MotionEvent e) {
                    float y = e.getY();
                    float x = e.getX();
                    
                    float hourHeightPx = dpToPx(80);
                    float topOffsetPx = dpToPx(40);
                    float leftMarginPx = dpToPx(64);
                    
                    if (y < topOffsetPx || x < leftMarginPx) return false;
                    
                    float hoursFromMidnight = (y - topOffsetPx) / hourHeightPx;
                    int hours = (int) hoursFromMidnight;
                    int minutes = (int) ((hoursFromMidnight - hours) * 60);
                    
                    // Snap to nearest 15 minutes
                    minutes = (minutes / 15) * 15;
                    
                    java.util.Calendar c = java.util.Calendar.getInstance();
                    c.setTimeInMillis(selectedDate.getTimeInMillis());
                    c.set(java.util.Calendar.HOUR_OF_DAY, hours);
                    c.set(java.util.Calendar.MINUTE, minutes);
                    c.set(java.util.Calendar.SECOND, 0);
                    c.set(java.util.Calendar.MILLISECOND, 0);
                    
                    openEditor("event", null, c.getTimeInMillis());
                    return true;
                }
                
                @Override
                public boolean onDown(android.view.MotionEvent e) {
                    return true;
                }
            });
            bgView.setOnTouchListener((v, event) -> tapDetector.onTouchEvent(event));
        }
    }


    private void showViewSelectorMenu() {
        PopupMenu popup = new PopupMenu(requireContext(), btnViewSelector);
        popup.getMenu().add(VIEW_SCHEDULE);
        popup.getMenu().add(VIEW_LIST);
        popup.getMenu().add(VIEW_AGENDA);

        popup.setOnMenuItemClickListener(item -> {
            switchViewType(item.getTitle().toString());
            return true;
        });
        popup.show();
    }

    private void switchViewType(String newViewType) {
        if (newViewType.equals(currentViewType)) return;
        currentViewType = newViewType;
        
        // Persist last used view
        SettingsRepository.getInstance(requireContext()).setString(SettingsRepository.KEY_LAST_CALENDAR_VIEW, currentViewType);
        
        // Ensure grid is visible in all three main views
        cardCalendarContainer.setVisibility(View.VISIBLE);
        layoutCalendarHeaders.setVisibility(View.VISIBLE);
        btnToday.setVisibility(View.VISIBLE);

        // Unconditionally initialize the calendar grid for all views
        setupCalendar("Monthly");

        if (VIEW_SCHEDULE.equals(currentViewType)) {
            layoutTimelineSchedule.setVisibility(View.VISIBLE);
            rvEvents.setVisibility(View.GONE);
            tvSelectedDateLabel.setVisibility(View.GONE);
            btnViewSelector.setText(currentViewType); 
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            loadEventsForSelectedDate();
        } else {
            layoutTimelineSchedule.setVisibility(View.GONE);
            rvEvents.setVisibility(View.VISIBLE);
            tvSelectedDateLabel.setVisibility(View.VISIBLE);
            btnViewSelector.setText(currentViewType);
            bottomSheetBehavior.setPeekHeight(dpToPx(80));
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            
            if (VIEW_LIST.equals(currentViewType)) {
                loadEventsForSelectedDate();
            } else if (VIEW_AGENDA.equals(currentViewType)) {
                loadAgendaData();
            }
        }
    }

    private void scrollToTodayInList() {
        if (eventAdapter == null || rvEvents == null) return;
        int position = findPositionForToday();
        if (position != -1) {
            ((LinearLayoutManager) rvEvents.getLayoutManager()).scrollToPositionWithOffset(position, 0);
        }
    }

    private int findPositionForToday() {
        List<Object> items = eventAdapter.getItems();
        if (items == null) return -1;
        
        long today = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(today);
        clearTime(cal);
        long startOfToday = cal.getTimeInMillis();
        
        for (int i = 0; i < items.size(); i++) {
            Object obj = items.get(i);
            if (obj instanceof Item) {
                Item item = (Item) obj;
                if (isSameDay(new Date(getDisplayTime(item)), new Date(startOfToday))) {
                    return i;
                }
            } else if (obj instanceof String) {
                // Approximate match for headers like "Tuesday, April 20, 2026"
                // But safer to check item time
            }
        }
        return -1;
    }

    private void loadAllEventsList() {
        tvSelectedDateLabel.setText("All Upcoming Events");
        Calendar start = Calendar.getInstance();
        start.add(Calendar.DAY_OF_YEAR, -1); // Include today
        Calendar end = Calendar.getInstance();
        end.add(Calendar.YEAR, 1);
        loadItemsInRange(start.getTimeInMillis(), end.getTimeInMillis(), false);
    }

    private void setupSelectionToolbar(View root) {
        View toolbar = root.findViewById(R.id.card_selection_toolbar);
        if (toolbar == null) return;

        root.findViewById(R.id.btn_clear_selection).setOnClickListener(v -> {
            if (VIEW_SCHEDULE.equals(currentViewType)) {
                clearTimelineSelection();
            } else {
                eventAdapter.clearSelection();
            }
        });

        root.findViewById(R.id.btn_delete_selected).setOnClickListener(v -> {
            Set<Long> ids = VIEW_SCHEDULE.equals(currentViewType)
                    ? new HashSet<>(timelineSelectedIds)
                    : eventAdapter.getSelectedIds();
            int count = ids.size();
            new AlertDialog.Builder(requireContext())
                    .setTitle("Delete " + count + " items?")
                    .setMessage("This action will remove the selected items permanently.")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        for (Long id : ids) {
                            itemViewModel.deleteById(id);
                        }
                        if (VIEW_SCHEDULE.equals(currentViewType)) {
                            clearTimelineSelection();
                        } else {
                            eventAdapter.clearSelection();
                        }
                        refreshCalendarAfterAction();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        root.findViewById(R.id.btn_share_text).setOnClickListener(v -> {
            getActiveSelectionAsync(items ->
                    com.astra.lifeorganizer.utils.ShareUtils.shareItemsAsText(requireContext(), items));
        });

        root.findViewById(R.id.btn_share_ics).setOnClickListener(v -> {
            getActiveSelectionAsync(items ->
                    com.astra.lifeorganizer.utils.ShareUtils.shareItemsAsIcs(requireContext(), items));
        });
    }

    private void updateTimelineSelectionToolbar() {
        View toolbar = root.findViewById(R.id.card_selection_toolbar);
        if (toolbar == null) return;
        int count = timelineSelectedIds.size();
        toolbar.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        TextView tvCount = toolbar.findViewById(R.id.tv_selection_count);
        if (tvCount != null) tvCount.setText(count + " selected");
    }

    private void clearTimelineSelection() {
        timelineSelectedIds.clear();
        updateTimelineSelectionToolbar();
        // Reset all timeline cards visually using their ID tag
        if (layoutTimelineEvents != null) {
            for (int i = 0; i < layoutTimelineEvents.getChildCount(); i++) {
                View card = layoutTimelineEvents.getChildAt(i);
                applyTimelineCardSelectionState(card, false);
            }
        }
    }

    private void applyTimelineCardSelectionState(View card, boolean selected) {
        card.animate()
                .scaleX(selected ? 0.95f : 1.0f)
                .scaleY(selected ? 0.95f : 1.0f)
                .alpha(selected ? 0.75f : 1.0f)
                .setDuration(120)
                .start();
        // Restore to the cardElevation default (2dp) when deselecting
        card.setElevation(selected ? dpToPx(6) : dpToPx(2));
    }


    private List<Item> getSelectedItems() {
        // This is tricky because eventAdapter items might be Strings (Headers)
        // and we need to fetch items from the ViewModel/Database by ID.
        // For simplicity, we'll fetch them from the current display list if available
        // or just let the repository handle it if we had a list.
        // Actually, we can just fetch from the itemViewModel if we want to be safe.
        List<Item> selected = new ArrayList<>();
        if (eventAdapter == null) return selected;
        
        Set<Long> ids = eventAdapter.getSelectedIds();
        // Since we don't have an easy 'itemIndex' in CalendarFragment like TodoList,
        // we'll fetch them synchronously or just from the adapter's known items if they were there.
        // Actually, let's keep it simple: we'll fetch them from the database in a real app, 
        // but here we might need to use the adapter's list.
        return itemViewModel.getItemsByIdsSync(new ArrayList<>(ids));
    }

    private void getSelectedItemsAsync(java.util.function.Consumer<List<Item>> callback) {
        if (eventAdapter == null) {
            callback.accept(new ArrayList<>());
            return;
        }
        Set<Long> ids = eventAdapter.getSelectedIds();
        if (ids.isEmpty()) {
            callback.accept(new ArrayList<>());
            return;
        }
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            List<Item> items = itemViewModel.getItemsByIdsSync(new ArrayList<>(ids));
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> callback.accept(items));
            }
        });
    }

    private void getActiveSelectionAsync(java.util.function.Consumer<List<Item>> callback) {
        Set<Long> ids = VIEW_SCHEDULE.equals(currentViewType)
                ? new HashSet<>(timelineSelectedIds)
                : (eventAdapter != null ? eventAdapter.getSelectedIds() : new HashSet<>());
        if (ids.isEmpty()) {
            callback.accept(new ArrayList<>());
            return;
        }
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            List<Item> items = itemViewModel.getItemsByIdsSync(new ArrayList<>(ids));
            // Fallback: if bulk query returned nothing, fetch individually
            if (items == null || items.isEmpty()) {
                items = new ArrayList<>();
                for (Long id : ids) {
                    Item single = itemViewModel.getItemByIdSync(id);
                    if (single != null) items.add(single);
                }
            }
            final List<Item> result = items;
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> callback.accept(result));
            }
        });
    }

    private void setupLabelFilter() {
        if (btnLabelFilter == null) {
            return;
        }
        itemViewModel.getDistinctLabels().observe(getViewLifecycleOwner(), labels -> {
            availableLabels.clear();
            if (labels != null) {
                for (String label : labels) {
                    String normalized = LabelUtils.normalizeLabel(label);
                    if (normalized != null && !availableLabels.contains(normalized)) {
                        availableLabels.add(normalized);
                    }
                }
            }
            if (!labelFilterInitialized) {
                selectedLabels.clear();
                labelFilterInitialized = true;
            } else {
                selectedLabels.retainAll(new HashSet<>(availableLabels));
            }
            updateLabelFilterButtonText();
        });

        btnLabelFilter.setOnClickListener(v -> {
            showLabelFilterDialog();
        });
    }

    private void loadAllEventsAgenda() {
        Calendar start = Calendar.getInstance();
        start.add(Calendar.YEAR, -2);
        Calendar end = Calendar.getInstance();
        end.add(Calendar.YEAR, 3);
        currentViewType = VIEW_AGENDA;
        tvSelectedDateLabel.setText("All Events Agenda");
        loadItemsInRange(start.getTimeInMillis(), end.getTimeInMillis(), true);
    }

    private void showLabelFilterDialog() {
        if (!isAdded()) {
            return;
        }

        List<CharSequence> options = new ArrayList<>();
        options.add("All");
        options.addAll(availableLabels);
        boolean[] checked = new boolean[options.size()];
        if (selectedLabels.isEmpty()) {
            for (int i = 0; i < checked.length; i++) {
                checked[i] = true;
            }
        } else {
            checked[0] = false;
            for (int i = 1; i < options.size(); i++) {
                checked[i] = selectedLabels.contains(options.get(i).toString());
            }
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Labels")
                .setMultiChoiceItems(options.toArray(new CharSequence[0]), checked, (dialog, which, isChecked) -> {
                    if (which == 0) {
                        for (int i = 0; i < checked.length; i++) {
                            checked[i] = isChecked;
                        }
                    } else {
                        checked[which] = isChecked;
                        if (!isChecked) {
                            checked[0] = false;
                        } else {
                            boolean allSelected = true;
                            for (int i = 1; i < checked.length; i++) {
                                if (!checked[i]) {
                                    allSelected = false;
                                    break;
                                }
                            }
                            checked[0] = allSelected;
                        }
                    }
                })
                .setPositiveButton("Apply", (dialog, which) -> {
                    selectedLabels.clear();
                    boolean anySelected = false;
                    boolean allSelected = checked[0];
                    for (int i = 1; i < checked.length; i++) {
                        if (checked[i]) {
                            selectedLabels.add(options.get(i).toString());
                            anySelected = true;
                        }
                    }
                    if (!anySelected || allSelected) {
                        selectedLabels.clear();
                    }
                    updateLabelFilterButtonText();
                    refreshCalendarAfterAction();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateLabelFilterButtonText() {
        if (btnLabelFilter == null) {
            return;
        }
        if (selectedLabels.isEmpty()) {
            btnLabelFilter.setText("All labels");
        } else {
            btnLabelFilter.setText("Labels (" + selectedLabels.size() + ")");
        }
    }

    private void setupCalendar(String viewType) {
        List<Date> dates = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        cal.setTime(selectedDate.getTime());
        long startRange, endRange;
        int weekStartDay = SettingsRepository.resolveWeekStartDay(requireContext());

        updateWeekdayHeaders(weekStartDay);

        // Removed legacy view type checks. switchViewType now handles visibilities.
        
            GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 7);
            rvCalendarGrid.setLayoutManager(layoutManager);
            // Start 2 years ago, first of the month
            cal.add(Calendar.YEAR, -2);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            clearTime(cal);
            
            // Adjust back to the selected first day of that week
            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
            int startOffset = getStartOffset(dayOfWeek, weekStartDay);
            cal.add(Calendar.DAY_OF_YEAR, -startOffset);
            startRange = cal.getTimeInMillis();
            
            // End 3 years in the future, last of that month
            Calendar endCal = Calendar.getInstance();
            endCal.setTime(selectedDate.getTime());
            endCal.add(Calendar.YEAR, 3);
            endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH));
            clearTime(endCal);
            
            // Adjust forward to the selected week boundary
            int endDayOfWeek = endCal.get(Calendar.DAY_OF_WEEK);
            int endOffset = getEndOffset(endDayOfWeek, weekStartDay);
            endCal.add(Calendar.DAY_OF_YEAR, endOffset);

            // Generate everything in between
            while (!cal.after(endCal)) {
                dates.add(cal.getTime());
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
            endRange = endCal.getTimeInMillis();

            rvCalendarGrid.clearOnScrollListeners();
            rvCalendarGrid.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    int first = layoutManager.findFirstVisibleItemPosition();
                    int last = layoutManager.findLastVisibleItemPosition();
                    if (first != RecyclerView.NO_POSITION && last != RecyclerView.NO_POSITION) {
                        // Majority rule: take the month from the middle item
                        int mid = first + (last - first) / 2;
                        if (mid < dates.size()) {
                            updateTitleForDate(dates.get(mid));
                        }
                    }
                }
            });


        boolean showWeekNumbers = SettingsRepository.getInstance(requireContext()).getBoolean(SettingsRepository.KEY_SHOW_WEEK_NUMBERS, false);
        dayAdapter = new CalendarDayAdapter(dates, selectedDate.getTime(), new CalendarDayAdapter.OnDateClickListener() {
            @Override public void onDateClicked(Date date) {
                selectedDate.setTime(date);
                loadEventsForSelectedDate();
                dayAdapter.setSelectedDate(date);
            }
            @Override public void onDateDoubleClicked(Date date) { openEditor("event", null, date.getTime()); }
            @Override public void onDateLongPressed(Date date) { openEditor("event", null, date.getTime()); }
        }, showWeekNumbers, weekStartDay);
        rvCalendarGrid.setAdapter(dayAdapter);
        fetchDots(startRange, endRange);

        // Sync initial title
        updateTitleForDate(selectedDate.getTime());

        scrollToToday(dates, viewType);
    }

    private void updateWeekdayHeaders() {
        updateWeekdayHeaders(SettingsRepository.resolveWeekStartDay(requireContext()));
    }

    private void updateWeekdayHeaders(int weekStartDay) {
        if (layoutCalendarHeaders == null) {
            return;
        }

        String[] labels = buildWeekdayLabels(weekStartDay);
        for (int i = 0; i < layoutCalendarHeaders.getChildCount() && i < labels.length; i++) {
            View child = layoutCalendarHeaders.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setText(labels[i]);
            }
        }
    }

    private String[] buildWeekdayLabels(int weekStartDay) {
        String[] ordered = new String[]{"S", "M", "T", "W", "T", "F", "S"};
        int startIndex;
        if (weekStartDay == Calendar.MONDAY) {
            startIndex = 1;
        } else if (weekStartDay == Calendar.SATURDAY) {
            startIndex = 6;
        } else {
            startIndex = 0;
        }
        String[] labels = new String[7];
        for (int i = 0; i < 7; i++) {
            labels[i] = ordered[(startIndex + i) % 7];
        }
        return labels;
    }

    private void scrollToToday(List<Date> dates, String viewType) {
        Date target = selectedDate.getTime();
        for (int i = 0; i < dates.size(); i++) {
            if (dates.get(i) != null && isSameDay(dates.get(i), target)) {
                rvCalendarGrid.scrollToPosition(i);
                updateTitleForDate(dates.get(i));
                break;
            }
        }
    }

    private boolean isSameDay(Date d1, Date d2) {
        Calendar c1 = Calendar.getInstance(); c1.setTime(d1);
        Calendar c2 = Calendar.getInstance(); c2.setTime(d2);
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }

    private void clearTime(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    private int getStartOffset(int dayOfWeek, int weekStartDay) {
        return (dayOfWeek - weekStartDay + 7) % 7;
    }

    private int getEndOffset(int dayOfWeek, int weekStartDay) {
        int weekEndDay = weekStartDay == Calendar.SUNDAY ? Calendar.SATURDAY : weekStartDay - 1;
        return (weekEndDay - dayOfWeek + 7) % 7;
    }

    private void loadEventsForSelectedDate() {
        if (VIEW_AGENDA.equals(currentViewType)) {
            loadAgendaData();
        } else {
            tvSelectedDateLabel.setText(DateTimeFormatUtils.formatDate(requireContext(), selectedDate.getTimeInMillis()));
            loadItemsInRange(selectedDate.getTimeInMillis(), selectedDate.getTimeInMillis(), false);
        }
    }

    private void loadAgendaData() {
        tvSelectedDateLabel.setText("Agenda (from " + DateTimeFormatUtils.formatDate(requireContext(), selectedDate.getTimeInMillis()) + ")");
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(selectedDate.getTimeInMillis());
        long start = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_YEAR, 30);
        loadItemsInRange(start, cal.getTimeInMillis(), true);
    }

    private void loadItemsInRange(long startMillis, long endMillis, boolean isAgenda) {
        Calendar s = Calendar.getInstance(); s.setTimeInMillis(startMillis);
        s.set(Calendar.HOUR_OF_DAY, 0); s.set(Calendar.MINUTE, 0); s.set(Calendar.SECOND, 0);

        Calendar e = Calendar.getInstance(); e.setTimeInMillis(endMillis);
        e.set(Calendar.HOUR_OF_DAY, 23); e.set(Calendar.MINUTE, 59); e.set(Calendar.SECOND, 59);

        // For event expansion we look up to 3 years out
        Calendar windowEnd = Calendar.getInstance();
        windowEnd.add(Calendar.YEAR, 3);
        final long expandWindowEnd = windowEnd.getTimeInMillis();
        final long rangeStart = s.getTimeInMillis();
        final long rangeEnd   = e.getTimeInMillis();

        itemViewModel.getItemsForDateRange(0, expandWindowEnd).observe(getViewLifecycleOwner(), items -> {
            if (items == null) return;
            AstraDatabase.databaseWriteExecutor.execute(() -> {
                List<Item> renderable = buildRenderableItems(items, rangeStart, rangeEnd);
                List<Object> display = buildDisplayList(renderable, isAgenda, rangeStart);
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (isAdded()) {
                        eventAdapter.setItems(display);
                        if (isAgenda && rvEvents != null) {
                            rvEvents.scheduleLayoutAnimation();
                        }
                        
                        // Always keep the daily timeline representation fresh for the selected date
                        if (!isAgenda) {
                            renderTimelineEvents(renderable);
                        }
                    }
                });
            });
        });
    }

    private static class EventNode {
        Item item;
        View view;
        int col = 0;
        int maxCols = 1;
        EventNode(Item i) { item = i; }
    }

    private void renderTimelineEvents(List<Item> items) {
        if (layoutTimelineEvents == null || layoutAllDayEvents == null) return;
        
        layoutTimelineEvents.removeAllViews();
        layoutAllDayEvents.removeAllViews();
        
        tvTimelineDate.setText(DateTimeFormatUtils.formatDate(requireContext(), selectedDate.getTimeInMillis()));

        // Notify background view of the selected date so it can detect DST transitions
        com.astra.lifeorganizer.ui.views.TimelineBackgroundView bgView = root.findViewById(R.id.timeline_background);
        if (bgView != null) bgView.setDisplayedDate(selectedDate.getTimeInMillis());
        
        List<Item> timedItems = new ArrayList<>();
        List<Item> allDayItems = new ArrayList<>();
        
        for (Item item : items) {
            boolean isAllDay = item.allDay || (item.startAt != null && item.endAt != null && (item.endAt - item.startAt) >= 86400000L);
            if (isAllDay) {
                allDayItems.add(item);
            } else if (item.startAt != null && item.endAt != null && item.endAt > item.startAt) {
                timedItems.add(item);
            }
        }
        
        // All Day Rendering
        if (allDayItems.isEmpty()) {
            layoutAllDayEventsContainer.setVisibility(View.GONE);
        } else {
            layoutAllDayEventsContainer.setVisibility(View.VISIBLE);
            for (Item item : allDayItems) {
                View v = getLayoutInflater().inflate(R.layout.item_timeline_event, layoutAllDayEvents, false);
                TextView title = v.findViewById(R.id.tv_event_title);
                TextView time = v.findViewById(R.id.tv_event_time);
                androidx.cardview.widget.CardView card = (androidx.cardview.widget.CardView) v;
                
                title.setText(item.title);
                time.setText("All Day");
                
                int color = LabelUtils.resolveLabelColor(requireContext(), item.label);
                android.widget.LinearLayout bg = v.findViewById(R.id.layout_event_background);
                bg.setBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(color, 60));
                title.setTextColor(color);
                time.setTextColor(color);
                
                v.setOnClickListener(view -> openEditor(item.type, item.id, null));
                layoutAllDayEvents.addView(v);
            }
        }
        
        // Timed Rendering with Overlap Support
        float hourHeightPx = dpToPx(80);
        float topOffsetPx = dpToPx(40);
        
        timedItems.sort((a, b) -> Long.compare(a.startAt, b.startAt));
        
        List<EventNode> nodes = new ArrayList<>();
        for (Item i : timedItems) nodes.add(new EventNode(i));
        
        List<EventNode> group = new ArrayList<>();
        long groupEnd = 0;
        
        for (EventNode node : nodes) {
            if (group.isEmpty()) {
                group.add(node);
                groupEnd = node.item.endAt;
            } else if (node.item.startAt < groupEnd) {
                group.add(node);
                groupEnd = Math.max(groupEnd, node.item.endAt);
            } else {
                packTimelineGroup(group);
                group.clear();
                group.add(node);
                groupEnd = node.item.endAt;
            }
        }
        if (!group.isEmpty()) packTimelineGroup(group);
        
        boolean is24Hour = com.astra.lifeorganizer.data.repositories.SettingsRepository.is24HourTime(requireContext());
        SimpleDateFormat sdf = new SimpleDateFormat(is24Hour ? "H:mm" : "h:mm a", java.util.Locale.getDefault());
        
        Calendar startCal = Calendar.getInstance();
        Calendar endCal = Calendar.getInstance();
        
        for (EventNode node : nodes) {
            Item item = node.item;
            startCal.setTimeInMillis(item.startAt);
            endCal.setTimeInMillis(item.endAt);
            
            int startMins = startCal.get(Calendar.HOUR_OF_DAY) * 60 + startCal.get(Calendar.MINUTE);
            int endMins   = endCal.get(Calendar.HOUR_OF_DAY) * 60 + endCal.get(Calendar.MINUTE);
            if (endCal.get(Calendar.DAY_OF_YEAR) != startCal.get(Calendar.DAY_OF_YEAR) && endCal.after(startCal)) {
                // Compute actual minutes in the selected day (23 h on spring-forward, 25 h on fall-back)
                Calendar dayStart = Calendar.getInstance();
                dayStart.setTimeInMillis(selectedDate.getTimeInMillis());
                dayStart.set(Calendar.HOUR_OF_DAY, 0); dayStart.set(Calendar.MINUTE, 0);
                dayStart.set(Calendar.SECOND, 0);      dayStart.set(Calendar.MILLISECOND, 0);
                Calendar dayEnd = (Calendar) dayStart.clone();
                dayEnd.add(Calendar.DAY_OF_MONTH, 1); // rolls past DST transition correctly
                endMins = (int) ((dayEnd.getTimeInMillis() - dayStart.getTimeInMillis()) / 60000L);
            }
            
            int durationMins = endMins - startMins;
            if (durationMins < 30) durationMins = 30; // Min render height
            
            float eventGap = dpToPx(4); // Visual gap between consecutive events
            float topMargin = topOffsetPx + (startMins / 60f) * hourHeightPx + eventGap;
            float height = ((durationMins / 60f) * hourHeightPx) - dpToPx(2) - eventGap;
            
            View v = getLayoutInflater().inflate(R.layout.item_timeline_event, layoutTimelineEvents, false);
            node.view = v;
            TextView title = v.findViewById(R.id.tv_event_title);
            TextView time = v.findViewById(R.id.tv_event_time);
            androidx.cardview.widget.CardView card = (androidx.cardview.widget.CardView) v;
            
            title.setText(item.title);
            time.setText(sdf.format(startCal.getTime()) + " - " + sdf.format(endCal.getTime()));
            int color = LabelUtils.resolveLabelColor(requireContext(), item.label);
            android.widget.LinearLayout bg = v.findViewById(R.id.layout_event_background);
            bg.setBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(color, 60));
            title.setTextColor(color);
            time.setTextColor(androidx.core.graphics.ColorUtils.setAlphaComponent(color, 200));
            
            android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, (int) height);
            params.topMargin = (int) topMargin;
            v.setLayoutParams(params);
            v.setTag(item.id); // Tag for reliable selection reset
            
            v.setOnLongClickListener(view -> {
                // Enter selection mode and select this card
                if (!timelineSelectedIds.contains(item.id)) {
                    timelineSelectedIds.add(item.id);
                } else {
                    timelineSelectedIds.remove(item.id);
                }
                applyTimelineCardSelectionState(view, timelineSelectedIds.contains(item.id));
                updateTimelineSelectionToolbar();
                return true;
            });

            v.setOnClickListener(view -> {
                if (!timelineSelectedIds.isEmpty()) {
                    // We're in selection mode — toggle this card
                    boolean nowSelected;
                    if (timelineSelectedIds.contains(item.id)) {
                        timelineSelectedIds.remove(item.id);
                        nowSelected = false;
                    } else {
                        timelineSelectedIds.add(item.id);
                        nowSelected = true;
                    }
                    applyTimelineCardSelectionState(view, nowSelected);
                    updateTimelineSelectionToolbar();
                } else {
                    openEditor(item.type, item.id, null);
                }
            });
            layoutTimelineEvents.addView(v);

        }
        
        // Apply column widths after layout pass
        layoutTimelineEvents.post(() -> {
            int parentWidth = layoutTimelineEvents.getWidth();
            for (EventNode node : nodes) {
                if (node.view != null) {
                    int colGap = dpToPx(2); // Gap between overlapping columns
                    int totalGap = (node.maxCols > 1) ? colGap : 0;
                    int width = (parentWidth - totalGap * (node.maxCols - 1)) / node.maxCols;
                    int leftMargin = node.col * (width + totalGap);
                    android.widget.FrameLayout.LayoutParams p = (android.widget.FrameLayout.LayoutParams) node.view.getLayoutParams();
                    p.width = width;
                    p.leftMargin = leftMargin;
                    node.view.setLayoutParams(p);
                }
            }
            
            // Scroll to current time if viewing today
            long todayStart = Calendar.getInstance().getTimeInMillis();
            if (isSameDay(new java.util.Date(todayStart), selectedDate.getTime())) {
                androidx.core.widget.NestedScrollView scroll = root.findViewById(R.id.scroll_timeline);
                if (scroll != null) {
                    int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                    float scrollY = topOffsetPx + (currentHour * hourHeightPx) - dpToPx(80);
                    scroll.smoothScrollTo(0, Math.max(0, (int)scrollY));
                }
            }
        });
    }

    private void packTimelineGroup(List<EventNode> group) {
        List<List<EventNode>> columns = new ArrayList<>();
        for (EventNode node : group) {
            boolean placed = false;
            for (int i = 0; i < columns.size(); i++) {
                List<EventNode> col = columns.get(i);
                EventNode last = col.get(col.size() - 1);
                if (last.item.endAt <= node.item.startAt) {
                    col.add(node);
                    node.col = i;
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                List<EventNode> newCol = new ArrayList<>();
                newCol.add(node);
                node.col = columns.size();
                columns.add(newCol);
            }
        }
        for (EventNode node : group) {
            node.maxCols = columns.size();
        }
    }

    private void fetchDots(long start, long end) {
        Calendar windowEnd = Calendar.getInstance();
        windowEnd.add(Calendar.YEAR, 3);
        itemViewModel.getItemsForDateRange(0, windowEnd.getTimeInMillis()).observe(getViewLifecycleOwner(), items -> {
            if (items == null) return;
            android.content.Context appContext = requireContext().getApplicationContext();
            AstraDatabase.databaseWriteExecutor.execute(() -> {
                List<Item> renderable = buildRenderableItems(items, start, end);
                Map<String, List<Integer>> dotColors = buildDotColors(renderable, appContext);
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (isAdded() && dayAdapter != null) {
                        dayAdapter.setEventDotColors(dotColors);
                    }
                });
            });
        });
    }

    private void updateTitleForDate(Date date) {
        if (date == null || btnViewSelector == null) return;
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        String newTitle = sdf.format(date);
        
        if (VIEW_SCHEDULE.equals(currentViewType)) {
            btnViewSelector.setText(newTitle);
        } else {
            btnViewSelector.setText(currentViewType);
        }
        
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        if (dayAdapter != null && !isDraggingSheet) {
            rvCalendarGrid.post(() -> {
                if (dayAdapter != null && isAdded()) {
                    dayAdapter.setDominantMonth(cal.get(Calendar.MONTH), cal.get(Calendar.YEAR));
                }
            });
        }
    }

    private void openEditor(String type, Long id, Long prePopulatedDate) {
        AddTaskBottomSheetFragment fragment = AddTaskBottomSheetFragment.newInstance(type, id);
        if (prePopulatedDate != null) {
            Bundle args = fragment.getArguments();
            if (args != null) args.putLong("PRE_POP_DATE", prePopulatedDate);
        }
        fragment.show(getParentFragmentManager(), "EditItemCalendar");
    }

    private void showEventActionsMenu(Item item, View anchor) {
        if (item == null || anchor == null || !isAdded()) {
            return;
        }

        PopupMenu popupMenu = new PopupMenu(requireContext(), anchor);
        popupMenu.getMenu().add(Menu.NONE, 1, 0, "Share");
        popupMenu.getMenu().add(Menu.NONE, 2, 1, "Delete");
        if ("event".equalsIgnoreCase(item.type)) {
            if (isCanceled(item)) {
                popupMenu.getMenu().add(Menu.NONE, 3, 2, "Uncancel event");
            } else {
                popupMenu.getMenu().add(Menu.NONE, 3, 2, "Cancel event");
            }
            popupMenu.getMenu().add(Menu.NONE, 4, 3, "Reschedule");
            if (isRecurring(item)) {
                popupMenu.getMenu().add(Menu.NONE, 5, 4, "Finish recurring permanently");
            }
        } else if ("todo".equalsIgnoreCase(item.type) && isRecurring(item)) {
            popupMenu.getMenu().add(Menu.NONE, 6, 2, "Task completion history");
        }

        popupMenu.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == 1) {
                com.astra.lifeorganizer.utils.ShareUtils.shareItemsAsText(requireContext(), java.util.Collections.singletonList(item));
                return true;
            } else if (id == 7) {
                com.astra.lifeorganizer.utils.ShareUtils.shareItemsAsIcs(requireContext(), java.util.Collections.singletonList(item));
                return true;
            } else if (id == 2) {
                confirmDeleteItem(item);
                return true;
            } else if (id == 3) {
                if (isCanceled(item)) {
                    restoreEvent(item);
                } else {
                    cancelEventInstance(item);
                }
                return true;
            } else if (id == 4) {
                showRescheduleChoice(item);
                return true;
            } else if (id == 5) {
                confirmFinishRecurring(item);
                return true;
            } else if (id == 6) {
                showTaskCompletionHistory(item);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void shareItem(Item item) {
        if (!isAdded() || item == null) {
            return;
        }
        long time = getDisplayTime(item);
        StringBuilder body = new StringBuilder();
        body.append(item.title == null ? "" : item.title);
        if (item.allDay) {
            body.append("\n").append("Time: All day");
        } else if (time > 0) {
            body.append("\n").append("Time: ").append(formatDateTimeForShare(time));
        }
        if (item.description != null && !item.description.trim().isEmpty()) {
            body.append("\n\n").append(item.description.trim());
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, item.title);
        intent.putExtra(Intent.EXTRA_TEXT, body.toString());
        startActivity(Intent.createChooser(intent, "Share event"));
    }

    private void confirmDeleteItem(Item item) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete event")
                .setMessage("Delete this event and all of its future data?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    itemViewModel.delete(item);
                    refreshCalendarAfterAction();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void cancelEventInstance(Item item) {
        long scheduledTime = getDisplayTime(item);
        if (!isRecurring(item) || scheduledTime <= 0) {
            Item copy = item;
            copy.status = "canceled";
            itemViewModel.update(copy);
            AlarmScheduler.cancelForItem(requireContext(), copy.id);
            refreshCalendarAfterAction();
            return;
        }

        final android.content.Context appContext = requireContext().getApplicationContext();
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            List<ItemOccurrence> occurrences = itemViewModel.getOccurrencesForItemSync(item.id);
            ItemOccurrence occurrence = findOccurrence(occurrences, scheduledTime);
            if (occurrence == null) {
                occurrence = new ItemOccurrence();
                occurrence.itemId = item.id;
                occurrence.scheduledFor = scheduledTime;
            }
            occurrence.status = "canceled";
            occurrence.overrideStartAt = null;
            occurrence.overrideEndAt = null;
            saveOccurrence(occurrence);
            if (scheduledTime == AlarmScheduler.getAlarmTime(item) || scheduledTime == AlarmScheduler.getScheduledTime(item)) {
                AlarmScheduler.cancelForItem(appContext, item.id);
            }
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    if (root != null) {
                        root.postDelayed(this::refreshCalendarAfterAction, 250);
                    } else {
                        refreshCalendarAfterAction();
                    }
                });
            }
        });
    }

    private void restoreEvent(Item item) {
        if (item == null) {
            return;
        }

        if (!isRecurring(item)) {
            item.status = "pending";
            itemViewModel.update(item);
            AlarmScheduler.scheduleForItem(requireContext(), item);
            refreshCalendarAfterAction();
            return;
        }

        long scheduledTime = getDisplayTime(item);
        if (scheduledTime <= 0) {
            item.status = "pending";
            itemViewModel.update(item);
            refreshCalendarAfterAction();
            return;
        }

        final android.content.Context appContext = requireContext().getApplicationContext();
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            List<ItemOccurrence> occurrences = itemViewModel.getOccurrencesForItemSync(item.id);
            ItemOccurrence occurrence = findOccurrence(occurrences, scheduledTime);
            if (occurrence != null) {
                if (occurrence.id > 0) {
                    // No explicit delete API exists for occurrences yet, so mark it as active again.
                    occurrence.status = "pending";
                    saveOccurrence(occurrence);
                }
            }
            if (scheduledTime == AlarmScheduler.getAlarmTime(item) || scheduledTime == AlarmScheduler.getScheduledTime(item)) {
                AlarmScheduler.scheduleForItem(appContext, item);
            }
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    if (root != null) {
                        root.postDelayed(this::refreshCalendarAfterAction, 250);
                    } else {
                        refreshCalendarAfterAction();
                    }
                });
            }
        });
    }

    private void showRescheduleChoice(Item item) {
        if (!isAdded() || item == null) {
            return;
        }

        if (!isRecurring(item)) {
            openEditor(item.type, item.id, getDisplayTime(item));
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Reschedule event")
                .setItems(new CharSequence[]{"All future events", "Just this instance"}, (dialog, which) -> {
                    if (which == 0) {
                        openEditor(item.type, item.id, getDisplayTime(item));
                    } else {
                        promptRescheduleSingleInstance(item);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptRescheduleSingleInstance(Item item) {
        long baseTime = getDisplayTime(item);
        if (baseTime <= 0) {
            Toast.makeText(requireContext(), "This event does not have a schedulable time.", Toast.LENGTH_SHORT).show();
            return;
        }

        Calendar initial = Calendar.getInstance();
        initial.setTimeInMillis(baseTime);
        new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
            Calendar date = Calendar.getInstance();
            date.set(Calendar.YEAR, year);
            date.set(Calendar.MONTH, month);
            date.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            MaterialTimePicker picker = new MaterialTimePicker.Builder()
                    .setTimeFormat(SettingsRepository.getInstance(requireContext()).is24HourTime(requireContext())
                            ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                    .setInputMode(MaterialTimePicker.INPUT_MODE_KEYBOARD)
                    .setHour(initial.get(Calendar.HOUR_OF_DAY))
                    .setMinute(initial.get(Calendar.MINUTE))
                    .setTitleText("Set time")
                    .build();
            picker.addOnPositiveButtonClickListener(dialog -> {
                Calendar picked = Calendar.getInstance();
                picked.setTimeInMillis(date.getTimeInMillis());
                picked.set(Calendar.HOUR_OF_DAY, picker.getHour());
                picked.set(Calendar.MINUTE, picker.getMinute());
                picked.set(Calendar.SECOND, 0);
                picked.set(Calendar.MILLISECOND, 0);
                applyOccurrenceReschedule(item, baseTime, picked.getTimeInMillis());
            });
            picker.show(getParentFragmentManager(), "calendar_reschedule_time");
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void applyOccurrenceReschedule(Item item, long originalTime, long newTime) {
        final android.content.Context appContext = requireContext().getApplicationContext();
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            List<ItemOccurrence> occurrences = itemViewModel.getOccurrencesForItemSync(item.id);
            ItemOccurrence occurrence = findOccurrence(occurrences, originalTime);
            if (occurrence == null) {
                occurrence = new ItemOccurrence();
                occurrence.itemId = item.id;
                occurrence.scheduledFor = originalTime;
            }
            occurrence.status = "rescheduled";
            occurrence.overrideStartAt = newTime;
            if (item.startAt != null && item.endAt != null && item.endAt > item.startAt) {
                occurrence.overrideEndAt = newTime + (item.endAt - item.startAt);
            } else {
                occurrence.overrideEndAt = null;
            }
            saveOccurrence(occurrence);
            if (originalTime == AlarmScheduler.getAlarmTime(item) || originalTime == AlarmScheduler.getScheduledTime(item)) {
                AlarmScheduler.cancelForItem(appContext, item.id);
            }
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    if (root != null) {
                        root.postDelayed(this::refreshCalendarAfterAction, 250);
                    } else {
                        refreshCalendarAfterAction();
                    }
                });
            }
        });
    }

    private void confirmFinishRecurring(Item item) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Finish recurring event")
                .setMessage("Stop this recurrence permanently?")
                .setPositiveButton("Finish", (dialog, which) -> {
                    item.recurrenceRule = null;
                    itemViewModel.update(item);
                    itemViewModel.deleteOccurrencesForItem(item.id);
                    if (root != null) {
                        root.postDelayed(this::refreshCalendarAfterAction, 250);
                    } else {
                        refreshCalendarAfterAction();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showTaskCompletionHistory(Item item) {
        if (!isAdded() || item == null) {
            return;
        }

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dpToPx(16);
        container.setPadding(padding, padding, padding, padding);

        TextView emptyState = new TextView(requireContext());
        emptyState.setText("No completion history yet.");
        emptyState.setPadding(0, 0, 0, dpToPx(12));
        emptyState.setVisibility(View.GONE);

        RecyclerView historyRecycler = new RecyclerView(requireContext());
        historyRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        TaskHistoryAdapter historyAdapter = new TaskHistoryAdapter(null);
        historyRecycler.setAdapter(historyAdapter);

        container.addView(emptyState);
        container.addView(historyRecycler, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(320)));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle((item.title != null && !item.title.trim().isEmpty() ? item.title.trim() : "Task") + " history")
                .setView(container)
                .setPositiveButton("Close", null)
                .create();

        itemViewModel.getHistoryForItemLive(item.id).observe(getViewLifecycleOwner(), histories -> {
            List<Object> display = buildHistoryDisplayItems(item, histories);
            historyAdapter.setItems(display);
            boolean hasEntries = !display.isEmpty();
            emptyState.setVisibility(hasEntries ? View.GONE : View.VISIBLE);
            historyRecycler.setVisibility(hasEntries ? View.VISIBLE : View.GONE);
        });

        dialog.show();
    }

    private void refreshCalendarAfterAction() {
        if (!isAdded()) {
            return;
        }
        if ("Agenda".equals(currentViewType)) {
            loadAllEventsAgenda();
        } else {
            setupCalendar(currentViewType);
            loadEventsForSelectedDate();
        }
    }

    private boolean isRecurring(Item item) {
        return item != null && item.recurrenceRule != null && !item.recurrenceRule.trim().isEmpty() && !"NONE".equalsIgnoreCase(item.recurrenceRule);
    }

    private boolean isCanceled(Item item) {
        return item != null && item.status != null && "canceled".equalsIgnoreCase(item.status);
    }

    private long getDisplayTime(Item item) {
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

    private String formatDateTimeForShare(long timestamp) {
        SimpleDateFormat format = new SimpleDateFormat("EEE, MMM d - h:mm a", Locale.getDefault());
        return format.format(new Date(timestamp));
    }

    private ItemOccurrence findOccurrence(List<ItemOccurrence> occurrences, long scheduledFor) {
        if (occurrences == null) {
            return null;
        }
        for (ItemOccurrence occurrence : occurrences) {
            if (occurrence != null && occurrence.scheduledFor == scheduledFor) {
                return occurrence;
            }
        }
        return null;
    }

    private List<Object> buildHistoryDisplayItems(Item item, List<CompletionHistory> histories) {
        List<Object> display = new ArrayList<>();
        if (item == null || histories == null || histories.isEmpty()) {
            return display;
        }

        List<TaskHistoryAdapter.HistoryEntry> entries = new ArrayList<>();
        for (CompletionHistory history : histories) {
            if (history == null || history.timestamp <= 0 || !"done".equalsIgnoreCase(history.action)) {
                continue;
            }
            entries.add(new TaskHistoryAdapter.HistoryEntry(item, history));
        }

        entries.sort((a, b) -> Long.compare(b.history.timestamp, a.history.timestamp));

        String currentHeader = null;
        SimpleDateFormat headerFormat = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault());
        for (TaskHistoryAdapter.HistoryEntry entry : entries) {
            String header = headerFormat.format(new Date(entry.history.timestamp));
            if (!header.equals(currentHeader)) {
                display.add(header);
                currentHeader = header;
            }
            display.add(entry);
        }
        return display;
    }

    private List<Item> buildRenderableItems(List<Item> sourceItems, long rangeStart, long rangeEnd) {
        List<Item> renderable = new ArrayList<>();
        if (sourceItems == null || sourceItems.isEmpty()) {
            return renderable;
        }

        Map<Long, List<ItemOccurrence>> occurrenceMap = new HashMap<>();
        List<ItemOccurrence> allOccurrences = itemViewModel.getAllOccurrencesSync();
        if (allOccurrences != null) {
            for (ItemOccurrence occurrence : allOccurrences) {
                if (occurrence == null) {
                    continue;
                }
                List<ItemOccurrence> bucket = occurrenceMap.get(occurrence.itemId);
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    occurrenceMap.put(occurrence.itemId, bucket);
                }
                bucket.add(occurrence);
            }
        }

        Set<String> seen = new HashSet<>();
        for (Item item : sourceItems) {
            if (item == null || item.status != null && "archived".equalsIgnoreCase(item.status)) {
                continue;
            }
            if (!matchesLabelFilter(item)) {
                continue;
            }

            List<Item> candidates;
            if (isRecurring(item)) {
                candidates = RecurrenceRuleParser.expandOccurrences(item, rangeStart, rangeEnd);
            } else {
                candidates = new ArrayList<>();
                candidates.add(item);
            }

            if (!isRecurring(item) && getDisplayTime(item) == 0L) {
                continue;
            }

            // Filtering based on visibility settings
            SettingsRepository settings = SettingsRepository.getInstance(requireContext());
            if ("todo".equals(item.type)) {
                boolean showTasks = settings.getBoolean(SettingsRepository.KEY_CALENDAR_SHOW_TASKS, false);
                boolean hasTimeAndDuration = item.startAt != null && item.endAt != null && item.endAt > item.startAt;
                if (!showTasks || !hasTimeAndDuration) {
                    continue;
                }
            } else if ("habit".equals(item.type)) {
                boolean showHabits = settings.getBoolean(SettingsRepository.KEY_CALENDAR_SHOW_HABITS, false);
                boolean hasTimeAndDuration = item.startAt != null && item.endAt != null && item.endAt > item.startAt;
                if (!showHabits || !hasTimeAndDuration) {
                    continue;
                }
            }

            List<ItemOccurrence> itemOccurrences = occurrenceMap.get(item.id);
            for (Item candidate : candidates) {
                long candidateTime = getDisplayTime(candidate);
                if (candidateTime <= 0L) {
                    continue;
                }
                long candidateEnd = (candidate.endAt != null && candidate.endAt > candidateTime)
                        ? candidate.endAt
                        : candidateTime + 1L;

                ItemOccurrence occurrence = findOccurrence(itemOccurrences, candidateTime);
                if (occurrence != null) {
                    if ("canceled".equalsIgnoreCase(occurrence.status)) {
                        candidate.status = "canceled";
                    } else {
                        applyOccurrenceOverride(candidate, occurrence);
                        if ("rescheduled".equalsIgnoreCase(occurrence.status)) {
                            candidate.status = "pending";
                        }
                    }
                    candidateTime = getDisplayTime(candidate);
                    candidateEnd = (candidate.endAt != null && candidate.endAt > candidateTime)
                            ? candidate.endAt
                            : candidateTime + 1L;
                }

                if (candidateEnd <= rangeStart || candidateTime > rangeEnd) {
                    continue;
                }

                String key = candidate.id + ":" + candidateTime + ":" + (candidate.endAt != null ? candidate.endAt : 0L);
                if (seen.add(key)) {
                    renderable.add(candidate);
                }
            }

            if (itemOccurrences != null) {
                for (ItemOccurrence occurrence : itemOccurrences) {
                    if (occurrence == null || occurrence.overrideStartAt == null) {
                        continue;
                    }
                    if (occurrence.overrideStartAt < rangeStart || occurrence.overrideStartAt > rangeEnd) {
                        continue;
                    }
                    Item overridden = copyItem(item);
                    applyOccurrenceOverride(overridden, occurrence);
                    if (occurrence.overrideStartAt == 0L) {
                        continue;
                    }
                    long overriddenStart = getDisplayTime(overridden);
                    long overriddenEnd = (overridden.endAt != null && overridden.endAt > overriddenStart)
                            ? overridden.endAt
                            : overriddenStart + 1L;
                    if (overriddenEnd <= rangeStart || overriddenStart > rangeEnd) {
                        continue;
                    }
                    String key = overridden.id + ":" + getDisplayTime(overridden) + ":" + (overridden.endAt != null ? overridden.endAt : 0L);
                    if (seen.add(key)) {
                        renderable.add(overridden);
                    }
                }
            }
        }

        renderable.sort((a, b) -> Long.compare(getDisplayTime(a), getDisplayTime(b)));
        return renderable;
    }

    private boolean matchesLabelFilter(Item item) {
        if (selectedLabels.isEmpty()) {
            return true;
        }
        String label = LabelUtils.normalizeLabel(item != null ? item.label : null);
        return label != null && selectedLabels.contains(label);
    }

    private void applyOccurrenceOverride(Item item, ItemOccurrence occurrence) {
        if (item == null || occurrence == null || occurrence.overrideStartAt == null) {
            return;
        }

        if (item.startAt != null) {
            long duration = (item.endAt != null && item.endAt > item.startAt) ? (item.endAt - item.startAt) : 0L;
            item.startAt = occurrence.overrideStartAt;
            item.endAt = (occurrence.overrideEndAt != null) ? occurrence.overrideEndAt : (duration > 0 ? occurrence.overrideStartAt + duration : null);
        } else {
            item.dueAt = occurrence.overrideStartAt;
        }
    }

    private void saveOccurrence(ItemOccurrence occurrence) {
        if (occurrence == null) {
            return;
        }
        if (occurrence.id > 0) {
            itemViewModel.updateOccurrence(occurrence);
        } else {
            itemViewModel.insertOccurrence(occurrence);
        }
    }

    private Item copyItem(Item src) {
        if (src == null) {
            return null;
        }
        Item copy = new Item();
        copy.id = src.id;
        copy.title = src.title;
        copy.description = src.description;
        copy.type = src.type;
        copy.startAt = src.startAt;
        copy.endAt = src.endAt;
        copy.dueAt = src.dueAt;
        copy.allDay = src.allDay;
        copy.reminderAt = src.reminderAt;
        copy.calendarId = src.calendarId;
        copy.priority = src.priority;
        copy.status = src.status;
        copy.recurrenceRule = src.recurrenceRule;
        copy.createdAt = src.createdAt;
        copy.streakEnabled = src.streakEnabled;
        copy.label = src.label;
        copy.frequency = src.frequency;
        copy.isPositive = src.isPositive;
        copy.projectId = src.projectId;
        return copy;
    }

    private List<Object> buildDisplayList(List<Item> items, boolean groupByDate, long referenceStart) {
        List<Object> display = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return display;
        }

        if (!groupByDate) {
            display.addAll(items);
            return display;
        }

        String currentHeader = "";
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault());
        long now = System.currentTimeMillis();
        for (Item item : items) {
            long itemStartTime = getDisplayTime(item);
            long itemEndTime = (item.endAt != null) ? item.endAt : itemStartTime;
            if (itemStartTime == 0 || itemEndTime < now) {
                continue;
            }

            long headerTime = itemStartTime < referenceStart ? referenceStart : itemStartTime;
            String dateHeader = sdf.format(new Date(headerTime));
            if (!dateHeader.equals(currentHeader)) {
                display.add(dateHeader);
                currentHeader = dateHeader;
            }
            display.add(item);
        }
        return display;
    }

    private Map<String, List<Integer>> buildDotColors(List<Item> items, android.content.Context context) {
        Map<String, java.util.LinkedHashSet<Integer>> grouped = new HashMap<>();
        SettingsRepository settings = SettingsRepository.getInstance(context);
        boolean showTasks = settings.getBoolean(SettingsRepository.KEY_CALENDAR_SHOW_TASKS, false);
        boolean showHabits = settings.getBoolean(SettingsRepository.KEY_CALENDAR_SHOW_HABITS, false);

        for (Item item : items) {
            String type = item.type;
            boolean hasTimeAndDuration = item.startAt != null && item.endAt != null && item.endAt > item.startAt;

            if ("todo".equals(type)) {
                if (!showTasks || !hasTimeAndDuration) continue;
            } else if ("habit".equals(type)) {
                if (!showHabits || !hasTimeAndDuration) continue;
            }

            long startTime = getDisplayTime(item);
            long endTime = (item.endAt != null && item.endAt > 0L) ? item.endAt : startTime;
            if (startTime <= 0) {
                continue;
            }
            int labelColor = LabelUtils.resolveLabelColor(context, item.label);

            Calendar day = Calendar.getInstance();
            day.setTimeInMillis(startTime);
            normalizeToDayStart(day);
            addDotColor(grouped, day, labelColor);

            if (endTime > startTime) {
                Calendar cursor = (Calendar) day.clone();
                cursor.add(Calendar.DAY_OF_YEAR, 1);
                normalizeToDayStart(cursor);
                while (cursor.getTimeInMillis() < endTime) {
                    addDotColor(grouped, cursor, labelColor);
                    cursor.add(Calendar.DAY_OF_YEAR, 1);
                    normalizeToDayStart(cursor);
                }
            }
        }
        Map<String, List<Integer>> colors = new HashMap<>();
        for (Map.Entry<String, java.util.LinkedHashSet<Integer>> entry : grouped.entrySet()) {
            colors.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return colors;
    }

    private void addDotColor(Map<String, java.util.LinkedHashSet<Integer>> grouped, Calendar calendar, int color) {
        if (grouped == null || calendar == null) {
            return;
        }
        String key = calendar.get(Calendar.YEAR) + "-" + calendar.get(Calendar.DAY_OF_YEAR);
        java.util.LinkedHashSet<Integer> colors = grouped.get(key);
        if (colors == null) {
            colors = new java.util.LinkedHashSet<>();
            grouped.put(key, colors);
        }
        colors.add(color);
    }

    private void normalizeToDayStart(Calendar calendar) {
        if (calendar == null) {
            return;
        }
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }
}
