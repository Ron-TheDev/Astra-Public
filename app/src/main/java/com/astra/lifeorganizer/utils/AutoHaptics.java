package com.astra.lifeorganizer.utils;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;

import com.astra.lifeorganizer.data.repositories.SettingsRepository;

/**
 * AutoHaptics v3 (stable production version)
 *
 * Fixes:
 * - No "fires once then stops" bug
 * - No scroll / move haptics
 * - No Vibrator API duplication
 * - No fragile coordinate-only hit system
 * - Stable across dialogs, fragments, RecyclerView, NavBar
 */
public final class AutoHaptics {

    private AutoHaptics() {}

    private static final long DEBOUNCE_MS = 80;
    private static long lastHapticTime = 0;

    // ------------------------------------------------------------
    // Enable
    // ------------------------------------------------------------
    public static void enable(@NonNull Activity activity) {
        Window window = activity.getWindow();
        if (window == null) return;

        Window.Callback current = window.getCallback();

        if (current instanceof HapticWindowCallback) return;

        window.setCallback(new HapticWindowCallback(current, window));
    }

    // ------------------------------------------------------------
    // Manual API
    // ------------------------------------------------------------
    public static void click(View view) {
        perform(view);
    }

    public static void longPress(View view) {
        perform(view);
    }

    // ------------------------------------------------------------
    // Core haptics
    // ------------------------------------------------------------
    public static void perform(View view) {
        if (view == null) return;

        Context context = view.getContext();

        if (!SettingsRepository.getInstance(context)
                .getBoolean(SettingsRepository.KEY_HAPTICS_ENABLED, true)) {
            return;
        }

        // debounce FIRST (critical)
        long now = SystemClock.elapsedRealtime();
        if (now - lastHapticTime < DEBOUNCE_MS) return;
        lastHapticTime = now;

        if (!view.isHapticFeedbackEnabled()) {
            view.setHapticFeedbackEnabled(true);
        }


        //Creator comment:
        // I tried view.performHapticFeedback but the vibrations kept getting blocked
        // Therefore I am brute forcing it
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(
                        50,
                        VibrationEffect.DEFAULT_AMPLITUDE
                ));
            } else {
                vibrator.vibrate(50);
            }
        }
    }

    // ------------------------------------------------------------
    // Window hook
    // ------------------------------------------------------------
    private static class HapticWindowCallback extends WindowCallbackWrapper {

        private final Window window;

        HapticWindowCallback(Window.Callback wrapped, Window window) {
            super(wrapped);
            this.window = window;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {

            if (event == null) return super.dispatchTouchEvent(event);

            int action = event.getActionMasked();

            // ONLY react on finger lift
            if (action != MotionEvent.ACTION_UP) {
                return super.dispatchTouchEvent(event);
            }

            // ignore multi-touch
            if (event.getPointerCount() > 1) {
                return super.dispatchTouchEvent(event);
            }

            View root = window.getDecorView();
            if (root != null) {

                // Stable target selection (no fragile recursion)
                View target = findClickableTarget(root, (int) event.getRawX(), (int) event.getRawY());

                if (target != null && isEligible(target) && !isOptedOut(target)) {
                    perform(target);
                }
            }

            return super.dispatchTouchEvent(event);
        }
    }

    // ------------------------------------------------------------
    // Stable hit detection (NOT leaf-only)
    // ------------------------------------------------------------
    private static View findClickableTarget(View root, int x, int y) {

        if (root == null || root.getVisibility() != View.VISIBLE) return null;

        int[] loc = new int[2];
        root.getLocationOnScreen(loc);

        boolean inside =
                x >= loc[0] && y >= loc[1] &&
                        x <= loc[0] + root.getWidth() &&
                        y <= loc[1] + root.getHeight();

        if (!inside) return null;

        if (root.isClickable() && root.isEnabled() && root.hasOnClickListeners()) {
            return root;
        }

        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View hit = findClickableTarget(group.getChildAt(i), x, y);
                if (hit != null) return hit;
            }
        }

        return null;
    }

    // ------------------------------------------------------------
    // Eligibility rules (safe & non-overstrict)
    // ------------------------------------------------------------
    private static boolean isEligible(View view) {
        return view != null
                && view.isClickable()
                && view.isEnabled()
                && view.getVisibility() == View.VISIBLE
                && view.hasOnClickListeners();
    }

    // ------------------------------------------------------------
    // Opt-out support
    // ------------------------------------------------------------
    private static boolean isOptedOut(View view) {
        Object tag = view.getTag(com.astra.lifeorganizer.R.id.no_haptics);
        return tag instanceof Boolean && (Boolean) tag;
    }

    // ------------------------------------------------------------
    // Window wrapper
    // ------------------------------------------------------------
    private static class WindowCallbackWrapper implements Window.Callback {

        private final Window.Callback wrapped;

        WindowCallbackWrapper(Window.Callback wrapped) {
            this.wrapped = wrapped;
        }

        @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
            return wrapped != null && wrapped.dispatchKeyEvent(event);
        }

        @Override public boolean dispatchKeyShortcutEvent(android.view.KeyEvent event) {
            return wrapped != null && wrapped.dispatchKeyShortcutEvent(event);
        }

        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            return wrapped != null && wrapped.dispatchTouchEvent(event);
        }

        @Override public boolean dispatchTrackballEvent(MotionEvent event) {
            return wrapped != null && wrapped.dispatchTrackballEvent(event);
        }

        @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
            return wrapped != null && wrapped.dispatchGenericMotionEvent(event);
        }

        @Override public boolean dispatchPopulateAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
            return wrapped != null && wrapped.dispatchPopulateAccessibilityEvent(event);
        }

        @Override public View onCreatePanelView(int featureId) {
            return wrapped != null ? wrapped.onCreatePanelView(featureId) : null;
        }

        @Override public boolean onCreatePanelMenu(int featureId, android.view.Menu menu) {
            return wrapped != null && wrapped.onCreatePanelMenu(featureId, menu);
        }

        @Override public boolean onPreparePanel(int featureId, View view, android.view.Menu menu) {
            return wrapped != null && wrapped.onPreparePanel(featureId, view, menu);
        }

        @Override public boolean onMenuOpened(int featureId, android.view.Menu menu) {
            return wrapped != null && wrapped.onMenuOpened(featureId, menu);
        }

        @Override public boolean onMenuItemSelected(int featureId, android.view.MenuItem item) {
            return wrapped != null && wrapped.onMenuItemSelected(featureId, item);
        }

        @Override public void onWindowAttributesChanged(android.view.WindowManager.LayoutParams attrs) {
            if (wrapped != null) wrapped.onWindowAttributesChanged(attrs);
        }

        @Override public void onContentChanged() {
            if (wrapped != null) wrapped.onContentChanged();
        }

        @Override public void onWindowFocusChanged(boolean hasFocus) {
            if (wrapped != null) wrapped.onWindowFocusChanged(hasFocus);
        }

        @Override public void onAttachedToWindow() {
            if (wrapped != null) wrapped.onAttachedToWindow();
        }

        @Override public void onDetachedFromWindow() {
            if (wrapped != null) wrapped.onDetachedFromWindow();
        }

        @Override public void onPanelClosed(int featureId, android.view.Menu menu) {
            if (wrapped != null) wrapped.onPanelClosed(featureId, menu);
        }

        @Override public boolean onSearchRequested() {
            return wrapped != null && wrapped.onSearchRequested();
        }

        @Override public boolean onSearchRequested(android.view.SearchEvent searchEvent) {
            return wrapped != null && wrapped.onSearchRequested(searchEvent);
        }

        @Override public android.view.ActionMode onWindowStartingActionMode(android.view.ActionMode.Callback callback) {
            return wrapped != null ? wrapped.onWindowStartingActionMode(callback) : null;
        }

        @Override public android.view.ActionMode onWindowStartingActionMode(android.view.ActionMode.Callback callback, int type) {
            return wrapped != null ? wrapped.onWindowStartingActionMode(callback, type) : null;
        }

        @Override public void onActionModeStarted(android.view.ActionMode mode) {
            if (wrapped != null) wrapped.onActionModeStarted(mode);
        }

        @Override public void onActionModeFinished(android.view.ActionMode mode) {
            if (wrapped != null) wrapped.onActionModeFinished(mode);
        }
    }
}