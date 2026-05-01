package com.astra.lifeorganizer.utils;

import androidx.annotation.Nullable;

import com.astra.lifeorganizer.data.entities.Item;
import com.astra.lifeorganizer.utils.LabelUtils;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Calendar;

public class IcsUtils {

    private static final String DATE_TIME_FORMAT = "yyyyMMdd'T'HHmmss";
    private static final String UTC_FORMAT = "yyyyMMdd'T'HHmmss'Z'";

    /**
     * Exports a list of Items to an RFC 5545 compliant ICS string.
     */
    public static String exportToIcs(List<Item> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//Astra//Life Organizer//EN\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");

        // Add local TimeZone definition
        sb.append(getVTimeZone());

        String dtStamp = formatUtc(System.currentTimeMillis());

        for (Item item : items) {
            if ("event".equals(item.type) || "todo".equals(item.type)) {
                if ("todo".equals(item.type)) {
                    sb.append("BEGIN:VTODO\r\n");
                    sb.append("UID:").append(item.id).append("@astra.app\r\n");
                    sb.append("DTSTAMP:").append(dtStamp).append("\r\n");
                    sb.append("SUMMARY:").append(foldLine(escape(item.title))).append("\r\n");
                    if (item.description != null && !item.description.isEmpty()) {
                        sb.append("DESCRIPTION:").append(foldLine(escape(item.description))).append("\r\n");
                    }
                    if (item.dueAt != null) {
                        sb.append("DUE:").append(formatWithTz(item.dueAt)).append("\r\n");
                    }
                    if ("done".equals(item.status)) {
                        sb.append("STATUS:COMPLETED\r\n");
                    }
                    sb.append("END:VTODO\r\n");
                } else {
                    sb.append("BEGIN:VEVENT\r\n");
                    sb.append("UID:").append(item.id).append("@astra.app\r\n");
                    sb.append("DTSTAMP:").append(dtStamp).append("\r\n");
                    sb.append("SUMMARY:").append(foldLine(escape(item.title))).append("\r\n");
                    if (item.description != null && !item.description.isEmpty()) {
                        sb.append("DESCRIPTION:").append(foldLine(escape(item.description))).append("\r\n");
                    }
                    
                    if (item.allDay) {
                        if (item.startAt != null) {
                            sb.append("DTSTART;VALUE=DATE:").append(formatDateOnly(item.startAt)).append("\r\n");
                        }
                        if (item.endAt != null) {
                            sb.append("DTEND;VALUE=DATE:").append(formatDateOnly(item.endAt)).append("\r\n");
                        }
                    } else if (item.startAt != null) {
                        sb.append("DTSTART;TZID=").append(TimeZone.getDefault().getID()).append(":")
                          .append(formatLocal(item.startAt)).append("\r\n");
                        if (item.endAt != null) {
                            sb.append("DTEND;TZID=").append(TimeZone.getDefault().getID()).append(":")
                              .append(formatLocal(item.endAt)).append("\r\n");
                        } else if (item.dueAt != null) {
                            sb.append("DTEND;TZID=").append(TimeZone.getDefault().getID()).append(":")
                              .append(formatLocal(item.dueAt)).append("\r\n");
                        }
                    } else if (item.dueAt != null) {
                        sb.append("DTEND;TZID=").append(TimeZone.getDefault().getID()).append(":")
                          .append(formatLocal(item.dueAt)).append("\r\n");
                    }

                    if (item.recurrenceRule != null) {
                        sb.append("RRULE:FREQ=").append(item.recurrenceRule.toUpperCase()).append("\r\n");
                    }
                    
                    sb.append("END:VEVENT\r\n");
                }
            }
        }

        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    private static String getVTimeZone() {
        TimeZone tz = TimeZone.getDefault();
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VTIMEZONE\r\n");
        sb.append("TZID:").append(tz.getID()).append("\r\n");
        sb.append("X-LIC-LOCATION:").append(tz.getID()).append("\r\n");
        
        // Simple VTIMEZONE block (Daylight/Standard transition logic can be complex, 
        // using a basic descriptive approach here as per RFC 5545 minimal requirements)
        sb.append("BEGIN:DAYLIGHT\r\n");
        sb.append("TZOFFSETFROM:").append(formatOffset(tz.getRawOffset())).append("\r\n");
        sb.append("TZOFFSETTO:").append(formatOffset(tz.getRawOffset() + tz.getDSTSavings())).append("\r\n");
        sb.append("TZNAME:").append(tz.getDisplayName(true, TimeZone.SHORT)).append("\r\n");
        sb.append("DTSTART:19700308T020000\r\n");
        sb.append("RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=2SU\r\n");
        sb.append("END:DAYLIGHT\r\n");

        sb.append("BEGIN:STANDARD\r\n");
        sb.append("TZOFFSETFROM:").append(formatOffset(tz.getRawOffset() + tz.getDSTSavings())).append("\r\n");
        sb.append("TZOFFSETTO:").append(formatOffset(tz.getRawOffset())).append("\r\n");
        sb.append("TZNAME:").append(tz.getDisplayName(false, TimeZone.SHORT)).append("\r\n");
        sb.append("DTSTART:19701101T020000\r\n");
        sb.append("RRULE:FREQ=YEARLY;BYMONTH=11;BYDAY=1SU\r\n");
        sb.append("END:STANDARD\r\n");
        
        sb.append("END:VTIMEZONE\r\n");
        return sb.toString();
    }

    private static String formatOffset(int millis) {
        int hours = Math.abs(millis) / 3600000;
        int minutes = (Math.abs(millis) / 60000) % 60;
        return (millis >= 0 ? "+" : "-") + String.format(Locale.US, "%02d%02d", hours, minutes);
    }

    private static String formatLocal(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_TIME_FORMAT, Locale.US);
        return sdf.format(new Date(timestamp));
    }

