package co.ritmo.app;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import androidx.core.app.NotificationCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Tarjeta fija "Ahora" en las notificaciones (mantiene viva la app) y
 * muestra los avisos: ventana encima de todo, pantalla completa o notificación.
 */
public class NowService extends Service {
    static final String ACTION_UPDATE = "co.ritmo.app.UPDATE";
    static final String ACTION_ALERT = "co.ritmo.app.SHOW_ALERT";
    static final int NOW_ID = 7;
    static final int ALERT_NOTIF_BASE = 1000;
    static final String CH_NOW = "ritmo_ahora", CH_BIG = "ritmo_avisos", CH_SOFT = "ritmo_suaves";

    private static View overlay;
    private static WindowManager overlayWm;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::refreshNow;

    static void start(Context c, String action, String alert) {
        Intent i = new Intent(c, NowService.class);
        i.setAction(action);
        if (alert != null) i.putExtra("alert", alert);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception e) {
            // Si el sistema no deja arrancar el servicio, al menos mostramos la notificación.
            if (alert != null) try { postAlertNotification(c, new JSONObject(alert)); } catch (Exception ignored) { }
        }
    }

    static void refresh(Context c) { start(c, ACTION_UPDATE, null); }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        channels(this);
        Notification n = buildNow(this);
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOW_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOW_ID, n);
        if (intent != null && ACTION_ALERT.equals(intent.getAction())) {
            try { showAlert(new JSONObject(intent.getStringExtra("alert"))); } catch (Exception ignored) { }
        }
        refreshNow();
        return START_STICKY;
    }

    private void refreshNow() {
        handler.removeCallbacks(tick);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOW_ID, buildNow(this));
        long next = nextBoundary(this) - System.currentTimeMillis();
        handler.postDelayed(tick, Math.max(20000, Math.min(next + 1000, 10 * 60000)));
    }

    // ---------- Canales ----------
    static void channels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CH_BIG) != null) return;
        NotificationChannel now = new NotificationChannel(CH_NOW, "Ahora (tarjeta fija)", NotificationManager.IMPORTANCE_LOW);
        now.setDescription("Lo que estás haciendo y lo que sigue");
        now.setShowBadge(false);
        NotificationChannel big = new NotificationChannel(CH_BIG, "Avisos importantes", NotificationManager.IMPORTANCE_HIGH);
        big.setDescription("Empezar tareas, salir, pantallas fuera");
        big.enableVibration(true);
        big.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationChannel soft = new NotificationChannel(CH_SOFT, "Avisos suaves", NotificationManager.IMPORTANCE_DEFAULT);
        soft.setDescription("Agua, descanso, tiempo libre");
        nm.createNotificationChannel(now);
        nm.createNotificationChannel(big);
        nm.createNotificationChannel(soft);
    }

    // ---------- Tarjeta "Ahora" ----------
    private static String hhmm(long ms) { return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ms)); }

    static long nextBoundary(Context c) {
        JSONArray t = Store.array(c, "timeline");
        long now = System.currentTimeMillis(), best = Long.MAX_VALUE;
        for (int i = 0; i < t.length(); i++) {
            JSONObject b = t.optJSONObject(i);
            if (b == null) continue;
            long s = b.optLong("start"), e = b.optLong("end");
            if (s > now && s < best) best = s;
            if (e > now && e < best) best = e;
        }
        return best == Long.MAX_VALUE ? now + 15 * 60000 : best;
    }

    static Notification buildNow(Context c) {
        JSONArray t = Store.array(c, "timeline");
        long now = System.currentTimeMillis();
        JSONObject cur = null, next = null;
        for (int i = 0; i < t.length(); i++) {
            JSONObject b = t.optJSONObject(i);
            if (b == null) continue;
            long s = b.optLong("start"), e = b.optLong("end");
            if (s <= now && e > now && (cur == null || b.optInt("prio") > cur.optInt("prio"))) cur = b;
            if (s > now && (next == null || s < next.optLong("start"))) next = b;
        }
        String title = cur != null ? cur.optString("title") : "Rato libre";
        StringBuilder text = new StringBuilder();
        if (cur != null) text.append("Hasta las ").append(hhmm(cur.optLong("end")));
        if (next != null) { if (text.length() > 0) text.append("  ·  "); text.append("Luego: ").append(next.optString("title")).append(" ").append(hhmm(next.optLong("start"))); }
        if (text.length() == 0) text.append("Nada más planeado hoy");
        Intent open = new Intent(c, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH_NOW)
                .setSmallIcon(R.drawable.ic_stat_ritmo)
                .setColor(0xFF5B4FE0)
                .setContentTitle(title)
                .setContentText(text.toString())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text.toString()))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(cur != null)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(pi);
        if (cur != null) {
            b.setWhen(cur.optLong("end")).setUsesChronometer(true);
            if (Build.VERSION.SDK_INT >= 24) b.setChronometerCountDown(true);
        }
        return b.build();
    }

    // ---------- Avisos ----------
    private void showAlert(JSONObject a) {
        boolean big = a.optBoolean("big");
        vibrate(big);
        if (big && canOverlayNow(this)) { showOverlay(this, a); return; }
        postAlertNotification(this, a);
    }

    private void vibrate(boolean big) {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v == null) return;
            long[] pat = big ? new long[]{0, 220, 120, 220} : new long[]{0, 120};
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pat, -1)); else v.vibrate(pat, -1);
        } catch (Exception ignored) { }
    }

    static boolean canOverlayNow(Context c) {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(c)) return false;
        PowerManager pm = (PowerManager) c.getSystemService(POWER_SERVICE);
        KeyguardManager km = (KeyguardManager) c.getSystemService(KEYGUARD_SERVICE);
        boolean on = pm == null || pm.isInteractive();
        boolean locked = km != null && km.isKeyguardLocked();
        return on && !locked;
    }

    static void showOverlay(Context c, JSONObject a) {
        dismissOverlay(c);
        WindowManager wm = (WindowManager) c.getSystemService(WINDOW_SERVICE);
        if (wm == null) return;
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        int m = Card.dp(c, 18);
        lp.horizontalMargin = 0;
        final String json = a.toString();
        View card = Card.build(c, a, act -> Actions.handle(c, a.optString("key"), act, json, false));
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(c);
        wrap.setPadding(m, m, m, m);
        wrap.addView(card);
        try { wm.addView(wrap, lp); overlay = wrap; overlayWm = wm; } catch (Exception e) { postAlertNotification(c, a); }
    }

    static void dismissOverlay(Context c) {
        if (overlay != null && overlayWm != null) {
            try { overlayWm.removeView(overlay); } catch (Exception ignored) { }
        }
        overlay = null;
        overlayWm = null;
    }

    static PendingIntent actionIntent(Context c, JSONObject a, String act, int req) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        if (Actions.opensApp(act)) {
            Intent i = new Intent(c, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            i.putExtra("ritmo_key", a.optString("key")).putExtra("ritmo_act", act).putExtra("ritmo_alert", a.toString());
            return PendingIntent.getActivity(c, req, i, flags);
        }
        Intent i = new Intent(c, ActionReceiver.class);
        i.putExtra("key", a.optString("key")).putExtra("act", act).putExtra("alert", a.toString());
        return PendingIntent.getBroadcast(c, req, i, flags);
    }

    static void postAlertNotification(Context c, JSONObject a) {
        channels(c);
        boolean big = a.optBoolean("big");
        int nid = ALERT_NOTIF_BASE + Math.abs(a.optInt("id") % 100000);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, big ? CH_BIG : CH_SOFT)
                .setSmallIcon(R.drawable.ic_stat_ritmo)
                .setColor(0xFF5B4FE0)
                .setContentTitle(a.optString("title"))
                .setContentText(a.optString("body"))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(a.optString("body")))
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(big ? NotificationCompat.PRIORITY_MAX : NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(big ? NotificationCompat.CATEGORY_ALARM : NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(actionIntent(c, a, "open", nid));
        JSONArray btns = a.optJSONArray("buttons");
        if (btns != null) for (int i = 0; i < btns.length() && i < 3; i++) {
            JSONArray x = btns.optJSONArray(i);
            if (x == null || "shutdown".equals(x.optString(0))) continue;
            b.addAction(0, x.optString(1), actionIntent(c, a, x.optString(0), nid * 4 + i + 1));
        }
        if (big) {
            Intent full = new Intent(c, AlertActivity.class);
            full.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            full.putExtra("alert", a.toString());
            b.setFullScreenIntent(PendingIntent.getActivity(c, nid, full, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE), true);
        }
        NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
        try { if (nm != null) nm.notify(nid, b.build()); } catch (SecurityException ignored) { }
    }

    @Override
    public void onDestroy() { handler.removeCallbacks(tick); super.onDestroy(); }
}
