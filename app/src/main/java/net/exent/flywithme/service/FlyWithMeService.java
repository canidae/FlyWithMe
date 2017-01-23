package net.exent.flywithme.service;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.extensions.android.json.AndroidJsonFactory;
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.googleapis.services.GoogleClientRequestInitializer;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;

import net.exent.flywithme.FlyWithMe;
import net.exent.flywithme.data.Database;
import net.exent.flywithme.fragment.NoaaForecast;
import net.exent.flywithme.server.flyWithMeServer.FlyWithMeServer;
import net.exent.flywithme.server.flyWithMeServer.model.Forecast;
import net.exent.flywithme.server.flyWithMeServer.model.ForecastCollection;
import net.exent.flywithme.server.flyWithMeServer.model.Takeoff;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class handles Google Cloud Messages to and from server, and displays notifications.
 */
public class FlyWithMeService extends IntentService {
    public static final String ACTION_INIT = "init";
    public static final String ACTION_REGISTER_PILOT = "registerPilot";
    public static final String ACTION_GET_METEOGRAM = "getMeteogram";
    public static final String ACTION_GET_SOUNDING = "getSounding";
    public static final String ACTION_GET_UPDATED_TAKEOFFS = "getUpdatedTakeoffs";
    public static final String ACTION_CHECK_CURRENT_LOCATION = "checkCurrentLocation";

    public static final String ARG_REFRESH_TOKEN = "refreshToken";
    public static final String ARG_TAKEOFF_ID = "takeoffId";
    public static final String ARG_TIMESTAMP_IN_SECONDS = "timestamp";

    private static final String TAG = FlyWithMeService.class.getName();
    private static final String PROJECT_ID = "586531582715";
    private static final String SERVER_URL = "https://4-dot-flywithme-server.appspot.com/_ah/api/"; // "http://88.95.84.204:8080/_ah/api/"
    private static final long CHECK_LOCATION_INTERVAL = 600000; // 10 minutes

    private GoogleApiClient googleApiClient;

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
        if (ACTION_INIT.equals(action)) {
            initGoogleApiClient();
        } else if (ACTION_REGISTER_PILOT.equals(action)) {
            boolean refreshToken = bundle.getBoolean(ARG_REFRESH_TOKEN, false);
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            String pilotName = sharedPref.getString("pref_pilot_name", "<unknown>").trim();
            if (pilotName.equals(""))
                pilotName = "<unknown>";
            String pilotPhone = sharedPref.getString("pref_pilot_phone", "<unknown>").trim();
            if (pilotPhone.equals(""))
                pilotPhone = "<unknown>";

            String pilotId = sharedPref.getString("pilot_id", null);
            try {
                if (refreshToken || pilotId == null) {
                    pilotId = InstanceID.getInstance(this).getToken(PROJECT_ID, GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
                    sharedPref.edit().putString("pilot_id", pilotId).apply();
                }
                getServer().registerPilot(pilotId, pilotName, pilotPhone).execute();
            } catch (IOException e) {
                Log.w(TAG, "Registering pilot failed", e);
            }
        } else if (ACTION_GET_METEOGRAM.equals(action)) {
            long takeoffId = bundle.getLong(ARG_TAKEOFF_ID, -1);
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
            long takeoffId = bundle.getLong(ARG_TAKEOFF_ID, -1);
            long timestamp = bundle.getLong(ARG_TIMESTAMP_IN_SECONDS, -1);
            List<Forecast> forecasts = null;
            try {
                ForecastCollection forecastCollection = getServer().getSounding(takeoffId, timestamp).execute();
                if (forecastCollection != null)
                    forecasts = forecastCollection.getItems();
            } catch (IOException e) {
                Log.w(TAG, "Fetching sounding failed", e);
            }
            sendDisplayForecastIntent(takeoffId, forecasts);
        } else if (ACTION_GET_UPDATED_TAKEOFFS.equals(action)) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            long timestamp = sharedPref.getLong("takeoff_last_update_timestamp", 0);
            try {
                List<Takeoff> updatedTakeoffs = getServer().getUpdatedTakeoffs(timestamp).execute().getItems();
                long lastUpdated = timestamp;
                for (Takeoff takeoff : updatedTakeoffs) {
                    Log.i(TAG, "Updating takeoff with ID: " + takeoff.getId());
                    if (takeoff.getLastUpdated() > lastUpdated)
                        lastUpdated = takeoff.getLastUpdated();
                    net.exent.flywithme.bean.Takeoff updatedTakeoff = Database.getTakeoff(getApplicationContext(), takeoff.getId());
                    if (updatedTakeoff != null) {
                        updatedTakeoff.setTakeoff(takeoff);
                    } else {
                        updatedTakeoff = new net.exent.flywithme.bean.Takeoff(takeoff);
                    }
                    Database.updateTakeoff(getApplicationContext(), updatedTakeoff);
                }
                sharedPref.edit().putLong("takeoff_last_update_timestamp", lastUpdated).apply();
            } catch (IOException e) {
                Log.w(TAG, "Fetching updated takeoffs failed", e);
            }
        } else {
            Log.w(TAG, "Unknown action: " + intent.getAction());
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

    private void initGoogleApiClient() {
        if (googleApiClient != null && googleApiClient.isConnected())
            return;
        if (!checkLocationAccessPermission())
            return;
        googleApiClient = new GoogleApiClient.Builder(this).addApi(LocationServices.API).build();
        googleApiClient.blockingConnect();
        Intent locationIntent = new Intent(this, FlyWithMeService.class);
        locationIntent.setAction(ACTION_CHECK_CURRENT_LOCATION);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, locationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        LocationRequest locationRequest = LocationRequest.create()
                .setSmallestDisplacement((float) 100.0)
                .setInterval(CHECK_LOCATION_INTERVAL * 2)
                .setFastestInterval(CHECK_LOCATION_INTERVAL)
                .setPriority(LocationRequest.PRIORITY_LOW_POWER);
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, pendingIntent);
    }


    private boolean checkLocationAccessPermission() {
        return ActivityCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private FlyWithMeServer getServer() {
        FlyWithMeServer.Builder builder = new FlyWithMeServer.Builder(AndroidHttp.newCompatibleTransport(), new AndroidJsonFactory(), new HttpRequestInitializer() {
            @Override
            public void initialize(HttpRequest request) throws IOException {
                request.setConnectTimeout(30000);
                request.setReadTimeout(120000);
            }
        });
        builder.setApplicationName("FlyWithMe");
        builder.setRootUrl(SERVER_URL);
        builder.setGoogleClientRequestInitializer(new GoogleClientRequestInitializer() {
            @Override
            public void initialize(AbstractGoogleClientRequest<?> abstractGoogleClientRequest) throws IOException {
                abstractGoogleClientRequest.setDisableGZipContent(true);
            }
        });

        return builder.build();
    }
}
