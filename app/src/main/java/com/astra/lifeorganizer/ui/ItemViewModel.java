package com.astra.lifeorganizer.ui;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.astra.lifeorganizer.data.entities.CompletionHistory;
import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.data.entities.ItemOccurrence;
import com.astra.lifeorganizer.data.entities.Project;
import com.astra.lifeorganizer.data.entities.Subtask;
import com.astra.lifeorganizer.data.repositories.ItemRepository;

import java.util.List;

public class ItemViewModel extends AndroidViewModel {
    private ItemRepository repository;

    public ItemViewModel(Application application) {
        super(application);
        repository = new ItemRepository(application);
        checkAndInitProjects();
    }

    private void checkAndInitProjects() {
        repository.getAllProjects().observeForever(projects -> {
            if (projects == null || projects.isEmpty()) {
                repository.insertProject(new Project("In-Box", "ic_menu_edit", "#9E9E9E"));
                repository.insertProject(new Project("Work", "ic_menu_edit", "#3F51B5"));
                repository.insertProject(new Project("Personal", "ic_menu_edit", "#4CAF50"));
                repository.insertProject(new Project("Home", "ic_menu_today", "#FF9800"));
            }
        });
    }

    public LiveData<List<Item>> getItemsByType(String type) {
        return repository.getItemsByType(type);
    }

    public LiveData<List<String>> getDistinctLabels() {
        return repository.getDistinctLabels();
    }

    public LiveData<Item> getItemById(long id) {
        return repository.getItemById(id);
    }

    public Item getItemByIdSync(long id) {
        return repository.getItemByIdSync(id);
    }

    public LiveData<List<Item>> getDueOrOverdueTasks(long currentTime) {
        return repository.getDueOrOverdueTasks(currentTime);
    }

    public LiveData<List<Item>> getUpcomingEvents(long currentTime) {
        return repository.getUpcomingEvents(currentTime);
    }

    public LiveData<List<Item>> getItemsForDateRange(long start, long end) {
        return repository.getItemsForDateRange(start, end);
    }

    public LiveData<List<Item>> getAllItems() {
        return repository.getAllItems();
    }

    public List<Item> getAllItemsSync() {
        return repository.getAllItemsSync();
    }

    public LiveData<List<ItemOccurrence>> getOccurrencesForDay(long startOfDay, long endOfDay) {
        return repository.getOccurrencesForDay(startOfDay, endOfDay);
    }

    public List<ItemOccurrence> getOccurrencesForItemSync(long itemId) {
        return repository.getOccurrencesForItemSync(itemId);
    }

    public List<ItemOccurrence> getAllOccurrencesSync() {
        return repository.getAllOccurrencesSync();
    }

    public void insert(Item item) {
        repository.insert(item);
    }

    public void insert(Item item, List<Subtask> subtasks) {
        repository.insert(item, subtasks);
    }

    public void update(Item item) {
        repository.update(item);
    }

    public void delete(Item item) {
        repository.delete(item);
    }

    public void deleteById(long id) {
        repository.deleteById(id);
    }

    public List<Item> getItemsByIdsSync(List<Long> ids) {
        return repository.getItemsByIdsSync(ids);
    }

    public void updateOccurrence(ItemOccurrence occurrence) {
        repository.updateOccurrence(occurrence);
    }

    public void insertOccurrence(ItemOccurrence occurrence) {
        repository.insertOccurrence(occurrence);
    }

    public void recordHistory(CompletionHistory history) {
        repository.recordHistory(history);
    }

    public void incrementHabitCount(long itemId, long timestamp) {
        repository.incrementHabitCount(itemId, timestamp);
    }

    public LiveData<List<CompletionHistory>> getHistoryForItemLive(long itemId) {
        return repository.getHistoryForItemLive(itemId);
    }

    public void deleteHistory(CompletionHistory history) {
        repository.deleteHistory(history);
    }

    public LiveData<List<CompletionHistory>> getAllHistory() {
        return repository.getAllHistory();
    }

    public List<CompletionHistory> getAllHistorySync() {
        return repository.getAllHistorySync();
    }

    public void insertSubtask(Subtask subtask) {
        repository.insertSubtask(subtask);
    }

    public LiveData<List<Subtask>> getSubtasksForItem(long itemId) {
        return repository.getSubtasksForItem(itemId);
    }

    public LiveData<List<Subtask>> getAllSubtasksLive() {
        return repository.getAllSubtasksLive();
    }

    public void updateSubtask(Subtask subtask) {
        repository.updateSubtask(subtask);
    }

    public void deleteSubtask(Subtask subtask) {
        repository.deleteSubtask(subtask);
    }

    public void deleteSubtasksForItem(long itemId) {
        repository.deleteSubtasksForItem(itemId);
    }

    public void deleteOccurrencesForItem(long itemId) {
        repository.deleteOccurrencesForItem(itemId);
    }

    // --- Projects ---
    public LiveData<List<Project>> getAllProjects() {
        return repository.getAllProjects();
    }

    public void insertProject(Project project) {
        repository.insertProject(project);
    }
}
