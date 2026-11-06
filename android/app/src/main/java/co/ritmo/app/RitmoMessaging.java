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
        try { AlertDisplay.show(this, new JSONObject(alert)); } catch (Exception ignored) { }
    }

    @Override
    public void onNewToken(String token) {
        Store.put(this, "fcmToken", token);
        RitmoPlugin.signal();
    }
}
