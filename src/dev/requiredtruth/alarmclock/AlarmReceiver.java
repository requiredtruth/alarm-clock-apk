package dev.requiredtruth.alarmclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.time.ZoneId;

public final class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        String action = i.getAction();
        if (!Alarms.REGULAR.equals(action) && !Alarms.LATER.equals(action)) {
            Alarms.restore(c, Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action));
            return;
        }
        Alarms.Alarm alarm = Alarms.get(c, Alarms.id(i));
        if (alarm == null) return;
        long when = i.getLongExtra("when", 0);
        if (when <= 0) return;
        if (Alarms.REGULAR.equals(action)) {
            if (!alarm.enabled || when != alarm.next) return;
            alarm.enabled = alarm.days != 0;
            alarm.next = alarm.enabled
                ? Alarms.next(alarm, Math.max(when, System.currentTimeMillis()), ZoneId.systemDefault()) : 0;
        } else {
            if (alarm.snoozed != when) return;
            alarm.snoozed = 0;
        }
        Alarms.apply(c, alarm);
        c.startForegroundService(Alarms.intent(c, RingService.class, "ring", alarm.id));
    }
}
