package com.astra.lifeorganizer.utils;

import android.app.Dialog;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public final class BottomSheetUtils {
    private BottomSheetUtils() {}

    public static void expandToViewport(@Nullable Dialog dialog) {
        if (!(dialog instanceof BottomSheetDialog)) {
            return;
        }

        BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) dialog;
        Window window = bottomSheetDialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        android.widget.FrameLayout bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) {
            return;
        }

        ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
        if (layoutParams != null) {
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            bottomSheet.setLayoutParams(layoutParams);
        }

        bottomSheet.post(() -> {
            BottomSheetBehavior<android.widget.FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
            behavior.setFitToContents(false);
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });
    }
}
