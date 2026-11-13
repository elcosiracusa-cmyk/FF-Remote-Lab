package it.fflab.remote;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Base64;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/** Demonstration app with visible, owner-activated commands through paired ADB shell only. */
public final class MainActivity extends Activity {
    private static final String ACTION = "it.fflab.remote.COMMAND";
    private static final String SENDER_PERMISSION = "android.permission.DUMP";
    private static final int BG = Color.rgb(13, 19, 30);
    private static final int SURFACE = Color.rgb(26, 38, 53);
    private static final int ACCENT = Color.rgb(255, 161, 67);
    private static final int MUTED = Color.rgb(188, 204, 219);
    private final ArrayDeque<String> history = new ArrayDeque<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status, output, log;
    private Button start, stop;
    private boolean active = false;
    private boolean registered = false;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            // The receiver is already gated by android.permission.DUMP in
            // registerReceiver(), so ordinary third-party apps cannot send commands.
            // On some Android versions, "adb shell am broadcast" is delivered
            // with the system UID rather than the shell UID. Checking only
            // Process.SHELL_UID incorrectly rejected authorized commands.
            runCommand(intent);
        }
    };

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildScreen();
        writeLog("Avviata: abilita i comandi sul telefono");
    }

    @Override protected void onStop() {
        disable("Sessione terminata: app non visibile");
        super.onStop();
    }

    @Override protected void onDestroy() {
        disable("Sessione chiusa");
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
        scroll.addView(root);
        root.addView(text("FF REMOTE LAB", 26, ACCENT, true));
        root.addView(text("Versione 2.0 • Kali Linux / Android Wi-Fi", 14, MUTED, false));

        LinearLayout card = card();
        root.addView(card, spaced(22));
        card.addView(text("SESSIONE AUTORIZZATA", 13, ACCENT, true));
        status = text("DISATTIVATA", 20, Color.WHITE, true);
        card.addView(status, spaced(12));
        card.addView(text("Il PC deve essere gia' associato tramite Android Debug wireless.", 14, MUTED, false));
        start = button("ABILITA COMANDI", ACCENT, BG);
        card.addView(start, spaced(14));
        stop = button("REVOCA SUBITO", SURFACE, Color.WHITE);
        card.addView(stop, spaced(8));
        start.setOnClickListener(v -> enable());
        stop.setOnClickListener(v -> disable("Revocato sul telefono"));

        LinearLayout response = card();
        root.addView(response, spaced(18));
        response.addView(text("RISPOSTA AI COMANDI", 13, ACCENT, true));
        output = text("Nessun comando", 17, Color.WHITE, false);
        response.addView(output, spaced(9));

        LinearLayout logCard = card();
        root.addView(logCard, spaced(18));
        logCard.addView(text("REGISTRO VISIBILE", 13, ACCENT, true));
        log = text("", 13, MUTED, false);
        logCard.addView(log, spaced(10));

        root.addView(text(
            "Nessuna funzione nascosta, nessun accesso a SMS, foto, file o microfono. " +
            "Uscire dall'app interrompe la sessione. Per revocare il PC: " +
            "Android > Debug wireless > Dispositivi associati.",
            13, MUTED, false), spaced(16));
        setContentView(scroll);
        showState();
    }

    private void enable() {
        if (active) return;
        try {
            IntentFilter filter = new IntentFilter(ACTION);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(receiver, filter, SENDER_PERMISSION, null, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(receiver, filter, SENDER_PERMISSION, null);
            }
            registered = true;
            active = true;
            output.setText("Comandi abilitati finche' l'app rimane visibile");
            writeLog("Sessione avviata dal telefono");
        } catch (SecurityException e) {
            output.setText("Impossibile abilitare il ricevitore protetto");
            writeLog("Errore autorizzazione");
        }
        showState();
    }

    private void disable(String why) {
        if (registered) {
            try { unregisterReceiver(receiver); }
            catch (IllegalArgumentException ignored) { }
            registered = false;
        }
        if (active) writeLog(why);
        active = false;
        if (output != null) output.setText("Sessione disattivata");
        showState();
    }

    private void showState() {
        if (status == null) return;
        status.setText(active ? "ATTIVA / VISIBILE" : "DISATTIVATA");
        start.setEnabled(!active);
        stop.setEnabled(active);
    }

    private void runCommand(Intent intent) {
        if (!active || intent == null || !ACTION.equals(intent.getAction())) return;
        String command = intent.getStringExtra("ff.command");
        if (command == null) return;
        command = command.trim().toUpperCase(Locale.ROOT);
        switch (command) {
            case "PING":
                output.setText("PONG — Kali collegato");
                break;
            case "MESSAGE":
                try {
                    String encoded = intent.getStringExtra("ff.text64");
                    if (encoded == null || encoded.length() > 1000) return;
                    String msg = new String(Base64.decode(encoded, Base64.DEFAULT),
                                            StandardCharsets.UTF_8).trim();
                    if (msg.isEmpty()) return;
                    output.setText(msg.substring(0, Math.min(120, msg.length())));
                } catch (IllegalArgumentException ex) {
                    writeLog("MESSAGE non valido"); return;
                }
                break;
            case "VIBRATE":
                Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (vib != null && vib.hasVibrator()) {
                    vib.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE));
                }
                output.setText("Vibrazione richiesta");
                break;
            case "BEEP":
                ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 250);
                handler.postDelayed(tone::release, 350);
                output.setText("Segnale audio eseguito");
                break;
            case "STATUS":
                BatteryManager battery = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
                int pct = battery == null ? -1 :
                    battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                output.setText(Build.MANUFACTURER + " " + Build.MODEL +
                    "\nAndroid " + Build.VERSION.RELEASE +
                    "\nBatteria " + (pct >= 0 ? pct + "%" : "n/d"));
                break;
            case "STOP":
                disable("Arresto richiesto da Kali"); return;
            default:
                writeLog("Comando non riconosciuto"); return;
        }
        writeLog("ESEGUITO " + command);
    }

    private void writeLog(String message) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.ITALY).format(new Date());
        history.addFirst(time + " — " + message);
        while (history.size() > 12) history.removeLast();
        if (log != null) {
            StringBuilder s = new StringBuilder();
            for (String entry : history) s.append(entry).append('\n');
            log.setText(s.toString());
        }
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SURFACE);
        bg.setCornerRadius(dp(16));
        box.setBackground(bg);
        return box;
    }

    private TextView text(String content, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(content);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String content, int bgColor, int foreground) {
        Button b = new Button(this);
        b.setText(content);
        b.setAllCaps(false);
        b.setTextColor(foreground);
        b.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(10));
        b.setBackground(bg);
        return b;
    }

    private LinearLayout.LayoutParams spaced(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(top);
        return p;
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}
