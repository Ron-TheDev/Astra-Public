package com.astra.lifeorganizer;

import static androidx.core.content.ContentProviderCompat.requireContext;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.content.Intent;
import android.Manifest;
import android.app.AlarmManager;
import android.content.pm.PackageManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.view.WindowManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.data.repositories.ItemRepository;
import com.astra.lifeorganizer.data.repositories.SettingsRepository;
import com.astra.lifeorganizer.ui.AddTaskBottomSheetFragment;
import com.astra.lifeorganizer.ui.ItemViewModel;
import com.astra.lifeorganizer.utils.AlarmConstants;
import com.astra.lifeorganizer.utils.ContactsBirthdaySync;
import com.astra.lifeorganizer.utils.NotificationHelper;
import com.astra.lifeorganizer.utils.RecapBriefConstants;
import com.astra.lifeorganizer.utils.RecapBriefScheduler;
import com.astra.lifeorganizer.widgets.WidgetConstants;
import com.astra.lifeorganizer.widgets.WidgetUpdater;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import android.view.Menu;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {
    
    private ItemViewModel itemViewModel;
    private boolean isSpeedDialOpen = false;
    private View layoutSpeedDial;
    private FloatingActionButton fabAdd;
    private int currentDestinationId = -1;
    private boolean alarmIntentHandled = false;
    private String pendingWidgetPage = null;
    private Long pendingWidgetItemId = null;
    private String pendingWidgetItemType = null;
    private NavController navController;
    private ActivityResultLauncher<String[]> startupPermissionsLauncher;
    private ActivityResultLauncher<Intent> exactAlarmPermissionLauncher;
    private boolean exactAlarmPermissionRequested = false;
    private boolean biometricPromptShowing = false;
    private boolean biometricUnlockedForSession = false;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo biometricPromptInfo;
    private BiometricActionCallback biometricActionCallback;
    private boolean biometricFinishOnFailure = true;
    private boolean mainUiInitialized = false;

    public interface BiometricActionCallback {
        void onSuccess();
        void onFailure();
    }

    private static boolean sessionUnlockedStatic = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(SettingsRepository.resolveThemeStyle(this));
        AppCompatDelegate.setDefaultNightMode(SettingsRepository.resolveNightMode(this));
        super.onCreate(savedInstanceState);
        
        SettingsRepository settingsRepo = SettingsRepository.getInstance(this);
        boolean blackTheme = settingsRepo.getBoolean(
                SettingsRepository.KEY_BLACK_THEME,
                settingsRepo.getInt(SettingsRepository.KEY_NIGHT_MODE, SettingsRepository.NIGHT_MODE_SYSTEM) == SettingsRepository.NIGHT_MODE_DARK
        );
        if (settingsRepo.getInt(SettingsRepository.KEY_NIGHT_MODE, SettingsRepository.NIGHT_MODE_SYSTEM) == SettingsRepository.NIGHT_MODE_LIGHT) {
            blackTheme = false;
        }
        if (settingsRepo.getBoolean(SettingsRepository.KEY_DYNAMIC_THEME, true) && !blackTheme) {
            DynamicColors.applyToActivityIfAvailable(this);
        }
        applyPrivacySettings();

        NotificationHelper.createNotificationChannel(this);
        registerPermissionLaunchers();
        initBiometricAuth();

        boolean isRecreation = savedInstanceState != null;
        if (isRecreation) {
            biometricUnlockedForSession = sessionUnlockedStatic;
        }

        if (!biometricUnlockedForSession && shouldDeferMainUi()) {
            setContentView(R.layout.activity_biometric_gate);
            maybePromptBiometric();
        } else {
            initializeMainUi();
            // Only perform heavy startup tasks on fresh launch
            if (!isRecreation) {
                requestStartupPermissions();
                RecapBriefScheduler.rescheduleAll(this);
                WidgetUpdater.updateAll(this);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        applyPrivacySettings();
        if (!mainUiInitialized && shouldDeferMainUi()) {
            maybePromptBiometric();
        } else if (mainUiInitialized) {
            maybePromptBiometric();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        biometricUnlockedForSession = false;
        biometricPromptShowing = false;
        biometricActionCallback = null;
        biometricFinishOnFailure = true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        alarmIntentHandled = false;
        handleNotificationClose(intent);
        handleAlarmOpenIntent(intent);
        handleWidgetOpenIntent(intent);
        handleIncomingIcs(intent);
        processPendingWidgetOpen();
    }

    private void handleIncomingIcs(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String type = intent.getType();
        
        if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_SEND.equals(action)) {
            Uri uri = null;
            if (Intent.ACTION_VIEW.equals(action)) {
                uri = intent.getData();
            } else if (Intent.ACTION_SEND.equals(action)) {
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }
            
            if (uri != null && (type == null || type.contains("calendar") || type.contains("octet-stream") || intent.getDataString() != null && intent.getDataString().endsWith(".ics"))) {
                showIcsImportDialog(uri);
            }
        }
    }

    private void showIcsImportDialog(Uri uri) {
        new AlertDialog.Builder(this)
                .setTitle("Import calendar")
                .setMessage("Astra Todo found a calendar file. Would you like to import its events?")
                .setPositiveButton("Import", (dialog, which) -> {
                    importIcsFromUri(uri);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void importIcsFromUri(Uri uri) {
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            try (java.io.InputStream is = getContentResolver().openInputStream(uri);
                 java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                List<Item> importedItems = com.astra.lifeorganizer.utils.IcsUtils.importFromIcs(sb.toString(), null, true);
                if (!importedItems.isEmpty()) {
                    new ItemRepository(getApplication()).insertItems(importedItems);
                    runOnUiThread(() -> android.widget.Toast.makeText(this, "Imported " + importedItems.size() + " items.", android.widget.Toast.LENGTH_SHORT).show());
                } else {
                    runOnUiThread(() -> android.widget.Toast.makeText(this, "No valid events found.", android.widget.Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> android.widget.Toast.makeText(this, "Import failed: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void handleNotificationClose(Intent intent) {
        if (intent == null) return;
        int notificationId = intent.getIntExtra("notification_id", -1);
        if (notificationId != -1) {
            NotificationHelper.cancelNotification(this, notificationId);
        }
    }

    private void registerPermissionLaunchers() {
        startupPermissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    requestExactAlarmPermissionIfNeeded();
                    maybeSyncBirthdays();
                }
        );
        exactAlarmPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> exactAlarmPermissionRequested = false
        );
    }

    private void requestStartupPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CONTACTS);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CALENDAR);
        }
        if (!permissions.isEmpty() && startupPermissionsLauncher != null) {
            startupPermissionsLauncher.launch(permissions.toArray(new String[0]));
        } else {
            requestExactAlarmPermissionIfNeeded();
            maybeSyncBirthdays();
        }
    }

    private void requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null && !alarmManager.canScheduleExactAlarms() && !exactAlarmPermissionRequested) {
            exactAlarmPermissionRequested = true;
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + getPackageName()));
            if (exactAlarmPermissionLauncher != null) {
                exactAlarmPermissionLauncher.launch(intent);
            }
        }
    }

    private void updateMenuItemVisibility(NavController navController, Menu menu, int itemId, boolean enabled) {
        if (menu.findItem(itemId) != null) {
            menu.findItem(itemId).setVisible(enabled);
        }
        if (currentDestinationId == itemId && !enabled) {
            navigateToFirstVisiblePage(navController, menu);
        }
    }

    private void navigateToFirstVisiblePage(NavController navController, Menu menu) {
        int[] order = {
                R.id.HomepageFragment,
                R.id.TodoListFragment,
                R.id.HabitTrackerFragment,
                R.id.CalendarFragment
        };
        for (int destinationId : order) {
            if (menu.findItem(destinationId) != null && menu.findItem(destinationId).isVisible()) {
                navController.navigate(destinationId);
                return;
            }
        }
    }

    private void toggleSpeedDial() {
        if (!isSpeedDialOpen) {
            layoutSpeedDial.setVisibility(View.VISIBLE);
            layoutSpeedDial.animate().alpha(1f).translationY(0f).setDuration(300).start();
            fabAdd.animate().rotation(45f).setDuration(300).start();
            isSpeedDialOpen = true;
        } else {
            layoutSpeedDial.animate().alpha(0f).translationY(100f).setDuration(300)
                .withEndAction(() -> layoutSpeedDial.setVisibility(View.GONE)).start();
            fabAdd.animate().rotation(0f).setDuration(300).start();
            isSpeedDialOpen = false;
        }
    }

    private void openAddTask(String type) {
        AddTaskBottomSheetFragment.newInstance(type, null).show(getSupportFragmentManager(), "AddTaskBottomSheetFragment");
    }

    private void handleAlarmOpenIntent(Intent intent) {
        if (alarmIntentHandled || intent == null) {
            return;
        }

        long itemId = intent.getLongExtra(AlarmConstants.EXTRA_TARGET_ITEM_ID, 0L);
        String type = intent.getStringExtra(AlarmConstants.EXTRA_ITEM_TYPE);
        if (itemId <= 0 || type == null) {
            return;
        }

        alarmIntentHandled = true;
        openEditItem(type, itemId);
    }


    private void handleWidgetOpenIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        String page = intent.getStringExtra(WidgetConstants.EXTRA_WIDGET_PAGE);
        pendingWidgetPage = null;
        if (page != null && !page.trim().isEmpty()) {
            pendingWidgetPage = page.trim().toLowerCase();
        }

        long itemId = intent.getLongExtra(WidgetConstants.EXTRA_WIDGET_ITEM_ID, 0L);
        pendingWidgetItemId = null;
        pendingWidgetItemType = null;
        if (itemId > 0L) {
            pendingWidgetItemId = itemId;
            pendingWidgetItemType = intent.getStringExtra(WidgetConstants.EXTRA_WIDGET_ITEM_TYPE);
        }
    }


    private void processPendingWidgetOpen() {
        if (pendingWidgetItemId != null && pendingWidgetItemId > 0L && pendingWidgetItemType != null) {
            openEditItem(pendingWidgetItemType, pendingWidgetItemId);
            pendingWidgetItemId = null;
            pendingWidgetItemType = null;
            return;
        }

        if (navController == null || pendingWidgetPage == null) {
            return;
        }

        int destinationId = -1;
        if ("home".equals(pendingWidgetPage)) {
            destinationId = R.id.HomepageFragment;
        } else if ("todo".equals(pendingWidgetPage)) {
            destinationId = R.id.TodoListFragment;
        } else if ("calendar".equals(pendingWidgetPage) || "upcoming".equals(pendingWidgetPage)) {
            destinationId = R.id.CalendarFragment;
        }

        if (destinationId != -1 && currentDestinationId != destinationId) {
            navController.navigate(destinationId);
        }
        pendingWidgetPage = null;
    }

    private void openEditItem(String type, long itemId) {
        AddTaskBottomSheetFragment.newInstance(type, itemId)
                .show(getSupportFragmentManager(), "AlarmOpenItem");
    }

    public void applyPrivacySettings() {
        SettingsRepository settingsRepository = SettingsRepository.getInstance(this);
        boolean preventScreenshots = settingsRepository.getBoolean(SettingsRepository.KEY_PREVENT_SCREENSHOTS, false);
        if (preventScreenshots) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    public void enforceBiometricLockIfEnabled() {
        requestBiometricAuthentication(true, null);
    }

    private boolean shouldDeferMainUi() {
        return SettingsRepository.getInstance(this).getBoolean(SettingsRepository.KEY_BIOMETRIC_LOCK, false);
    }

    private void initializeMainUi() {
        if (mainUiInitialized) {
            return;
        }
        mainUiInitialized = true;

        setContentView(R.layout.activity_main);
        com.astra.lifeorganizer.utils.AutoHaptics.enable(this);

        itemViewModel = new ViewModelProvider(this).get(ItemViewModel.class);
        handleNotificationClose(getIntent());
        handleAlarmOpenIntent(getIntent());
        handleWidgetOpenIntent(getIntent());

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_content_main);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();

            BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
            NavigationUI.setupWithNavController(bottomNav, navController);

            Menu menu = bottomNav.getMenu();

            bottomNav.setOnItemSelectedListener(item -> {
                return NavigationUI.onNavDestinationSelected(item, navController);
            });


            SettingsRepository settingsRepo = SettingsRepository.getInstance(this);

            settingsRepo.isHomeEnabled.observe(this, enabled ->
                updateMenuItemVisibility(navController, menu, R.id.HomepageFragment, enabled)
            );
            settingsRepo.isTodoEnabled.observe(this, enabled ->
                updateMenuItemVisibility(navController, menu, R.id.TodoListFragment, enabled)
            );
            settingsRepo.isHabitsEnabled.observe(this, enabled ->
                updateMenuItemVisibility(navController, menu, R.id.HabitTrackerFragment, enabled)
            );
            settingsRepo.isCalendarEnabled.observe(this, enabled ->
                updateMenuItemVisibility(navController, menu, R.id.CalendarFragment, enabled)
            );

            processPendingWidgetOpen();
        }
        handleIncomingIcs(getIntent());

        fabAdd = findViewById(R.id.fab_add);
        layoutSpeedDial = findViewById(R.id.layout_speed_dial);

        fabAdd.setOnClickListener(v -> {
            if (currentDestinationId == R.id.HomepageFragment) {
                toggleSpeedDial();
            } else if (currentDestinationId == R.id.TodoListFragment) {
                openAddTask("todo");
            } else if (currentDestinationId == R.id.HabitTrackerFragment) {
                openAddTask("habit");
            } else if (currentDestinationId == R.id.CalendarFragment) {
                openAddTask("event");
            } else {
                showAddItemDialog();
            }
        });

        findViewById(R.id.fab_new_todo).setOnClickListener(v -> { toggleSpeedDial(); openAddTask("todo"); });
        findViewById(R.id.fab_new_habit).setOnClickListener(v -> { toggleSpeedDial(); openAddTask("habit"); });
        findViewById(R.id.fab_new_event).setOnClickListener(v -> { toggleSpeedDial(); openAddTask("event"); });

        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                currentDestinationId = destination.getId();
                if (isSpeedDialOpen) toggleSpeedDial();

                if (currentDestinationId == R.id.SettingsFragment) {
                    fabAdd.hide();
                } else {
                    fabAdd.show();
                    if (currentDestinationId == R.id.TodoListFragment) {
                        fabAdd.setImageResource(android.R.drawable.ic_menu_edit);
                    } else if (currentDestinationId == R.id.HabitTrackerFragment) {
                        fabAdd.setImageResource(android.R.drawable.ic_menu_recent_history);
                    } else if (currentDestinationId == R.id.CalendarFragment) {
                        fabAdd.setImageResource(android.R.drawable.ic_menu_today);
                    } else {
                        fabAdd.setImageResource(android.R.drawable.ic_input_add);
                    }
                }
            });
        }
    }

    private void initBiometricAuth() {
        Executor executor = ContextCompat.getMainExecutor(this);
        biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                biometricUnlockedForSession = true;
                sessionUnlockedStatic = true;
                biometricPromptShowing = false;
                BiometricActionCallback callback = biometricActionCallback;
                biometricActionCallback = null;
                if (callback != null) {
                    callback.onSuccess();
                }
                if (!mainUiInitialized) {
                    initializeMainUi();
                }
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                biometricPromptShowing = false;
                biometricUnlockedForSession = false;
                sessionUnlockedStatic = false;
                BiometricActionCallback callback = biometricActionCallback;
                biometricActionCallback = null;
                if (callback != null) {
                    callback.onFailure();
                } else if (biometricFinishOnFailure && SettingsRepository.getInstance(MainActivity.this).getBoolean(SettingsRepository.KEY_BIOMETRIC_LOCK, false)) {
                    finishAndRemoveTask();
                }
            }
        });
        biometricPromptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Astra Todo")
                .setSubtitle("Use your fingerprint or device credential to continue")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();
    }

    private void maybePromptBiometric() {
        requestBiometricAuthentication(true, null);
    }

    public void requestBiometricAuthentication(boolean finishOnFailure, @Nullable BiometricActionCallback callback) {
        requestBiometricAuthentication(finishOnFailure, false, callback);
    }

    public void requestBiometricAuthentication(boolean finishOnFailure, boolean forcePrompt, @Nullable BiometricActionCallback callback) {
        SettingsRepository settingsRepository = SettingsRepository.getInstance(this);
        if (!forcePrompt && !settingsRepository.getBoolean(SettingsRepository.KEY_BIOMETRIC_LOCK, false)) {
            biometricUnlockedForSession = true;
            if (callback != null) {
                callback.onSuccess();
            }
            return;
        }
        if (biometricPrompt == null || biometricPromptInfo == null) {
            if (callback != null) {
                callback.onFailure();
            } else if (finishOnFailure && settingsRepository.getBoolean(SettingsRepository.KEY_BIOMETRIC_LOCK, false)) {
                finishAndRemoveTask();
            } else {
                biometricUnlockedForSession = true;
            }
            return;
        }
        if (!forcePrompt && (biometricUnlockedForSession || biometricPromptShowing || biometricPrompt == null)) {
            if (callback != null && biometricUnlockedForSession) {
                callback.onSuccess();
            }
            return;
        }
        BiometricManager biometricManager = BiometricManager.from(this);
        int canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            biometricActionCallback = callback;
            biometricFinishOnFailure = finishOnFailure;
            biometricPromptShowing = true;
            biometricPrompt.authenticate(biometricPromptInfo);
        } else {
            if (callback != null) {
                callback.onFailure();
            } else if (finishOnFailure && settingsRepository.getBoolean(SettingsRepository.KEY_BIOMETRIC_LOCK, false)) {
                finishAndRemoveTask();
            } else {
                biometricUnlockedForSession = true;
            }
        }
    }

    private void maybeSyncBirthdays() {
        SettingsRepository settingsRepository = SettingsRepository.getInstance(this);
        if (settingsRepository.getBoolean(SettingsRepository.KEY_IMPORT_BIRTHDAYS, false)) {
            ContactsBirthdaySync.sync(this,
                    new ItemRepository(getApplication()),
                    settingsRepository,
                    (count, message) -> {
                    });
        }
    }
    
    private void showAddItemDialog() {
        String[] options = {"Task (Todo)", "Habit", "Event"};
        AlertDialog.Builder typeBuilder = new AlertDialog.Builder(this);
        typeBuilder.setTitle("What would you like to create?");
        typeBuilder.setItems(options, (dialogInterface, which) -> {
            String type = "todo";
            if (which == 1) type = "habit";
            if (which == 2) type = "event";
            AddTaskBottomSheetFragment.newInstance(type, null).show(getSupportFragmentManager(), "AddTaskBottomSheetFragment");
        });
        typeBuilder.show();
    }
}
