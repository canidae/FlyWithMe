package net.exent.flywithme.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by canidae on 6/6/14.
 */
public class FlyWithMeBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(getClass().getName(), "onReceive(" + context + ", " + intent + ")");
        // start schedule fetching service
        Intent scheduleService = new Intent(context, ScheduleService.class);
        context.startService(scheduleService);
    }
}
