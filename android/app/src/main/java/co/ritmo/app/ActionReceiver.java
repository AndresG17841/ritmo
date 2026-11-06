package co.ritmo.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Botones de las notificaciones que no necesitan abrir la app. */
public class ActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Actions.handle(c, i.getStringExtra("key"), i.getStringExtra("act"), i.getStringExtra("alert"), false);
    }
}
