package co.ritmo.app;

import android.content.Intent;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(RitmoPlugin.class);
        super.onCreate(savedInstanceState);
        handleAction(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleAction(intent);
    }

    /** Si se abrió desde un botón de un aviso, guarda la acción para que la app la aplique. */
    private void handleAction(Intent i) {
        if (i == null || i.getStringExtra("ritmo_act") == null) return;
        String act = i.getStringExtra("ritmo_act");
        Actions.handle(this, i.getStringExtra("ritmo_key"), act, i.getStringExtra("ritmo_alert"), true);
        i.removeExtra("ritmo_act");
    }
}
