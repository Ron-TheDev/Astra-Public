package com.astra.lifeorganizer.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.astra.lifeorganizer.R;
import com.astra.lifeorganizer.utils.BottomSheetUtils;
import com.astra.lifeorganizer.utils.RecurrenceRuleParser;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

public class RecurrencePickerBottomSheet extends BottomSheetDialogFragment {

    private String currentRrule = "NONE";

    private RadioGroup rgFrequency;
    private LinearLayout layoutWeeklyOptions;
    private LinearLayout layoutMonthlyOptions;
    
    private Chip[] btnDays;
    
    private TabLayout tabsMonthlyType;
    private LinearLayout layoutMonthlyByDate;
    private LinearLayout layoutMonthlyByPattern;
    
    private Spinner spinnerMonthlyDate;
    private Spinner spinnerPatternWeek;
    private Spinner spinnerPatternDay;
    
    private TextView tvSummary;
    private Button btnSave;
    private Button btnCancel;

    public interface OnRecurrenceSetListener {
        void onRecurrenceSet(String rrule);
    }

    private OnRecurrenceSetListener listener;

    public static RecurrencePickerBottomSheet newInstance(String initialRrule) {
        RecurrencePickerBottomSheet fragment = new RecurrencePickerBottomSheet();
        Bundle args = new Bundle();
        args.putString("initial_rrule", initialRrule);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnRecurrenceSetListener(OnRecurrenceSetListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_recurrence_picker_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        if (getArguments() != null) {
            String initial = getArguments().getString("initial_rrule");
            if (initial != null && !initial.isEmpty()) {
                currentRrule = initial;
            }
        }

        bindViews(view);
        setupAdapters();
        setupListeners();
        restoreInitialState();
        updateSummary();
    }

    @Override
    public void onStart() {
        super.onStart();
        BottomSheetUtils.expandToViewport(getDialog());
    }

    private void bindViews(View view) {
        rgFrequency = view.findViewById(R.id.rg_frequency);
        layoutWeeklyOptions = view.findViewById(R.id.layout_weekly_options);
        layoutMonthlyOptions = view.findViewById(R.id.layout_monthly_options);
        
        btnDays = new Chip[]{
            view.findViewById(R.id.btn_day_mo),
            view.findViewById(R.id.btn_day_tu),
            view.findViewById(R.id.btn_day_we),
            view.findViewById(R.id.btn_day_th),
            view.findViewById(R.id.btn_day_fr),
            view.findViewById(R.id.btn_day_sa),
            view.findViewById(R.id.btn_day_su)
        };
        
        tabsMonthlyType = view.findViewById(R.id.tabs_monthly_type);
        layoutMonthlyByDate = view.findViewById(R.id.layout_monthly_by_date);
        layoutMonthlyByPattern = view.findViewById(R.id.layout_monthly_by_pattern);
        
        spinnerMonthlyDate = view.findViewById(R.id.spinner_monthly_date);
        spinnerPatternWeek = view.findViewById(R.id.spinner_pattern_week);
        spinnerPatternDay = view.findViewById(R.id.spinner_pattern_day);
        
        tvSummary = view.findViewById(R.id.tv_summary);
        btnSave = view.findViewById(R.id.btn_save);
        btnCancel = view.findViewById(R.id.btn_cancel);
    }
    
    private void setupAdapters() {
        // Date spinner: 1 to 31, plus 'Last day'
        List<String> dates = new ArrayList<>();
        for (int i = 1; i <= 31; i++) dates.add(String.valueOf(i));
        dates.add("Last day");
        ArrayAdapter<String> dateAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, dates);
        dateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMonthlyDate.setAdapter(dateAdapter);

        // Pattern Week: First, Second, Third, Fourth, Last
        String[] weeks = {"First", "Second", "Third", "Fourth", "Last"};
        ArrayAdapter<String> weekAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, weeks);
        weekAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPatternWeek.setAdapter(weekAdapter);

