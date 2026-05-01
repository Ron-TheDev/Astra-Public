package com.astra.lifeorganizer.data.daos;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.astra.lifeorganizer.data.entities.CalendarEntity;

import java.util.List;

@Dao
public interface CalendarDao {
    @Insert
    long insert(CalendarEntity calendar);

    @Update
    void update(CalendarEntity calendar);

    @Query("SELECT * FROM calendars")
    LiveData<List<CalendarEntity>> getAllCalendars();
}
