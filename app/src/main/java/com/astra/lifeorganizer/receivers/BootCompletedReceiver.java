package com.astra.lifeorganizer.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.data.repositories.ItemRepository;
import com.astra.lifeorganizer.data.repositories.SettingsRepository;
import com.astra.lifeorganizer.utils.ContactsBirthdaySync;
import com.astra.lifeorganizer.utils.AlarmScheduler;
import com.astra.lifeorganizer.utils.AlarmTaskExecutor;
import com.astra.lifeorganizer.utils.RecapBriefScheduler;

public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        BroadcastReceiver.PendingResult result = goAsync();
        AlarmTaskExecutor.execute(() -> {
            try {
                ItemRepository repository = new ItemRepository((android.app.Application) context.getApplicationContext());
                for (Item item : repository.getAllExportableItems()) {
                    AlarmScheduler.scheduleForItem(context, item);
                }
                RecapBriefScheduler.rescheduleAll(context);
                ContactsBirthdaySync.sync(context, repository, SettingsRepository.getInstance(context), (count, message) -> {});
                com.astra.lifeorganizer.widgets.WidgetUpdater.updateAll(context);
            } finally {
                result.finish();
            }
        });
    }
}
