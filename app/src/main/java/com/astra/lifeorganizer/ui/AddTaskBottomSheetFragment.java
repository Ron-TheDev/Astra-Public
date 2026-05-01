package com.astra.lifeorganizer.ui;

import android.app.DatePickerDialog;
import android.app.AlarmManager;
import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.lifecycle.ViewModelProvider;
import com.astra.lifeorganizer.R;
import com.astra.lifeorganizer.data.entities.Subtask;
import com.astra.lifeorganizer.data.repositories.SettingsRepository;

import com.astra.lifeorganizer.utils.DateTimeFormatUtils;
import com.astra.lifeorganizer.utils.LabelUtils;
import com.astra.lifeorganizer.utils.RecurrenceRuleParser;
import com.astra.lifeorganizer.utils.BottomSheetUtils;
import com.astra.lifeorganizer.data.entities.Item;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import java.util.ArrayList;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

public class AddTaskBottomSheetFragment extends BottomSheetDialogFragment {

    private String itemType = "todo";
    private Long editingItemId = null;
    private Item existingItem = null;
    private boolean isDataLoaded = false;
    private boolean waitingForExactAlarmPermission = false;
    private boolean bypassEventConflictWarning = false;
    
    private EditText etTitle, etNotes, etDailyTarget, etHabitDuration, etDurationHours, etDurationMinutes;
    private TextView tvDueDateLabel, tvStartTimeLabel, tvEndDateLabel, tvDialogTitle, tvRepeatSummary;
    private Chip chipLabelPreview;
    private Button btnAddLabel;
    private MaterialAutoCompleteTextView spinnerPriority, spinnerHabitType, spinnerReminder, spinnerDuration;
    private String currentRecurrenceRule = null;
    private String selectedLabel = null;
    private MaterialSwitch switchLastForever;
    private MaterialSwitch switchAllDay;
    private Button btnClearDate, btnClearStartTime;
    private View layoutTaskEventBlock, layoutHabitDetails, layoutSubtasksContainer;
    private LinearLayout layoutSubtasks, layoutDurationPicker, layoutCustomDuration, layoutHabitDuration, layoutStartTimeRow, layoutEndDateRow;
    private View habitFrequencyRow;
    private Button btnAddSubtask, btnPickEndDate;
    
    private ItemViewModel itemViewModel;
    private Long selectedDueDate = null;
    private Long selectedStartTime = null;
    private Long selectedEndDate = null;
    private boolean isAllDayEvent = false;
    private final List<SubtaskRow> subtaskRows = new ArrayList<>();
    private final List<Subtask> loadedSubtasks = new ArrayList<>();
    private ActivityResultLauncher<Intent> exactAlarmPermissionLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    private static final String ARG_TYPE = "ARG_TYPE";
    private static final String ARG_ITEM_ID = "ARG_ITEM_ID";
    private static final String[] TASK_DURATION_OPTIONS = new String[]{"15 min", "30 min", "1 hour", "Custom"};

