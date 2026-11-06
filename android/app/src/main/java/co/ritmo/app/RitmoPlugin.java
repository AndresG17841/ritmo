package co.ritmo.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.app.NotificationManagerCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import org.json.JSONObject;

/** Puente entre la app (JavaScript) y los avisos nativos de Android. */
@CapacitorPlugin(name = "Ritmo")
public class RitmoPlugin extends Plugin {
    private static RitmoPlugin instance;

    @Override
    public void load() { instance = this; }

    static void signal() {
        RitmoPlugin p = instance;
        if (p != null) p.notifyListeners("alertAction", new JSObject(), true);
    }

    @PluginMethod
    public void sync(PluginCall call) {
        Context c = getContext();
        JSArray alerts = call.getArray("alerts");
        JSArray timeline = call.getArray("timeline");
        Scheduler.replaceAll(c, alerts == null ? "[]" : alerts.toString(), timeline == null ? "[]" : timeline.toString());
        AlertDisplay.channels(c);
        android.app.NotificationManager nm = (android.app.NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(7);
        try { c.stopService(new Intent(c, Class.forName("co.ritmo.app.NowService"))); } catch (Exception ignored) { }
        call.resolve();
    }

    @PluginMethod
    public void status(PluginCall call) {
        Context c = getContext();
        JSObject r = new JSObject();
        r.put("overlay", Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c));
        r.put("notifications", NotificationManagerCompat.from(c).areNotificationsEnabled());
        call.resolve(r);
    }

    @PluginMethod
    public void openOverlaySettings(PluginCall call) {
        Context c = getContext();
        Intent i = Build.VERSION.SDK_INT >= 23
                ? new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + c.getPackageName()))
                : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(i); } catch (Exception e) { openApp(c); }
        call.resolve();
    }

    @PluginMethod
    public void openAppSettings(PluginCall call) { openApp(getContext()); call.resolve(); }

    private static void openApp(Context c) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(i); } catch (Exception ignored) { }
    }

    @PluginMethod
    public void takePending(PluginCall call) {
        JSObject r = new JSObject();
        try { r.put("actions", new JSArray(Store.takePending(getContext()))); } catch (Exception e) { r.put("actions", new JSArray()); }
        call.resolve(r);
    }

    @PluginMethod
    public void pushToken(PluginCall call) {
        Context c = getContext();
        JSObject r = new JSObject();
        if (com.google.firebase.FirebaseApp.getApps(c).isEmpty()) { r.put("available", false); call.resolve(r); return; }
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken().addOnCompleteListener(t -> {
                JSObject o = new JSObject();
                o.put("available", true);
                if (t.isSuccessful() && t.getResult() != null) { o.put("token", t.getResult()); Store.put(c, "fcmToken", t.getResult()); }
                else o.put("error", t.getException() == null ? "sin token" : t.getException().getMessage());
                call.resolve(o);
            });
        } catch (Exception e) { r.put("available", false); r.put("error", e.getMessage()); call.resolve(r); }
    }

    @PluginMethod
    public void test(PluginCall call) {
        int delay = call.getInt("seconds", 10);
        try {
            JSONObject a = new JSONObject();
            a.put("id", 1999999999);
            a.put("key", "test");
            a.put("kind", "task");
            a.put("big", true);
            a.put("title", "Toca: tarea de prueba");
            a.put("body", "Así te llegan los avisos de Ritmo.");
            a.put("buttons", new org.json.JSONArray().put(new org.json.JSONArray().put("ok").put("Entendido")).put(new org.json.JSONArray().put("snooze1").put("En 1 min")));
            a.put("at", System.currentTimeMillis() + delay * 1000L);
            Scheduler.schedule(getContext(), a);
        } catch (Exception ignored) { }
        call.resolve();
    }
}
