package com.astra.lifeorganizer.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "reminders")
public class Reminder {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "item_id")
    public Long itemId;

    @ColumnInfo(name = "occurrence_id")
    public Long occurrenceId;

    public long triggerAt;
    
    public String message;
    public boolean isFired;
}
