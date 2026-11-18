package dev.requiredtruth.alarmclock;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class RingActivity extends Screen {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String id;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!id.equals(RingService.ringingId)) {
                finish();
                return;
            }
            handler.postDelayed(this, 500);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        id = Alarms.id(getIntent());
        Alarms.Alarm alarm = Alarms.get(this, id);
        if (alarm == null || !id.equals(RingService.ringingId)) {
            finish();
            return;
        }
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        LinearLayout layout = root();
        layout.setGravity(Gravity.CENTER);
        TextView time = text(android.text.format.DateFormat.getTimeFormat(this)
            .format(new java.util.Date()), 56, mint);
        time.setGravity(Gravity.CENTER);
        layout.addView(time);
        TextView title = text(alarm.title(), 28, ink);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);
        space(layout, 32);
        layout.addView(button("Snooze 5 minutes", false, v -> act(Alarms.LATER)));
        space(layout, 12);
        layout.addView(button("Dismiss", true, v -> act(Alarms.STOP)));
        setContentView(layout);
        handler.post(refresh);
    }

    private void act(String action) {
        startService(Alarms.intent(this, RingService.class, action, id));
        finish();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(refresh);
        super.onDestroy();
    }
}
