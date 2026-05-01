package com.astra.lifeorganizer.ui;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.lifecycle.ViewModelProvider;

import com.astra.lifeorganizer.R;
import com.astra.lifeorganizer.data.repositories.SettingsRepository;
import com.astra.lifeorganizer.utils.BottomSheetUtils;
import com.astra.lifeorganizer.utils.LabelUtils;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

public class LabelPickerBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String ARG_INITIAL_LABEL = "arg_initial_label";

    private ItemViewModel itemViewModel;
    private ChipGroup chipGroup;
    private LinearLayout layoutCreateLabel;
    private TextInputLayout inputLayout;
    private TextInputEditText etLabelName;
    private TextView tvEmptyState;
    private Button btnAddNewLabel;
    private Button btnCancelCreate;
    private ChipGroup chipGroupColors;
    private SettingsRepository settingsRepository;

    private final List<String> labels = new ArrayList<>();
    private String initialLabel = null;
    private boolean createInputVisible = false;
    private int selectedColor = 0xFF2563EB;

    public interface OnLabelSelectedListener {
        void onLabelSelected(String label);
    }

    private OnLabelSelectedListener listener;

    public static LabelPickerBottomSheetFragment newInstance(@Nullable String initialLabel) {
        LabelPickerBottomSheetFragment fragment = new LabelPickerBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_INITIAL_LABEL, initialLabel);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnLabelSelectedListener(OnLabelSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            initialLabel = getArguments().getString(ARG_INITIAL_LABEL);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_label_picker_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        itemViewModel = new ViewModelProvider(requireActivity()).get(ItemViewModel.class);
        settingsRepository = SettingsRepository.getInstance(requireContext());

        chipGroup = view.findViewById(R.id.chip_group_labels);
        layoutCreateLabel = view.findViewById(R.id.layout_create_label);
        inputLayout = view.findViewById(R.id.input_layout_label_name);
        etLabelName = view.findViewById(R.id.et_label_name);
        tvEmptyState = view.findViewById(R.id.tv_empty_state);
        btnAddNewLabel = view.findViewById(R.id.btn_add_new_label);
        btnCancelCreate = view.findViewById(R.id.btn_cancel_create);
        chipGroupColors = view.findViewById(R.id.chip_group_label_colors);

        btnAddNewLabel.setOnClickListener(v -> {
            if (createInputVisible) {
                submitLabel();
            } else {
                showCreateInput();
            }
        });
        btnCancelCreate.setOnClickListener(v -> hideCreateInput());

        etLabelName.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                updateCreateActionState();
            }
        });

        etLabelName.setOnEditorActionListener((v, actionId, event) -> {
            submitLabel();
            return true;
        });

        itemViewModel.getDistinctLabels().observe(getViewLifecycleOwner(), existingLabels -> {
            labels.clear();
            if (existingLabels != null) {
                for (String label : existingLabels) {
                    String normalized = LabelUtils.normalizeLabel(label);
                    if (normalized != null && !containsLabel(normalized)) {
                        labels.add(normalized);
                    }
                }
            }

            String normalizedInitial = LabelUtils.normalizeLabel(initialLabel);
            if (normalizedInitial != null && !containsLabel(normalizedInitial)) {
                labels.add(0, normalizedInitial);
            }

            renderLabels();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        BottomSheetUtils.expandToViewport(getDialog());
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void renderLabels() {
        chipGroup.removeAllViews();

        tvEmptyState.setVisibility(labels.isEmpty() ? View.VISIBLE : View.GONE);
        btnAddNewLabel.setText(labels.isEmpty() ? "Create your first label" : "+ Add new label");
        if (labels.isEmpty() && !createInputVisible) {
            showCreateInput();
        }

        for (String label : labels) {
            Chip chip = createChip(label);
            chip.setChecked(initialLabel != null && label.equalsIgnoreCase(initialLabel.trim()));
            chipGroup.addView(chip);
        }
    }

    private Chip createChip(String label) {
        Chip chip = new Chip(requireContext());
        chip.setId(View.generateViewId());
        chip.setText(LabelUtils.displayLabel(label));
        chip.setCheckable(true);
        chip.setClickable(true);
        chip.setCheckedIconVisible(true);
        chip.setChipIconVisible(false);
        chip.setMaxLines(1);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setCloseIconVisible(false);

        int baseColor = settingsRepository.getLabelColor(label, LabelUtils.fallbackColor(label));
        chip.setChipBackgroundColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(baseColor, 36)));
        chip.setTextColor(baseColor);
        chip.setChipStrokeColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(baseColor, 120)));
        chip.setChipStrokeWidth(2f);

        chip.setOnClickListener(v -> selectLabel(label));
        return chip;
    }

    private void selectLabel(String label) {
        if (listener != null) {
            listener.onLabelSelected(LabelUtils.normalizeLabel(label));
        }
        dismiss();
    }

    private void showCreateInput() {
        createInputVisible = true;
        layoutCreateLabel.setVisibility(View.VISIBLE);
        btnAddNewLabel.setText("Create label");
        updateCreateActionState();
        inputLayout.setError(null);
        if (chipGroupColors.getChildCount() == 0) {
            buildColorPalette();
        }
        if (chipGroupColors.getCheckedChipId() == View.NO_ID && chipGroupColors.getChildCount() > 0) {
            Chip first = (Chip) chipGroupColors.getChildAt(0);
            first.setChecked(true);
        }
        if (chipGroupColors.getCheckedChipId() != View.NO_ID) {
            View checked = chipGroupColors.findViewById(chipGroupColors.getCheckedChipId());
            if (checked instanceof Chip) {
                selectedColor = ((Chip) checked).getChipBackgroundColor().getDefaultColor();
            }
        }
        etLabelName.requestFocus();
        etLabelName.post(() -> {
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etLabelName, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void hideCreateInput() {
        createInputVisible = false;
        layoutCreateLabel.setVisibility(View.GONE);
        btnAddNewLabel.setText(labels.isEmpty() ? "Create your first label" : "+ Add new label");
        btnAddNewLabel.setEnabled(true);
        inputLayout.setError(null);
        etLabelName.setText("");
        if (chipGroupColors != null) {
            chipGroupColors.clearCheck();
        }
        inputLayout.clearFocus();
    }

    private void submitLabel() {
        String raw = etLabelName.getText() != null ? etLabelName.getText().toString() : "";
        String trimmed = LabelUtils.normalizeLabel(raw);

        if (trimmed == null) {
            inputLayout.setError("Label cannot be empty");
            return;
        }

        inputLayout.setError(null);

        String existing = findMatchingLabel(trimmed);
        if (existing != null) {
            Toast.makeText(requireContext(), "Label already exists", Toast.LENGTH_SHORT).show();
            selectLabel(existing);
            return;
        }

        settingsRepository.setLabelColor(trimmed, selectedColor);
        selectLabel(trimmed);
    }

    private void updateCreateActionState() {
        if (btnAddNewLabel == null) {
            return;
        }
        if (!createInputVisible) {
            btnAddNewLabel.setEnabled(true);
            btnAddNewLabel.setText(labels.isEmpty() ? "Create your first label" : "+ Add new label");
            return;
        }
        String raw = etLabelName.getText() != null ? etLabelName.getText().toString() : "";
        btnAddNewLabel.setEnabled(LabelUtils.normalizeLabel(raw) != null);
        btnAddNewLabel.setText("Create label");
    }

    @Nullable
    private String findMatchingLabel(String label) {
        for (String existing : labels) {
            if (existing != null && existing.trim().equalsIgnoreCase(label.trim())) {
                return existing;
            }
        }
        return null;
    }

    private boolean containsLabel(String label) {
        return findMatchingLabel(label) != null;
    }

    private void buildColorPalette() {
        chipGroupColors.removeAllViews();
        int[][] palette = new int[][]{
                {0xFF2563EB, 0xFF1D4ED8}, // Blue
                {0xFF7C3AED, 0xFF6D28D9}, // Purple
                {0xFFDC2626, 0xFFB91C1C}, // Red
                {0xFFF97316, 0xFFEA580C}, // Orange
                {0xFF16A34A, 0xFF15803D}, // Green
                {0xFF0EA5E9, 0xFF0284C7}  // Sky
        };
        String[] names = new String[]{"Blue", "Purple", "Red", "Orange", "Green", "Sky"};
        int firstChipId = View.NO_ID;
        for (int i = 0; i < palette.length; i++) {
            Chip chip = new Chip(requireContext());
            chip.setId(View.generateViewId());
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setSingleLine(true);
            chip.setText(names[i]);
            chip.setTextColor(0xFFFFFFFF);
            chip.setChipBackgroundColor(ColorStateList.valueOf(palette[i][0]));
            chip.setChipStrokeColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette[i][1], 180)));
            chip.setChipStrokeWidth(2f);
            chip.setCheckedIconVisible(true);
            if (i == 0) {
                selectedColor = palette[i][0];
                firstChipId = chip.getId();
            }
            int finalIndex = i;
            chip.setOnClickListener(v -> {
                selectedColor = palette[finalIndex][0];
                chip.setChecked(true);
            });
            chipGroupColors.addView(chip);
        }
        if (firstChipId != View.NO_ID) {
            chipGroupColors.check(firstChipId);
        }
    }
}
