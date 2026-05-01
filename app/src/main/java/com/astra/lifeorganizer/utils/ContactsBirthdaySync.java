package com.astra.lifeorganizer.utils;

import android.Manifest;
import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;

import androidx.core.content.ContextCompat;

import com.astra.lifeorganizer.data.database.AstraDatabase;
import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.data.repositories.ItemRepository;
import com.astra.lifeorganizer.data.repositories.SettingsRepository;
import com.astra.lifeorganizer.utils.LabelUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public final class ContactsBirthdaySync {
    private ContactsBirthdaySync() {}

    public interface Callback {
        void onFinished(int importedCount, String message);
    }

    public static void sync(Context context, ItemRepository itemRepository, SettingsRepository settingsRepository, Callback callback) {
        Context appContext = context.getApplicationContext();
        if (!settingsRepository.getBoolean(SettingsRepository.KEY_IMPORT_BIRTHDAYS, false)) {
            notify(callback, 0, "Birthday import is disabled.");
            return;
        }
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notify(callback, 0, "Contacts permission is required to import birthdays.");
            return;
        }

        AstraDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<Item> imported = loadBirthdays(appContext);
                itemRepository.replaceImportedBirthdaysSync(imported);
                if (!imported.isEmpty()) {
                    for (Item item : imported) {
                        int defaultColor = LabelUtils.fallbackColor(LabelUtils.displayLabel(item.label));
                        settingsRepository.setLabelColor(item.label, settingsRepository.getLabelColor(item.label, defaultColor));
                    }
                }
                notify(callback, imported.size(), imported.isEmpty() ? "No birthdays found." : "Imported " + imported.size() + " birthdays.");
            } catch (Exception e) {
                notify(callback, 0, "Birthday sync failed: " + e.getMessage());
            }
        });
    }

    private static List<Item> loadBirthdays(Context context) {
        List<Item> imported = new ArrayList<>();
        String[] projection = {
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Event.START_DATE
        };
        String selection = ContactsContract.Data.MIMETYPE + "=? AND " + ContactsContract.CommonDataKinds.Event.TYPE + "=?";
        String[] selectionArgs = {
                ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                String.valueOf(ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY)
        };

        try (Cursor cursor = context.getContentResolver().query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                ContactsContract.Contacts.DISPLAY_NAME + " COLLATE NOCASE ASC")) {
            if (cursor == null) {
                return imported;
            }
            while (cursor.moveToNext()) {
                long contactId = cursor.getLong(0);
                String name = cursor.getString(1);
                String birthday = cursor.getString(2);
                Calendar cal = parseBirthday(birthday);
                long birthdayKey = Math.abs((contactId + ":" + birthday).hashCode());
                if (name == null || cal == null) {
                    continue;
                }

                Item item = new Item();
                item.title = name.trim() + " Birthday";
                item.description = "Imported from contacts";
                item.type = "event";
                item.startAt = cal.getTimeInMillis();
                Calendar end = Calendar.getInstance();
                end.setTimeInMillis(item.startAt);
                end.add(Calendar.DAY_OF_YEAR, 1);
                item.endAt = end.getTimeInMillis();
                item.allDay = true;
                item.status = "pending";
                item.priority = 1;
                item.createdAt = System.currentTimeMillis();
                item.label = "Birthdays";
                item.calendarId = birthdayKey;
                imported.add(item);
            }
        }
        return imported;
    }

    private static Calendar parseBirthday(String raw) {
        try {
            if (raw == null || raw.trim().isEmpty()) {
                return null;
            }
            String normalized = raw.trim();
            String digitsOnly = normalized.replaceAll("[^0-9]", "");
            if (digitsOnly.length() == 8) {
                int monthIndex = Integer.parseInt(digitsOnly.substring(4, 6)) - 1;
                int dayOfMonth = Integer.parseInt(digitsOnly.substring(6, 8));
                return buildBirthdayCalendar(monthIndex, dayOfMonth);
            }
            if (digitsOnly.length() == 4) {
                int monthIndex = Integer.parseInt(digitsOnly.substring(0, 2)) - 1;
                int dayOfMonth = Integer.parseInt(digitsOnly.substring(2, 4));
                return buildBirthdayCalendar(monthIndex, dayOfMonth);
            }
            String[] parts = normalized.split("[^0-9]+");
            List<Integer> numbers = new ArrayList<>();
            for (String part : parts) {
                if (part != null && !part.isEmpty()) {
                    numbers.add(Integer.parseInt(part));
                }
            }
            if (numbers.size() < 2) {
                return null;
            }
            int monthIndex;
            int dayOfMonth;
            if (numbers.size() >= 3 && parts[0].length() == 4) {
                monthIndex = numbers.get(1) - 1;
                dayOfMonth = numbers.get(2);
            } else {
                if (numbers.get(0) > 12) {
                    monthIndex = numbers.get(1) - 1;
                    dayOfMonth = numbers.get(2);
                } else {
                    monthIndex = numbers.get(0) - 1;
                    dayOfMonth = numbers.get(1);
                }
            }
            return buildBirthdayCalendar(monthIndex, dayOfMonth);
        } catch (Exception e) {
            return null;
        }
    }

    private static Calendar buildBirthdayCalendar(int monthIndex, int dayOfMonth) {
        if (monthIndex < 0 || monthIndex > 11 || dayOfMonth < 1 || dayOfMonth > 31) {
            return null;
        }
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR));
        cal.set(Calendar.MONTH, monthIndex);
        cal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() < System.currentTimeMillis()) {
            cal.add(Calendar.YEAR, 1);
        }
        return cal;
    }

    private static void notify(Callback callback, int count, String message) {
        if (callback != null) {
            callback.onFinished(count, message);
        }
    }
}