        // Pattern Day: Monday...Sunday
        String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        ArrayAdapter<String> dayAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, days);
        dayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPatternDay.setAdapter(dayAdapter);
    }

    private void setupListeners() {
        rgFrequency.setOnCheckedChangeListener((group, checkedId) -> {
            updateVisibility();
            generateRruleAndUpdateSummary();
        });

        for (Chip btn : btnDays) {
            btn.setOnCheckedChangeListener((buttonView, isChecked) -> generateRruleAndUpdateSummary());
        }

        tabsMonthlyType.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    layoutMonthlyByDate.setVisibility(View.VISIBLE);
                    layoutMonthlyByPattern.setVisibility(View.GONE);
                } else {
                    layoutMonthlyByDate.setVisibility(View.GONE);
                    layoutMonthlyByPattern.setVisibility(View.VISIBLE);
                }
                generateRruleAndUpdateSummary();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        AdapterView.OnItemSelectedListener spinnerListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                generateRruleAndUpdateSummary();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };
        spinnerMonthlyDate.setOnItemSelectedListener(spinnerListener);
        spinnerPatternWeek.setOnItemSelectedListener(spinnerListener);
        spinnerPatternDay.setOnItemSelectedListener(spinnerListener);

        btnSave.setOnClickListener(v -> {
            generateRruleAndUpdateSummary();
            if (listener != null) {
                listener.onRecurrenceSet(currentRrule);
            }
            dismiss();
        });

        btnCancel.setOnClickListener(v -> dismiss());
    }

    private void updateVisibility() {
        int checkedId = rgFrequency.getCheckedRadioButtonId();
        layoutWeeklyOptions.setVisibility(checkedId == R.id.rb_weekly ? View.VISIBLE : View.GONE);
        layoutMonthlyOptions.setVisibility(checkedId == R.id.rb_monthly ? View.VISIBLE : View.GONE);
    }

    private void restoreInitialState() {
        if (currentRrule.equals("NONE")) {
            rgFrequency.check(R.id.rb_none);
        } else if (currentRrule.contains("FREQ=DAILY")) {
            rgFrequency.check(R.id.rb_daily);
        } else if (currentRrule.contains("FREQ=WEEKLY")) {
            rgFrequency.check(R.id.rb_weekly);
            if (currentRrule.contains("BYDAY=")) {
                String daysStr = currentRrule.substring(currentRrule.indexOf("BYDAY=") + 6);
                btnDays[0].setChecked(daysStr.contains("MO"));
                btnDays[1].setChecked(daysStr.contains("TU"));
                btnDays[2].setChecked(daysStr.contains("WE"));
                btnDays[3].setChecked(daysStr.contains("TH"));
                btnDays[4].setChecked(daysStr.contains("FR"));
                btnDays[5].setChecked(daysStr.contains("SA"));
                btnDays[6].setChecked(daysStr.contains("SU"));
            }
        } else if (currentRrule.contains("FREQ=MONTHLY")) {
            rgFrequency.check(R.id.rb_monthly);
            if (currentRrule.contains("BYMONTHDAY=")) {
                tabsMonthlyType.getTabAt(0).select();
                String dateStr = currentRrule.substring(currentRrule.indexOf("BYMONTHDAY=") + 11);
                if (dateStr.equals("-1")) {
                    spinnerMonthlyDate.setSelection(31); // "Last day"
                } else {
                    int day = Integer.parseInt(dateStr);
                    spinnerMonthlyDate.setSelection(day - 1);
                }
            } else if (currentRrule.contains("BYSETPOS=") && currentRrule.contains("BYDAY=")) {
                tabsMonthlyType.getTabAt(1).select();
                String setPosStr = currentRrule.substring(currentRrule.indexOf("BYSETPOS=") + 9);
                if (setPosStr.contains(";")) setPosStr = setPosStr.substring(0, setPosStr.indexOf(";"));
                String dayStr = currentRrule.substring(currentRrule.indexOf("BYDAY=") + 6);
                if (dayStr.contains(";")) dayStr = dayStr.substring(0, dayStr.indexOf(";"));
                
                int weekIndex = 0;
                switch (setPosStr) {
                    case "1": weekIndex = 0; break;
                    case "2": weekIndex = 1; break;
                    case "3": weekIndex = 2; break;
                    case "4": weekIndex = 3; break;
                    case "-1": weekIndex = 4; break;
                }
                spinnerPatternWeek.setSelection(weekIndex);

                int dayIndex = 0;
                switch (dayStr) {
                    case "MO": dayIndex = 0; break;
                    case "TU": dayIndex = 1; break;
                    case "WE": dayIndex = 2; break;
                    case "TH": dayIndex = 3; break;
                    case "FR": dayIndex = 4; break;
                    case "SA": dayIndex = 5; break;
                    case "SU": dayIndex = 6; break;
                }
                spinnerPatternDay.setSelection(dayIndex);
            }
        }
        updateVisibility();
    }

    private void generateRruleAndUpdateSummary() {
        int checkedId = rgFrequency.getCheckedRadioButtonId();
        
        if (checkedId == R.id.rb_none) {
            currentRrule = "NONE";
        } else if (checkedId == R.id.rb_daily) {
            currentRrule = "FREQ=DAILY";
        } else if (checkedId == R.id.rb_weekly) {
            List<String> selectedDays = new ArrayList<>();
            if (btnDays[0].isChecked()) selectedDays.add("MO");
            if (btnDays[1].isChecked()) selectedDays.add("TU");
            if (btnDays[2].isChecked()) selectedDays.add("WE");
            if (btnDays[3].isChecked()) selectedDays.add("TH");
            if (btnDays[4].isChecked()) selectedDays.add("FR");
            if (btnDays[5].isChecked()) selectedDays.add("SA");
            if (btnDays[6].isChecked()) selectedDays.add("SU");
            
            if (selectedDays.isEmpty()) {
                currentRrule = "FREQ=WEEKLY"; // Default fallback
            } else {
                currentRrule = "FREQ=WEEKLY;BYDAY=" + String.join(",", selectedDays);
            }
        } else if (checkedId == R.id.rb_monthly) {
            int selectedTab = tabsMonthlyType.getSelectedTabPosition();
            if (selectedTab == 0) { // By Date
                int pos = spinnerMonthlyDate.getSelectedItemPosition();
                if (pos == 31) { // "Last day"
                    currentRrule = "FREQ=MONTHLY;BYMONTHDAY=-1";
                } else {
                    currentRrule = "FREQ=MONTHLY;BYMONTHDAY=" + (pos + 1);
                }
            } else { // By Pattern
                int wPos = spinnerPatternWeek.getSelectedItemPosition();
                int dPos = spinnerPatternDay.getSelectedItemPosition();
                
                String posStr = "1";
                switch (wPos) {
                    case 0: posStr = "1"; break;
                    case 1: posStr = "2"; break;
                    case 2: posStr = "3"; break;
                    case 3: posStr = "4"; break;
                    case 4: posStr = "-1"; break;
                }
                
                String dayStr = "MO";
                switch (dPos) {
                    case 0: dayStr = "MO"; break;
                    case 1: dayStr = "TU"; break;
                    case 2: dayStr = "WE"; break;
                    case 3: dayStr = "TH"; break;
                    case 4: dayStr = "FR"; break;
                    case 5: dayStr = "SA"; break;
                    case 6: dayStr = "SU"; break;
                }
                
                currentRrule = "FREQ=MONTHLY;BYSETPOS=" + posStr + ";BYDAY=" + dayStr;
            }
        }
        
        updateSummary();
    }

    private void updateSummary() {
        tvSummary.setText(RecurrenceRuleParser.getHumanReadableSummary(currentRrule));
    }
}
