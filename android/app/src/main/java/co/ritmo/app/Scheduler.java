package co.ritmo.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;

/** Programa los avisos con el reloj del sistema: suenan aunque la app esté cerrada. */
final class Scheduler {
    static final String ACTION_ALERT = "co.ritmo.app.ALERT";

    private static PendingIntent pi(Context c, JSONObject a) {
        Intent i = new Intent(c, AlertReceiver.class);
        i.setAction(ACTION_ALERT);
        i.putExtra("alert", a.toString());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(c, a.optInt("id"), i, flags);
    }

    static void schedule(Context c, JSONObject a) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long at = a.optLong("at");
        PendingIntent p = pi(c, a);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (Build.VERSION.SDK_INT >= 23) {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, at, p);
        }
    }

    static void cancel(Context c, JSONObject a) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pi(c, a));
    }

    /** Quita de los avisos guardados el que ya se atendió en otro dispositivo. */
    static synchronized void cancelKey(Context c, String key) {
        if (key == null) return;
        JSONArray old = Store.array(c, "alerts"), keep = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject a = old.optJSONObject(i); if (a == null) continue;
            if (key.equals(a.optString("key"))) cancel(c, a); else keep.put(a);
        }
        Store.put(c, "alerts", keep.toString());
    }

    /** Reemplaza todos los avisos por la lista nueva. */
    static synchronized void replaceAll(Context c, String alertsJson, String timelineJson) {
        JSONArray old = Store.array(c, "alerts");
        for (int i = 0; i < old.length(); i++) { JSONObject a = old.optJSONObject(i); if (a != null) cancel(c, a); }
        Store.put(c, "alerts", alertsJson);
        Store.put(c, "timeline", timelineJson);
        reschedule(c);
    }

    /** Vuelve a programar lo guardado (al prender el celular o al actualizar la app). */
    static void reschedule(Context c) {
        JSONArray arr = Store.array(c, "alerts");
        long now = System.currentTimeMillis();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject a = arr.optJSONObject(i);
            if (a != null && a.optLong("at") > now) schedule(c, a);
        }
    }

    static void snooze(Context c, JSONObject a, int minutes) {
        try {
            JSONObject b = new JSONObject(a.toString());
            long at = System.currentTimeMillis() + minutes * 60000L;
            b.put("at", at);
            b.put("snz", true);
            b.put("id", (int) ((a.optInt("id") * 31L + at / 1000) % 2000000000L));
            schedule(c, b);
        } catch (Exception ignored) { }
    }
}
