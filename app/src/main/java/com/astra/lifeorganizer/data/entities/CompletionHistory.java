package com.astra.lifeorganizer.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "completion_history")
public class CompletionHistory {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "item_id")
    public long itemId;

    @ColumnInfo(name = "occurrence_id")
    public Long occurrenceId;

    // done, uncompleted, skipped, missed, canceled, rescheduled
    public String action;

    public long timestamp;
}
