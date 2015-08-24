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
import net.exent.flywithme.data.Database;
import net.exent.flywithme.layout.NoaaForecast;
import net.exent.flywithme.server.flyWithMeServer.FlyWithMeServer;
import net.exent.flywithme.server.flyWithMeServer.model.Forecast;
import net.exent.flywithme.server.flyWithMeServer.model.Takeoff;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by canidae on 6/23/15.
 */
public class FlyWithMeService extends IntentService {
    public static final String ACTION_REGISTER_PILOT = "registerPilot";
    public static final String ACTION_GET_METEOGRAM = "getMeteogram";
    public static final String ACTION_GET_SOUNDING = "getSounding";
    public static final String ACTION_GET_UPDATED_TAKEOFFS = "getUpdatedTakeoffs";

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
        Log.d(getClass().getName(), "onHandleIntent(" + intent + ")");
        String action = intent.getAction();
        Bundle bundle = intent.getExtras();
        if (bundle == null)
            bundle = new Bundle();
        if (ACTION_REGISTER_PILOT.equals(action)) {
            boolean refreshToken = bundle.getBoolean(DATA_BOOLEAN_REFRESH_TOKEN, false);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            String pilotName = prefs.getString("pref_pilot_name", "<unknown>");
            if (pilotName.trim().equals(""))
                pilotName = "<unknown>";
            String pilotPhone = prefs.getString("pref_pilot_phone", "<unknown>");
            if (pilotPhone.trim().equals(""))
                pilotPhone = "<unknown>";
            registerPilot(refreshToken, pilotName, pilotPhone);
        } else if (ACTION_GET_METEOGRAM.equals(action)) {
            long takeoffId = bundle.getLong(DATA_LONG_TAKEOFF_ID, -1);
            Forecast forecast = null;
            try {
                forecast = getServer().getMeteogram(takeoffId).execute();
            } catch (IOException e) {
                Log.w(TAG, "Fetching meteogram failed", e);
            }
            List<Forecast> forecasts = new ArrayList<>();
            forecasts.add(forecast);
            sendDisplayForecastIntent(takeoffId, forecasts);
        } else if (ACTION_GET_SOUNDING.equals(action)) {
            long takeoffId = bundle.getLong(DATA_LONG_TAKEOFF_ID, -1);
            long timestamp = bundle.getLong(DATA_LONG_TIMESTAMP, -1);
            List<Forecast> forecasts = null;
            try {
                forecasts = getServer().getSounding(takeoffId, timestamp).execute().getItems();
            } catch (IOException e) {
                Log.w(TAG, "Fetching sounding failed", e);
            }
            sendDisplayForecastIntent(takeoffId, forecasts);
        } else if (ACTION_GET_UPDATED_TAKEOFFS.equals(action)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            long timestamp = prefs.getLong("pref_last_takeoff_update_timestamp", 0);
            try {
                List<Takeoff> updatedTakeoffs = getServer().getUpdatedTakeoffs(timestamp).execute().getItems();
                Database database = new Database(getApplicationContext());
                long lastUpdated = timestamp;
                for (Takeoff takeoff : updatedTakeoffs) {
                    if (takeoff.getLastUpdated() > lastUpdated)
                        lastUpdated = takeoff.getLastUpdated();
                    net.exent.flywithme.bean.Takeoff updatedTakeoff = database.getTakeoff(takeoff.getId());
                    if (updatedTakeoff != null) {
                        updatedTakeoff.setTakeoff(takeoff);
                    } else {
                        updatedTakeoff = new net.exent.flywithme.bean.Takeoff(takeoff);
                    }
                    database.updateTakeoff(updatedTakeoff);
                    Log.i(TAG, "Updated takeoff: " + updatedTakeoff);
                }
                prefs.edit().putLong("pref_last_takeoff_update_timestamp", lastUpdated).apply();
            } catch (IOException e) {
                Log.w(TAG, "Fetching updated takeoffs failed", e);
            }
        } else {
            Log.w(TAG, "Unknown action: " + intent.getAction());
        }
    }

    private void registerPilot(boolean refreshToken, String name, String phone) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String token = prefs.getString("token", null);
        try {
            if (refreshToken || token == null) {
                token = InstanceID.getInstance(getApplicationContext()).getToken(PROJECT_ID, GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
                prefs.edit().putString("token", token).apply();
            }
            getServer().registerPilot(token, name, phone).execute();
        } catch (IOException e) {
            Log.w(TAG, "Registering pilot failed", e);
        }
    }

    private void sendDisplayForecastIntent(long takeoffId, List<Forecast> forecasts) {
        Log.d(getClass().getName(), "sendDisplayForecastIntent(" + forecasts + ")");
        Intent intent = new Intent(this, FlyWithMe.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(FlyWithMe.ACTION_SHOW_FORECAST);
        if (forecasts == null)
            forecasts = new ArrayList<>();
        if (forecasts.isEmpty() || forecasts.get(0) == null || forecasts.get(0).getImage() == null) {
            forecasts.clear();
            Forecast forecast = new Forecast();
            forecast.setTakeoffId(takeoffId);
            forecast.setType("ERROR");
            forecasts.add(forecast);
        }
        /* AAH!
         * Models in client library generated from endpoint are not serializable, we can't just pass the object.
         */
        for (int i = 0; i < forecasts.size(); ++i) {
            Forecast forecast = forecasts.get(i);
            intent.putExtra(NoaaForecast.ARG_IMAGE + "_" + i, forecast.decodeImage());
            intent.putExtra(NoaaForecast.ARG_TYPE + "_" + i, forecast.getType());
        }
        intent.putExtra(NoaaForecast.ARG_LAST_UPDATED, forecasts.get(0).getLastUpdated());
        intent.putExtra(NoaaForecast.ARG_TAKEOFF_ID, forecasts.get(0).getTakeoffId());
        intent.putExtra(NoaaForecast.ARG_VALID_FOR, forecasts.get(0).getValidFor());
        startActivity(intent);
    }

    private FlyWithMeServer getServer() {
        FlyWithMeServer.Builder builder = new FlyWithMeServer.Builder(AndroidHttp.newCompatibleTransport(), new AndroidJsonFactory(), null);
        // Need setRootUrl and setGoogleClientRequestInitializer only for local testing,
        // otherwise they can be skipped
        builder.setApplicationName("FlyWithMe");
        //builder.setRootUrl("http://88.95.84.204:8080/_ah/api/");
        builder.setRootUrl("https://40-dot-flywithme-server.appspot.com/_ah/api/");
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
