package com.astra.lifeorganizer.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "items")
public class Item {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String title;
    public String description;

    // "todo" | "habit" | "event"
    public String type;

    @ColumnInfo(name = "start_at")
    public Long startAt;

    @ColumnInfo(name = "end_at")
    public Long endAt;

    @ColumnInfo(name = "due_at")
    public Long dueAt;

    @ColumnInfo(name = "all_day")
    public boolean allDay;

    @ColumnInfo(name = "reminder_at")
    public Long reminderAt;

    @ColumnInfo(name = "calendar_id")
    public Long calendarId;

    public int priority;
    public String status; // pending, done, archived

    @ColumnInfo(name = "recurrence_rule")
    public String recurrenceRule;

    @ColumnInfo(name = "created_at")
    public long createdAt;
    
    @ColumnInfo(name = "streak_enabled")
    public boolean streakEnabled;

    public String label;
    
    public int frequency; // times per day/week
    
    @ColumnInfo(name = "is_positive")
    public boolean isPositive;

    @ColumnInfo(name = "project_id")
    public Long projectId;

    @ColumnInfo(name = "daily_target_count")
    public int dailyTargetCount = 1;
}
