package it.fflab.remote;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Explicitly owner-enabled foreground session.
 *
 * The receiver accepts broadcasts only from callers holding the privileged
 * android.permission.DUMP permission (ADB shell/system), not ordinary apps.
 * Leaving MainActivity does NOT end the session. The notification exposes STOP.
 * This is not a hidden background agent and does not turn on ADB automatically.
 */
public final class RemoteSessionService extends Service {
    public static final String ACTION_START = "it.fflab.remote.START";
    public static final String ACTION_STOP = "it.fflab.remote.STOP_SESSION";
    public static final String ACTION_COMMAND = "it.fflab.remote.COMMAND";
    public static final String PREFS = "ffremote_session";
    public static final String KEY_ACTIVE = "active";
    public static final String KEY_OUTPUT = "output";
    public static final String KEY_HISTORY = "history";

    private static final String CHANNEL_ID = "ffremote_active";
    private static final int NOTIFICATION_ID = 3100;
    private static final String SENDER_PERMISSION = "android.permission.DUMP";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean receiverRegistered;
    private boolean sessionStarted;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent != null && ACTION_COMMAND.equals(intent.getAction())) {
                executeCommand(intent);
            }
        }
    };

    public static boolean isActive(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ACTIVE, false);
    }

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            record("Sessione revocata dal telefono", "Sessione disattivata");
            stopSelf();
            return START_NOT_STICKY;
        }

        // START_STICKY restarts may have a null intent. Only resume an existing
        // consented session, never start a session without a local user action.
        if (!ACTION_START.equals(action) && !(intent == null && isActive(this))) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!sessionStarted) {
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    startForeground(
                        NOTIFICATION_ID, buildNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification());
                }

                IntentFilter filter = new IntentFilter(ACTION_COMMAND);
                if (Build.VERSION.SDK_INT >= 33) {
                    registerReceiver(receiver, filter, SENDER_PERMISSION, null,
                                     Context.RECEIVER_EXPORTED);
                } else {
                    registerReceiver(receiver, filter, SENDER_PERMISSION, null);
                }
                receiverRegistered = true;
                sessionStarted = true;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(KEY_ACTIVE, true).apply();
                record("Sessione attiva con notifica visibile",
                       "Pronto: comandi ADB abilitati anche fuori dall'app");
            } catch (RuntimeException ex) {
                record("Impossibile avviare il servizio: " + ex.getClass().getSimpleName(),
                       "Errore di avvio sessione");
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        return START_STICKY;
    }

    private void createNotificationChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "FF Remote Lab - sessione attiva",
            NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Sessione di comando ADB autorizzata e revocabile");
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent = new Intent(this, RemoteSessionService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("FF Remote Lab: sessione attiva")
            .setContentText("Controllo ADB autorizzato. Tocca per aprire l'app.")
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                       "INTERROMPI", stop)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build();
    }

    private void executeCommand(Intent intent) {
        if (!sessionStarted) return;
        String command = intent.getStringExtra("ff.command");
        if (command == null) return;
        command = command.trim().toUpperCase(Locale.ROOT);

        switch (command) {
            case "PING":
                record("ESEGUITO PING", "PONG - Kali collegato");
                return;
            case "MESSAGE":
                try {
                    String encoded = intent.getStringExtra("ff.text64");
                    if (encoded == null || encoded.length() > 1000) return;
                    String msg = new String(
                        Base64.decode(encoded, Base64.DEFAULT),
                        StandardCharsets.UTF_8).trim();
                    if (msg.isEmpty()) return;
                    record("ESEGUITO MESSAGE",
                           msg.substring(0, Math.min(120, msg.length())));
                } catch (IllegalArgumentException ex) {
                    record("MESSAGE non valido", "Testo non valido");
                }
                return;
            case "VIBRATE":
                Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(VibrationEffect.createOneShot(
                        250, VibrationEffect.DEFAULT_AMPLITUDE));
                }
                record("ESEGUITO VIBRATE", "Vibrazione richiesta");
                return;
            case "BEEP":
                ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 250);
                handler.postDelayed(tone::release, 350);
                record("ESEGUITO BEEP", "Segnale audio richiesto");
                return;
            case "STATUS":
                BatteryManager battery =
                    (BatteryManager) getSystemService(BATTERY_SERVICE);
                int pct = battery == null ? -1 :
                    battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                record("ESEGUITO STATUS",
                       Build.MANUFACTURER + " " + Build.MODEL
                       + "\nAndroid " + Build.VERSION.RELEASE
                       + "\nBatteria " + (pct >= 0 ? pct + "%" : "n/d"));
                return;
            case "STOP":
                record("STOP ricevuto da Kali", "Sessione disattivata");
                stopSelf();
                return;
            default:
                record("Comando non riconosciuto", "Comando non supportato");
        }
    }

    private void record(String entry, String response) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String time = new SimpleDateFormat("HH:mm:ss", Locale.ITALY).format(new Date());
        StringBuilder history = new StringBuilder(time + " - " + entry);
        String previous = prefs.getString(KEY_HISTORY, "");
        int n = 0;
        for (String line : previous.split("\n")) {
            if (!line.isEmpty() && n++ < 11) history.append("\n").append(line);
        }
        prefs.edit()
            .putString(KEY_OUTPUT, response)
            .putString(KEY_HISTORY, history.toString())
            .apply();
    }

    @Override public void onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(receiver); }
            catch (IllegalArgumentException ignored) { }
            receiverRegistered = false;
        }
        sessionStarted = false;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, false).apply();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
