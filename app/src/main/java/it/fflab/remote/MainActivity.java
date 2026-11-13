package it.fflab.remote;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;

/** Simple owner-visible screen for starting and revoking a foreground session. */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(13, 19, 30);
    private static final int SURFACE = Color.rgb(26, 38, 53);
    private static final int ACCENT = Color.rgb(255, 161, 67);
    private static final int MUTED = Color.rgb(188, 204, 219);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status, output, log;
    private Button start, stop;
    private EditText remoteUrl, remoteToken;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            showState();
            handler.postDelayed(this, 700);
        }
    };

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildScreen();
        // Android 13+ requires runtime permission for drawer notifications.
        // The foreground-service indicator remains visible in Task Manager.
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                   != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 71);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        handler.post(refresh);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
        scroll.addView(root);
        root.addView(text("FF REMOTE LAB", 26, ACCENT, true));
        root.addView(text("Versione 4.0 - Wi-Fi / 4G / 5G", 14, MUTED, false));

        LinearLayout card = card();
        root.addView(card, spaced(22));
        card.addView(text("SESSIONE AUTORIZZATA", 13, ACCENT, true));
        status = text("DISATTIVATA", 20, Color.WHITE, true);
        card.addView(status, spaced(12));
        card.addView(text("Il PC deve essere gia' associato tramite Android Debug wireless.", 14, MUTED, false));
        root.addView(text("CONNESSIONE INTERNET PRIVATA (FACOLTATIVA)", 14, ACCENT, true), spaced(18));
        root.addView(text("Configura l'URL HTTPS di Tailscale Serve e il token riservato ad Android. Il token resta sul telefono; non mostrarlo ad altri.", 13, MUTED, false), spaced(8));
        remoteUrl = new EditText(this);
        remoteUrl.setSingleLine(true);
        remoteUrl.setHint("https://nome.tailnet.ts.net/");
        remoteUrl.setText(RemoteInternetClient.savedUrl(this));
        remoteUrl.setTextColor(Color.WHITE);
        remoteUrl.setHintTextColor(MUTED);
        root.addView(remoteUrl, spaced(8));
        remoteToken = new EditText(this);
        remoteToken.setSingleLine(true);
        remoteToken.setHint("Token Android (da server Kali)");
        remoteToken.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        remoteToken.setTextColor(Color.WHITE);
        remoteToken.setHintTextColor(MUTED);
        root.addView(remoteToken, spaced(8));
        Button save = button("SALVA CONNESSIONE HTTPS", SURFACE, Color.WHITE);
        root.addView(save, spaced(8));
        save.setOnClickListener(v -> {
            try {
                String url = remoteUrl.getText().toString().trim();
                String token = remoteToken.getText().toString().trim();
                RemoteInternetClient.save(this, url, token);
                remoteToken.setText("");
                android.widget.Toast.makeText(this,
                    "Connessione memorizzata. Attiva la sessione per collegarti.",
                    android.widget.Toast.LENGTH_LONG).show();
            } catch (IllegalArgumentException ex) {
                android.widget.Toast.makeText(this, ex.getMessage(),
                    android.widget.Toast.LENGTH_LONG).show();
            }
        });

        start = button("ABILITA SESSIONE CONTINUA", ACCENT, BG);
        card.addView(start, spaced(14));
        stop = button("REVOCA SUBITO", SURFACE, Color.WHITE);
        card.addView(stop, spaced(8));
        start.setOnClickListener(v -> enable());
        stop.setOnClickListener(v -> disable());

        LinearLayout response = card();
        root.addView(response, spaced(18));
        response.addView(text("RISPOSTA AI COMANDI", 13, ACCENT, true));
        output = text("Nessun comando", 17, Color.WHITE, false);
        response.addView(output, spaced(9));

        LinearLayout history = card();
        root.addView(history, spaced(18));
        history.addView(text("REGISTRO VISIBILE", 13, ACCENT, true));
        log = text("", 13, MUTED, false);
        history.addView(log, spaced(10));

        root.addView(text(
            "Una notifica persistente indica quando la sessione e' attiva. " +
            "Uscire da questa schermata non revoca i comandi. Tocca REVOCA " +
            "o INTERROMPI nella notifica per terminare. Il servizio NON attiva " +
            "da solo Debug wireless e non aggira le autorizzazioni Android.",
            13, MUTED, false), spaced(16));
        setContentView(scroll);
        showState();
    }

    private void enable() {
        try {
            Intent service = new Intent(this, RemoteSessionService.class);
            service.setAction(RemoteSessionService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
            output.setText("Avvio servizio richiesto");
        } catch (RuntimeException ex) {
            output.setText("Avvio non riuscito: " + ex.getClass().getSimpleName());
        }
        showState();
    }

    private void disable() {
        // stopService destroys receiver and clears active state, even in background.
        stopService(new Intent(this, RemoteSessionService.class));
        getSharedPreferences(RemoteSessionService.PREFS, MODE_PRIVATE)
            .edit().putBoolean(RemoteSessionService.KEY_ACTIVE, false).apply();
        output.setText("Sessione revocata");
        showState();
    }

    private void showState() {
        if (status == null) return;
        SharedPreferences prefs = getSharedPreferences(RemoteSessionService.PREFS, MODE_PRIVATE);
        boolean active = RemoteSessionService.isActive(this);
        status.setText(active ? "ATTIVA / IN BACKGROUND" : "DISATTIVATA");
        start.setEnabled(!active);
        stop.setEnabled(active);
        output.setText(prefs.getString(RemoteSessionService.KEY_OUTPUT, "Nessun comando"));
        log.setText(prefs.getString(RemoteSessionService.KEY_HISTORY, ""));
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

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
