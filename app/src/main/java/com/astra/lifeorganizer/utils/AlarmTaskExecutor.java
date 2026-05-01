package com.astra.lifeorganizer.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AlarmTaskExecutor {
    private static final ExecutorService BACKGROUND = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AlarmTaskExecutor() {}

    public static void execute(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        BACKGROUND.execute(runnable);
    }

    public static void postToMain(Runnable runnable) {
        if (runnable == null) {
            return;
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            MAIN.post(runnable);
        }
    }
}
