package co.ritmo.app;

import android.content.Context;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

/** Avisa a la nube que ya atendiste un aviso, para que el PC y el servidor no lo repitan. */
final class Cloud {
    static final String URL_ = "https://xjrawzqxffjbpvhfvhce.supabase.co/rest/v1/rpc/ritmo_ack";
    static final String KEY = "sb_publishable_6OV-s2vIdQ0PCJYtAXxfkA_C2MT0ZMW";

    static void ack(Context c, String key, long until) {
        final String token = Store.get(c, "fcmToken", "");
        if (key == null || key.isEmpty() || token.isEmpty()) return;
        new Thread(() -> {
            for (int tries = 0; tries < 3; tries++) {
                try {
                    JSONObject b = new JSONObject();
                    b.put("p_token", token); b.put("p_key", key); b.put("p_until", until);
                    HttpURLConnection h = (HttpURLConnection) new URL(URL_).openConnection();
                    h.setRequestMethod("POST"); h.setDoOutput(true); h.setConnectTimeout(8000); h.setReadTimeout(8000);
                    h.setRequestProperty("Content-Type", "application/json");
                    h.setRequestProperty("apikey", KEY);
                    try (OutputStream o = h.getOutputStream()) { o.write(b.toString().getBytes("UTF-8")); }
                    int code = h.getResponseCode(); h.disconnect();
                    if (code < 300) return;
                } catch (Exception ignored) { }
                try { Thread.sleep(4000); } catch (InterruptedException e) { return; }
            }
        }).start();
    }
}
