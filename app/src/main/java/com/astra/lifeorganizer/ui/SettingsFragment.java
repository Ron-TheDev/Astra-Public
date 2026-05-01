package com.astra.lifeorganizer.ui;

import android.Manifest;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.text.InputType;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.view.Gravity;
import android.view.LayoutInflater;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.astra.lifeorganizer.MainActivity;
import com.astra.lifeorganizer.R;
import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.data.repositories.ItemRepository;
import com.astra.lifeorganizer.data.repositories.SettingsRepository;
import com.astra.lifeorganizer.utils.ContactsBirthdaySync;
import com.astra.lifeorganizer.utils.BackupUtils;
import com.astra.lifeorganizer.utils.DateTimeFormatUtils;

import com.astra.lifeorganizer.utils.LabelUtils;
import com.astra.lifeorganizer.utils.IcsUtils;
import com.astra.lifeorganizer.utils.RecapBriefScheduler;
import com.astra.lifeorganizer.utils.AlarmConstants;
import com.astra.lifeorganizer.utils.AlarmScheduler;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class SettingsFragment extends Fragment {

    private SettingsRepository settingsRepository;
    private ItemRepository itemRepository;
    private final List<String> managedLabels = new ArrayList<>();

    private MaterialSwitch switchDynamicTheme;
    private MaterialSwitch switchBlackTheme;
    private ChipGroup accentChipGroup;
    private Spinner spinnerDarkMode;

    private final ActivityResultLauncher<String> createIcsLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/calendar"),
            uri -> { if (uri != null) exportIcsToUri(uri); }
    );

    private final ActivityResultLauncher<String[]> openIcsLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> { if (uri != null) importIcsFromUri(uri); }
    );

    private final ActivityResultLauncher<String> exportJsonLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> { if (uri != null) exportJsonToUri(uri); }
    );

    private final ActivityResultLauncher<String[]> importJsonLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> { if (uri != null) importJsonFromUri(uri); }
    );

    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> onPermissionResult(granted)
    );

    private final ActivityResultLauncher<Intent> ringtonePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    if (uri != null) {
                        settingsRepository.setString(SettingsRepository.KEY_ALARM_RINGTONE, uri.toString());
                        updateRingtoneSummary();
                    }
                }
            }
    );

    private String pendingPermissionAction;
    private MaterialSwitch birthdaysSwitch;
    private static int lastScrollY = 0;
    private boolean suppressSwitchCallbacks;
    private boolean suppressSpinnerCallbacks;
    private MaterialButton btnRingtone;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        itemRepository.getDistinctLabels().observe(getViewLifecycleOwner(), labels -> {
            managedLabels.clear();
            if (labels == null) {
                return;
            }
            for (String label : labels) {
                String normalized = LabelUtils.normalizeLabel(label);
                if (normalized != null && !containsManagedLabel(normalized)) {
                    managedLabels.add(normalized);
                }
            }
        });
        
        setupAllRows(view);

        if (lastScrollY > 0) {
            androidx.core.widget.NestedScrollView sv = view.findViewById(R.id.settings_scroll_view);
            if (sv != null) {
                sv.post(() -> sv.scrollTo(0, lastScrollY));
            }
            lastScrollY = 0;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_settings, container, false);
        settingsRepository = SettingsRepository.getInstance(requireContext());
        itemRepository = new ItemRepository(requireActivity().getApplication());

        if (savedInstanceState != null && savedInstanceState.containsKey("scroll_y")) {
            final int scrollY = savedInstanceState.getInt("scroll_y");
            root.post(() -> {
                View scrollView = root.findViewById(R.id.settings_scroll_view);
                if (scrollView != null) {
                    scrollView.scrollTo(0, scrollY);
                }
            });
        }

        return root;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        View root = getView();
        if (root != null) {
            View scrollView = root.findViewById(R.id.settings_scroll_view);
            if (scrollView != null) {
                outState.putInt("scroll_y", scrollView.getScrollY());
            }
        }
    }

    @Override
    public void onDestroy() {
        if (restartRunnable != null) {
            restartHandler.removeCallbacks(restartRunnable);
        }
        super.onDestroy();
    }

    private void setupAllRows(View root) {
        // Testing & Debug
        bindButton(root.findViewById(R.id.btn_test_alarm_overlay), v -> {
            Executors.newSingleThreadExecutor().execute(() -> {
                Item lastItem = itemRepository.getLastCreatedItemSync();
                long itemId = lastItem != null ? lastItem.id : 0L;
                requireActivity().runOnUiThread(() -> {
                    com.astra.lifeorganizer.utils.AlarmActionHandler.launchFullscreenAlarm(requireContext(), itemId, true);
                });
            });
        });
        bindButton(root.findViewById(R.id.btn_test_30s_alarm), v -> {
            Executors.newSingleThreadExecutor().execute(() -> {
                Item testItem = new Item();
                testItem.title = "🔔 Test Alarm System";
                testItem.type = "todo";
                testItem.priority = 3; 
                testItem.dueAt = System.currentTimeMillis() + 30000;
                testItem.createdAt = System.currentTimeMillis();
                long id = itemRepository.insertSync(testItem);
                testItem.id = id;
                requireActivity().runOnUiThread(() -> {
                    com.astra.lifeorganizer.utils.AlarmScheduler.scheduleForItem(requireContext(), testItem);
                    toast("Alarm scheduled for 30s from now. Countdown should appear.");
                });
            });
        });

        setupRecapTestRow(root.findViewById(R.id.row_test_weekly_recap),
                "Weekly recap",
                "Preview the weekly summary layout.",
                "Open",
                () -> navigateToRecapPreview("weekly"));
        setupRecapTestRow(root.findViewById(R.id.row_test_monthly_recap),
                "Monthly recap",
                "Preview the monthly summary layout.",
                "Open",
                () -> navigateToRecapPreview("monthly"));
        setupRecapTestRow(root.findViewById(R.id.row_test_yearly_recap),
                "Yearly recap",
                "Preview the yearly summary layout.",
                "Open",
                () -> navigateToRecapPreview("yearly"));

        // Page Visibility
        setupPageToggle(root.findViewById(R.id.switch_page_home), SettingsRepository.KEY_PAGE_HOME, settingsRepository.isHomeEnabled);
        setupPageToggle(root.findViewById(R.id.switch_page_todo), SettingsRepository.KEY_PAGE_TODO, settingsRepository.isTodoEnabled);
        setupPageToggle(root.findViewById(R.id.switch_page_habits), SettingsRepository.KEY_PAGE_HABITS, settingsRepository.isHabitsEnabled);
        setupPageToggle(root.findViewById(R.id.switch_page_calendar), SettingsRepository.KEY_PAGE_CALENDAR, settingsRepository.isCalendarEnabled);

        // Appearance
        switchDynamicTheme = bindSwitch(root.findViewById(R.id.switch_dynamic_theme),
                settingsRepository.getBoolean(SettingsRepository.KEY_DYNAMIC_THEME, true),
                (sw, checked) -> {
                    settingsRepository.setBoolean(SettingsRepository.KEY_DYNAMIC_THEME, checked);
                    updateAppearanceState();
                    restartForAppearanceChange();
                });

        accentChipGroup = bindAccentChooser(root.findViewById(R.id.chip_group_accent));
        updateAppearanceState();

        spinnerDarkMode = root.findViewById(R.id.spinner_dark_mode);
        bindSpinner(spinnerDarkMode,
                R.array.settings_dark_mode_options,
                settingsRepository.getInt(SettingsRepository.KEY_NIGHT_MODE, SettingsRepository.NIGHT_MODE_SYSTEM),
                position -> {
                    settingsRepository.setInt(SettingsRepository.KEY_NIGHT_MODE, position);
                    if (position == SettingsRepository.NIGHT_MODE_LIGHT) {
                        settingsRepository.setBoolean(SettingsRepository.KEY_BLACK_THEME, false);
                        if (switchBlackTheme != null) {
                            setSwitchSilently(switchBlackTheme, false);
                        }
                    }
                    AppCompatDelegate.setDefaultNightMode(SettingsRepository.resolveNightMode(requireContext()));
                    updateAppearanceState();
                });

        switchBlackTheme = bindSwitch(root.findViewById(R.id.switch_black_theme),
                settingsRepository.getBoolean(SettingsRepository.KEY_BLACK_THEME,
                        settingsRepository.getInt(SettingsRepository.KEY_NIGHT_MODE, SettingsRepository.NIGHT_MODE_SYSTEM) == SettingsRepository.NIGHT_MODE_DARK),
                (sw, checked) -> {
                    if (getSelectedNightMode() == SettingsRepository.NIGHT_MODE_LIGHT) {
                        setSwitchSilently(sw, false);
                        settingsRepository.setBoolean(SettingsRepository.KEY_BLACK_THEME, false);
                        updateAppearanceState();
                        return;
                    }
                    settingsRepository.setBoolean(SettingsRepository.KEY_BLACK_THEME, checked);
                    restartForAppearanceChange();
                });
        updateAppearanceState();

        bindSpinner(root.findViewById(R.id.spinner_date_format),
                R.array.settings_date_format_options,
                dateFormatIndex(settingsRepository.getString(SettingsRepository.KEY_DATE_FORMAT, "medium")),
                position -> {
                    settingsRepository.setString(SettingsRepository.KEY_DATE_FORMAT, dateFormatValue(position));
                });

        bindSpinner(root.findViewById(R.id.spinner_time_format),
                R.array.settings_time_format_options,
                timeFormatIndex(settingsRepository.getString(SettingsRepository.KEY_TIME_FORMAT, "12h")),
                position -> {
                    settingsRepository.setString(SettingsRepository.KEY_TIME_FORMAT, position == 1 ? "24h" : "12h");
                });

        // Calendar
        bindButton(root.findViewById(R.id.btn_import_android_calendar), v -> requestPermissionAndRun(Manifest.permission.READ_CALENDAR, "import_calendar"));
        bindButton(root.findViewById(R.id.btn_import_ics_file), v -> openIcsLauncher.launch(new String[]{"text/calendar", "application/octet-stream"}));

        birthdaysSwitch = bindSwitch(root.findViewById(R.id.switch_import_birthdays),
                settingsRepository.getBoolean(SettingsRepository.KEY_IMPORT_BIRTHDAYS, false),
                (sw, checked) -> {
                    settingsRepository.setBoolean(SettingsRepository.KEY_IMPORT_BIRTHDAYS, checked);
                    if (checked) {
                        requestPermissionAndRun(Manifest.permission.READ_CONTACTS, "import_birthdays");
                    } else {
                        itemRepository.replaceImportedBirthdays(new ArrayList<>());
                    }
                });

        bindSpinner(root.findViewById(R.id.spinner_auto_import),
                R.array.settings_auto_import_options,
                settingsRepository.getInt(SettingsRepository.KEY_AUTO_IMPORT_INTERVAL, 0),
                position -> settingsRepository.setInt(SettingsRepository.KEY_AUTO_IMPORT_INTERVAL, position));

        bindSwitch(root.findViewById(R.id.switch_show_week_numbers),
                settingsRepository.getBoolean(SettingsRepository.KEY_SHOW_WEEK_NUMBERS, false),
                (sw, checked) -> settingsRepository.setBoolean(SettingsRepository.KEY_SHOW_WEEK_NUMBERS, checked));

        bindSpinner(root.findViewById(R.id.spinner_week_start),
                R.array.settings_week_start_options,
                weekStartIndex(settingsRepository.getInt(SettingsRepository.KEY_WEEK_START, SettingsRepository.WEEK_START_SUNDAY)),
                position -> settingsRepository.setInt(SettingsRepository.KEY_WEEK_START, position));

        bindSwitch(root.findViewById(R.id.switch_show_tasks),
                settingsRepository.getBoolean(SettingsRepository.KEY_CALENDAR_SHOW_TASKS, false),
                (sw, checked) -> settingsRepository.setBoolean(SettingsRepository.KEY_CALENDAR_SHOW_TASKS, checked));

        bindSwitch(root.findViewById(R.id.switch_show_habits),
                settingsRepository.getBoolean(SettingsRepository.KEY_CALENDAR_SHOW_HABITS, false),
                (sw, checked) -> settingsRepository.setBoolean(SettingsRepository.KEY_CALENDAR_SHOW_HABITS, checked));

        // Behavior
        bindSwitch(root.findViewById(R.id.switch_auto_conversion),
                settingsRepository.getBoolean(SettingsRepository.KEY_AUTO_CONVERSION_ENABLED, false),
                (sw, checked) -> settingsRepository.setBoolean(SettingsRepository.KEY_AUTO_CONVERSION_ENABLED, checked));

        // Label Manager
        bindButton(root.findViewById(R.id.btn_manage_labels), v -> showLabelManagerDialog());

        // Defaults
        bindSpinner(root.findViewById(R.id.spinner_default_reminder),
                R.array.settings_default_reminder_options,
                settingsRepository.getInt(SettingsRepository.KEY_DEFAULT_REMINDER, 0),
                position -> settingsRepository.setInt(SettingsRepository.KEY_DEFAULT_REMINDER, position));

        bindSpinner(root.findViewById(R.id.spinner_default_priority),
                R.array.settings_default_priority_options,
                settingsRepository.getInt(SettingsRepository.KEY_DEFAULT_PRIORITY, 2),
                position -> settingsRepository.setInt(SettingsRepository.KEY_DEFAULT_PRIORITY, position));

        bindSpinner(root.findViewById(R.id.spinner_default_duration),
                R.array.settings_default_duration_options,
                settingsRepository.getInt(SettingsRepository.KEY_DEFAULT_DURATION, 2),
                position -> settingsRepository.setInt(SettingsRepository.KEY_DEFAULT_DURATION, position));

        bindSpinner(root.findViewById(R.id.spinner_default_recurrence),
                R.array.repeat_options,
                settingsRepository.getInt(SettingsRepository.KEY_DEFAULT_RECURRENCE, 1),
                position -> settingsRepository.setInt(SettingsRepository.KEY_DEFAULT_RECURRENCE, position));

        bindSpinner(root.findViewById(R.id.spinner_default_calendar_view),
                R.array.settings_calendar_view_state_options,
                settingsRepository.getInt(SettingsRepository.KEY_DEFAULT_CALENDAR_VIEW, 0),
                position -> settingsRepository.setInt(SettingsRepository.KEY_DEFAULT_CALENDAR_VIEW, position));

        // Summaries
        bindSwitch(root.findViewById(R.id.switch_morning_brief),
                settingsRepository.getBoolean(SettingsRepository.KEY_MORNING_BRIEF_ENABLED, false),
                (sw, checked) -> {
                    settingsRepository.setBoolean(SettingsRepository.KEY_MORNING_BRIEF_ENABLED, checked);
                    RecapBriefScheduler.rescheduleAll(requireContext());
                });
        bindTimeButton(root.findViewById(R.id.btn_morning_time),
                settingsRepository.getLong(SettingsRepository.KEY_MORNING_BRIEF_TIME, defaultTime(8, 0)),
                value -> {
                    settingsRepository.setLong(SettingsRepository.KEY_MORNING_BRIEF_TIME, value);
                    RecapBriefScheduler.rescheduleAll(requireContext());
                });

        bindSwitch(root.findViewById(R.id.switch_evening_wrap),
                settingsRepository.getBoolean(SettingsRepository.KEY_EVENING_WRAP_ENABLED, false),
                (sw, checked) -> {
                    settingsRepository.setBoolean(SettingsRepository.KEY_EVENING_WRAP_ENABLED, checked);
                    RecapBriefScheduler.rescheduleAll(requireContext());
                });
        bindTimeButton(root.findViewById(R.id.btn_evening_time),
                settingsRepository.getLong(SettingsRepository.KEY_EVENING_WRAP_TIME, defaultTime(18, 0)),
                value -> {
                    settingsRepository.setLong(SettingsRepository.KEY_EVENING_WRAP_TIME, value);
                    RecapBriefScheduler.rescheduleAll(requireContext());
                });
        RecapBriefScheduler.rescheduleAll(requireContext());

        // Alarm
        btnRingtone = (MaterialButton) bindButton(root.findViewById(R.id.btn_alarm_ringtone), v -> {
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Tone");
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, (Uri) null);
            String current = settingsRepository.getString(SettingsRepository.KEY_ALARM_RINGTONE, null);
            if (current != null) {
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(current));
            }
            ringtonePickerLauncher.launch(intent);
        });
        updateRingtoneSummary();

        // Privacy
        bindSwitch(root.findViewById(R.id.switch_biometric_lock),
                settingsRepository.getBoolean(SettingsRepository.KEY_BIOMETRIC_LOCK, false),
                (sw, checked) -> {
                    if (checked) {
                        requestBiometricEnable(sw);
                    } else {
                        settingsRepository.setBoolean(SettingsRepository.KEY_BIOMETRIC_LOCK, false);
                        applyPrivacySettingsToActivity();
                    }
                });
        bindSwitch(root.findViewById(R.id.switch_prevent_screenshots),
                settingsRepository.getBoolean(SettingsRepository.KEY_PREVENT_SCREENSHOTS, false),
                (sw, checked) -> {
                    settingsRepository.setBoolean(SettingsRepository.KEY_PREVENT_SCREENSHOTS, checked);
                    applyPrivacySettingsToActivity();
                });

        // Widgets
        bindButton(root.findViewById(R.id.btn_widget_picker), v -> navigateToWidgetPicker());

        // Data Management
        bindButton(root.findViewById(R.id.btn_export_backup), v -> exportJsonLauncher.launch("astra_backup_" + System.currentTimeMillis() + ".json"));
        bindButton(root.findViewById(R.id.btn_restore_backup), v -> importJsonLauncher.launch(new String[]{"application/json", "application/octet-stream", "*/*"}));
        bindButton(root.findViewById(R.id.btn_export_calendar), v -> createIcsLauncher.launch("astra_calendar.ics"));
        bindButton(root.findViewById(R.id.btn_purge_archived), v -> new AlertDialog.Builder(requireContext())
                .setTitle("Purge archived items?")
                .setMessage("This removes archived items permanently from the local database.")
                .setPositiveButton("Purge", (d, w) -> itemRepository.purgeArchivedItems())
                .setNegativeButton("Cancel", null)
                .show());
        bindButton(root.findViewById(R.id.btn_reset_app), v -> new AlertDialog.Builder(requireContext())
                .setTitle("Reset app?")
                .setMessage("This will erase all local items, history, subtasks, and occurrences.")
                .setPositiveButton("Reset", (d, w) -> Executors.newSingleThreadExecutor().execute(() -> {
                    itemRepository.restoreWipeSync();
                    settingsRepository.clearAll();
                    com.astra.lifeorganizer.utils.AlarmQueueManager.clear(requireContext());
                    RecapBriefScheduler.rescheduleAll(requireContext());
                    com.astra.lifeorganizer.utils.NotificationHelper.cancelAll(requireContext());
                    requireActivity().runOnUiThread(() -> {
                        toast("All data erased.");
                        requireActivity().recreate();
                    });
                }))
                .setNegativeButton("Cancel", null)
                .show());

        // Misc
        bindSwitch(root.findViewById(R.id.switch_haptics), settingsRepository.getBoolean(SettingsRepository.KEY_HAPTICS_ENABLED, true), (sw, checked) -> settingsRepository.setBoolean(SettingsRepository.KEY_HAPTICS_ENABLED, checked));
        TextView tvVersion = root.findViewById(R.id.tv_app_version);
        if (tvVersion != null) tvVersion.setText(settingsRepository.getVersionName());

        hideRowDivider(root.findViewById(R.id.row_test_yearly_recap));
        hideRowDivider(root.findViewById(R.id.row_page_settings_info));
        hideRowDivider(root.findViewById(R.id.row_time_format));
        hideRowDivider(root.findViewById(R.id.row_calendar_show_habits));
        hideRowDivider(root.findViewById(R.id.row_auto_conversion));
        hideRowDivider(root.findViewById(R.id.row_manage_labels));
        hideRowDivider(root.findViewById(R.id.row_default_calendar_view));
        hideRowDivider(root.findViewById(R.id.row_evening_time));
        hideRowDivider(root.findViewById(R.id.row_alarm_ringtone));
        hideRowDivider(root.findViewById(R.id.row_privacy_info));
        hideRowDivider(root.findViewById(R.id.row_widget_picker));
        hideRowDivider(root.findViewById(R.id.row_reset_app));
        hideRowDivider(root.findViewById(R.id.row_app_version));
    }

    private void updateRingtoneSummary() {
        if (btnRingtone != null && btnRingtone.getParent() != null && btnRingtone.getParent().getParent() instanceof View) {
            View row = (View) btnRingtone.getParent().getParent();
            TextView subtitle = row.findViewById(R.id.tv_row_subtitle);
            if (subtitle != null) {
                subtitle.setText(getRingtoneName());
            }
        }
    }

    private String getRingtoneName() {
        String uriStr = settingsRepository.getString(SettingsRepository.KEY_ALARM_RINGTONE, null);
        if (uriStr == null) return "System default";
        try {
            Uri uri = Uri.parse(uriStr);
            Ringtone r = RingtoneManager.getRingtone(requireContext(), uri);
            if (r != null) {
                return r.getTitle(requireContext());
            }
        } catch (Exception ignored) {}
        return "Unknown Tone";
    }


    private void showLabelManagerDialog() {
        List<String> labels = getManagedLabelsSnapshot();
        if (labels.isEmpty()) {
            toast("No labels found.");
            return;
        }

        CharSequence[] choices = new CharSequence[labels.size()];
        for (int i = 0; i < labels.size(); i++) {
            choices[i] = LabelUtils.displayLabel(labels.get(i));
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Choose a label")
                .setItems(choices, (dialog, which) -> showLabelActionsDialog(labels.get(which)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLabelActionsDialog(String label) {
        String display = LabelUtils.displayLabel(label);
        new AlertDialog.Builder(requireContext())
                .setTitle(display)
                .setItems(new CharSequence[]{
                        "Rename label",
                        "Merge into another label",
                        "Delete label"
                }, (dialog, which) -> {
                    if (which == 0) {
                        showRenameLabelDialog(label);
                    } else if (which == 1) {
                        showMergeLabelDialog(label);
                    } else {
                        showDeleteLabelDialog(label);
                    }
                })
                .setNegativeButton("Back", null)
                .show();
    }

    private void showRenameLabelDialog(String currentLabel) {
        EditText input = new EditText(requireContext());
        input.setText(LabelUtils.displayLabel(currentLabel));
        input.setSelection(input.getText() != null ? input.getText().length() : 0);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        int pad = dp(20);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(requireContext())
                .setTitle("Rename label")
                .setMessage("This updates upcoming events, active habits, and uncompleted tasks.")
                .setView(input)
                .setPositiveButton("Rename", (dialog, which) -> {
                    String newLabel = LabelUtils.normalizeLabel(input.getText() != null ? input.getText().toString() : null);
                    if (newLabel == null) {
                        toast("Enter a label name.");
                        return;
                    }
                    if (newLabel.equalsIgnoreCase(currentLabel)) {
                        toast("Choose a different label.");
                        return;
                    }
                    if (containsManagedLabel(newLabel)) {
                        toast("That label already exists. Use merge instead.");
                        return;
                    }
                    itemRepository.renameLabel(currentLabel, newLabel);
                    toast("Label rename queued.");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showMergeLabelDialog(String sourceLabel) {
        List<String> labels = getManagedLabelsSnapshot();
        List<String> targets = new ArrayList<>();
        for (String label : labels) {
            if (!label.equalsIgnoreCase(sourceLabel)) {
                targets.add(label);
            }
        }

        if (targets.isEmpty()) {
            toast("No other labels to merge into.");
            return;
        }

        CharSequence[] choices = new CharSequence[targets.size()];
        for (int i = 0; i < targets.size(); i++) {
            choices[i] = LabelUtils.displayLabel(targets.get(i));
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Merge into")
                .setMessage("Choose the label to keep. This updates upcoming events, active habits, and uncompleted tasks.")
                .setItems(choices, (dialog, which) -> {
                    String targetLabel = targets.get(which);
                    itemRepository.mergeLabel(sourceLabel, targetLabel);
                    toast("Label merge queued.");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteLabelDialog(String label) {
        String display = LabelUtils.displayLabel(label);
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete " + display + "?")
                .setMessage("This applies only to upcoming events, active habits, and uncompleted tasks.")
                .setPositiveButton("Label only", (dialog, which) -> {
                    itemRepository.clearLabel(label);
                    toast("Label removal queued.");
                })
                .setNeutralButton("Label and items", (dialog, which) -> {
                    itemRepository.deleteItemsWithLabel(label);
                    toast("Label and item deletion queued.");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private List<String> getManagedLabelsSnapshot() {
        return new ArrayList<>(managedLabels);
    }

    private boolean containsManagedLabel(String label) {
        for (String existing : managedLabels) {
            if (existing.equalsIgnoreCase(label)) {
                return true;
            }
        }
        return false;
    }

    private void setupPageToggle(MaterialSwitch sw, String key, androidx.lifecycle.MutableLiveData<Boolean> liveData) {
        if (sw == null) return;
        sw.setChecked(liveData.getValue() == null || liveData.getValue());
        sw.setOnCheckedChangeListener((button, checked) -> {
            if (!settingsRepository.setPageEnabled(key, checked, liveData)) {
                setSwitchSilently(sw, true);
                if (key.equals(SettingsRepository.KEY_PAGE_HOME)) {
                     toast("Settings must leave at least one functional page enabled.");
                } else {
                     toast("At least two of Todo, Habits, or Calendar must remain enabled.");
                }
            }
        });
    }

    private MaterialSwitch bindSwitch(View v, boolean checked, SwitchCallback callback) {
        if (!(v instanceof MaterialSwitch)) return null;
        MaterialSwitch sw = (MaterialSwitch) v;
        suppressSwitchCallbacks = true;
        sw.setChecked(checked);
        suppressSwitchCallbacks = false;
        sw.setOnCheckedChangeListener((button, checkedState) -> {
            if (!suppressSwitchCallbacks) callback.onChanged(sw, checkedState);
        });
        return sw;
    }

    private void bindSpinner(View v, int arrayRes, int selected, SpinnerCallback callback) {
        if (!(v instanceof Spinner)) return;
        Spinner spinner = (Spinner) v;
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(requireContext(),
                arrayRes, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        suppressSpinnerCallbacks = true;
        spinner.setSelection(selected);
        suppressSpinnerCallbacks = false;
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (!suppressSpinnerCallbacks) callback.onSelected(pos);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
    }

    private Button bindButton(View v, View.OnClickListener listener) {
        if (!(v instanceof Button)) return null;
        Button btn = (Button) v;
        btn.setOnClickListener(listener);
        return btn;
    }

    private void hideRowDivider(@Nullable View rowView) {
        if (rowView == null) {
            return;
        }
        View divider = rowView.findViewById(R.id.row_divider);
        if (divider != null) {
            divider.setVisibility(View.GONE);
        }
    }

    private void setupRecapTestRow(View rowView, String title, String subtitle, String buttonLabel, Runnable action) {
        if (rowView == null) {
            return;
        }

        TextView rowTitle = rowView.findViewById(R.id.tv_row_title);
        TextView rowSubtitle = rowView.findViewById(R.id.tv_row_subtitle);
        Button rowButton = rowView.findViewById(R.id.row_button);

        if (rowTitle != null) {
            rowTitle.setText(title);
        }
        if (rowSubtitle != null) {
            rowSubtitle.setText(subtitle);
        }
        if (rowButton != null) {
            rowButton.setVisibility(View.VISIBLE);
            rowButton.setText(buttonLabel);
            rowButton.setOnClickListener(v -> action.run());
        }

        rowView.setVisibility(View.VISIBLE);
        rowView.setOnClickListener(v -> action.run());
    }

    private void bindTimeButton(View v, long initialTime, TimeCallback callback) {
        if (!(v instanceof Button)) return;
        Button btn = (Button) v;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(initialTime);
        btn.setText(DateTimeFormatUtils.formatTime(requireContext(), initialTime));
        btn.setOnClickListener(v1 -> {
            MaterialTimePicker picker = new MaterialTimePicker.Builder()
                    .setTimeFormat(settingsRepository.is24HourTime(requireContext()) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                    .setHour(c.get(Calendar.HOUR_OF_DAY))
                    .setMinute(c.get(Calendar.MINUTE))
                    .setTitleText("Select Time")
                    .build();
            picker.addOnPositiveButtonClickListener(v2 -> {
                c.set(Calendar.HOUR_OF_DAY, picker.getHour());
                c.set(Calendar.MINUTE, picker.getMinute());
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);
                long ts = c.getTimeInMillis();
                btn.setText(DateTimeFormatUtils.formatTime(requireContext(), ts));
                callback.onPicked(ts);
            });
            picker.show(getParentFragmentManager(), "settings_time_picker");
        });
    }

    private ChipGroup bindAccentChooser(View v) {
        if (!(v instanceof ChipGroup)) return null;
        ChipGroup group = (ChipGroup) v;
        group.removeAllViews();
        group.setChipSpacingHorizontal(dp(8));
        group.setChipSpacingVertical(dp(8));
        String[] labels = getResources().getStringArray(R.array.settings_accent_options);
        String current = settingsRepository.getString(SettingsRepository.KEY_ACCENT_COLOR, "blue");
        for (String label : labels) {
            Chip chip = new Chip(requireContext());
            chip.setText(label);
            chip.setCheckable(true);
            chip.setChecked(label.equalsIgnoreCase(current));
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    settingsRepository.setString(SettingsRepository.KEY_ACCENT_COLOR, label.toLowerCase(Locale.getDefault()));
                    if (!settingsRepository.getBoolean(SettingsRepository.KEY_DYNAMIC_THEME, true)) {
                        restartForAppearanceChange();
                    }
                }
            });
            group.addView(chip);
        }
        return group;
    }

    private void setSwitchSilently(MaterialSwitch sw, boolean checked) {
        suppressSwitchCallbacks = true;
        sw.setChecked(checked);
        suppressSwitchCallbacks = false;
    }

    private void updateAppearanceState() {
        if (accentChipGroup != null) {
            boolean enabled = !settingsRepository.getBoolean(SettingsRepository.KEY_DYNAMIC_THEME, true);
            accentChipGroup.setEnabled(enabled);
            accentChipGroup.setAlpha(enabled ? 1f : 0.45f);
        }
        if (switchBlackTheme != null) {
            boolean lightMode = getSelectedNightMode() == SettingsRepository.NIGHT_MODE_LIGHT;
            switchBlackTheme.setEnabled(!lightMode);
            if (lightMode && switchBlackTheme.isChecked()) {
                settingsRepository.setBoolean(SettingsRepository.KEY_BLACK_THEME, false);
                setSwitchSilently(switchBlackTheme, false);
            }
        }
    }

    private int getSelectedNightMode() {
        if (spinnerDarkMode == null || spinnerDarkMode.getSelectedItemPosition() < 0) {
            return SettingsRepository.NIGHT_MODE_SYSTEM;
        }
        return spinnerDarkMode.getSelectedItemPosition();
    }

    private final android.os.Handler restartHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable restartRunnable;

    private void restartForAppearanceChange() {
        if (!isAdded()) return;
        
        // Save scroll position for smooth UX
        View v = getView();
        if (v != null) {
            androidx.core.widget.NestedScrollView sv = v.findViewById(R.id.settings_scroll_view);
            if (sv != null) {
                lastScrollY = sv.getScrollY();
            }
        }
        
        // Debounce the restart so clicking multiple chips doesn't trigger multiple reloads
        if (restartRunnable != null) {
            restartHandler.removeCallbacks(restartRunnable);
        }
        
        restartRunnable = () -> {
            if (isAdded()) {
                requireActivity().recreate();
            }
        };
        
        // 400ms delay gives the user time to see the selection before the "flash"
        restartHandler.postDelayed(restartRunnable, 400);
    }

    private void navigateToRecapPreview() {
        navigateToRecapPreview("weekly");
    }

    private void navigateToRecapPreview(String mode) {
        if (!isAdded()) {
            return;
        }
        NavController navController = NavHostFragment.findNavController(this);
        Bundle args = new Bundle();
        args.putString("initial_mode", mode);
        navController.navigate(R.id.RecapPreviewFragment, args);
    }


    private void navigateToWidgetPicker() {
        if (!isAdded()) {
            return;
        }
        NavController navController = NavHostFragment.findNavController(this);
        navController.navigate(R.id.WidgetPickerFragment);
    }

    private void requestBiometricEnable(MaterialSwitch sw) {
        if (!isAdded() || !(requireActivity() instanceof MainActivity)) {
            return;
        }
        MainActivity activity = (MainActivity) requireActivity();
        activity.requestBiometricAuthentication(false, true, new MainActivity.BiometricActionCallback() {
            @Override
            public void onSuccess() {
                settingsRepository.setBoolean(SettingsRepository.KEY_BIOMETRIC_LOCK, true);
            }

            @Override
            public void onFailure() {
                settingsRepository.setBoolean(SettingsRepository.KEY_BIOMETRIC_LOCK, false);
                setSwitchSilently(sw, false);
                toast("Biometric unlock is required to enable app lock.");
            }
        });
    }

    private void applyPrivacySettingsToActivity() {
        if (isAdded() && requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).applyPrivacySettings();
        }
    }

    private int weekStartIndex(int value) {
        switch (value) {
            case SettingsRepository.WEEK_START_MONDAY:
                return 1;
            case SettingsRepository.WEEK_START_SATURDAY:
                return 2;
            case SettingsRepository.WEEK_START_SUNDAY:
            default:
                return 0;
        }
    }

    private void requestPermissionAndRun(String permission, String action) {
        if (ContextCompat.checkSelfPermission(requireContext(), permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingPermissionAction = null;
            onPermissionGranted(action);
            return;
        }
        pendingPermissionAction = action;
        permissionLauncher.launch(permission);
    }

    private void onPermissionResult(boolean granted) {
        if (!granted) {
            if ("import_birthdays".equals(pendingPermissionAction)) {
                setSwitchSilently(birthdaysSwitch, false);
                settingsRepository.setBoolean(SettingsRepository.KEY_IMPORT_BIRTHDAYS, false);
                itemRepository.replaceImportedBirthdays(new ArrayList<>());
                toast("Contacts permission is required to import birthdays.");
            } else if ("import_calendar".equals(pendingPermissionAction)) {
                toast("Calendar permission is required to import device events.");
            }
            pendingPermissionAction = null;
            return;
        }
        onPermissionGranted(pendingPermissionAction);
        pendingPermissionAction = null;
    }

    private void onPermissionGranted(@Nullable String action) {
        if ("import_birthdays".equals(action)) {
            importBirthdaysFromContacts();
        } else if ("import_calendar".equals(action)) {
            importLocalCalendar();
        }
    }

    private long defaultTime(int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private void toast(String message) {
        if (isAdded()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private int dateFormatIndex(String value) {
        if ("short".equalsIgnoreCase(value)) return 1;
        if ("iso".equalsIgnoreCase(value)) return 2;
        if ("long".equalsIgnoreCase(value)) return 3;
        return 0;
    }

    private String dateFormatValue(int index) {
        switch (index) {
            case 1: return "short";
            case 2: return "iso";
            case 3: return "long";
            default: return "medium";
        }
    }

    private int timeFormatIndex(String value) {
        return "24h".equalsIgnoreCase(value) ? 1 : 0;
    }

    private void importLocalCalendar() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                List<Item> imported = new ArrayList<>();
                List<Item> existingItems = itemRepository.getAllItemsSync();
                long now = System.currentTimeMillis();
                long yearAhead = now + (365L * 24 * 60 * 60 * 1000L);
                String[] calendarProjection = {
                        CalendarContract.Calendars._ID,
                        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
                };
                String calendarSelection = CalendarContract.Calendars.VISIBLE + " = 1 AND " + CalendarContract.Calendars.SYNC_EVENTS + " = 1";
                try (Cursor calendarCursor = requireContext().getContentResolver().query(
                        CalendarContract.Calendars.CONTENT_URI,
                        calendarProjection,
                        calendarSelection,
                        null,
                        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " COLLATE NOCASE ASC")) {
                    if (calendarCursor != null) {
                        while (calendarCursor.moveToNext()) {
                            long calendarId = calendarCursor.getLong(0);
                            String calendarName = calendarCursor.getString(1);
                            if (calendarName == null || calendarName.trim().isEmpty()) {
                                calendarName = "Calendar " + calendarId;
                            }

                            String[] eventProjection = {
                                    CalendarContract.Events._ID,
                                    CalendarContract.Events.TITLE,
                                    CalendarContract.Events.DESCRIPTION,
                                    CalendarContract.Events.DTSTART,
                                    CalendarContract.Events.DTEND,
                                    CalendarContract.Events.ALL_DAY
                            };
                            String eventSelection = CalendarContract.Events.CALENDAR_ID + " = ? AND "
                                    + CalendarContract.Events.DTSTART + " >= ? AND "
                                    + CalendarContract.Events.DTSTART + " <= ? AND "
                                    + CalendarContract.Events.DELETED + " = 0";
                            String[] eventSelectionArgs = {String.valueOf(calendarId), String.valueOf(now), String.valueOf(yearAhead)};
                            try (Cursor cursor = requireContext().getContentResolver().query(
                                    CalendarContract.Events.CONTENT_URI,
                                    eventProjection,
                                    eventSelection,
                                    eventSelectionArgs,
                                    CalendarContract.Events.DTSTART + " ASC")) {
                                if (cursor == null) {
                                    continue;
                                }
                                while (cursor.moveToNext()) {
                                    long eventId = cursor.getLong(0);
                                    String title = cursor.getString(1);
                                    String description = cursor.getString(2);
                                    long start = cursor.getLong(3);
                                    long end = cursor.isNull(4) ? start : cursor.getLong(4);
                                    boolean allDay = !cursor.isNull(5) && cursor.getInt(5) == 1;

                                    if (allDay) {
                                        // CR-014: Adjust for UTC offset to align all-day events with the user's local day
                                        java.util.TimeZone utc = java.util.TimeZone.getTimeZone("UTC");
                                        java.util.Calendar cal = java.util.Calendar.getInstance(utc);
                                        cal.setTimeInMillis(start);
                                        int startY = cal.get(java.util.Calendar.YEAR);
                                        int startM = cal.get(java.util.Calendar.MONTH);
                                        int startD = cal.get(java.util.Calendar.DAY_OF_MONTH);

                                        // Calculate duration in days and force Local midnight bounds
                                        long durationMs = end - start;
                                        int days = (int) Math.max(1, Math.round(durationMs / (24L * 60 * 60 * 1000.0)));

                                        cal = java.util.Calendar.getInstance(); // Uses default local timezone
                                        cal.set(startY, startM, startD, 0, 0, 0);
                                        cal.set(java.util.Calendar.MILLISECOND, 0);
                                        start = cal.getTimeInMillis();

                                        cal.add(java.util.Calendar.DAY_OF_MONTH, days);
                                        end = cal.getTimeInMillis();
                                    }
                                    if (title == null || title.trim().isEmpty() || calendarAlreadyImported(existingItems, eventId, title, start)) {
                                        continue;
                                    }
                                    Item item = new Item();
                                    item.title = title.trim();
                                    item.description = description;
                                    item.type = "event";
                                    item.startAt = start;
                                    item.endAt = end;
                                    item.allDay = allDay;
                                    item.status = "pending";
                                    item.priority = 1;
                                    item.createdAt = System.currentTimeMillis();
                                    item.calendarId = eventId;
                            item.label = LabelUtils.displayLabel(calendarName);
                                    imported.add(item);
                                }
                            }
                        }
                    }
                }
                if (!imported.isEmpty()) {
                    itemRepository.insertItems(imported);
                    assignColorsToImportedLabels(imported);
                    requireActivity().runOnUiThread(() -> toast("Imported " + imported.size() + " calendar events."));
                } else {
                    requireActivity().runOnUiThread(() -> toast("No new calendar events found."));
                }
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> toast("Calendar import failed: " + e.getMessage()));
            }
        });
    }

    private void importBirthdaysFromContacts() {
        ContactsBirthdaySync.sync(requireContext(), itemRepository, settingsRepository, (count, message) -> {
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> toast(message));
            }
        });
    }

    private boolean calendarAlreadyImported(List<Item> items, long eventId, String title, long startAt) {
        for (Item item : items) {
            if (item.calendarId != null && item.calendarId == eventId) {
                return true;
            }
            if (item.title != null && item.title.equalsIgnoreCase(title) && item.startAt != null && item.startAt == startAt) {
                return true;
            }
        }
        return false;
    }

    private long timeAt(int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private void assignColorsToImportedLabels(List<Item> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (Item item : items) {
            String label = LabelUtils.displayLabel(item != null ? item.label : null);
            if (label == null) {
                continue;
            }
            int defaultColor = LabelUtils.fallbackColor(label);
            settingsRepository.setLabelColor(label, settingsRepository.getLabelColor(label, defaultColor));
        }
    }

    private int dp(int value) {
        return (int) (value * requireContext().getResources().getDisplayMetrics().density);
    }

    private void exportIcsToUri(Uri uri) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String ics = IcsUtils.exportToIcs(itemRepository.getAllExportableItems());
                try (OutputStream os = requireContext().getContentResolver().openOutputStream(uri)) {
                    if (os != null) {
                        os.write(ics.getBytes(StandardCharsets.UTF_8));
                        requireActivity().runOnUiThread(() -> toast("Calendar exported successfully."));
                    }
                }
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> toast("Export failed: " + e.getMessage()));
            }
        });
    }

    private void importIcsFromUri(Uri uri) {
        showIcsImportOptions(uri);
    }

    private void showIcsImportOptions(Uri uri) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Import .ics file")
                .setItems(new CharSequence[]{
                        "Unlabeled",
                        "With a label",
                        "Use calendar names"
                }, (dialog, which) -> {
                    if (which == 0) {
                        importIcsWithLabel(uri, null, false);
                    } else if (which == 1) {
                        showIcsLabelPicker(uri);
                    } else {
                        importIcsWithLabel(uri, null, true);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showIcsLabelPicker(Uri uri) {
        LabelPickerBottomSheetFragment picker = LabelPickerBottomSheetFragment.newInstance(null);
        picker.setOnLabelSelectedListener(label -> importIcsWithLabel(uri, label, false));
        picker.show(getChildFragmentManager(), "ics_label_picker");
    }

    private void importIcsWithLabel(Uri uri, @Nullable String label, boolean useCalendarNames) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try (InputStream is = requireContext().getContentResolver().openInputStream(uri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                List<Item> importedItems = IcsUtils.importFromIcs(sb.toString(), label, useCalendarNames);
                if (!importedItems.isEmpty()) {
                    itemRepository.insertItems(importedItems);
                    assignColorsToImportedLabels(importedItems);
                    requireActivity().runOnUiThread(() -> toast("Imported " + importedItems.size() + " items."));
                } else {
                    requireActivity().runOnUiThread(() -> toast("No valid events found."));
                }
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> toast("Import failed: " + e.getMessage()));
            }
        });
    }

    private void exportJsonToUri(Uri uri) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String json = BackupUtils.createBackupJson(itemRepository);
                try (OutputStream os = requireContext().getContentResolver().openOutputStream(uri)) {
                    if (os != null) {
                        os.write(json.getBytes(StandardCharsets.UTF_8));
                        requireActivity().runOnUiThread(() -> toast("Data exported successfully."));
                    }
                }
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> toast("Export failed: " + e.getMessage()));
            }
        });
    }

    private void importJsonFromUri(Uri uri) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try (InputStream is = requireContext().getContentResolver().openInputStream(uri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                boolean success = BackupUtils.restoreFromBackup(sb.toString(), itemRepository);
                requireActivity().runOnUiThread(() -> toast(success ? "Data restored successfully." : "Import failed: invalid file."));
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> toast("Import failed: " + e.getMessage()));
            }
        });
    }


    private static class SectionHolder {
        final MaterialCardView card;
        final LinearLayout body;
        SectionHolder(MaterialCardView card, LinearLayout body) {
            this.card = card;
            this.body = body;
        }
    }

    private interface SwitchCallback {
        void onChanged(MaterialSwitch sw, boolean checked);
    }

    private interface SpinnerCallback {
        void onSelected(int position);
    }

    private interface TimeCallback {
        void onPicked(long timeValue);
    }
}
