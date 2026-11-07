package co.ritmo.app;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import org.json.JSONObject;

/** Avisos que llegan por push (Firebase), aunque la app esté cerrada. */
public class RitmoMessaging extends FirebaseMessagingService {
    @Override
    public void onMessageReceived(RemoteMessage m) {
        String alert = m.getData().get("alert");
        if (alert == null) return;
        try {
            JSONObject a = new JSONObject(alert);
            if (a.has("cancel")) { AlertDisplay.cancel(this, a.optInt("id"), a.optString("cancel")); return; }
            AlertDisplay.show(this, a);
        } catch (Exception ignored) { }
    }

    @Override
    public void onNewToken(String token) {
        Store.put(this, "fcmToken", token);
        RitmoPlugin.signal();
    }
}
