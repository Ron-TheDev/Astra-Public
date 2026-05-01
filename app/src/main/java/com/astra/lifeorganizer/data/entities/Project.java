package com.astra.lifeorganizer.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "projects")
public class Project {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String name;
    public String iconName; // e.g., "ic_work", "ic_home"
    public String colorHex; // e.g., "#3F51B5"

    public Project(String name, String iconName, String colorHex) {
        this.name = name;
        this.iconName = iconName;
        this.colorHex = colorHex;
    }
}
