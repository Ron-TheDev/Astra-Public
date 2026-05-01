package com.astra.lifeorganizer.ui;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.astra.lifeorganizer.R;
import com.astra.lifeorganizer.data.database.AstraDatabase;
import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.data.repositories.ItemRepository;
import com.astra.lifeorganizer.data.repositories.SettingsRepository;
import com.astra.lifeorganizer.utils.AlarmActionHandler;
import com.astra.lifeorganizer.utils.AlarmConstants;
import com.astra.lifeorganizer.utils.DateTimeFormatUtils;

public class AlarmFullScreenActivity extends AppCompatActivity {

    private long itemId;
    private Item currentItem;

    private TextView tvType;
    private TextView tvTitle;
    private TextView tvTime;
    private TextView tvNoteLabel;
    private TextView tvNote;
    private Button btnSnooze;
    private Button btnPrimary;
    private boolean previewMode;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyPrivacyFlags();
        configureLockScreen();
        setContentView(R.layout.activity_alarm_full_screen);

        itemId = getIntent().getLongExtra(AlarmConstants.EXTRA_ITEM_ID, 0L);
        previewMode = getIntent().getBooleanExtra(AlarmConstants.EXTRA_FORCE_FULLSCREEN_PREVIEW, false);
        bindViews();
        loadItem();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        itemId = intent.getLongExtra(AlarmConstants.EXTRA_ITEM_ID, 0L);
        previewMode = intent.getBooleanExtra(AlarmConstants.EXTRA_FORCE_FULLSCREEN_PREVIEW, false);
        loadItem();
    }

    @Override
    public void onBackPressed() {
        handleDismiss();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // DO NOT auto-finish here for alarm apps
    }

    private void configureLockScreen() {
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager.requestDismissKeyguard(this, null);
        }
    }

    private void applyPrivacyFlags() {
        if (SettingsRepository.getInstance(this).getBoolean(SettingsRepository.KEY_PREVENT_SCREENSHOTS, false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    private void bindViews() {
        tvType = findViewById(R.id.tv_alarm_type);
        tvTitle = findViewById(R.id.tv_alarm_title);
        tvTime = findViewById(R.id.tv_alarm_time);
        tvNoteLabel = findViewById(R.id.tv_alarm_note_label);
        tvNote = findViewById(R.id.tv_alarm_note);
        btnSnooze = findViewById(R.id.btn_alarm_snooze);
        btnPrimary = findViewById(R.id.btn_alarm_primary);

        btnSnooze.setOnClickListener(v -> {
            if (currentItem != null) {
                AlarmActionHandler.snooze(this, currentItem.id);
            }
            finish();
        });
    }

    private void loadItem() {
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            ItemRepository repository = new ItemRepository(getApplication());
            Item item = repository.getItemByIdSync(itemId);
            runOnUiThread(() -> {
                currentItem = item;
                if (currentItem == null) {
                    if (previewMode) {
                        bindPreviewState();
                        return;
                    }
                    Log.e("AlarmUI", "Item is null - not finishing silently");
                    return;
                }
                if (!previewMode && !com.astra.lifeorganizer.utils.AlarmScheduler.shouldSchedule(currentItem)) {
                    finish();
                    return;
                }

                bindItem(currentItem);
            });
        });
    }

    private void bindPreviewState() {
        tvType.setText("Preview");
        tvTitle.setText("Fullscreen alarm preview");
        tvTime.setText("Wake screen and show over lockscreen");
        tvNoteLabel.setVisibility(View.VISIBLE);
        tvNote.setVisibility(View.VISIBLE);
        tvNote.setText("This preview bypasses alarm scheduling checks so we can verify the fullscreen experience.");
        btnPrimary.setText("Close");
        btnPrimary.setOnClickListener(v -> finish());
        btnSnooze.setText("Test Snooze");
        btnSnooze.setOnClickListener(v -> finish());
    }

    private void bindItem(Item item) {
        String typeLabel = "event".equalsIgnoreCase(item.type) ? "Event" : "Task";
        long scheduledTime = com.astra.lifeorganizer.utils.AlarmScheduler.getAlarmTime(item);
        tvType.setText(typeLabel);
        tvTitle.setText(item.title != null ? item.title : "Alarm");
        tvTime.setText(DateTimeFormatUtils.formatTime(this, scheduledTime));

        if (item.description != null && !item.description.trim().isEmpty()) {
            tvNote.setText(item.description.trim());
            tvNote.setVisibility(View.VISIBLE);
            tvNoteLabel.setVisibility(View.VISIBLE);
        } else {
            tvNote.setVisibility(View.GONE);
            tvNoteLabel.setVisibility(View.GONE);
        }

        btnPrimary.setText("event".equalsIgnoreCase(item.type) ? "Dismiss" : "Mark Done");
        btnPrimary.setOnClickListener(v -> {
            if ("event".equalsIgnoreCase(item.type)) {
                handleDismiss();
            } else {
                handleMarkDone();
            }
        });
    }

    private void handleMarkDone() {
        if (currentItem != null) {
            AlarmActionHandler.markDoneFromAlarmUi(this, currentItem.id);
        }
        finish();
    }

    private void handleDismiss() {
        if (currentItem != null) {
            AlarmActionHandler.dismissFromAlarmUi(this, currentItem.id);
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}
