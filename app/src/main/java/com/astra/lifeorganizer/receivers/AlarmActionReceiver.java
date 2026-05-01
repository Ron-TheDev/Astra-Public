package com.astra.lifeorganizer.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.astra.lifeorganizer.utils.AlarmActionHandler;
import com.astra.lifeorganizer.utils.AlarmConstants;
import com.astra.lifeorganizer.utils.AlarmTaskExecutor;

public class AlarmActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pendingResult = goAsync();
        AlarmTaskExecutor.execute(() -> {
            try {
                if (intent == null) {
                    return;
                }

                long itemId = intent.getLongExtra(AlarmConstants.EXTRA_ITEM_ID, 0L);
                String action = intent.getAction();
                if (itemId <= 0 || action == null) {
                    return;
                }

                switch (action) {
                    case AlarmConstants.ACTION_ALARM_SNOOZE:
                        AlarmActionHandler.snooze(context, itemId);
                        break;
                    case AlarmConstants.ACTION_ALARM_DONE:
                    case AlarmConstants.ACTION_ALARM_DISMISS:
                        AlarmActionHandler.markDoneOrDismiss(context, itemId);
                        break;
                    case AlarmConstants.ACTION_ALARM_OPEN:
                        // Mark done/dismiss first as requested
                        AlarmActionHandler.markDoneOrDismiss(context, itemId);
                        
                        String itemType = intent.getStringExtra(AlarmConstants.EXTRA_ITEM_TYPE);
                        AlarmTaskExecutor.postToMain(() -> {
                            Intent openIntent = AlarmActionHandler.createOpenIntent(context, itemId, itemType);
                            openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(openIntent);
                        });
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                pendingResult.finish();
            }
        });
    }
}
