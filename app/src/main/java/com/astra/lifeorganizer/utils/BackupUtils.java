package com.astra.lifeorganizer.utils;

import android.content.Context;
import com.astra.lifeorganizer.data.entities.CompletionHistory;
import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.data.entities.ItemOccurrence;
import com.astra.lifeorganizer.data.entities.Subtask;
import com.astra.lifeorganizer.data.repositories.ItemRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

public class BackupUtils {

    public static class DataBundle {
        public List<Item> items;
        public List<ItemOccurrence> occurrences;
        public List<CompletionHistory> history;
        public List<Subtask> subtasks;
        public long exportTimestamp;
        public String appVersion = "1.0";
    }

    public static String createBackupJson(ItemRepository repository) {
        DataBundle bundle = new DataBundle();
        bundle.items = repository.getAllItemsSync();
        bundle.occurrences = repository.getAllOccurrencesSync();
        bundle.history = repository.getAllHistorySync();
        bundle.subtasks = repository.getAllSubtasksSync();
        bundle.exportTimestamp = System.currentTimeMillis();

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(bundle);
    }

    public static boolean restoreFromBackup(String json, ItemRepository repository) {
        try {
            Gson gson = new Gson();
            DataBundle bundle = gson.fromJson(json, DataBundle.class);
            if (bundle == null || bundle.items == null) return false;

            // Atomic-ish Wipe & Restore
            repository.restoreWipeSync();
            repository.restoreInsertSync(bundle.items, bundle.occurrences, bundle.history, bundle.subtasks);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
