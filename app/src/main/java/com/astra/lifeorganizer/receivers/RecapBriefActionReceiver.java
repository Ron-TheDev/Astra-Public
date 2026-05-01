package com.astra.lifeorganizer.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.navigation.NavDeepLinkBuilder;

import com.astra.lifeorganizer.R;
import com.astra.lifeorganizer.utils.NotificationHelper;
import com.astra.lifeorganizer.utils.RecapBriefConstants;

public class RecapBriefActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }

        String kind = intent.getStringExtra(RecapBriefConstants.EXTRA_BRIEF_KIND);
        String mode = intent.getStringExtra(RecapBriefConstants.EXTRA_BRIEF_MODE);
        int notificationId = RecapBriefConstants.KIND_EVENING.equals(kind) ? 82002 : 82001;
        NotificationHelper.cancelNotification(context, notificationId);

        Bundle args = new Bundle();
        args.putString("initial_mode", mode);
        args.putString(RecapBriefConstants.EXTRA_BRIEF_KIND, kind);

        PendingIntentHelper.sendDeepLink(context, args);
    }

    private static final class PendingIntentHelper {
        private static void sendDeepLink(Context context, Bundle args) {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.putExtras(args);
                context.startActivity(intent);
            }
        }
    }
}
