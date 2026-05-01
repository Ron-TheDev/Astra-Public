package com.astra.lifeorganizer.data.repositories;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.astra.lifeorganizer.data.daos.CalendarDao;
import com.astra.lifeorganizer.data.database.AstraDatabase;
import com.astra.lifeorganizer.data.entities.CalendarEntity;

import java.util.List;

public class CalendarRepository {
    private CalendarDao calendarDao;

    public CalendarRepository(Application application) {
        AstraDatabase db = AstraDatabase.getDatabase(application);
        calendarDao = db.calendarDao();
    }

    public LiveData<List<CalendarEntity>> getAllCalendars() {
        return calendarDao.getAllCalendars();
    }

    public void insert(CalendarEntity calendar) {
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            calendarDao.insert(calendar);
        });
    }

    public void update(CalendarEntity calendar) {
        AstraDatabase.databaseWriteExecutor.execute(() -> {
            calendarDao.update(calendar);
        });
    }
}
