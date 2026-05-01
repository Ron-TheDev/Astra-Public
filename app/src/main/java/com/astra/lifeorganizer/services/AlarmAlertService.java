package com.astra.lifeorganizer.services;

import android.app.Notification;
import android.app.Service;
import android.content.pm.ServiceInfo;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.data.repositories.ItemRepository;
import com.astra.lifeorganizer.utils.AlarmActionHandler;
import com.astra.lifeorganizer.utils.AlarmConstants;
import com.astra.lifeorganizer.ui.AlarmFullScreenActivity;
import com.astra.lifeorganizer.utils.AlarmScheduler;
import com.astra.lifeorganizer.utils.AlarmTaskExecutor;
import com.astra.lifeorganizer.utils.NotificationHelper;

public class AlarmAlertService extends Service {
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private volatile boolean destroyed;
    private volatile int latestStartId = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createNotificationChannel(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        long itemId = intent != null ? intent.getLongExtra(AlarmConstants.EXTRA_ITEM_ID, 0L) : 0L;
        if (itemId <= 0) {
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }

        latestStartId = startId;

        // 1. Immediately start foreground with a placeholder to satisfy the OS and prevent ANRs/Crashes
        // Foreground service MUST call startForeground shortly after startService.
        android.app.Notification placeholder = NotificationHelper.buildPlaceholderAlarmNotification(this, itemId).build();
        int notificationId = AlarmScheduler.getAlarmNotificationId(itemId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, placeholder, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(notificationId, placeholder);
        }

        // 2. Load Item on Background Thread
        AlarmTaskExecutor.execute(() -> {
            Item item = new ItemRepository((android.app.Application) getApplicationContext()).getItemByIdSync(itemId);

            if (destroyed || startId != latestStartId) {
                return;
            }

            if (item == null || !AlarmScheduler.shouldSchedule(item)) {
                AlarmTaskExecutor.postToMain(() -> {
                    if (destroyed || startId != latestStartId) {
                        return;
                    }
                    stopForeground(true);
                    stopSelfResult(startId);
                });
                return;
            }

            // 3. Update UI/Launch Activity on Main Thread
            AlarmTaskExecutor.postToMain(() -> {
                if (destroyed || startId != latestStartId) {
                    return;
                }
                android.app.Notification realNotification = NotificationHelper.buildFullScreenAlarmNotification(this, item).build();
                android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) {
                    nm.notify(notificationId, realNotification);
                }

                try {
                    AlarmActionHandler.launchFullscreenAlarm(this, itemId, false);
                } catch (Exception e) {
                    android.util.Log.w("AlarmService", "Fullscreen launch failed, relying on notification intent", e);
                }
                startAlarmEffects();
            });
        });

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        try {
            stopForeground(true);
        } catch (Exception ignored) {
        }
        stopAlarmEffects();
        super.onDestroy();
    }

    private void startAlarmEffects() {
        if (mediaPlayer == null) {
            com.astra.lifeorganizer.data.repositories.SettingsRepository settings = com.astra.lifeorganizer.data.repositories.SettingsRepository.getInstance(this);
            String savedUri = settings.getString(com.astra.lifeorganizer.data.repositories.SettingsRepository.KEY_ALARM_RINGTONE, null);
            Uri uri;
            if (savedUri != null) {
                uri = Uri.parse(savedUri);
            } else {
                uri = android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI;
                if (uri == null) {
                    uri = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
                }
            }
            try {
                mediaPlayer = MediaPlayer.create(this, uri);
                if (mediaPlayer != null) {
                    mediaPlayer.setLooping(true);
                    mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
                    mediaPlayer.start();
                }
            } catch (Exception ignored) {
                mediaPlayer = null;
            }
        }

        if (vibrator == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vibratorManager = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
                if (vibratorManager != null) {
                    vibrator = vibratorManager.getDefaultVibrator();
                }
            } else {
                vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            }
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = new long[]{0, 500, 500};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        }
    }

    private void stopAlarmEffects() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {
            }
            mediaPlayer = null;
        }

        if (vibrator != null) {
            vibrator.cancel();
        }
    }
}
