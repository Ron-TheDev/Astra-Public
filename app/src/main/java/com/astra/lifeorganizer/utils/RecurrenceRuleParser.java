package com.astra.lifeorganizer.utils;

import com.astra.lifeorganizer.data.entities.Item;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class RecurrenceRuleParser {

    // ─── Human-readable summaries ────────────────────────────────────────────

    public static String getHumanReadableSummary(String rrule) {
        if (rrule == null || rrule.isEmpty() || rrule.equalsIgnoreCase("NONE")) {
            return "Does not repeat";
        }

        if (rrule.contains("FREQ=DAILY")) {
            return "Repeats daily";
        }

        if (rrule.contains("FREQ=WEEKLY")) {
            if (rrule.contains("BYDAY=")) {
                String daysStr = extract(rrule, "BYDAY=");
                String[] split = daysStr.split(",");
                List<String> humanDays = new ArrayList<>();
                for (String d : split) {
                    switch (d) {
                        case "MO": humanDays.add("Monday"); break;
                        case "TU": humanDays.add("Tuesday"); break;
                        case "WE": humanDays.add("Wednesday"); break;
                        case "TH": humanDays.add("Thursday"); break;
                        case "FR": humanDays.add("Friday"); break;
                        case "SA": humanDays.add("Saturday"); break;
                        case "SU": humanDays.add("Sunday"); break;
                    }
                }

                if (humanDays.size() == 2 && humanDays.contains("Saturday") && humanDays.contains("Sunday")) {
                    return "Repeats every weekend";
                }
                if (humanDays.size() == 5 && !humanDays.contains("Saturday") && !humanDays.contains("Sunday")) {
                    return "Repeats every weekday";
                }
                if (humanDays.isEmpty()) return "Repeats weekly";

                String joined;
                if (humanDays.size() == 1) {
                    joined = humanDays.get(0);
                } else {
                    joined = String.join(", ", humanDays.subList(0, humanDays.size() - 1))
                            + " and " + humanDays.get(humanDays.size() - 1);
                }
                return "Repeats every " + joined;
            }
            return "Repeats weekly";
        }

        if (rrule.contains("FREQ=MONTHLY")) {
            if (rrule.contains("BYMONTHDAY=")) {
                String dateStr = extract(rrule, "BYMONTHDAY=");
                if (dateStr.equals("-1")) return "Repeats on the last day of each month";
                return "Repeats on the " + addOrdinal(Integer.parseInt(dateStr)) + " of each month";
            }

            if (rrule.contains("BYDAY=") && rrule.contains("BYSETPOS=")) {
                String setPosStr = extract(rrule, "BYSETPOS=");
                String dayStr = extract(rrule, "BYDAY=");

                String posWord;
                switch (setPosStr) {
                    case "1":  posWord = "first";  break;
                    case "2":  posWord = "second"; break;
                    case "3":  posWord = "third";  break;
                    case "4":  posWord = "fourth"; break;
                    default:   posWord = "last";   break;
                }

                String dayWord;
                switch (dayStr) {
                    case "MO": dayWord = "Monday";    break;
                    case "TU": dayWord = "Tuesday";   break;
                    case "WE": dayWord = "Wednesday"; break;
                    case "TH": dayWord = "Thursday";  break;
                    case "FR": dayWord = "Friday";    break;
                    case "SA": dayWord = "Saturday";  break;
                    default:   dayWord = "Sunday";    break;
                }
                return "Repeats on the " + posWord + " " + dayWord + " of each month";
            }
            return "Repeats monthly";
        }

        return rrule;
    }

    // ─── Next occurrence calculation ─────────────────────────────────────────

    /**
     * Returns the timestamp of the next occurrence STRICTLY after {@code afterTimestamp},
     * preserving the time-of-day from the original timestamp.
     * Returns -1 if the rule is null/NONE or unrecognised.
     */
    public static long getNextOccurrence(String rrule, long afterTimestamp) {
        if (rrule == null || rrule.isEmpty() || rrule.equalsIgnoreCase("NONE")) return -1;

        Calendar base = Calendar.getInstance();
        base.setTimeInMillis(afterTimestamp);

        // ── DAILY ───────────────────────────────────────────────────────────
        if (rrule.contains("FREQ=DAILY")) {
            base.add(Calendar.DAY_OF_YEAR, 1);
            return base.getTimeInMillis();
        }

        // ── WEEKLY ──────────────────────────────────────────────────────────
        if (rrule.contains("FREQ=WEEKLY")) {
            if (!rrule.contains("BYDAY=")) {
                base.add(Calendar.WEEK_OF_YEAR, 1);
                return base.getTimeInMillis();
            }

            String daysStr = extract(rrule, "BYDAY=");
            List<Integer> targetDays = new ArrayList<>();
            for (String d : daysStr.split(",")) {
                switch (d) {
                    case "MO": targetDays.add(Calendar.MONDAY);    break;
                    case "TU": targetDays.add(Calendar.TUESDAY);   break;
                    case "WE": targetDays.add(Calendar.WEDNESDAY); break;
                    case "TH": targetDays.add(Calendar.THURSDAY);  break;
                    case "FR": targetDays.add(Calendar.FRIDAY);    break;
                    case "SA": targetDays.add(Calendar.SATURDAY);  break;
                    case "SU": targetDays.add(Calendar.SUNDAY);    break;
                }
            }
            if (targetDays.isEmpty()) {
                base.add(Calendar.WEEK_OF_YEAR, 1);
                return base.getTimeInMillis();
            }

            // Walk day-by-day (max 14 steps) to find next matching day
            Calendar probe = Calendar.getInstance();
            probe.setTimeInMillis(afterTimestamp);
            for (int i = 0; i < 14; i++) {
                probe.add(Calendar.DAY_OF_YEAR, 1);
                if (targetDays.contains(probe.get(Calendar.DAY_OF_WEEK))) {
                    return probe.getTimeInMillis();
                }
            }
            base.add(Calendar.WEEK_OF_YEAR, 1);
            return base.getTimeInMillis();
        }

        // ── MONTHLY ─────────────────────────────────────────────────────────
        if (rrule.contains("FREQ=MONTHLY")) {

            if (rrule.contains("BYMONTHDAY=")) {
                String dateStr = extract(rrule, "BYMONTHDAY=");
                Calendar next = Calendar.getInstance();
                next.setTimeInMillis(afterTimestamp);
                next.add(Calendar.MONTH, 1);

                if (dateStr.equals("-1")) {
                    next.set(Calendar.DAY_OF_MONTH, next.getActualMaximum(Calendar.DAY_OF_MONTH));
                } else {
                    int day = Integer.parseInt(dateStr);
                    next.set(Calendar.DAY_OF_MONTH, Math.min(day, next.getActualMaximum(Calendar.DAY_OF_MONTH)));
                }
                return next.getTimeInMillis();
            }

            if (rrule.contains("BYSETPOS=") && rrule.contains("BYDAY=")) {
                String setPosStr = extract(rrule, "BYSETPOS=");
                String dayStr   = extract(rrule, "BYDAY=");

                int targetDow;
                switch (dayStr) {
                    case "MO": targetDow = Calendar.MONDAY;    break;
                    case "TU": targetDow = Calendar.TUESDAY;   break;
                    case "WE": targetDow = Calendar.WEDNESDAY; break;
                    case "TH": targetDow = Calendar.THURSDAY;  break;
                    case "FR": targetDow = Calendar.FRIDAY;    break;
                    case "SA": targetDow = Calendar.SATURDAY;  break;
                    default:   targetDow = Calendar.SUNDAY;    break;
                }

                Calendar next = Calendar.getInstance();
                next.setTimeInMillis(afterTimestamp);
                next.add(Calendar.MONTH, 1);
                next.set(Calendar.DAY_OF_MONTH, 1);

                if (setPosStr.equals("-1")) {
                    // Last occurrence of targetDow in that month
                    next.set(Calendar.DAY_OF_MONTH, next.getActualMaximum(Calendar.DAY_OF_MONTH));
                    while (next.get(Calendar.DAY_OF_WEEK) != targetDow) {
                        next.add(Calendar.DAY_OF_MONTH, -1);
                    }
                } else {
                    int setPos = Integer.parseInt(setPosStr);
                    // Advance to first target weekday
                    while (next.get(Calendar.DAY_OF_WEEK) != targetDow) {
                        next.add(Calendar.DAY_OF_MONTH, 1);
                    }
                    // Then skip (setPos-1) more weeks
                    next.add(Calendar.WEEK_OF_YEAR, setPos - 1);
                }
                return next.getTimeInMillis();
            }

            // Fallback – same day next month
            return base.getTimeInMillis();
        }

        return -1;
    }

    public static boolean isValidOccurrence(long timestamp, Item habit) {
        if (habit == null) return false;
        
        long start = habit.startAt != null ? habit.startAt : (habit.dueAt != null ? habit.dueAt : habit.createdAt);
        if (start == 0) return false;

        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(timestamp);
        normalizeToStartOfDay(target);

        Calendar anchor = Calendar.getInstance();
        anchor.setTimeInMillis(start);
        normalizeToStartOfDay(anchor);

        // Check if target date is before start date
        if (target.before(anchor)) return false;

        // Check end date if exists
        if (habit.endAt != null) {
            Calendar end = Calendar.getInstance();
            end.setTimeInMillis(habit.endAt);
            normalizeToStartOfDay(end);
            if (target.after(end)) return false;
        }

        // If no recurrence rule, it's only valid on the start date
        if (habit.recurrenceRule == null || habit.recurrenceRule.isEmpty() || habit.recurrenceRule.equalsIgnoreCase("NONE")) {
            return target.getTimeInMillis() == anchor.getTimeInMillis();
        }

        // For recurring items, we check if target falls on a valid day in the cycle
        String rrule = habit.recurrenceRule;
        
        if (rrule.contains("FREQ=DAILY")) {
            return true; // Any day after start is valid
        }

        if (rrule.contains("FREQ=WEEKLY")) {
            if (!rrule.contains("BYDAY=")) {
                // If weekly without specific days, it's every 7 days from anchor
                long diff = target.getTimeInMillis() - anchor.getTimeInMillis();
                return (diff % (7 * 24 * 60 * 60 * 1000L)) == 0;
            }

            String daysStr = extract(rrule, "BYDAY=");
            List<Integer> targetDays = new ArrayList<>();
            for (String d : daysStr.split(",")) {
                switch (d) {
                    case "MO": targetDays.add(Calendar.MONDAY);    break;
                    case "TU": targetDays.add(Calendar.TUESDAY);   break;
                    case "WE": targetDays.add(Calendar.WEDNESDAY); break;
                    case "TH": targetDays.add(Calendar.THURSDAY);  break;
                    case "FR": targetDays.add(Calendar.FRIDAY);    break;
                    case "SA": targetDays.add(Calendar.SATURDAY);  break;
                    case "SU": targetDays.add(Calendar.SUNDAY);    break;
                }
            }
            return targetDays.contains(target.get(Calendar.DAY_OF_WEEK));
        }

        if (rrule.contains("FREQ=MONTHLY")) {
            if (rrule.contains("BYMONTHDAY=")) {
                String dateStr = extract(rrule, "BYMONTHDAY=");
                int dayOfMonth = target.get(Calendar.DAY_OF_MONTH);
                if (dateStr.equals("-1")) {
                    return dayOfMonth == target.getActualMaximum(Calendar.DAY_OF_MONTH);
                } else {
                    return dayOfMonth == Integer.parseInt(dateStr);
                }
            }
            if (rrule.contains("BYSETPOS=") && rrule.contains("BYDAY=")) {
                String setPosStr = extract(rrule, "BYSETPOS=");
                String dayStr = extract(rrule, "BYDAY=");
                
                int targetDow;
                switch (dayStr) {
                    case "MO": targetDow = Calendar.MONDAY;    break;
                    case "TU": targetDow = Calendar.TUESDAY;   break;
                    case "WE": targetDow = Calendar.WEDNESDAY; break;
                    case "TH": targetDow = Calendar.THURSDAY;  break;
                    case "FR": targetDow = Calendar.FRIDAY;    break;
                    case "SA": targetDow = Calendar.SATURDAY;  break;
                    default:   targetDow = Calendar.SUNDAY;    break;
                }

                if (target.get(Calendar.DAY_OF_WEEK) != targetDow) return false;

                if (setPosStr.equals("-1")) {
                    Calendar nextWeek = (Calendar) target.clone();
                    nextWeek.add(Calendar.WEEK_OF_YEAR, 1);
                    return nextWeek.get(Calendar.MONTH) != target.get(Calendar.MONTH);
                } else {
                    int setPos = Integer.parseInt(setPosStr);
                    int currentPos = (target.get(Calendar.DAY_OF_MONTH) - 1) / 7 + 1;
                    return currentPos == setPos;
                }
            }
            // Fallback: same day-of-month as anchor
            return target.get(Calendar.DAY_OF_MONTH) == anchor.get(Calendar.DAY_OF_MONTH);
        }

        return false;
    }

    private static void normalizeToStartOfDay(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    // ─── Virtual occurrence expansion (for calendar rendering) ───────────────

    /**
     * Expands a recurring item into virtual copies for every occurrence between
     * {@code windowStart} and {@code windowEnd}. The original item's
     * startAt / dueAt is used as the series anchor.
     */
    public static List<Item> expandOccurrences(Item item, long windowStart, long windowEnd) {
        List<Item> result = new ArrayList<>();
        if (item.recurrenceRule == null || item.recurrenceRule.equalsIgnoreCase("NONE")) {
            return result;
        }

        long anchor = item.startAt != null ? item.startAt : (item.dueAt != null ? item.dueAt : 0);
        if (anchor == 0) return result;

        // Keep event duration offset for virtual copies
        long durationMs = (item.startAt != null && item.endAt != null) ? (item.endAt - item.startAt) : 0;

        long cursor = anchor;
        int safety = 0;
        while (cursor <= windowEnd && safety++ < 2000) {
            if (cursor >= windowStart) {
                Item copy = shallowCopy(item);
                if (item.startAt != null) {
                    copy.startAt = cursor;
                    copy.endAt   = durationMs > 0 ? cursor + durationMs : null;
                } else {
                    copy.dueAt = cursor;
                }
                result.add(copy);
            }
            long next = getNextOccurrence(item.recurrenceRule, cursor);
            if (next <= cursor) break; // stall guard
            cursor = next;
        }
        return result;
    }

    // ─── Internal helpers ────────────────────────────────────────────────────

    private static String extract(String rrule, String key) {
        int idx = rrule.indexOf(key);
        if (idx < 0) return "";
        String val = rrule.substring(idx + key.length());
        int semi = val.indexOf(';');
        return semi >= 0 ? val.substring(0, semi) : val;
    }

    private static String addOrdinal(int number) {
        if (number >= 11 && number <= 13) return number + "th";
        switch (number % 10) {
            case 1:  return number + "st";
            case 2:  return number + "nd";
            case 3:  return number + "rd";
            default: return number + "th";
        }
    }

    /** Shallow-copies an Item for use as a virtual occurrence (same id = same source). */
    private static Item shallowCopy(Item src) {
        Item copy = new Item();
        copy.id             = src.id;
        copy.title          = src.title;
        copy.description    = src.description;
        copy.type           = src.type;
        copy.startAt        = src.startAt;
        copy.endAt          = src.endAt;
        copy.dueAt          = src.dueAt;
        copy.allDay         = src.allDay;
        copy.reminderAt     = src.reminderAt;
        copy.calendarId     = src.calendarId;
        copy.priority       = src.priority;
        copy.status         = src.status;
        copy.recurrenceRule = src.recurrenceRule;
        copy.createdAt      = src.createdAt;
        copy.label          = src.label;
        copy.frequency      = src.frequency;
        copy.isPositive     = src.isPositive;
        copy.projectId      = src.projectId;
        return copy;
    }
}
