package net.kolya.android.extraquiet;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.audiofx.AudioEffect;
import android.os.Build;
import android.os.IBinder;
import android.service.quicksettings.TileService;
import android.util.Log;

/**
 * Foreground service that owns the attenuation effects while Extra quiet is on.
 * Audio effects only live as long as the process that created them, so the
 * service keeps the process alive; stopping it restores normal volume.
 */
public class QuietService extends Service {
    private static final String TAG = "ExtraQuiet";
    private static final String CHANNEL_ID = "extra_quiet";
    private static final int NOTIFICATION_ID = 1;
    static final String ACTION_STOP = "net.kolya.android.extraquiet.STOP";

    private static volatile boolean running;
    private static volatile String globalStatus = "";

    private QuietEngine engine;
    private BroadcastReceiver sessionReceiver;
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;

    static boolean isRunning() {
        return running;
    }

    static String getGlobalStatus() {
        return globalStatus;
    }

    /** May throw if the system disallows a foreground-service start right now. */
    static void start(Context c) {
        c.startForegroundService(new Intent(c, QuietService.class));
        Prefs.setEnabled(c, true);
    }

    static void stop(Context c) {
        Prefs.setEnabled(c, false);
        c.stopService(new Intent(c, QuietService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stop(this);
            return START_NOT_STICKY;
        }
        // A null intent means the system restarted us after killing the process.
        if (intent == null && !Prefs.isEnabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startInForeground();
        if (engine == null) {
            engine = new QuietEngine(Prefs.getReductionDb(this));
            engine.attach(0);
            globalStatus = engine.describeGlobal();
            registerSessionReceiver();
            prefListener = (prefs, key) -> {
                if (Prefs.KEY_REDUCTION_DB.equals(key)) {
                    engine.setReductionDb(Prefs.getReductionDb(this));
                    startInForeground();  // refresh notification text
                    refreshTile();
                }
            };
            Prefs.get(this).registerOnSharedPreferenceChangeListener(prefListener);
        }
        running = true;
        refreshTile();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        globalStatus = "";
        if (prefListener != null) {
            Prefs.get(this).unregisterOnSharedPreferenceChangeListener(prefListener);
        }
        if (sessionReceiver != null) {
            unregisterReceiver(sessionReceiver);
        }
        if (engine != null) {
            engine.releaseAll();
            engine = null;
        }
        refreshTile();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Also covers individual player sessions announced by well-behaved media apps. */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")  // flags only exist on API 33+
    private void registerSessionReceiver() {
        sessionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR);
                if (session <= 0 || engine == null) {
                    return;
                }
                if (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(intent.getAction())) {
                    Log.i(TAG, "Session opened: " + session);
                    engine.attach(session);
                } else {
                    Log.i(TAG, "Session closed: " + session);
                    engine.detach(session);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        filter.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(sessionReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(sessionReceiver, filter);
        }
    }

    private void startInForeground() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW));

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, QuietService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text, Prefs.getReductionDb(this)))
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, getString(R.string.turn_off), stop).build())
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }

    private void refreshTile() {
        TileService.requestListeningState(this, new ComponentName(this, QuietTileService.class));
    }
}
