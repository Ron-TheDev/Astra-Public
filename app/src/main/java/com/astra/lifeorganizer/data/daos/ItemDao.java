package com.astra.lifeorganizer.data.daos;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Delete;

import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.data.entities.ItemOccurrence;
import com.astra.lifeorganizer.data.entities.CompletionHistory;
import com.astra.lifeorganizer.data.entities.Project;
import com.astra.lifeorganizer.data.entities.Subtask;

import java.util.List;

@Dao
public interface ItemDao {
    @Insert
    long insert(Item item);

    @Insert
    void insertAll(List<Item> items);

    @Update
    void update(Item item);

    @Delete
    void delete(Item item);

    @Query("SELECT * FROM items WHERE type = :type AND status != 'archived'")
    LiveData<List<Item>> getItemsByType(String type);

    @Query("SELECT DISTINCT label FROM items WHERE label IS NOT NULL AND TRIM(label) != '' ORDER BY label COLLATE NOCASE")
    LiveData<List<String>> getDistinctLabels();

    @Query("UPDATE items SET label = :newLabel WHERE label IS NOT NULL AND TRIM(label) != '' AND LOWER(TRIM(label)) = LOWER(TRIM(:oldLabel)) AND ((type = 'todo' AND status != 'done' AND status != 'archived') OR (type = 'habit' AND status != 'done' AND status != 'archived') OR (type = 'event' AND start_at >= :nowMs AND status != 'archived' AND status != 'canceled'))")
    int renameLabelScoped(String oldLabel, String newLabel, long nowMs);

    @Query("UPDATE items SET label = :newLabel WHERE label IS NOT NULL AND TRIM(label) != '' AND LOWER(TRIM(label)) = LOWER(TRIM(:oldLabel)) AND ((type = 'todo' AND status != 'done' AND status != 'archived') OR (type = 'habit' AND status != 'done' AND status != 'archived') OR (type = 'event' AND start_at >= :nowMs AND status != 'archived' AND status != 'canceled'))")
    int mergeLabelScoped(String oldLabel, String newLabel, long nowMs);

    @Query("UPDATE items SET label = NULL WHERE label IS NOT NULL AND TRIM(label) != '' AND LOWER(TRIM(label)) = LOWER(TRIM(:label)) AND ((type = 'todo' AND status != 'done' AND status != 'archived') OR (type = 'habit' AND status != 'done' AND status != 'archived') OR (type = 'event' AND start_at >= :nowMs AND status != 'archived' AND status != 'canceled'))")
    int clearLabelScoped(String label, long nowMs);

    @Query("DELETE FROM items WHERE label IS NOT NULL AND TRIM(label) != '' AND LOWER(TRIM(label)) = LOWER(TRIM(:label)) AND ((type = 'todo' AND status != 'done' AND status != 'archived') OR (type = 'habit' AND status != 'done' AND status != 'archived') OR (type = 'event' AND start_at >= :nowMs AND status != 'archived' AND status != 'canceled'))")
    int deleteItemsWithLabelScoped(String label, long nowMs);

    @Query("SELECT * FROM items WHERE type = 'todo' AND due_at <= :currentTime AND status != 'done' AND status != 'archived' ORDER BY due_at ASC")
    LiveData<List<Item>> getDueOrOverdueTasks(long currentTime);

    @Query("SELECT * FROM items WHERE type = 'event' AND start_at >= :currentTime AND ((priority = 3 AND start_at <= :thirtyDaysTime) OR (priority != 3 AND start_at <= :sevenDaysTime)) AND status != 'archived' ORDER BY start_at ASC")
    LiveData<List<Item>> getUpcomingEvents(long currentTime, long sevenDaysTime, long thirtyDaysTime);

    @Query("SELECT * FROM items WHERE ((due_at >= :start AND due_at <= :end) OR (start_at >= :start AND start_at <= :end)) AND status != 'archived'")
    LiveData<List<Item>> getItemsForDateRange(long start, long end);

    @Query("SELECT * FROM items WHERE id = :id")
    LiveData<Item> getItemById(long id);

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    Item getItemByIdSync(long id);

    @Query("SELECT * FROM items WHERE id IN (:ids)")
    List<Item> getItemsByIdsSync(List<Long> ids);

    @Query("SELECT * FROM items WHERE status != 'archived'")
    List<Item> getAllExportableItems();

    @Insert
    long insertOccurrence(ItemOccurrence occurrence);

    @Update
    void updateOccurrence(ItemOccurrence occurrence);

    @Query("SELECT * FROM item_occurrences WHERE scheduled_for >= :startOfDay AND scheduled_for <= :endOfDay")
    LiveData<List<ItemOccurrence>> getOccurrencesForDay(long startOfDay, long endOfDay);

    @Query("SELECT * FROM item_occurrences WHERE item_id = :itemId")
    List<ItemOccurrence> getOccurrencesForItem(long itemId);

    @Query("DELETE FROM item_occurrences WHERE item_id = :itemId")
    void deleteOccurrencesForItem(long itemId);

    @Insert
    void insertHistory(CompletionHistory history);

    @Query("SELECT * FROM completion_history WHERE item_id = :itemId ORDER BY timestamp DESC")
    LiveData<List<CompletionHistory>> getHistoryForItemLive(long itemId);

    @Delete
    void deleteHistory(CompletionHistory history);

    @Query("SELECT * FROM completion_history ORDER BY timestamp DESC")
    LiveData<List<CompletionHistory>> getAllHistory();

    @Insert
    long insertSubtask(Subtask subtask);

    @Query("SELECT * FROM subtasks WHERE itemId = :itemId")
    LiveData<List<Subtask>> getSubtasksForItem(long itemId);

    @Update
    void updateSubtask(Subtask subtask);

    @Delete
    void deleteSubtask(Subtask subtask);

    @Query("DELETE FROM subtasks WHERE itemId = :itemId")
    void deleteSubtasksForItem(long itemId);

    // --- Backup & Restore ---
    @Query("SELECT * FROM items")
    List<Item> getAllItemsSync();

    @Query("SELECT * FROM item_occurrences")
    List<ItemOccurrence> getAllOccurrencesSync();

    @Query("SELECT * FROM completion_history")
    List<CompletionHistory> getAllHistorySync();

    @Query("SELECT * FROM subtasks")
    List<Subtask> getAllSubtasksSync();

    @Query("DELETE FROM items")
    void deleteAllItems();

    @Query("DELETE FROM items WHERE status = 'archived'")
    void deleteArchivedItems();

    @Query("DELETE FROM items WHERE label = :label AND description = :description")
    void deleteItemsByLabelAndDescription(String label, String description);

    @Query("DELETE FROM item_occurrences")
    void deleteAllOccurrences();

    @Query("DELETE FROM completion_history")
    void deleteAllHistory();

    @Query("DELETE FROM subtasks")
    void deleteAllSubtasks();

    // --- Projects ---
    @Insert
    long insertProject(Project project);

    @Query("SELECT * FROM projects")
    LiveData<List<Project>> getAllProjects();

    @Query("SELECT * FROM projects WHERE id = :id")
    Project getProjectById(long id);

    @Delete
    void deleteProject(Project project);

    @Query("SELECT * FROM items WHERE project_id = :projectId")
    LiveData<List<Item>> getItemsByProject(long projectId);

    @Query("SELECT * FROM subtasks")
    LiveData<List<Subtask>> getAllSubtasksLive();
    @Query("SELECT * FROM items")
    LiveData<List<Item>> getAllItems();

    @Query("SELECT * FROM items ORDER BY created_at DESC LIMIT 1")
    Item getLastCreatedItemSync();
}
