package net.exent.flywithme.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Automatically start FlyWithMeService upon boot.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(getClass().getName(), "onReceive(" + context + ", " + intent + ")");
        Intent flyWithMeService = new Intent(context, FlyWithMeService.class);
        flyWithMeService.setAction(FlyWithMeService.ACTION_INIT);
        context.startService(flyWithMeService);
    }
}