    private static String formatDateOnly(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date(timestamp));
    }

    private static String formatUtc(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat(UTC_FORMAT, Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date(timestamp));
    }
    
    private static String formatWithTz(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_TIME_FORMAT, Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date(timestamp));
    }

    /**
     * Parses an ICS string into a list of Items. Improved to handle TZID.
     */
    public static List<Item> importFromIcs(String icsContent) {
        return importFromIcs(icsContent, null, false);
    }

    /**
     * Imports ICS content and optionally applies a fixed label or calendar-name labels.
     */
    public static List<Item> importFromIcs(String icsContent, @Nullable String fixedLabel, boolean useCalendarNames) {
        List<Item> items = new ArrayList<>();
        String[] lines = icsContent.split("\\r?\\n");
        Item currentItem = null;
        String currentCalendarName = null;
        boolean insideCalendar = false;

        for (String line : lines) {
            line = line.trim();
            if (line.equals("BEGIN:VCALENDAR")) {
                insideCalendar = true;
                currentCalendarName = null;
            } else if (line.equals("END:VCALENDAR")) {
                insideCalendar = false;
                currentCalendarName = null;
            } else if (insideCalendar && (line.startsWith("X-WR-CALNAME") || line.startsWith("NAME"))) {
                int colon = line.indexOf(':');
                if (colon >= 0) {
                    currentCalendarName = unescape(line.substring(colon + 1)).trim();
                }
            } else if (line.equals("BEGIN:VEVENT")) {
                currentItem = new Item();
                currentItem.type = "event";
                currentItem.label = resolveImportedLabel(fixedLabel, useCalendarNames, currentCalendarName);
            } else if (line.equals("BEGIN:VTODO")) {
                currentItem = new Item();
                currentItem.type = "todo";
                currentItem.label = resolveImportedLabel(fixedLabel, useCalendarNames, currentCalendarName);
            } else if (line.equals("END:VEVENT") || line.equals("END:VTODO")) {
                if (currentItem != null) {
                    currentItem.createdAt = System.currentTimeMillis();
                    currentItem.status = "pending";
                    if (useCalendarNames && (currentItem.label == null || currentItem.label.trim().isEmpty())) {
                        currentItem.label = currentCalendarName;
                    }
                    items.add(currentItem);
                }
                currentItem = null;
            } else if (currentItem != null) {
                if (line.startsWith("SUMMARY:")) {
                    currentItem.title = unescape(line.substring(8));
                } else if (line.startsWith("DESCRIPTION:")) {
                    currentItem.description = unescape(line.substring(12));
                } else if (line.contains("DTSTART")) {
                    boolean isDateOnly = line.contains("VALUE=DATE");
                    currentItem.allDay = currentItem.allDay || isDateOnly;
                    currentItem.startAt = parseDate(line.substring(line.lastIndexOf(":") + 1), isDateOnly);
                    
                    if (currentItem.allDay && currentItem.startAt != null) {
                        // CR-017/CR-019: Force to local midnight
                        currentItem.startAt = forceToLocalMidnight(currentItem.startAt);
                    }
                } else if (line.contains("DTEND")) {
                    boolean isDateOnly = line.contains("VALUE=DATE");
                    currentItem.allDay = currentItem.allDay || isDateOnly;
                    Long endAtValue = parseDate(line.substring(line.lastIndexOf(":") + 1), isDateOnly);
                    
                    if (currentItem.allDay && endAtValue != null) {
                        // DTEND in ICS is exclusive. For all-day events, it's the midnight of the day AFTER it ends.
                        // We shift it to local midnight if it was UTC, then subtract 1ms.
                        long normalizedEnd = forceToLocalMidnight(endAtValue);
                        currentItem.endAt = normalizedEnd - 1;
                    } else {
                        currentItem.endAt = endAtValue;
                    }
                } else if (line.contains("DUE")) {
                    currentItem.dueAt = parseDate(line.substring(line.lastIndexOf(":") + 1), false);
                } else if (line.startsWith("RRULE:")) {
                    currentItem.recurrenceRule = parseRRule(line.substring(6));
                }
            }
        }
        return items;
    }

    private static long forceToLocalMidnight(long timestamp) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(timestamp);
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);
        
        Calendar localCal = Calendar.getInstance(); // Default TZ
        localCal.set(year, month, day, 0, 0, 0);
        localCal.set(Calendar.MILLISECOND, 0);
        return localCal.getTimeInMillis();
    }

    private static String resolveImportedLabel(@Nullable String fixedLabel, boolean useCalendarNames, @Nullable String calendarName) {
        if (fixedLabel != null && !fixedLabel.trim().isEmpty()) {
            return LabelUtils.normalizeLabel(fixedLabel);
        }
        if (useCalendarNames && calendarName != null && !calendarName.trim().isEmpty()) {
            return LabelUtils.normalizeLabel(calendarName);
        }
        return null;
    }

    private static Long parseDate(String icsDate, boolean dateOnly) {
        if (icsDate.contains(":")) {
            icsDate = icsDate.substring(icsDate.indexOf(":") + 1);
        }
        
        try {
            if (dateOnly || icsDate.length() == 8) { // YYYYMMDD
                SimpleDateFormat shortSdf = new SimpleDateFormat("yyyyMMdd", Locale.US);
                shortSdf.setTimeZone(TimeZone.getDefault());
                Calendar cal = Calendar.getInstance();
                cal.setTime(shortSdf.parse(icsDate));
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                return cal.getTimeInMillis();
            }
            if (icsDate.endsWith("Z")) {
                SimpleDateFormat sdf = new SimpleDateFormat(UTC_FORMAT, Locale.US);
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                return sdf.parse(icsDate).getTime();
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat(DATE_TIME_FORMAT, Locale.US);
                sdf.setTimeZone(TimeZone.getDefault());
                return sdf.parse(icsDate).getTime();
            }
        } catch (ParseException e) {
            return null;
        }
    }

    private static String parseRRule(String rrule) {
        if (rrule.contains("FREQ=DAILY")) return "daily";
        if (rrule.contains("FREQ=WEEKLY")) return "weekly";
        if (rrule.contains("FREQ=MONTHLY")) return "monthly";
        return null;
    }

    private static String foldLine(String line) {
        if (line.length() <= 75) return line;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < line.length()) {
            int end = Math.min(i + 75, line.length());
            sb.append(line, i, end);
            i = end;
            if (i < line.length()) sb.append("\r\n ");
        }
        return sb.toString();
    }

    private static String escape(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n");
    }

    private static String unescape(String str) {
        if (str == null) return "";
        return str.replace("\\\\", "\\").replace("\\;", ";").replace("\\,", ",").replace("\\n", "\n");
    }
}
