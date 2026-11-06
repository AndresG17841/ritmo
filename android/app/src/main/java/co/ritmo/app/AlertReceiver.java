package co.ritmo.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Llega la hora de un aviso: lo muestra la tarjeta de Ritmo. */
public class AlertReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String alert = intent.getStringExtra("alert");
        if (alert == null) return;
        NowService.start(c, NowService.ACTION_ALERT, alert);
    }
}
