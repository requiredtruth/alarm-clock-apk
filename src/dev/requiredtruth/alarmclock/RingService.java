package dev.requiredtruth.alarmclock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import java.util.LinkedHashMap;

public final class RingService extends Service {
    private static final String CHANNEL = "ringing";
    private static final int NOTIFICATION = 1;
    private static final long RING_LIMIT = 10 * 60_000L;
    static String ringingId;
    private final LinkedHashMap<String, Alarms.Alarm> queue = new LinkedHashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = () -> finishAlarm(ringingId, false);
    private MediaPlayer player;
    private Vibrator vibrator;

    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Ringing alarms",
            NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null, null);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        if (i == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String id = Alarms.id(i);
        if ("ring".equals(i.getAction())) {
            Alarms.Alarm alarm = Alarms.get(this, id);
            if (alarm != null) queue.put(id, alarm);
            if (ringingId == null && !queue.isEmpty()) ringNext();
            if (queue.isEmpty()) stopSelf();
        } else {
            finishAlarm(id, Alarms.LATER.equals(i.getAction()));
        }
        return START_NOT_STICKY;
    }

    private void ringNext() {
        Alarms.Alarm alarm = queue.values().iterator().next();
        ringingId = alarm.id;
        PendingIntent screen = PendingIntent.getActivity(this, 0,
            Alarms.intent(this, RingActivity.class, "show", alarm.id),
            PendingIntent.FLAG_IMMUTABLE);
        Notification notice = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(alarm.title())
            .setContentText("Alarm ringing")
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "Snooze 5 min", action(Alarms.LATER, alarm.id)).build())
            .addAction(new Notification.Action.Builder(null, "Dismiss", action(Alarms.STOP, alarm.id)).build())
            .build();
        startForeground(NOTIFICATION, notice);
        if (alarm.vibrate) {
            vibrator = getSystemService(Vibrator.class);
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 550, 700, 550, 1500}, 0),
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
            }
        }
        startSound();
        handler.postDelayed(timeout, RING_LIMIT);
    }

    private PendingIntent action(String action, String id) {
        return PendingIntent.getService(this, 0, Alarms.intent(this, RingService.class, action, id),
            PendingIntent.FLAG_IMMUTABLE);
    }

    private void startSound() {
        player = new MediaPlayer();
        try (android.content.res.AssetFileDescriptor tone = getResources().openRawResourceFd(R.raw.alarm)) {
            player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            player.setDataSource(tone.getFileDescriptor(), tone.getStartOffset(), tone.getLength());
            player.setLooping(true);
            player.setOnPreparedListener(MediaPlayer::start);
            player.prepareAsync();
        } catch (java.io.IOException | RuntimeException e) {
            android.util.Log.e("AlarmClock", "Alarm audio unavailable", e);
            player.release();
            player = null;
        }
    }

    private void finishAlarm(String id, boolean snooze) {
        if (id == null || !id.equals(ringingId)) {
            if (queue.isEmpty()) stopSelf();
            return;
        }
        Alarms.Alarm alarm = Alarms.get(this, id);
        if (alarm != null && snooze) {
            alarm.snoozed = System.currentTimeMillis() + Alarms.SNOOZE;
            Alarms.apply(this, alarm);
        }
        queue.remove(id);
        silence();
        if (queue.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        } else {
            ringNext();
        }
    }

    private void silence() {
        ringingId = null;
        handler.removeCallbacks(timeout);
        if (player != null) {
            player.setOnPreparedListener(null);
            player.release();
            player = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
    }

    @Override public void onDestroy() {
        silence();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
