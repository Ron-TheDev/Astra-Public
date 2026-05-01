package com.astra.lifeorganizer.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(
    tableName = "subtasks",
    foreignKeys = @ForeignKey(
        entity = Item.class,
        parentColumns = "id",
        childColumns = "itemId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("itemId")}
)
public class Subtask {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long itemId;
    public String title;
    public boolean isDone;
}
