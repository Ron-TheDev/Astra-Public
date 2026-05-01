package com.astra.lifeorganizer.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "item_occurrences")
public class ItemOccurrence {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "item_id")
    public long itemId;

    @ColumnInfo(name = "scheduled_for")
    public long scheduledFor;

    // status: pending, done, missed, skipped, canceled, rescheduled
    public String status;

    @ColumnInfo(name = "override_start_at")
    public Long overrideStartAt;

    @ColumnInfo(name = "override_end_at")
    public Long overrideEndAt;

    @ColumnInfo(name = "current_count")
    public int currentCount = 0;

    @ColumnInfo(name = "is_complete")
    public boolean isComplete = false;
}
