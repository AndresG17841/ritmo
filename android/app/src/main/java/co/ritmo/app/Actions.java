package co.ritmo.app;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import org.json.JSONObject;

/** Qué pasa cuando tocas un botón de un aviso. */
final class Actions {
    /** Acciones que necesitan abrir Ritmo para verse. */
    static boolean opensApp(String act) { return "start".equals(act) || "cant".equals(act) || "open".equals(act); }

    static void handle(Context c, String key, String act, String alertJson, boolean fromActivity) {
        if (act == null) act = "ok";
        AlertDisplay.dismissOverlay(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        JSONObject a = new JSONObject();
        try { if (alertJson != null) a = new JSONObject(alertJson); } catch (Exception ignored) { }
        if (nm != null) nm.cancel(AlertDisplay.ALERT_NOTIF_BASE + Math.abs(a.optInt("id") % 100000));
        if (act.startsWith("snooze")) {
            int m = 10;
            try { m = Integer.parseInt(act.substring(6)); } catch (Exception ignored) { }
            Scheduler.snooze(c, a, m);
            return;
        }
        if ("ok".equals(act)) return;
        if ("shutdown".equals(act)) return;
        try {
            JSONObject p = new JSONObject();
            p.put("key", key == null ? a.optString("key") : key);
            p.put("act", act);
            p.put("alert", a);
            Store.addPending(c, p);
        } catch (Exception ignored) { }
        RitmoPlugin.signal();
        if (opensApp(act) && !fromActivity) {
            Intent i = new Intent(c, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            try { c.startActivity(i); } catch (Exception ignored) { }
        }
    }
}
