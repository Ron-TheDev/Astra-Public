package com.astra.lifeorganizer.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AlarmQueueManager {
    private static final String PREFS = "alarm_queue_prefs";
    private static final String KEY_ACTIVE = "active_alarm_id";
    private static final String KEY_QUEUE = "queued_alarm_ids";
    private static final Object LOCK = new Object();

    private AlarmQueueManager() {}

    public static synchronized boolean registerIncoming(Context context, long itemId) {
        if (itemId <= 0) {
            return false;
        }

        synchronized (LOCK) {
            SharedPreferences prefs = prefs(context);
            QueueState state = readState(prefs);
            if (state.activeId == -1L) {
                state.activeId = itemId;
                state.queue.remove(itemId);
                return writeState(prefs, state);
            }

            if (state.activeId == itemId) {
                state.queue.remove(itemId);
                writeState(prefs, state);
                return false;
            }

            state.queue.remove(itemId);
            state.queue.addLast(itemId);
            return writeState(prefs, state);
        }
    }

    @Nullable
    public static synchronized Long releaseCurrentAndGetNext(Context context) {
        synchronized (LOCK) {
            SharedPreferences prefs = prefs(context);
            QueueState state = readState(prefs);
            if (state.queue.isEmpty()) {
                clearState(prefs);
                return null;
            }

            state.activeId = state.queue.removeFirst();
            if (!writeState(prefs, state)) {
                return null;
            }
            return state.activeId;
        }
    }

    public static synchronized void removeQueued(Context context, long itemId) {
        if (itemId <= 0) {
            return;
        }

        synchronized (LOCK) {
            SharedPreferences prefs = prefs(context);
            QueueState state = readState(prefs);
            if (state.activeId == itemId) {
                if (state.queue.isEmpty()) {
                    clearState(prefs);
                    return;
                }

                state.activeId = state.queue.removeFirst();
                writeState(prefs, state);
                return;
            }

            if (state.queue.remove(itemId)) {
                writeState(prefs, state);
            }
        }
    }

    public static synchronized void clear(Context context) {
        synchronized (LOCK) {
            clearState(prefs(context));
        }
    }

    public static synchronized long getActiveAlarmId(Context context) {
        synchronized (LOCK) {
            return prefs(context).getLong(KEY_ACTIVE, -1L);
        }
    }

    @Nullable
    public static synchronized Long acknowledgeAlarm(Context context, long itemId) {
        if (itemId <= 0) {
            return null;
        }

        synchronized (LOCK) {
            SharedPreferences prefs = prefs(context);
            QueueState state = readState(prefs);
            if (state.activeId == itemId) {
                if (state.queue.isEmpty()) {
                    clearState(prefs);
                    return null;
                }

                state.activeId = state.queue.removeFirst();
                if (!writeState(prefs, state)) {
                    return null;
                }
                return state.activeId;
            }

            if (state.queue.remove(itemId)) {
                writeState(prefs, state);
            }
            return null;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static QueueState readState(SharedPreferences prefs) {
        QueueState state = new QueueState();
        state.activeId = prefs.getLong(KEY_ACTIVE, -1L);

        String rawQueue = prefs.getString(KEY_QUEUE, "");
        if (rawQueue != null && !rawQueue.trim().isEmpty()) {
            String[] tokens = rawQueue.split(",");
            for (String token : tokens) {
                if (token == null) {
                    continue;
                }

                String trimmed = token.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                try {
                    long queuedId = Long.parseLong(trimmed);
                    if (queuedId > 0 && queuedId != state.activeId) {
                        state.queue.addLast(queuedId);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return state;
    }

    private static boolean writeState(SharedPreferences prefs, QueueState state) {
        SharedPreferences.Editor editor = prefs.edit();
        if (state.activeId > 0) {
            editor.putLong(KEY_ACTIVE, state.activeId);
        } else {
            editor.remove(KEY_ACTIVE);
        }

        if (state.queue.isEmpty()) {
            editor.remove(KEY_QUEUE);
        } else {
            editor.putString(KEY_QUEUE, serializeQueue(state.queue));
        }

        return editor.commit();
    }

    private static void clearState(SharedPreferences prefs) {
        prefs.edit().remove(KEY_ACTIVE).remove(KEY_QUEUE).commit();
    }

    private static String serializeQueue(Deque<Long> queue) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Long id : queue) {
            if (id == null || id <= 0) {
                continue;
            }
            if (!first) {
                builder.append(',');
            }
            builder.append(id);
            first = false;
        }
        return builder.toString();
    }

    private static final class QueueState {
        long activeId = -1L;
        final Deque<Long> queue = new ArrayDeque<>();
    }
}
