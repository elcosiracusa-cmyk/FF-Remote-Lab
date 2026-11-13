package it.fflab.remote;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * User-configured, HTTPS-only relay client for a private Tailscale network.
 * Uses a distinct Android device token. Cannot execute arbitrary shell commands.
 * The foreground service owns the lifecycle and provides the visible STOP action.
 */
final class RemoteInternetClient {
    static final String PREFS = "remote_internet";
    static final String KEY_URL = "url";
    static final String KEY_TOKEN = "token";
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private Thread thread;
    private final CommandHandler commands;

    interface CommandHandler {
        String execute(String command, String message);
    }

    RemoteInternetClient(Context context, CommandHandler commands) {
        this.context = context.getApplicationContext();
        this.commands = commands;
    }

    static boolean configured(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return !prefs.getString(KEY_URL, "").isEmpty()
            && !prefs.getString(KEY_TOKEN, "").isEmpty();
    }

    static String savedUrl(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_URL, "");
    }

    static void save(Context context, String url, String token) {
        if (!url.startsWith("https://") || url.contains("@") ||
                url.contains("?") || url.contains("#") || !url.endsWith("/")) {
            throw new IllegalArgumentException("Richiesto URL HTTPS terminante in /");
        }
        if (token.length() < 32 || token.contains(" ")) {
            throw new IllegalArgumentException("Token dispositivo non valido");
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_URL, url)
            .putString(KEY_TOKEN, token).apply();
    }

    void start() {
        if (running || !configured(context)) return;
        running = true;
        thread = new Thread(this::poll, "ffremote-internet-poll");
        thread.start();
    }

    void stop() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    private void poll() {
        while (running) {
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                String base = prefs.getString(KEY_URL, "");
                String token = prefs.getString(KEY_TOKEN, "");
                String response = request(base + "v1/android/poll", "GET", token, null);
                JSONObject command = new JSONObject(response);
                String kind = command.optString("command", "NONE").toUpperCase(Locale.ROOT);
                if (!"NONE".equals(kind) && running) {
                    String msg = command.optString("text", "");
                    int id = command.optInt("id", -1);
                    // Every action runs on the main thread so it can use Android services safely.
                    handler.post(() -> {
                        if (!running) return;
                        String result = commands.execute(kind, msg);
                        JSONObject acknowledgement = new JSONObject();
                        try {
                            acknowledgement.put("id", id);
                            acknowledgement.put("command", kind);
                            acknowledgement.put("result", result);
                            new Thread(() -> {
                                try {
                                    request(base + "v1/android/ack", "POST", token,
                                        acknowledgement.toString().getBytes(StandardCharsets.UTF_8));
                                } catch (Exception ignored) { }
                            }, "ffremote-internet-ack").start();
                        } catch (Exception ignored) { }
                    });
                }
            } catch (Exception ignored) {
                // Network may be unavailable during mobile/Wi-Fi changes.
            }
            try {
                Thread.sleep(3500);
            } catch (InterruptedException interrupt) {
                break;
            }
        }
    }

    private static String request(String path, String method, String token, byte[] body)
            throws Exception {
        URL url = new URL(path);
        if (!"https".equals(url.getProtocol()))
            throw new IllegalArgumentException("HTTPS richiesto");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(6000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/json");
        try {
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body);
                }
            }
            if (connection.getResponseCode() != 200) {
                throw new IllegalStateException("Errore HTTP " + connection.getResponseCode());
            }
            try (InputStream input = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                StringBuilder out = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) out.append(line);
                return out.toString();
            }
        } finally {
            connection.disconnect();
        }
    }
}
