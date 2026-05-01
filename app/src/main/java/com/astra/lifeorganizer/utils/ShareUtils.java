package com.astra.lifeorganizer.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import com.astra.lifeorganizer.data.entities.Item;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ShareUtils {

    private static final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());

    public static void shareItemsAsText(Context context, List<Item> items) {
        if (items == null || items.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            sb.append(getItemText(item));
            if (i < items.size() - 1) {
                sb.append("\n\n---\n\n");
            }
        }

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, items.size() == 1 ? items.get(0).title : "Shared " + items.size() + " Items");
        intent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        context.startActivity(Intent.createChooser(intent, "Share via"));
    }

    public static void shareItemsAsIcs(Context context, List<Item> items) {
        if (items == null || items.isEmpty()) return;

        String icsContent = IcsUtils.exportToIcs(items);
        try {
            File cachePath = new File(context.getCacheDir(), "shared_events");
            cachePath.mkdirs();
            File file = new File(cachePath, "event.ics");
            FileOutputStream stream = new FileOutputStream(file);
            stream.write(icsContent.getBytes());
            stream.close();

            Uri contentUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/calendar");
            intent.putExtra(Intent.EXTRA_STREAM, contentUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(intent, "Share event"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getItemText(Item item) {
        StringBuilder sb = new StringBuilder();
        sb.append("📌 ").append(item.title).append("\n");
        
        if ("habit".equals(item.type)) {
            sb.append("Type: Habit\n");
        } else if ("event".equals(item.type)) {
            sb.append("Type: Event\n");
        }

        if (item.allDay) {
            sb.append("Time: All day\n");
        } else if (item.startAt != null && item.startAt > 0) {
            sb.append("Starts: ").append(dateTimeFormat.format(new Date(item.startAt))).append("\n");
            if (item.endAt != null && item.endAt > 0) {
                sb.append("Ends: ").append(dateTimeFormat.format(new Date(item.endAt))).append("\n");
            }
        } else if (item.dueAt != null && item.dueAt > 0) {
            sb.append("Due: ").append(dateTimeFormat.format(new Date(item.dueAt))).append("\n");
        }

        if (item.label != null && !item.label.trim().isEmpty()) {
            sb.append("Label: ").append(item.label).append("\n");
        }

        if (item.description != null && !item.description.trim().isEmpty()) {
            sb.append("\nNote:\n").append(item.description.trim());
        }

        return sb.toString();
    }
}
