package com.astra.lifeorganizer.data.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.astra.lifeorganizer.data.daos.CalendarDao;
import com.astra.lifeorganizer.data.daos.ItemDao;
import com.astra.lifeorganizer.data.entities.CalendarEntity;
import com.astra.lifeorganizer.data.entities.CompletionHistory;
import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.data.entities.ItemOccurrence;
import com.astra.lifeorganizer.data.entities.Project;
import com.astra.lifeorganizer.data.entities.Reminder;
import com.astra.lifeorganizer.data.entities.Subtask;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {
        Item.class,
        ItemOccurrence.class,
        CalendarEntity.class,
        CompletionHistory.class,
        Reminder.class,
        Subtask.class,
        Project.class
}, version = 5, exportSchema = false)
public abstract class AstraDatabase extends RoomDatabase {

    public abstract ItemDao itemDao();
    public abstract CalendarDao calendarDao();

    private static volatile AstraDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public static AstraDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AstraDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AstraDatabase.class, "astra_database")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
