package dev.requiredtruth.alarmclock;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public final class MainActivity extends Screen {
    private static final String[] DAYS = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private String shownRinging;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!java.util.Objects.equals(shownRinging, RingService.ringingId)) render();
            handler.postDelayed(this, 700);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setVolumeControlStream(android.media.AudioManager.STREAM_ALARM);
    }

    @Override protected void onResume() {
        super.onResume();
        Alarms.restore(this, false);
        render();
        handler.post(refresh);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    private void render() {
        shownRinging = RingService.ringingId;
        LinearLayout layout = root();
        TextView heading = text("Alarm Clock", 34, ink);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        layout.addView(heading);
        space(layout, 8);

        List<Alarms.Alarm> alarms = Alarms.all(this);
        long next = Long.MAX_VALUE;
        for (Alarms.Alarm a : alarms) {
            if (a.enabled && a.next > 0) next = Math.min(next, a.next);
            if (a.snoozed > 0) next = Math.min(next, a.snoozed);
        }
        String status = next == Long.MAX_VALUE ? "No upcoming alarms" : "Next alarm  •  " + nextLabel(next);
        layout.addView(text(status, 16, mint));
        space(layout, 18);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout cards = column();
        scroll.addView(cards);
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        if (shownRinging != null) {
            String id = shownRinging;
            cards.addView(button("Open ringing alarm", true, v ->
                startActivity(Alarms.intent(this, RingActivity.class, "show", id))));
            space(cards, 12);
        }
        if (!Alarms.canSchedule(this)) {
            cards.addView(button("Allow exact alarms", false, v -> openSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)));
            space(cards, 8);
        }
        NotificationManager notifications = getSystemService(NotificationManager.class);
        if (!notifications.areNotificationsEnabled()) {
            cards.addView(button("Allow alarm notifications", false, v -> allowNotifications()));
            space(cards, 8);
        } else if (Build.VERSION.SDK_INT >= 34 && !notifications.canUseFullScreenIntent()) {
            cards.addView(button("Allow lock-screen alarms", false, v -> openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)));
            space(cards, 8);
        }
        if (alarms.isEmpty()) {
            TextView empty = text("Add your first alarm to get started.", 17, muted);
            empty.setPadding(0, dp(12), 0, dp(12));
            cards.addView(empty);
        }
        for (Alarms.Alarm alarm : alarms) {
            if (!alarm.id.equals("test")) addCard(cards, alarm);
        }
        cards.addView(button("Test in 15 seconds", false, v -> testAlarm()));
        space(cards, 12);
        layout.addView(button("Add alarm", true, v -> edit(new Alarms.Alarm(), true)));
        setContentView(layout);
    }

    private void addCard(LinearLayout cards, Alarms.Alarm alarm) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(rounded(surface, 20));
        card.setPadding(dp(16), dp(11), dp(12), dp(11));
        LinearLayout details = column();
        Calendar time = Calendar.getInstance();
        time.set(Calendar.HOUR_OF_DAY, alarm.hour);
        time.set(Calendar.MINUTE, alarm.minute);
        String clock = DateFormat.getTimeFormat(this).format(time.getTime());
        details.addView(text(clock, 30, alarm.enabled ? ink : muted));
        String summary = (alarm.label.isEmpty() ? "" : alarm.label + "  •  ")
            + (alarm.snoozed > 0 ? "Snoozed until " + nextLabel(alarm.snoozed) : repeatLabel(alarm.days));
        details.addView(text(summary, 15, muted));
        details.setContentDescription("Edit " + clock + " " + summary);
        details.setOnClickListener(v -> {
            Alarms.Alarm current = Alarms.get(this, alarm.id);
            if (current != null) edit(current, false);
        });
        card.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = new Switch(this);
        toggle.setChecked(alarm.enabled || alarm.snoozed > 0);
        toggle.setMinHeight(dp(48));
        toggle.setMinWidth(dp(48));
        toggle.setContentDescription("Enable " + alarm.title() + " at " + clock);
        toggle.setThumbTintList(new ColorStateList(
            new int[][]{{android.R.attr.state_checked}, {}}, new int[]{mint, muted}));
        toggle.setOnCheckedChangeListener((view, enabled) -> {
            Alarms.Alarm current = Alarms.get(this, alarm.id);
            if (current == null) return;
            current.enabled = enabled;
            current.snoozed = 0;
            current.next = enabled ? Alarms.next(current, System.currentTimeMillis(), ZoneId.systemDefault()) : 0;
            Alarms.apply(this, current);
            render();
        });
        card.addView(toggle);
        cards.addView(card);
        space(cards, 10);
    }

    private String nextLabel(long when) {
        String time = DateFormat.getTimeFormat(this).format(new Date(when));
        String day = new java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault())
            .format(new Date(when));
        return day + " · " + time;
    }

    private String repeatLabel(int mask) {
        if (mask == 0) return "Once";
        if (mask == 127) return "Every day";
        if (mask == 31) return "Weekdays";
        if (mask == 96) return "Weekends";
        List<String> selected = new ArrayList<>();
        for (int day = 0; day < DAYS.length; day++) {
            if ((mask & (1 << day)) != 0) selected.add(DAYS[day]);
        }
        return String.join(", ", selected);
    }

    private void edit(Alarms.Alarm alarm, boolean adding) {
        LinearLayout form = column();
        form.setPadding(dp(18), 0, dp(18), dp(12));
        TimePicker time = new TimePicker(this);
        time.setIs24HourView(DateFormat.is24HourFormat(this));
        time.setHour(alarm.hour);
        time.setMinute(alarm.minute);
        form.addView(time);
        EditText label = new EditText(this);
        label.setHint("Label (optional)");
        label.setSingleLine();
        label.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(60)});
        label.setText(alarm.label);
        form.addView(label);
        space(form, 10);
        int[] selected = {alarm.days};
        android.widget.Button repeat = button("Repeat  •  " + repeatLabel(selected[0]), false, null);
        repeat.setOnClickListener(v -> pickDays(selected, repeat));
        form.addView(repeat);
        Switch vibrate = new Switch(this);
        vibrate.setText("Vibrate");
        vibrate.setMinHeight(dp(48));
        vibrate.setChecked(alarm.vibrate);
        form.addView(vibrate);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
            .setTitle(adding ? "Add alarm" : "Edit alarm")
            .setView(scroll).setNegativeButton("Cancel", null)
            .setPositiveButton("Save", (d, which) -> {
                time.clearFocus();
                alarm.hour = time.getHour();
                alarm.minute = time.getMinute();
                alarm.label = label.getText().toString().trim();
                alarm.days = selected[0];
                alarm.vibrate = vibrate.isChecked();
                alarm.enabled = true;
                alarm.snoozed = 0;
                alarm.next = Alarms.next(alarm, System.currentTimeMillis(), ZoneId.systemDefault());
                Alarms.apply(this, alarm);
                render();
            });
        if (!adding) dialog.setNeutralButton("Delete", (d, which) -> {
            Alarms.delete(this, alarm);
            render();
        });
        dialog.create().show();
    }

    private void pickDays(int[] selected, android.widget.Button button) {
        int[] draft = {selected[0]};
        boolean[] checked = new boolean[DAYS.length];
        for (int i = 0; i < DAYS.length; i++) checked[i] = (draft[0] & (1 << i)) != 0;
        new AlertDialog.Builder(this).setTitle("Repeat days")
            .setMultiChoiceItems(DAYS, checked, (dialog, day, on) -> {
                if (on) draft[0] |= 1 << day;
                else draft[0] &= ~(1 << day);
            })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Done", (dialog, which) -> {
                selected[0] = draft[0];
                button.setText("Repeat  •  " + repeatLabel(selected[0]));
            }).show();
    }

    private void testAlarm() {
        if (!Alarms.canSchedule(this)) {
            openSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            return;
        }
        Alarms.Alarm alarm = new Alarms.Alarm();
        alarm.id = "test";
        alarm.label = "Test alarm";
        alarm.next = System.currentTimeMillis() + 15_000L;
        Alarms.apply(this, alarm);
        render();
        Toast.makeText(this, "Test alarm set. You can lock your screen now.", Toast.LENGTH_LONG).show();
    }

    private void allowNotifications() {
        if (Build.VERSION.SDK_INT >= 33
            && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            && (!getPreferences(MODE_PRIVATE).getBoolean("asked_notifications", false)
                || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("asked_notifications", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        } else {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        render();
    }

    private void openSettings(String action) {
        startActivity(new Intent(action, Uri.parse("package:" + getPackageName())));
    }
}
