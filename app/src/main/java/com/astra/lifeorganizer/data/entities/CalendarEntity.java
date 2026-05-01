package com.astra.lifeorganizer.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "calendars")
public class CalendarEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String name;
    public String color;
    public boolean isVisible;
    public boolean isDefault;
}
