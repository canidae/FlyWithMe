package net.exent.flywithme.service;

import android.os.Bundle;
import android.util.Log;

/**
 * Created by canidae on 6/23/15.
 */
public class GcmListenerService extends com.google.android.gms.gcm.GcmListenerService {
    private static final String TAG = GcmListenerService.class.getName();

    @Override
    public void onMessageReceived(String from, Bundle data) {
        Log.i(TAG, "Received message from: " + from + ". Data: " + data);
        if (data == null)
            return;
        if (data.containsKey("takeoffUpdated")) {
            // a takeoff was updated or added, retrieve all takeoffs updated after the last updated takeoff stored on device

        }
    }
}
