package net.exent.flywithme.service;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * Created by canidae on 6/23/15.
 */
public class GcmListenerService extends com.google.android.gms.gcm.GcmListenerService {
    private static final String TAG = GcmListenerService.class.getName();

    @Override
    public void onMessageReceived(String from, Bundle data) {
        Log.d(TAG, "Received message from: " + from + ". Data: " + data);
        if (data == null)
            return;
        if (data.containsKey("takeoffUpdated")) {
            // a takeoff was updated or added, retrieve all takeoffs updated after the last updated takeoff stored on device
            Intent intent = new Intent(this, FlyWithMeService.class);
            intent.setAction(FlyWithMeService.ACTION_GET_UPDATED_TAKEOFFS);
            startService(intent);
        } else if (data.containsKey("activity")) {
            // there's activity somewhere
            Intent intent = new Intent(this, FlyWithMeService.class);
            intent.setAction(FlyWithMeService.ACTION_CHECK_ACTIVITY);
            intent.putExtra(FlyWithMeService.ARG_ACTIVITY, data.getString("activity"));
            startService(intent);
        }
    }
}
