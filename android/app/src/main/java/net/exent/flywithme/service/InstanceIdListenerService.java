package net.exent.flywithme.service;

import android.content.Intent;
import android.util.Log;

/**
 * Created by canidae on 6/23/15.
 */
public class InstanceIdListenerService extends com.google.android.gms.iid.InstanceIDListenerService {
    private static final String TAG = InstanceIdListenerService.class.getName();

    @Override
    public void onTokenRefresh() {
        // our token is no longer valid, tell FlyWithMeService to create a new one
        Log.d(TAG, "Token is no longer valid, updating");
        Intent intent = new Intent(this, FlyWithMeService.class);
        intent.setAction(FlyWithMeService.ACTION_REGISTER_PILOT);
        intent.putExtra(FlyWithMeService.DATA_BOOLEAN_REFRESH_TOKEN, true);
        startService(intent);
    }
}
