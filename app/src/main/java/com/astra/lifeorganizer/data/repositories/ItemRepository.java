package com.astra.lifeorganizer.data.repositories;

import android.app.Application;
import androidx.lifecycle.LiveData;
import com.astra.lifeorganizer.data.daos.ItemDao;
import com.astra.lifeorganizer.data.database.AstraDatabase;
import com.astra.lifeorganizer.data.entities.CompletionHistory;
import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.data.entities.ItemOccurrence;
import com.astra.lifeorganizer.data.entities.Project;
import com.astra.lifeorganizer.data.entities.Subtask;
import com.astra.lifeorganizer.data.repositories.SettingsRepository;
import com.astra.lifeorganizer.utils.ReminderManager;
import java.util.List;

public class ItemRepository {
    private ItemDao itemDao;
    private Application application;

    public ItemRepository(Application application) {
        this.application = application;
        AstraDatabase db = AstraDatabase.getDatabase(application);
        itemDao = db.itemDao();
    }

    public List<Item> getAllExportableItems() {
        return itemDao.getAllExportableItems();
    }

    public LiveData<List<Item>> getItemsByType(String type) {
        return itemDao.getItemsByType(type);
    }

    public LiveData<List<Item>> getAllItems() {
        return itemDao.getAllItems();
    }

    public LiveData<List<String>> getDistinctLabels() {
        return itemDao.getDistinctLabels();
    }

    public LiveData<Item> getItemById(long id) {
        return itemDao.getItemById(id);
    }

    public Item getItemByIdSync(long id) {
        return itemDao.getItemByIdSync(id);
    }

    public LiveData<List<Item>> getDueOrOverdueTasks(long currentTime) {
        return itemDao.getDueOrOverdueTasks(currentTime);
    }

    public LiveData<List<Item>> getUpcomingEvents(long currentTime) {
        long sevenDaysTime = currentTime + (7L * 24 * 60 * 60 * 1000L);
        long thirtyDaysTime = currentTime + (30L * 24 * 60 * 60 * 1000L);
        return itemDao.getUpcomingEvents(currentTime, sevenDaysTime, thirtyDaysTime);
    }

    public LiveData<List<Item>> getItemsForDateRange(long start, long end) {
        return itemDao.getItemsForDateRange(start, end);
    }

    public void insert(Item item) {
        insert(item, null);
    }

