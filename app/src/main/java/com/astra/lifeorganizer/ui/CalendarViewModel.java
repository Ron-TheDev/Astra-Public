package com.astra.lifeorganizer.ui;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.astra.lifeorganizer.data.entities.CalendarEntity;
import com.astra.lifeorganizer.data.repositories.CalendarRepository;

import java.util.List;

public class CalendarViewModel extends AndroidViewModel {
    private CalendarRepository repository;
    private LiveData<List<CalendarEntity>> allCalendars;

    public CalendarViewModel(Application application) {
        super(application);
        repository = new CalendarRepository(application);
        allCalendars = repository.getAllCalendars();
    }

    public LiveData<List<CalendarEntity>> getAllCalendars() {
        return allCalendars;
    }

    public void insert(CalendarEntity calendar) {
        repository.insert(calendar);
    }
}
