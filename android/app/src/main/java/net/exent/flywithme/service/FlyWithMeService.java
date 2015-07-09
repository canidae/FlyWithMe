package net.exent.flywithme.service;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.extensions.android.json.AndroidJsonFactory;
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.googleapis.services.GoogleClientRequestInitializer;

import net.exent.flywithme.FlyWithMe;
import net.exent.flywithme.server.flyWithMeServer.FlyWithMeServer;
import net.exent.flywithme.server.flyWithMeServer.model.Forecast;

import java.io.IOException;

/**
 * Created by canidae on 6/23/15.
 */
public class FlyWithMeService extends IntentService {
    public static final String ACTION_REGISTER_PILOT = "registerPilot";
    public static final String ACTION_GET_METEOGRAM = "getMeteogram";
    public static final String ACTION_GET_SOUNDING = "getSounding";

    public static final String DATA_BOOLEAN_REFRESH_TOKEN = "refreshToken";
    public static final String DATA_LONG_TAKEOFF_ID = "takeoffId";
    public static final String DATA_LONG_TIMESTAMP = "timestamp";

    private static final String TAG = FlyWithMeService.class.getName();
    private static final String PROJECT_ID = "586531582715";

    public FlyWithMeService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        try {
            String action = intent.getAction();
            Bundle bundle = intent.getExtras();
            if (bundle == null)
                bundle = new Bundle();
            if (ACTION_REGISTER_PILOT.equals(action)) {
                boolean refreshToken = bundle.getBoolean(DATA_BOOLEAN_REFRESH_TOKEN, false);
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                String pilotName = prefs.getString(FlyWithMe.PREFERENCE_PILOT_NAME, "<unknown>");
                String pilotPhone = prefs.getString(FlyWithMe.PREFERENCE_PILOT_PHONE, "<unknown>");
                registerPilot(refreshToken, pilotName, pilotPhone);
            } else if (ACTION_GET_METEOGRAM.equals(action)) {
                long takeoffId = bundle.getLong(DATA_LONG_TAKEOFF_ID, -1);
                getMeteogram(takeoffId);
            } else if (ACTION_GET_SOUNDING.equals(action)) {
                long takeoffId = bundle.getLong(DATA_LONG_TAKEOFF_ID, -1);
                long timestamp = bundle.getLong(DATA_LONG_TIMESTAMP, -1);
                getSounding(takeoffId, timestamp);
            } else {
                Log.w(TAG, "Unknown action: " + intent.getAction());
            }
        } catch (IOException e) {
            Log.w(TAG, "Action failed: " + intent.getAction(), e);
        }
    }

    private void registerPilot(boolean refreshToken, String name, String phone) throws IOException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String token = prefs.getString(FlyWithMe.PREFERENCE_TOKEN, null);
        if (refreshToken || token == null)
            token = InstanceID.getInstance(getApplicationContext()).getToken(PROJECT_ID, GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
        getServer().registerPilot(token, name, phone).execute();
    }

    private void getMeteogram(long takeoffId) throws IOException {
        sendDisplayForecastIntent(getServer().getMeteogram(takeoffId).execute());
    }

    private void getSounding(long takeoffId, long timestamp) throws IOException {
        sendDisplayForecastIntent(getServer().getSounding(takeoffId, timestamp).execute());
    }

    private void sendDisplayForecastIntent(Forecast forecast) {
        Intent intent = new Intent(this, FlyWithMe.class);
        intent.setAction(FlyWithMe.ACTION_SHOW_FORECAST);
        intent.putExtra("image", forecast.decodeImage());
        intent.putExtra("lastUpdated", forecast.getLastUpdated());
        intent.putExtra("takeoffId", forecast.getTakeoffId());
        intent.putExtra("type", forecast.getType());
        intent.putExtra("validFor", forecast.getValidFor());
        startActivity(intent);
    }

    private FlyWithMeServer getServer() {
        FlyWithMeServer.Builder builder = new FlyWithMeServer.Builder(AndroidHttp.newCompatibleTransport(), new AndroidJsonFactory(), null);
        // Need setRootUrl and setGoogleClientRequestInitializer only for local testing,
        // otherwise they can be skipped
        builder.setApplicationName("FlyWithMe");
        builder.setRootUrl("http://192.168.43.186:8080/_ah/api/");
        builder.setGoogleClientRequestInitializer(new GoogleClientRequestInitializer() {
            @Override
            public void initialize(AbstractGoogleClientRequest<?> abstractGoogleClientRequest) throws IOException {
                abstractGoogleClientRequest.setDisableGZipContent(true);
            }
        });
        // end of optional local run code

        return builder.build();
    }
}
