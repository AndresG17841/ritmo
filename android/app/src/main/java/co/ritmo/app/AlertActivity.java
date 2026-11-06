package co.ritmo.app;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import org.json.JSONObject;

/** Aviso a pantalla completa, como una alarma, cuando el celular está bloqueado. */
public class AlertActivity extends Activity {
    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String json = getIntent().getStringExtra("alert");
        JSONObject a;
        try { a = new JSONObject(json == null ? "{}" : json); } catch (Exception e) { finish(); return; }
        final JSONObject alert = a;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xE6131129);
        int m = Card.dp(this, 20);
        root.setPadding(m, m, m, m);
        View card = Card.build(this, a, act -> {
            if (Actions.opensApp(act) && Build.VERSION.SDK_INT >= 26) {
                KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                if (km != null) km.requestDismissKeyguard(this, null);
            }
            Actions.handle(this, alert.optString("key"), act, alert.toString(), false);
            finish();
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(card, lp);
        setContentView(root);
    }
}
