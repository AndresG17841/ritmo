package co.ritmo.app;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import androidx.core.app.NotificationCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Iterator;

/**
 * Muestra un aviso: ventana encima de todo si estás usando el celular,
 * pantalla completa si está bloqueado, o notificación normal si es suave.
 * Llega por la alarma local o por push; si llega dos veces, se muestra una sola.
 */
final class AlertDisplay {
    static final int ALERT_NOTIF_BASE = 1000;
    static final String CH_BIG = "ritmo_avisos", CH_SOFT = "ritmo_suaves";
    private static View overlay;
    private static WindowManager overlayWm;

    static void show(Context ctx, JSONObject a) {
        final Context c = ctx.getApplicationContext();
        if (alreadyShown(c, a)) return;
        // Quita la tarjeta fija de la versión anterior, si quedó.
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(7);
        boolean big = a.optBoolean("big");
        vibrate(c, big);
        if (big && canOverlayNow(c)) {
            new Handler(Looper.getMainLooper()).post(() -> showOverlay(c, a));
            return;
        }
        postNotification(c, a);
    }

    /** Evita mostrar el mismo aviso dos veces (alarma local + push). */
    private static synchronized boolean alreadyShown(Context c, JSONObject a) {
        String id = a.optString("key") + "|" + (a.optLong("at") / 60000);
        if ("test".equals(a.optString("key"))) return false;
        try {
            JSONObject shown = new JSONObject(Store.get(c, "shown", "{}"));
            if (shown.has(id)) return true;
            long now = System.currentTimeMillis();
            JSONObject keep = new JSONObject();
            for (Iterator<String> it = shown.keys(); it.hasNext(); ) {
                String k = it.next();
                if (now - shown.optLong(k) < 3 * 86400000L) keep.put(k, shown.optLong(k));
            }
            keep.put(id, now);
            Store.put(c, "shown", keep.toString());
        } catch (Exception ignored) { }
        return false;
    }

    static void channels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.deleteNotificationChannel("ritmo_ahora");
        if (nm.getNotificationChannel(CH_BIG) != null) return;
        NotificationChannel big = new NotificationChannel(CH_BIG, "Avisos importantes", NotificationManager.IMPORTANCE_HIGH);
        big.setDescription("Empezar tareas, salir, pantallas fuera");
        big.enableVibration(true);
        big.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationChannel soft = new NotificationChannel(CH_SOFT, "Avisos suaves", NotificationManager.IMPORTANCE_DEFAULT);
        soft.setDescription("Agua, descanso, tiempo libre");
        nm.createNotificationChannel(big);
        nm.createNotificationChannel(soft);
    }

    private static void vibrate(Context c, boolean big) {
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            long[] pat = big ? new long[]{0, 220, 120, 220} : new long[]{0, 120};
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pat, -1)); else v.vibrate(pat, -1);
        } catch (Exception ignored) { }
    }

    static boolean canOverlayNow(Context c) {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(c)) return false;
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        KeyguardManager km = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
        boolean on = pm == null || pm.isInteractive();
        boolean locked = km != null && km.isKeyguardLocked();
        return on && !locked;
    }

    static void showOverlay(Context c, JSONObject a) {
        dismissOverlay(c);
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) { postNotification(c, a); return; }
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        final String json = a.toString();
        View card = Card.build(c, a, act -> Actions.handle(c, a.optString("key"), act, json, false));
        FrameLayout wrap = new FrameLayout(c);
        int m = Card.dp(c, 18);
        wrap.setPadding(m, m, m, m);
        wrap.addView(card);
        try { wm.addView(wrap, lp); overlay = wrap; overlayWm = wm; } catch (Exception e) { postNotification(c, a); }
    }

    static void dismissOverlay(Context c) {
        final View v = overlay;
        final WindowManager wm = overlayWm;
        overlay = null;
        overlayWm = null;
        if (v != null && wm != null) new Handler(Looper.getMainLooper()).post(() -> { try { wm.removeView(v); } catch (Exception ignored) { } });
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

    static void postNotification(Context c, JSONObject a) {
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
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        try { if (nm != null) nm.notify(nid, b.build()); } catch (SecurityException ignored) { }
    }
}