    public static AddTaskBottomSheetFragment newInstance(String type, Long itemId) {
        AddTaskBottomSheetFragment fragment = new AddTaskBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type);
        if (itemId != null) args.putLong(ARG_ITEM_ID, itemId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        exactAlarmPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    waitingForExactAlarmPermission = false;
                    if (hasExactAlarmAccess()) {
                        saveItemInternal();
                    } else {
                        Toast.makeText(getContext(), "Exact alarm access is required for high priority items.", Toast.LENGTH_SHORT).show();
                    }
                }
        );
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        saveItem();
                    } else {
                        Toast.makeText(getContext(), "Notification permission is required for alarms to appear.", Toast.LENGTH_SHORT).show();
                    }
                }
        );
        if (getArguments() != null) {
            itemType = getArguments().getString(ARG_TYPE, "todo");
            if (getArguments().containsKey(ARG_ITEM_ID)) {
                editingItemId = getArguments().getLong(ARG_ITEM_ID);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_add_task_bottom_sheet, container, false);
        itemViewModel = new ViewModelProvider(requireActivity()).get(ItemViewModel.class);

        initViews(root);
        setupTypeVisibility();

        if (editingItemId != null) {
            loadItemData();
        } else {
            // Default to today for habits if not already set
            if (itemType.equals("habit") && selectedDueDate == null) {
                Calendar cal = Calendar.getInstance();
                normalizeToStartOfDay(cal);
                selectedDueDate = cal.getTimeInMillis();
                if (tvDueDateLabel != null) {
                    tvDueDateLabel.setText(formatDate(selectedDueDate));
                    btnClearDate.setVisibility(View.VISIBLE);
                }
            }
            tvDialogTitle.setText("New " + itemType);
            if (getArguments() != null && getArguments().containsKey("PRE_POP_DATE")) {
                long prePop = getArguments().getLong("PRE_POP_DATE");
                selectedDueDate = prePop;
                tvDueDateLabel.setText(formatDate(prePop));
                btnClearDate.setVisibility(View.VISIBLE);
                
                if (itemType.equals("event")) {
                    selectedStartTime = prePop;
                    tvStartTimeLabel.setText("Start: " + formatTime(prePop));
                    btnClearStartTime.setVisibility(View.VISIBLE);
                    layoutDurationPicker.setVisibility(View.VISIBLE);
                }
            }
        }

        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        BottomSheetUtils.expandToViewport(getDialog());
    }

    private void initViews(View root) {
        tvDialogTitle = root.findViewById(R.id.tv_dialog_title);
        etTitle = root.findViewById(R.id.et_title);
        etNotes = root.findViewById(R.id.et_notes);
        etDailyTarget = root.findViewById(R.id.et_daily_target);
        etHabitDuration = root.findViewById(R.id.et_habit_duration);
        etDurationHours = root.findViewById(R.id.et_duration_hours);
        etDurationMinutes = root.findViewById(R.id.et_duration_minutes);
        chipLabelPreview = root.findViewById(R.id.chip_label_preview);
        btnAddLabel = root.findViewById(R.id.btn_add_label);
        
        tvDueDateLabel = root.findViewById(R.id.tv_due_date_label);
        tvStartTimeLabel = root.findViewById(R.id.tv_start_time_label);
        tvEndDateLabel = root.findViewById(R.id.tv_end_date_label);
        
        spinnerPriority = root.findViewById(R.id.spinner_priority);
        spinnerHabitType = root.findViewById(R.id.spinner_habit_type);
        spinnerReminder = root.findViewById(R.id.spinner_reminder);
        spinnerDuration = root.findViewById(R.id.spinner_duration);
        tvRepeatSummary = root.findViewById(R.id.tv_repeat_summary);
        switchAllDay = root.findViewById(R.id.switch_all_day);

        setupDropdownAdapters();
        spinnerDuration.setOnItemClickListener((parent, view, position, id) -> updateDurationUi());

        if (editingItemId == null) {
            SettingsRepository settingsRepository = SettingsRepository.getInstance(requireContext());
            setDropdownSelection(spinnerPriority, getResources().getStringArray(R.array.priority_options),
                    settingsRepository.getInt(SettingsRepository.KEY_DEFAULT_PRIORITY, 2));
            setDropdownSelection(spinnerReminder, getResources().getStringArray(R.array.reminder_options),
                    settingsRepository.getInt(SettingsRepository.KEY_DEFAULT_REMINDER, 0));
            setDropdownSelection(spinnerDuration, TASK_DURATION_OPTIONS,
                    settingsRepository.getInt(SettingsRepository.KEY_DEFAULT_DURATION, 2));

            // Default recurrence from settings
            int defaultRecIndex = settingsRepository.getInt(SettingsRepository.KEY_DEFAULT_RECURRENCE, 1);
            if (defaultRecIndex > 0) {
                // Determine the rule string based on index
                String rule = "FREQ=DAILY";
                if (defaultRecIndex == 2) rule = "FREQ=WEEKLY";
                else if (defaultRecIndex == 3) rule = "FREQ=MONTHLY";
                
                // Only apply if it's a habit or if the user turns on repeating later (for tasks)
                // But for a new habit, we should set it NOW.
                if (itemType.equals("habit")) {
                    currentRecurrenceRule = rule;
                    tvRepeatSummary.setText(RecurrenceRuleParser.getHumanReadableSummary(rule));
                }
            } else {
                tvRepeatSummary.setText("None");
            }
        }

        root.findViewById(R.id.layout_repeat).setOnClickListener(v -> {
            Vibrate(v);
            RecurrencePickerBottomSheet picker = RecurrencePickerBottomSheet.newInstance(
                    currentRecurrenceRule != null ? currentRecurrenceRule : "NONE");
            picker.setOnRecurrenceSetListener(rrule -> {
                currentRecurrenceRule = rrule.equals("NONE") ? null : rrule;
                tvRepeatSummary.setText(RecurrenceRuleParser.getHumanReadableSummary(rrule));
                evaluateBestType();
            });
            picker.show(getChildFragmentManager(), "recurrence_picker");
        });

        btnAddLabel.setOnClickListener(v -> {
            Vibrate(v);
            showLabelPicker();
        });

        chipLabelPreview.setOnClickListener(v -> {
            Vibrate(v);
            showLabelPicker();
        });
        chipLabelPreview.setOnCloseIconClickListener(v -> {
            Vibrate(v);
            selectedLabel = null;
            updateLabelUi();
        });
        
        switchLastForever = root.findViewById(R.id.switch_last_forever);
        
        layoutTaskEventBlock = root.findViewById(R.id.layout_task_event_block);
        layoutHabitDetails = root.findViewById(R.id.layout_habit_details);
        layoutSubtasksContainer = root.findViewById(R.id.layout_subtasks_container);
        layoutSubtasks = root.findViewById(R.id.layout_subtasks);
        layoutDurationPicker = root.findViewById(R.id.layout_duration_picker);
        layoutCustomDuration = root.findViewById(R.id.layout_custom_duration);
        layoutHabitDuration = root.findViewById(R.id.layout_habit_duration);
        layoutStartTimeRow = root.findViewById(R.id.layout_start_time_row);
        layoutEndDateRow = root.findViewById(R.id.layout_end_date_row);
        btnAddSubtask = root.findViewById(R.id.btn_add_subtask);
        btnPickEndDate = root.findViewById(R.id.btn_pick_end_date);

        btnClearDate = root.findViewById(R.id.btn_clear_date);
        btnClearStartTime = root.findViewById(R.id.btn_clear_start_time);

        // Limit minutes to 59
        etDurationMinutes.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() > 0) {
                    try {
                        int min = Integer.parseInt(s.toString());
                        if (min > 59) {
                            etDurationMinutes.setText("59");
                            etDurationMinutes.setSelection(2);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        });

        switchLastForever.setOnCheckedChangeListener((btn, isChecked) -> {
            Vibrate(btn);
            updateHabitRepeatUi();
        });

        switchAllDay.setOnCheckedChangeListener((btn, isChecked) -> {
            Vibrate(btn);
            isAllDayEvent = isChecked;
            updateAllDayUi();
            evaluateBestType();
        });

        root.findViewById(R.id.btn_pick_date).setOnClickListener(v -> {
            Vibrate(v);
            showDatePicker(date -> {
                selectedDueDate = date;
                tvDueDateLabel.setText(formatDate(date));
                if (isAllDayEvent && selectedEndDate == null) {
                    selectedEndDate = date;
                    tvEndDateLabel.setText(formatDate(date));
                }
                btnClearDate.setVisibility(View.VISIBLE);
            });
        });
        
        btnPickEndDate.setOnClickListener(v -> {
            Vibrate(v);
            showDatePicker(date -> {
                if (selectedDueDate != null && date < startOfDay(selectedDueDate)) {
                    Toast.makeText(getContext(), "End date cannot be before start date.", Toast.LENGTH_SHORT).show();
                    return;
                }
                selectedEndDate = date;
                tvEndDateLabel.setText(formatDate(date));
            });
        });
        
        btnClearDate.setOnClickListener(v -> {
            Vibrate(v);
            selectedDueDate = null;
            tvDueDateLabel.setText("No Due Date");
            btnClearDate.setVisibility(View.GONE);
            
            clearStartTime();
            if (switchAllDay != null && switchAllDay.isChecked()) {
                switchAllDay.setChecked(false);
            }
            evaluateBestType();
        });

        root.findViewById(R.id.btn_pick_start_time).setOnClickListener(v -> {
            Vibrate(v);
            if (isAllDayEvent) {
                return;
            }
            if (selectedDueDate == null) {
                Toast.makeText(getContext(), "Please pick a Due Date first.", Toast.LENGTH_SHORT).show();
                return;
            }
            showTimePicker(time -> {
                selectedStartTime = time;
                tvStartTimeLabel.setText("Start: " + formatTime(time));
                btnClearStartTime.setVisibility(View.VISIBLE);
                layoutDurationPicker.setVisibility(View.VISIBLE);
                updateDurationUi();
                evaluateBestType();
            });
        });

        btnClearStartTime.setOnClickListener(v -> {
            Vibrate(v);
            clearStartTime();
            evaluateBestType();
        });

        btnAddSubtask.setOnClickListener(v -> {
            Vibrate(v);
            addSubtaskRow(null);
            evaluateBestType();
        });

        root.findViewById(R.id.btn_save_item).setOnClickListener(v -> {
            Vibrate(v);
            saveItem();
        });

        updateLabelUi();
        updateHabitRepeatUi();
    }

    private void clearStartTime() {
        selectedStartTime = null;
        tvStartTimeLabel.setText("No Start Time (Task)");
        btnClearStartTime.setVisibility(View.GONE);
        layoutDurationPicker.setVisibility(View.GONE);
        layoutCustomDuration.setVisibility(View.GONE);
    }

    private void setupDropdownAdapters() {
        ArrayAdapter<CharSequence> priorityAdapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.priority_options,
                android.R.layout.simple_dropdown_item_1line
        );
        spinnerPriority.setAdapter(priorityAdapter);

        ArrayAdapter<CharSequence> habitTypeAdapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.habit_type_options,
                android.R.layout.simple_dropdown_item_1line
        );
        spinnerHabitType.setAdapter(habitTypeAdapter);

        ArrayAdapter<CharSequence> reminderAdapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.reminder_options,
                android.R.layout.simple_dropdown_item_1line
        );
        spinnerReminder.setAdapter(reminderAdapter);

        ArrayAdapter<String> durationAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                TASK_DURATION_OPTIONS
        );
        spinnerDuration.setAdapter(durationAdapter);
    }

    private void setDropdownSelection(MaterialAutoCompleteTextView dropdown, String[] options, int index) {
        if (dropdown == null || options == null || options.length == 0) {
            return;
        }
        int safeIndex = Math.max(0, Math.min(index, options.length - 1));
        dropdown.setText(options[safeIndex], false);
    }

    private int getDropdownIndex(MaterialAutoCompleteTextView dropdown, String[] options) {
        if (dropdown == null || options == null || options.length == 0) {
            return 0;
        }
        CharSequence selected = dropdown.getText();
        if (selected == null) {
            return 0;
        }
        String value = selected.toString().trim();
        for (int i = 0; i < options.length; i++) {
            if (options[i].equalsIgnoreCase(value)) {
                return i;
            }
        }
        return 0;
    }

    private void updateDurationUi() {
        if (layoutCustomDuration == null) {
            return;
        }
        boolean showCustom = !isAllDayEvent && getDropdownIndex(spinnerDuration, TASK_DURATION_OPTIONS) == 3;
        layoutCustomDuration.setVisibility(showCustom ? View.VISIBLE : View.GONE);
    }

    private void updateAllDayUi() {
        if (layoutStartTimeRow != null) {
            layoutStartTimeRow.setVisibility(isAllDayEvent ? View.GONE : View.VISIBLE);
        }
        if (layoutDurationPicker != null) {
            layoutDurationPicker.setVisibility(isAllDayEvent ? View.GONE : (selectedStartTime != null ? View.VISIBLE : View.GONE));
        }
        updateDurationUi();
        if (layoutEndDateRow != null) {
            layoutEndDateRow.setVisibility(isAllDayEvent ? View.VISIBLE : View.GONE);
            if (isAllDayEvent && selectedEndDate == null && selectedDueDate != null) {
                selectedEndDate = selectedDueDate;
                tvEndDateLabel.setText(formatDate(selectedEndDate));
            } else if (isAllDayEvent && selectedEndDate != null) {
                tvEndDateLabel.setText(formatDate(selectedEndDate));
            }
        }
    }

    private void Vibrate(View v) {
        // handled globally by AutoHaptics
    }

    private void evaluateBestType() {
        if (requireContext() == null) return;
        SettingsRepository settings = SettingsRepository.getInstance(requireContext());
        if (!settings.getBoolean(SettingsRepository.KEY_AUTO_CONVERSION_ENABLED, false)) {
            return;
        }

        boolean hasSubtasks = !subtaskRows.isEmpty();
        boolean isRepeating = currentRecurrenceRule != null && !currentRecurrenceRule.isEmpty();
        boolean hasStartTime = selectedStartTime != null || isAllDayEvent;

        String previousType = itemType;

        if (hasSubtasks) {
            // Tasks with subtasks cannot become events or habits.
            itemType = "todo";
        } else {
            if (isRepeating) {
                itemType = "habit";
            } else if (hasStartTime) {
                itemType = "event";
            } else {
                itemType = "todo";
            }
        }

        if (!itemType.equals(previousType)) {
            tvDialogTitle.setText((existingItem != null ? "Edit " : "New ") + 
                    itemType.substring(0, 1).toUpperCase() + itemType.substring(1));
            setupTypeVisibility();
        }
    }


    private void setupTypeVisibility() {
        SettingsRepository settings = SettingsRepository.getInstance(requireContext());
        boolean showHabitInCalendar = settings.getBoolean(SettingsRepository.KEY_CALENDAR_SHOW_HABITS, false);

        if (itemType.equals("habit")) {
            layoutTaskEventBlock.setVisibility(View.VISIBLE); // Always visible for habits now
            layoutSubtasksContainer.setVisibility(View.GONE);
            layoutHabitDetails.setVisibility(View.VISIBLE);
            
            if (selectedDueDate == null) {
                tvDueDateLabel.setText("No Start Date");
            }
            if (selectedStartTime == null) {
                tvStartTimeLabel.setText("No Start Time");
            }
        } else if (itemType.equals("event")) {
            layoutTaskEventBlock.setVisibility(View.VISIBLE);
            layoutSubtasksContainer.setVisibility(View.GONE);
            layoutHabitDetails.setVisibility(View.GONE);
            
            if (selectedDueDate == null) {
                tvDueDateLabel.setText("No Date");
            }
            if (selectedStartTime == null) {
                tvStartTimeLabel.setText("No Start Time");
            }
        } else {
            layoutTaskEventBlock.setVisibility(View.VISIBLE);
            layoutSubtasksContainer.setVisibility(View.VISIBLE);
            layoutHabitDetails.setVisibility(View.GONE);
            
            if (selectedDueDate == null) {
                tvDueDateLabel.setText("No Due Date");
            }
            if (selectedStartTime == null) {
                tvStartTimeLabel.setText("No Start Time (Task)");
            }
        }

        if (switchAllDay != null) {
            boolean canShowAllDay = "event".equalsIgnoreCase(itemType) || (itemType.equals("habit") && showHabitInCalendar);
            switchAllDay.setVisibility(canShowAllDay ? View.VISIBLE : View.GONE);
        }
        
        if (layoutStartTimeRow != null) {
            boolean isEvent = "event".equalsIgnoreCase(itemType);
            boolean isHabitWithTime = itemType.equals("habit") && showHabitInCalendar;
            layoutStartTimeRow.setVisibility((isAllDayEvent && (isEvent || isHabitWithTime)) ? View.GONE : View.VISIBLE);
        }
    }

    private void loadItemData() {
        itemViewModel.getItemById(editingItemId).observe(getViewLifecycleOwner(), item -> {
            if (item != null && !isDataLoaded) {
                existingItem = item;
                isDataLoaded = true;
                itemType = item.type;
                tvDialogTitle.setText("Edit " + itemType);

                etTitle.setText(item.title);
                etNotes.setText(item.description);
                selectedLabel = LabelUtils.normalizeLabel(item.label);
                updateLabelUi();

                if (item.recurrenceRule != null && !item.recurrenceRule.isEmpty()) {
                    currentRecurrenceRule = item.recurrenceRule;
                    tvRepeatSummary.setText(RecurrenceRuleParser.getHumanReadableSummary(item.recurrenceRule));
                }
                setDropdownSelection(spinnerPriority, getResources().getStringArray(R.array.priority_options), item.priority);
                
                if (item.dueAt != null) {
                    selectedDueDate = item.dueAt;
                    tvDueDateLabel.setText(formatDate(selectedDueDate));
                    btnClearDate.setVisibility(View.VISIBLE);
                } else if (item.allDay && item.startAt != null) {
                    selectedDueDate = item.startAt;
                    tvDueDateLabel.setText(formatDate(selectedDueDate));
                    btnClearDate.setVisibility(View.VISIBLE);
                }
                if (item.allDay && item.endAt != null) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(item.endAt);
                    normalizeToStartOfDay(cal);
                    selectedEndDate = cal.getTimeInMillis();
                }
                isAllDayEvent = item.allDay;
                if (switchAllDay != null) {
                    switchAllDay.setChecked(item.allDay);
                }
                updateAllDayUi();

                if (item.startAt != null) {
                    selectedStartTime = item.startAt;
                    tvStartTimeLabel.setText("Start: " + formatTime(selectedStartTime));
                    btnClearStartTime.setVisibility(View.VISIBLE);
                    if (!item.allDay) {
                        layoutDurationPicker.setVisibility(View.VISIBLE);
                    }
                    
                    if (!item.allDay && item.endAt != null) {
                        calculateAndSetDurationDropdown(item.startAt, item.endAt, item.dueAt);
                    } else {
                        setDropdownSelection(spinnerDuration, TASK_DURATION_OPTIONS, 0);
                        updateDurationUi();
                    }
                }
                
                if (item.type.equals("habit")) {
                    if (item.endAt != null) {
                        switchLastForever.setChecked(false);
                        long diff = item.endAt - item.createdAt;
                        long days = diff / (1000 * 60 * 60 * 24);
                        etHabitDuration.setText(String.valueOf(days));
                    } else {
                        switchLastForever.setChecked(true);
                    }
                    setDropdownSelection(spinnerHabitType, getResources().getStringArray(R.array.habit_type_options), item.isPositive ? 0 : 1);
                    etDailyTarget.setText(String.valueOf(item.dailyTargetCount));
                }

                // Restore Reminder Spinner
                if (item.reminderAt != null) {
                    long baseTime = (item.type.equals("event")) ? (item.startAt != null ? item.startAt : 0) : (item.dueAt != null ? item.dueAt : 0);
                    if (baseTime > 0) {
                        long diff = baseTime - item.reminderAt;
                        if (diff <= 15 * 60 * 1000L) {
                            setDropdownSelection(spinnerReminder, getResources().getStringArray(R.array.reminder_options), 1);
                        } else if (diff <= 30 * 60 * 1000L) {
                            setDropdownSelection(spinnerReminder, getResources().getStringArray(R.array.reminder_options), 2);
                        } else if (diff <= 60 * 60 * 1000L) {
                            setDropdownSelection(spinnerReminder, getResources().getStringArray(R.array.reminder_options), 3);
                        } else if (diff <= 24 * 60 * 60 * 1000L) {
                            setDropdownSelection(spinnerReminder, getResources().getStringArray(R.array.reminder_options), 4);
                        }
                    }
                } else {
                    setDropdownSelection(spinnerReminder, getResources().getStringArray(R.array.reminder_options), 0);
                }

                itemViewModel.getSubtasksForItem(item.id).observe(getViewLifecycleOwner(), subtasks -> {
                    if (subtasks == null) return;
                    bindSubtasks(subtasks);
                });

                setupTypeVisibility();
                updateHabitRepeatUi();
            }
        });
    }

    private void updateHabitRepeatUi() {
        if (!"habit".equalsIgnoreCase(itemType)) {
            return;
        }
        boolean lastForever = switchLastForever != null && switchLastForever.isChecked();
        if (layoutHabitDuration != null) {
            layoutHabitDuration.setVisibility(lastForever ? View.GONE : View.VISIBLE);
        }
    }

    private void bindSubtasks(List<Subtask> subtasks) {
        clearSubtaskRows();
        loadedSubtasks.clear();
        loadedSubtasks.addAll(subtasks);
        for (Subtask subtask : subtasks) {
            addSubtaskRow(subtask);
        }
    }

    private void clearSubtaskRows() {
        subtaskRows.clear();
        layoutSubtasks.removeAllViews();
    }

    private void addSubtaskRow(@Nullable Subtask existingSubtask) {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        containerParams.bottomMargin = dp(8);
        container.setLayoutParams(containerParams);

        TextInputLayout inputLayout = new TextInputLayout(requireContext());
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        LinearLayout.LayoutParams inputLayoutParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        inputLayoutParams.rightMargin = dp(8);
        inputLayout.setLayoutParams(inputLayoutParams);
        inputLayout.setHint("Subtask");

        TextInputEditText input = new TextInputEditText(requireContext());
        input.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (existingSubtask != null && existingSubtask.title != null) {
            input.setText(existingSubtask.title);
        }
        inputLayout.addView(input);

        MaterialButton btnRemove = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnRemove.setLayoutParams(removeParams);
        btnRemove.setText("");
        btnRemove.setIconResource(android.R.drawable.ic_menu_close_clear_cancel);
        btnRemove.setContentDescription("Remove subtask");
        btnRemove.setOnClickListener(v -> {
            Vibrate(v);
            layoutSubtasks.removeView(container);
            subtaskRows.removeIf(entry -> entry.container == container);
            evaluateBestType();
        });

        container.addView(inputLayout);
        container.addView(btnRemove);
        layoutSubtasks.addView(container);
        subtaskRows.add(new SubtaskRow(container, input, existingSubtask));
    }

    private List<Subtask> collectNewSubtasks() {
        List<Subtask> subtasks = new ArrayList<>();
        for (SubtaskRow row : subtaskRows) {
            if (row.boundSubtask != null) {
                continue;
            }
            String title = row.input.getText().toString().trim();
            if (title.isEmpty()) {
                continue;
            }
            Subtask subtask = new Subtask();
            subtask.title = title;
            subtask.isDone = false;
            subtasks.add(subtask);
        }
        return subtasks;
    }

    private void calculateAndSetDurationDropdown(long startAt, long endAt, Long dueAt) {
        long diffMs = endAt - startAt;
        long minutes = diffMs / (60 * 1000L);

        if (minutes == 15) {
            setDropdownSelection(spinnerDuration, TASK_DURATION_OPTIONS, 0);
        } else if (minutes == 30) {
            setDropdownSelection(spinnerDuration, TASK_DURATION_OPTIONS, 1);
        } else if (minutes == 60) {
            setDropdownSelection(spinnerDuration, TASK_DURATION_OPTIONS, 2);
        } else {
            setDropdownSelection(spinnerDuration, TASK_DURATION_OPTIONS, 3);
            long h = minutes / 60;
            long m = minutes % 60;
            etDurationHours.setText(String.valueOf(h));
            etDurationMinutes.setText(String.valueOf(m));
        }
        updateDurationUi();
    }

    private void saveItem() {
        if (requiresNotificationPermission() && !hasNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }

        if (requiresExactAlarmAccess() && !hasExactAlarmAccess() && !waitingForExactAlarmPermission) {
            waitingForExactAlarmPermission = true;
            requestExactAlarmAccess();
            return;
        }

        if (shouldWarnAboutConflict() && !bypassEventConflictWarning) {
            checkEventConflictsAndMaybePrompt();
            return;
        }

        saveItemInternal();
    }

    private boolean shouldWarnAboutConflict() {
        return "event".equalsIgnoreCase(itemType) && selectedStartTime != null && !isAllDayEvent;
    }

    private void checkEventConflictsAndMaybePrompt() {
        final long candidateStart = getCandidateEventStartTime();
        final long candidateEnd = calculateEventEndTime();
        if (candidateStart <= 0L || candidateEnd <= 0L) {
            saveItemInternal();
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            List<Item> items = itemViewModel.getAllItemsSync();
            List<Item> conflicts = new ArrayList<>();
            if (items != null) {
                long editingId = existingItem != null ? existingItem.id : -1L;
                for (Item item : items) {
                    if (item == null || item.id == editingId) {
                        continue;
                    }
                    if (!"event".equalsIgnoreCase(item.type) || item.allDay) {
                        // CR-021: Ignore conflicts involving all-day events
                        continue;
                    }
                    if (item.status != null && ("archived".equalsIgnoreCase(item.status) || "canceled".equalsIgnoreCase(item.status))) {
                        continue;
                    }
                    long otherStart = item.startAt != null ? item.startAt : 0L;
                    long otherEnd = item.endAt != null && item.endAt > 0L ? item.endAt : otherStart;
                    if (otherStart <= 0L || otherEnd <= 0L) {
                        continue;
                    }
                    if (rangesOverlap(candidateStart, candidateEnd, otherStart, otherEnd)) {
                        conflicts.add(item);
                    }
                }
            }

            if (!isAdded()) {
                return;
            }

            if (conflicts.isEmpty()) {
                requireActivity().runOnUiThread(this::saveItemInternal);
            } else {
                requireActivity().runOnUiThread(() -> showConflictDialog(conflicts));
            }
        });
    }

    private boolean rangesOverlap(long startA, long endA, long startB, long endB) {
        return startA < endB && endA > startB;
    }

    private long calculateEventEndTime() {
        if (isAllDayEvent) {
            return getAllDayEndExclusive();
        }
        if (selectedStartTime == null) {
            return 0L;
        }
        int durPos = getDropdownIndex(spinnerDuration, TASK_DURATION_OPTIONS);
        long minutesToAdd = 0;
        if (durPos == 0) {
            minutesToAdd = 15;
        } else if (durPos == 1) {
            minutesToAdd = 30;
        } else if (durPos == 2) {
            minutesToAdd = 60;
        } else if (durPos == 3) {
            try {
                int h = etDurationHours.getText().toString().isEmpty() ? 0 : Integer.parseInt(etDurationHours.getText().toString());
                int m = etDurationMinutes.getText().toString().isEmpty() ? 0 : Integer.parseInt(etDurationMinutes.getText().toString());
                minutesToAdd = (h * 60L) + m;
            } catch (Exception ignored) {}
        }

        Calendar endCal = Calendar.getInstance();
        endCal.setTimeInMillis(selectedStartTime);
        endCal.add(Calendar.MINUTE, (int) minutesToAdd);
        return endCal.getTimeInMillis();
    }

    private void showConflictDialog(List<Item> conflicts) {
        StringBuilder message = new StringBuilder("This event overlaps with:\n\n");
        for (int i = 0; i < conflicts.size(); i++) {
            Item item = conflicts.get(i);
            message.append("- ").append(item.title != null ? item.title : "Untitled event");
            if (item.allDay) {
                message.append(" (All day)");
            } else if (item.startAt != null && item.endAt != null && item.endAt > item.startAt) {
                message.append(" (").append(formatTime(item.startAt)).append(" - ").append(formatTime(item.endAt)).append(")");
            } else if (item.startAt != null) {
                message.append(" (").append(formatTime(item.startAt)).append(")");
            }
            if (i < conflicts.size() - 1) {
                message.append("\n");
            }
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Time conflict")
                .setMessage(message.toString())
                .setPositiveButton("Save anyway", (dialog, which) -> {
                    bypassEventConflictWarning = true;
                    saveItemInternal();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveItemInternal() {
        bypassEventConflictWarning = false;
        String title = etTitle.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(getContext(), "Please enter a title", Toast.LENGTH_SHORT).show();
            return;
        }

        Item item = (existingItem != null) ? existingItem : new Item();
        item.title = title;
        item.description = etNotes.getText().toString().trim();
        item.label = LabelUtils.normalizeLabel(selectedLabel);
        item.priority = getDropdownIndex(spinnerPriority, getResources().getStringArray(R.array.priority_options));
        item.allDay = false;
        
        item.dueAt = selectedDueDate;
        item.startAt = selectedStartTime;

        if (itemType.equals("habit")) {
            if (currentRecurrenceRule == null || currentRecurrenceRule.equals("NONE")) {
                Toast.makeText(getContext(), "Habits must have a repeat schedule enabled.", Toast.LENGTH_SHORT).show();
                return;
            }
            item.type = "habit";
            item.isPositive = (getDropdownIndex(spinnerHabitType, getResources().getStringArray(R.array.habit_type_options)) == 0);
            try {
                item.dailyTargetCount = Integer.parseInt(etDailyTarget.getText().toString());
            } catch (Exception e) {
                item.dailyTargetCount = 1;
            }
            item.frequency = item.dailyTargetCount; // Sync for backward compatibility

            // Map habit duration (series end) to recurrence rule if needed, 
            // but for now let's ensure endAt is used for the daily duration if startAt is set.
            if (selectedStartTime != null) {
                calculateEndAt(item);
            } else if (!switchLastForever.isChecked()) {
                try {
                    int days = Integer.parseInt(etHabitDuration.getText().toString());
                    Calendar cal = Calendar.getInstance();
                    cal.add(Calendar.DAY_OF_YEAR, days);
                    // If no start time, we can use endAt for series end. 
                    // But if it has start time, we use endAt for daily duration.
                    item.endAt = cal.getTimeInMillis();
                } catch (Exception e) {
                    item.endAt = null;
                }
            } else {
                item.endAt = null;
            }
        } else {
            if (isAllDayEvent && selectedDueDate == null) {
                Toast.makeText(getContext(), "Please pick a date for the all-day event.", Toast.LENGTH_SHORT).show();
                return;
            }

            if ("event".equalsIgnoreCase(itemType) && selectedDueDate == null) {
                Toast.makeText(getContext(), "Please select a date for the event.", Toast.LENGTH_SHORT).show();
                return;
            }

            if ("event".equalsIgnoreCase(itemType) && selectedStartTime == null) {
                Toast.makeText(getContext(), "Please select a time for the event.", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // If it has time, it *can* be an event, but let's respect the original itemType 
            // so settings toggles for "tasks" vs "events" work.
            // If it was created as a "todo", keep it as "todo".
            item.type = itemType; 
            
            if (isAllDayEvent && selectedDueDate != null) {
                long startAt = getAllDayStartTime();
                long endAtExclusive = getAllDayEndExclusive();
                item.startAt = startAt;
                item.dueAt = startAt;
                item.endAt = endAtExclusive > 0 ? endAtExclusive - 1 : startAt;
                item.allDay = true;
            } else if (selectedStartTime != null) {
                calculateEndAt(item);
            } else {
                item.endAt = null;
            }
        }

        item.recurrenceRule = currentRecurrenceRule;

        if (item.priority == com.astra.lifeorganizer.utils.AlarmConstants.PRIORITY_HIGH && !hasScheduledTime(item)) {
            Toast.makeText(getContext(), "High priority items need a due date or start time.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Calculate Reminder
        int reminderPos = getDropdownIndex(spinnerReminder, getResources().getStringArray(R.array.reminder_options));
        if (reminderPos > 0) {
            long baseTime = (item.type.equals("event"))
                    ? (item.allDay ? item.startAt : (selectedStartTime != null ? selectedStartTime : 0))
                    : (selectedDueDate != null ? selectedDueDate : 0);
            if (baseTime > 0) {
                long offset = 0;
                switch (reminderPos) {
                    case 1: offset = 0; break; // At start of event
                    case 2: offset = 15 * 60 * 1000L; break;
                    case 3: offset = 30 * 60 * 1000L; break;
                    case 4: offset = 60 * 60 * 1000L; break;
                    case 5: offset = 24 * 60 * 60 * 1000L; break;
                }
                item.reminderAt = baseTime - offset;
            } else {
                item.reminderAt = null;
            }
        } else {
            item.reminderAt = null;
        }

        if (existingItem != null) {
            itemViewModel.update(item);
            syncSubtasksAfterItemSave(item.id);
        } else {
            item.status = "pending";
            item.createdAt = System.currentTimeMillis();
            itemViewModel.insert(item, collectNewSubtasks());
        }

        String typeLabel = "Task";
        if ("habit".equalsIgnoreCase(item.type)) typeLabel = "Habit";
        else if ("event".equalsIgnoreCase(item.type)) typeLabel = "Event";
        
        Toast.makeText(requireContext(), typeLabel + " successfully saved", Toast.LENGTH_SHORT).show();
        
        dismiss();
    }

    private boolean requiresExactAlarmAccess() {
        return getDropdownIndex(spinnerPriority, getResources().getStringArray(R.array.priority_options)) == 3;
    }

    private boolean requiresNotificationPermission() {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU;
    }

    private boolean hasNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasScheduledTime(Item item) {
        if ("event".equals(item.type)) {
            return item.startAt != null && item.startAt > 0L;
        }
        return item.dueAt != null && item.dueAt > 0L;
    }

    private boolean hasExactAlarmAccess() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
            return true;
        }
        AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(android.content.Context.ALARM_SERVICE);
        return alarmManager != null && alarmManager.canScheduleExactAlarms();
    }

    private void calculateEndAt(Item item) {
        if (item.startAt == null) return;
        
        int durPos = getDropdownIndex(spinnerDuration, TASK_DURATION_OPTIONS);
        long minutesToAdd = 0;
        if (durPos == 0) minutesToAdd = 15;
        else if (durPos == 1) minutesToAdd = 30;
        else if (durPos == 2) minutesToAdd = 60;
        else if (durPos == 3) {
            try {
                int h = etDurationHours.getText().toString().isEmpty() ? 0 : Integer.parseInt(etDurationHours.getText().toString());
                int m = etDurationMinutes.getText().toString().isEmpty() ? 0 : Integer.parseInt(etDurationMinutes.getText().toString());
                minutesToAdd = (h * 60L) + m;
            } catch (Exception ignored) {}
        }

        Calendar endCal = Calendar.getInstance();
        endCal.setTimeInMillis(item.startAt);
        endCal.add(Calendar.MINUTE, (int) minutesToAdd);
        item.endAt = endCal.getTimeInMillis();
    }

    private void requestExactAlarmAccess() {
        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
        intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
        exactAlarmPermissionLauncher.launch(intent);
    }

    private void showLabelPicker() {
        if (getChildFragmentManager().findFragmentByTag("label_picker") != null) {
            return;
        }
        LabelPickerBottomSheetFragment picker = LabelPickerBottomSheetFragment.newInstance(selectedLabel);
        picker.setOnLabelSelectedListener(label -> {
            selectedLabel = LabelUtils.normalizeLabel(label);
            updateLabelUi();
        });
        picker.show(getChildFragmentManager(), "label_picker");
    }

    private void updateLabelUi() {
        if (chipLabelPreview == null) {
            return;
        }

        if (selectedLabel == null || selectedLabel.trim().isEmpty()) {
            chipLabelPreview.setText("No label");
            chipLabelPreview.setChipBackgroundColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(0xFF6B7280, 36)));
            chipLabelPreview.setTextColor(0xFF4B5563);
            chipLabelPreview.setCloseIconVisible(false);
        } else {
            String displayLabel = LabelUtils.displayLabel(selectedLabel);
            chipLabelPreview.setText(displayLabel);
            int baseColor = SettingsRepository.getInstance(requireContext()).getLabelColor(displayLabel, colorForLabel(displayLabel));
            chipLabelPreview.setChipBackgroundColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(baseColor, 40)));
            chipLabelPreview.setTextColor(baseColor);
            chipLabelPreview.setCloseIconVisible(true);
        }
        chipLabelPreview.setEllipsize(TextUtils.TruncateAt.END);
    }

    private int colorForLabel(String label) {
        int[] palette = new int[] {
                0xFF2563EB, 0xFF059669, 0xFFDB2777, 0xFFD97706,
                0xFF7C3AED, 0xFF0EA5E9, 0xFFDC2626, 0xFF16A34A
        };
        int index = Math.abs(label.toLowerCase(Locale.getDefault()).hashCode()) % palette.length;
        return palette[index];
    }

    private int dp(int value) {
        return (int) (value * requireContext().getResources().getDisplayMetrics().density);
    }

    private void syncSubtasksAfterItemSave(long itemId) {
        java.util.Set<Long> keptIds = new java.util.HashSet<>();
        for (SubtaskRow row : subtaskRows) {
            String title = row.input.getText().toString().trim();
            if (title.isEmpty()) {
                if (row.boundSubtask != null && row.boundSubtask.id > 0) {
                    itemViewModel.deleteSubtask(row.boundSubtask);
                }
                continue;
            }

            if (row.boundSubtask != null && row.boundSubtask.id > 0) {
                row.boundSubtask.title = title;
                row.boundSubtask.itemId = itemId;
                itemViewModel.updateSubtask(row.boundSubtask);
                keptIds.add(row.boundSubtask.id);
            } else {
                Subtask subtask = new Subtask();
                subtask.itemId = itemId;
                subtask.title = title;
                subtask.isDone = false;
                itemViewModel.insertSubtask(subtask);
            }
        }

        for (Subtask loaded : loadedSubtasks) {
            if (loaded.id > 0 && !keptIds.contains(loaded.id)) {
                itemViewModel.deleteSubtask(loaded);
            }
        }
    }

    interface OnDateTimePicked { void onPicked(long timestamp); }

    private void showDatePicker(OnDateTimePicked listener) {
        Calendar cal = Calendar.getInstance();
        if (selectedDueDate != null) {
            cal.setTimeInMillis(selectedDueDate);
        }
        long minLimit = System.currentTimeMillis() - 1000;
        if (existingItem != null && existingItem.dueAt != null && existingItem.dueAt < minLimit) {
            minLimit = existingItem.dueAt;
        }

        DatePickerDialog dialog = new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
            Calendar res = Calendar.getInstance();
            res.set(Calendar.YEAR, year);
            res.set(Calendar.MONTH, month);
            res.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            if (isAllDayEvent) {
                normalizeToStartOfDay(res);
            }
            listener.onPicked(res.getTimeInMillis());
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        
        dialog.getDatePicker().setMinDate(minLimit);
        dialog.show();
    }

    private void showTimePicker(OnDateTimePicked listener) {
        Calendar cal = Calendar.getInstance();

        if (selectedStartTime != null) {
            cal.setTimeInMillis(selectedStartTime);
        } else {
            // CR-016: Provide a default time 10 mins in future, rounded up to next 5 min interval
            long future10 = System.currentTimeMillis() + 10 * 60 * 1000L;
            cal.setTimeInMillis(future10);
            int min = cal.get(Calendar.MINUTE);
            int remainder = min % 5;
            if (remainder != 0) {
                cal.add(Calendar.MINUTE, 5 - remainder);
            }
            cal.set(Calendar.SECOND, 0);

            if (selectedDueDate != null) {
                Calendar dueCal = Calendar.getInstance();
                dueCal.setTimeInMillis(selectedDueDate);
                
                Calendar now = Calendar.getInstance();
                if (dueCal.get(Calendar.YEAR) > now.get(Calendar.YEAR) || 
                   (dueCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) && dueCal.get(Calendar.DAY_OF_YEAR) > now.get(Calendar.DAY_OF_YEAR))) {
                    // Future event: default to start of day
                    cal.setTimeInMillis(selectedDueDate);
                    cal.set(Calendar.HOUR_OF_DAY, 0);
                    cal.set(Calendar.MINUTE, 0);
                }
            }
        }

        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(SettingsRepository.is24HourTime(requireContext()) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setInputMode(MaterialTimePicker.INPUT_MODE_KEYBOARD)
                .setHour(cal.get(Calendar.HOUR_OF_DAY))
                .setMinute(cal.get(Calendar.MINUTE))
                .setTitleText("Set time")
                .build();
        picker.addOnPositiveButtonClickListener(dialog -> {
            Calendar res = Calendar.getInstance();
            if (selectedDueDate != null) res.setTimeInMillis(selectedDueDate);
            res.set(Calendar.HOUR_OF_DAY, picker.getHour());
            res.set(Calendar.MINUTE, picker.getMinute());
            res.set(Calendar.SECOND, 0);
            res.set(Calendar.MILLISECOND, 0);

            long minAllowedTime = System.currentTimeMillis() + 9 * 60 * 1000L; // Allow slightly under 10 due to picking delay

            if (res.getTimeInMillis() < minAllowedTime) {
                Toast.makeText(getContext(), "Start time must be at least 10 minutes from now.", Toast.LENGTH_SHORT).show();
            } else {
                listener.onPicked(res.getTimeInMillis());
            }
        });
        picker.show(getParentFragmentManager(), "task_time_picker");
    }

    private String formatDate(long timestamp) {
        return DateTimeFormatUtils.formatDate(requireContext(), timestamp);
    }

    private String formatTime(long timestamp) {
        return DateTimeFormatUtils.formatTime(requireContext(), timestamp);
    }

    private long getCandidateEventStartTime() {
        if (isAllDayEvent) {
            return getAllDayStartTime();
        }
        return selectedStartTime != null ? selectedStartTime : 0L;
    }

    private long getAllDayStartTime() {
        if (selectedDueDate == null) {
            return 0L;
        }
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(selectedDueDate);
        normalizeToStartOfDay(cal);
        return cal.getTimeInMillis();
    }

    private long getAllDayEndExclusive() {
        long baseDate = selectedEndDate != null ? selectedEndDate : (selectedDueDate != null ? selectedDueDate : 0L);
        if (baseDate <= 0L) return 0L;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(baseDate);
        cal.add(Calendar.DAY_OF_YEAR, 1);
        normalizeToStartOfDay(cal);
        return cal.getTimeInMillis();
    }

    private void normalizeToStartOfDay(Calendar cal) {
        if (cal == null) {
            return;
        }
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    private long startOfDay(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        normalizeToStartOfDay(cal);
        return cal.getTimeInMillis();
    }

    private static class SubtaskRow {
        final View container;
        final EditText input;
        final Subtask boundSubtask;

        SubtaskRow(View container, EditText input, Subtask boundSubtask) {
            this.container = container;
            this.input = input;
            this.boundSubtask = boundSubtask;
        }
    }
}