    public void insert(Item item, List<Subtask> subtasks) {
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            long id = itemDao.insert(item);
            item.id = id;
            if (subtasks != null) {
                for (Subtask subtask : subtasks) {
                    subtask.itemId = id;
                    itemDao.insertSubtask(subtask);
                }
            }
            if (shouldScheduleLegacyReminder(item)) {
                ReminderManager.scheduleReminder(application, item, item.reminderAt);
            } else {
                ReminderManager.cancelReminder(application, item.id);
            }
            com.astra.lifeorganizer.utils.AlarmScheduler.scheduleForItem(application, item);
            com.astra.lifeorganizer.widgets.WidgetUpdater.updateAll(application);
        });
    }

    public void insertItems(List<Item> items) {
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            itemDao.insertAll(items);
            for (Item item : items) {
                if (shouldScheduleLegacyReminder(item)) {
                    ReminderManager.scheduleReminder(application, item, item.reminderAt);
                } else {
                    ReminderManager.cancelReminder(application, item.id);
                }
                com.astra.lifeorganizer.utils.AlarmScheduler.scheduleForItem(application, item);
            }
            com.astra.lifeorganizer.widgets.WidgetUpdater.updateAll(application);
        });
    }

    public void update(Item item) {
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            itemDao.update(item);
            if (shouldScheduleLegacyReminder(item)) {
                ReminderManager.scheduleReminder(application, item, item.reminderAt);
            } else {
                ReminderManager.cancelReminder(application, item.id);
            }
            com.astra.lifeorganizer.utils.AlarmScheduler.scheduleForItem(application, item);
            com.astra.lifeorganizer.widgets.WidgetUpdater.updateAll(application);
        });
    }

    public void delete(Item item) {
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            itemDao.delete(item);
            itemDao.deleteOccurrencesForItem(item.id);
            ReminderManager.cancelReminder(application, item.id);
            com.astra.lifeorganizer.utils.AlarmScheduler.cancelForItem(application, item.id);
            com.astra.lifeorganizer.widgets.WidgetUpdater.updateAll(application);
        });
    }

    public void deleteById(long id) {
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            Item item = itemDao.getItemByIdSync(id);
            if (item != null) {
                itemDao.delete(item);
                itemDao.deleteOccurrencesForItem(id);
                ReminderManager.cancelReminder(application, id);
                com.astra.lifeorganizer.utils.AlarmScheduler.cancelForItem(application, id);
                com.astra.lifeorganizer.widgets.WidgetUpdater.updateAll(application);
            }
        });
    }

    public List<Item> getItemsByIdsSync(List<Long> ids) {
        return itemDao.getItemsByIdsSync(ids);
    }

    public LiveData<List<ItemOccurrence>> getOccurrencesForDay(long startOfDay, long endOfDay) {
        return itemDao.getOccurrencesForDay(startOfDay, endOfDay);
    }

    public List<ItemOccurrence> getOccurrencesForItemSync(long itemId) {
        return itemDao.getOccurrencesForItem(itemId);
    }

    public List<ItemOccurrence> getAllOccurrencesSync() {
        return itemDao.getAllOccurrencesSync();
    }

    public void insertOccurrence(ItemOccurrence occurrence) {
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            itemDao.insertOccurrence(occurrence);
        });
    }

    public void updateOccurrence(ItemOccurrence occurrence) {
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            itemDao.updateOccurrence(occurrence);
        });
    }

    public void recordHistory(CompletionHistory history) {
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            itemDao.insertHistory(history);
        });
    }

    public void incrementHabitCount(long itemId, long timestamp) {
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            Item item = itemDao.getItemByIdSync(itemId);
            if (item == null) return;

            // Start of day for matching occurrence
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(timestamp);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            long startOfDay = cal.getTimeInMillis();

            List<ItemOccurrence> occurrences = itemDao.getOccurrencesForItem(itemId);
            ItemOccurrence todayOccurrence = null;
            for (ItemOccurrence o : occurrences) {
                if (o.scheduledFor == startOfDay) {
                    todayOccurrence = o;
                    break;
                }
            }

            if (todayOccurrence == null) {
                todayOccurrence = new ItemOccurrence();
                todayOccurrence.itemId = itemId;
                todayOccurrence.scheduledFor = startOfDay;
                todayOccurrence.status = "pending";
                todayOccurrence.currentCount = 0;
                todayOccurrence.isComplete = false;
                todayOccurrence.id = itemDao.insertOccurrence(todayOccurrence);
            }

            if (todayOccurrence.currentCount < item.dailyTargetCount) {
                todayOccurrence.currentCount++;
                if (todayOccurrence.currentCount >= item.dailyTargetCount) {
                    todayOccurrence.isComplete = true;
                    todayOccurrence.status = "done";
                    
                    // Also update parent item status if needed, 
                    // though for recurring items it usually stays pending/active.
                }
                itemDao.updateOccurrence(todayOccurrence);

                // Record history
                CompletionHistory history = new CompletionHistory();
                history.itemId = itemId;
                history.action = "done";
                history.timestamp = timestamp;
                itemDao.insertHistory(history);
            }
        });
    }

    public LiveData<List<CompletionHistory>> getHistoryForItemLive(long itemId) {
        return itemDao.getHistoryForItemLive(itemId);
    }

    public void deleteHistory(CompletionHistory history) {
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            itemDao.deleteHistory(history);
        });
    }

    public LiveData<List<CompletionHistory>> getAllHistory() {
        return itemDao.getAllHistory();
    }

    public void insertSubtask(Subtask subtask) {
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            itemDao.insertSubtask(subtask);
        });
    }

    public LiveData<List<Subtask>> getSubtasksForItem(long itemId) {
        return itemDao.getSubtasksForItem(itemId);
    }

    public void updateSubtask(Subtask subtask) {
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            itemDao.updateSubtask(subtask);
        });
    }

    public void deleteSubtask(Subtask subtask) {
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            itemDao.deleteSubtask(subtask);
        });
    }

    public void deleteSubtasksForItem(long itemId) {
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            itemDao.deleteSubtasksForItem(itemId);
        });
    }

    public void renameLabel(String oldLabel, String newLabel) {
        renameLabel(oldLabel, newLabel, System.currentTimeMillis());
    }

    public void renameLabel(String oldLabel, String newLabel, long nowMs) {
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            itemDao.renameLabelScoped(oldLabel, newLabel, nowMs);
            SettingsRepository.getInstance(application).renameLabelColor(oldLabel, newLabel);
            com.astra.lifeorganizer.widgets.WidgetUpdater.updateAll(application);
        });
    }

    public void mergeLabel(String fromLabel, String toLabel) {
        mergeLabel(fromLabel, toLabel, System.currentTimeMillis());
    }

    public void mergeLabel(String fromLabel, String toLabel, long nowMs) {
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            itemDao.mergeLabelScoped(fromLabel, toLabel, nowMs);
            SettingsRepository.getInstance(application).mergeLabelColor(fromLabel, toLabel);
            com.astra.lifeorganizer.widgets.WidgetUpdater.updateAll(application);
        });
    }

    public void clearLabel(String label) {
        clearLabel(label, System.currentTimeMillis());
    }

    public void clearLabel(String label, long nowMs) {
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            itemDao.clearLabelScoped(label, nowMs);
            SettingsRepository.getInstance(application).deleteLabelColor(label);
            com.astra.lifeorganizer.widgets.WidgetUpdater.updateAll(application);
        });
    }

    public void deleteItemsWithLabel(String label) {
        deleteItemsWithLabel(label, System.currentTimeMillis());
    }

    public void deleteItemsWithLabel(String label, long nowMs) {
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            itemDao.deleteItemsWithLabelScoped(label, nowMs);
            SettingsRepository.getInstance(application).deleteLabelColor(label);
            com.astra.lifeorganizer.widgets.WidgetUpdater.updateAll(application);
        });
    }

    // --- Backup Support ---
    public List<Item> getAllItemsSync() { return itemDao.getAllItemsSync(); }
    public List<CompletionHistory> getAllHistorySync() { return itemDao.getAllHistorySync(); }
    public List<Subtask> getAllSubtasksSync() { return itemDao.getAllSubtasksSync(); }

    public void restoreWipeSync() {
        itemDao.deleteAllItems();
        itemDao.deleteAllOccurrences();
        itemDao.deleteAllHistory();
        itemDao.deleteAllSubtasks();
        com.astra.lifeorganizer.widgets.WidgetUpdater.updateAll(application);
    }

    public void purgeArchivedItems() {
        AstraDatabase.databaseWriteExecutor.execute(itemDao::deleteArchivedItems);
    }

    public void deleteImportedBirthdays() {
        AstraDatabase.databaseWriteExecutor.execute(() ->
                itemDao.deleteItemsByLabelAndDescription("Birthdays", "Imported from contacts"));
    }

    public void replaceImportedBirthdays(List<Item> items) {
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            replaceImportedBirthdaysSync(items);
        });
    }

    public void replaceImportedBirthdaysSync(List<Item> items) {
        itemDao.deleteItemsByLabelAndDescription("Birthdays", "Imported from contacts");
        if (items != null && !items.isEmpty()) {
            itemDao.insertAll(items);
            for (Item item : items) {
                if (shouldScheduleLegacyReminder(item)) {
                    ReminderManager.scheduleReminder(application, item, item.reminderAt);
                } else {
                    ReminderManager.cancelReminder(application, item.id);
                }
                com.astra.lifeorganizer.utils.AlarmScheduler.scheduleForItem(application, item);
            }
        }
        com.astra.lifeorganizer.widgets.WidgetUpdater.updateAll(application);
    }

    public void deleteOccurrencesForItem(long itemId) {
        AstraDatabase.databaseWriteExecutor.execute(() -> itemDao.deleteOccurrencesForItem(itemId));
    }

    public void restoreInsertSync(List<Item> items, List<ItemOccurrence> occs, List<CompletionHistory> history, List<Subtask> subtasks) {
        if (items != null) itemDao.insertAll(items);
        if (occs != null) {
            for (ItemOccurrence o : occs) itemDao.insertOccurrence(o);
        }
        if (history != null) {
            for (CompletionHistory h : history) itemDao.insertHistory(h);
        }
        if (subtasks != null) {
            for (Subtask s : subtasks) itemDao.insertSubtask(s);
        }
        com.astra.lifeorganizer.widgets.WidgetUpdater.updateAll(application);
    }

    // --- Projects ---
    public LiveData<List<Project>> getAllProjects() {
        return itemDao.getAllProjects();
    }

    public void insertProject(Project project) {
        AstraDatabase.databaseWriteExecutor.execute(() -> itemDao.insertProject(project));
    }

    public LiveData<List<Item>> getItemsByProject(long projectId) {
        return itemDao.getItemsByProject(projectId);
    }

    public LiveData<List<Subtask>> getAllSubtasksLive() {
        return itemDao.getAllSubtasksLive();
    }

    public Item getLastCreatedItemSync() {
        return itemDao.getLastCreatedItemSync();
    }

    public long insertSync(Item item) {
        long id = itemDao.insert(item);
        item.id = id;
        return id;
    }

    private boolean shouldScheduleLegacyReminder(Item item) {
        if (item == null || item.reminderAt == null) {
            return false;
        }
        if (item.priority == com.astra.lifeorganizer.utils.AlarmConstants.PRIORITY_HIGH) {
            return false;
        }
        if (item.status != null && ("done".equalsIgnoreCase(item.status) || "archived".equalsIgnoreCase(item.status) || "canceled".equalsIgnoreCase(item.status))) {
            return false;
        }
        return true;
    }
}
