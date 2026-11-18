package dev.requiredtruth.alarmclock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONObject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class Alarms {
    static final long SNOOZE = 5 * 60_000L;
    static final String REGULAR = "regular", LATER = "snooze", STOP = "stop";

    static final class Alarm {
        String id = UUID.randomUUID().toString();
        String label = "";
        int hour = 7, minute = 0, days = 0;
        boolean enabled = true, vibrate = true;
        long next, snoozed;
        String title() { return label.isEmpty() ? "Alarm" : label; }
    }

    private static SharedPreferences prefs(Context c) {
        return c.createDeviceProtectedStorageContext().getSharedPreferences("alarms", Context.MODE_PRIVATE);
    }

    static Alarm get(Context c, String id) {
        if (id == null) return null;
        String raw = prefs(c).getString(id, null);
        if (raw == null) return null;
        try {
            JSONObject j = new JSONObject(raw);
            Alarm a = new Alarm();
            a.id = id;
            a.label = j.getString("label");
            a.hour = j.getInt("hour");
            a.minute = j.getInt("minute");
            a.days = j.getInt("days");
            a.enabled = j.getBoolean("enabled");
            a.vibrate = j.getBoolean("vibrate");
            a.next = j.getLong("next");
            a.snoozed = j.getLong("snoozed");
            return a;
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    static List<Alarm> all(Context c) {
        List<Alarm> list = new ArrayList<>();
        for (String id : prefs(c).getAll().keySet()) {
            Alarm a = get(c, id);
            if (a != null) list.add(a);
        }
        list.sort(Comparator.comparingInt(a -> a.hour * 60 + a.minute));
        return list;
    }

    static void save(Context c, Alarm a) {
        try {
            JSONObject j = new JSONObject();
            j.put("label", a.label).put("hour", a.hour).put("minute", a.minute)
                .put("days", a.days).put("enabled", a.enabled).put("vibrate", a.vibrate)
                .put("next", a.next).put("snoozed", a.snoozed);
            if (!prefs(c).edit().putString(a.id, j.toString()).commit()) {
                android.util.Log.e("AlarmClock", "Could not persist alarm");
            }
        } catch (org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    static long next(Alarm a, long now, ZoneId zone) {
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        for (int offset = 0; offset <= 7; offset++) {
            LocalDate date = today.plusDays(offset);
            int bit = 1 << (date.getDayOfWeek().getValue() - 1);
            if (a.days != 0 && (a.days & bit) == 0) continue;
            long candidate = date.atTime(LocalTime.of(a.hour, a.minute)).atZone(zone).toInstant().toEpochMilli();
            if (candidate > now) return candidate;
        }
        throw new IllegalStateException("No next occurrence");
    }

    static Intent intent(Context c, Class<?> type, String action, String id) {
        return new Intent(c, type).setAction(action)
            .setData(Uri.fromParts("alarmclock", id, null));
    }

    static String id(Intent i) {
        return i.getData() == null ? null : i.getData().getSchemeSpecificPart();
    }

    static boolean canSchedule(Context c) {
        return android.os.Build.VERSION.SDK_INT < 31
            || c.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    static void apply(Context c, Alarm a) {
        save(c, a);
        schedule(c, a, REGULAR, a.enabled ? a.next : 0);
        schedule(c, a, LATER, a.snoozed);
    }

    private static void schedule(Context c, Alarm a, String action, long when) {
        AlarmManager manager = c.getSystemService(AlarmManager.class);
        Intent trigger = intent(c, AlarmReceiver.class, action, a.id).putExtra("when", when);
        PendingIntent event = PendingIntent.getBroadcast(c, 0, trigger,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.cancel(event);
        if (when <= 0 || !canSchedule(c)) return;
        PendingIntent show = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE);
        try {
            manager.setAlarmClock(new AlarmManager.AlarmClockInfo(when, show), event);
        } catch (SecurityException e) {
            android.util.Log.w("AlarmClock", "Exact alarm access changed", e);
        }
    }

    static void restore(Context c, boolean clockChanged) {
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        for (Alarm a : all(c)) {
            if (a.enabled && ((clockChanged && !a.id.equals("test")) || a.next <= 0 || a.next <= now - 60_000)) {
                if (a.days == 0 && !clockChanged && a.next > 0) {
                    a.enabled = false;
                    a.next = 0;
                } else {
                    a.next = next(a, now, zone);
                }
            }
            if (a.snoozed > 0 && a.snoozed <= now - 60_000) a.snoozed = 0;
            apply(c, a);
        }
    }

    static void delete(Context c, Alarm a) {
        schedule(c, a, REGULAR, 0);
        schedule(c, a, LATER, 0);
        prefs(c).edit().remove(a.id).commit();
    }
}
