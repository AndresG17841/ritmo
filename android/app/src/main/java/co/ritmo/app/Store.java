package co.ritmo.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** Guarda en el celular los avisos, la línea del día y las acciones pendientes. */
final class Store {
    private static SharedPreferences p(Context c) { return c.getSharedPreferences("ritmo", Context.MODE_PRIVATE); }

    static String get(Context c, String k, String def) { return p(c).getString(k, def); }
    static void put(Context c, String k, String v) { p(c).edit().putString(k, v).apply(); }

    static JSONArray array(Context c, String k) {
        try { return new JSONArray(get(c, k, "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    static synchronized void addPending(Context c, JSONObject action) {
        JSONArray a = array(c, "pending");
        a.put(action);
        put(c, "pending", a.toString());
    }

    static synchronized String takePending(Context c) {
        String s = get(c, "pending", "[]");
        put(c, "pending", "[]");
        return s;
    }
}
