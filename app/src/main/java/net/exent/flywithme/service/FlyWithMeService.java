package net.exent.flywithme.service;

import android.Manifest;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.extensions.android.json.AndroidJsonFactory;
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.googleapis.services.GoogleClientRequestInitializer;

import net.exent.flywithme.FlyWithMe;
import net.exent.flywithme.R;
import net.exent.flywithme.data.Database;
import net.exent.flywithme.fragment.NoaaForecast;
import net.exent.flywithme.server.flyWithMeServer.FlyWithMeServer;
import net.exent.flywithme.server.flyWithMeServer.model.Forecast;
import net.exent.flywithme.server.flyWithMeServer.model.Schedule;
import net.exent.flywithme.server.flyWithMeServer.model.Takeoff;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * This class handles Google Cloud Messages to and from server, and displays notifications.
 */
public class FlyWithMeService extends IntentService implements GoogleApiClient.ConnectionCallbacks {
    public static final String ACTION_REGISTER_PILOT = "registerPilot";
    public static final String ACTION_SCHEDULE_FLIGHT = "scheduleFlight";
    public static final String ACTION_UNSCHEDULE_FLIGHT = "unscheduleFlight";
    public static final String ACTION_GET_METEOGRAM = "getMeteogram";
    public static final String ACTION_GET_SOUNDING = "getSounding";
    public static final String ACTION_GET_SCHEDULES = "getSchedules";
    public static final String ACTION_GET_UPDATED_TAKEOFFS = "getUpdatedTakeoffs";
    public static final String ACTION_CHECK_CURRENT_LOCATION = "checkCurrentLocation";
    public static final String ACTION_CHECK_ACTIVITY = "checkActivity";
    public static final String ACTION_DISMISS_TAKEOFF_NOTIFICATION = "dismissTakeoffNotification";
    public static final String ACTION_CLICK_TAKEOFF_NOTIFICATION = "clickTakeoffNotification";
    public static final String ACTION_SCHEDULE_TAKEOFF_NOTIFICATION = "scheduleTakeoffNotification";
    public static final String ACTION_BLACKLIST_TAKEOFF_NOTIFICATION = "blacklistTakeoffNotification";
    public static final String ACTION_DISMISS_ACTIVITY_NOTIFICATION = "dismissActivityNotification";
    public static final String ACTION_CLICK_ACTIVITY_NOTIFICATION = "clickActivityNotification";
    public static final String ACTION_SCHEDULE_ACTIVITY_NOTIFICATION = "scheduleActivityNotification";
    public static final String ACTION_BLACKLIST_ACTIVITY_NOTIFICATION = "blacklistActivityNotification";

    public static final String ARG_ACTIVITY = "activity";
    public static final String ARG_REFRESH_TOKEN = "refreshToken";
    public static final String ARG_TAKEOFF_ID = "takeoffId";
    public static final String ARG_TIMESTAMP_IN_SECONDS = "timestamp";

    private static final String TAG = FlyWithMeService.class.getName();
    private static final String PROJECT_ID = "586531582715";
    private static final String SERVER_URL = "https://4-dot-flywithme-server.appspot.com/_ah/api/"; // "http://88.95.84.204:8080/_ah/api/"
    private static final long DISMISS_TIMEOUT = 21600000;
    private static final long[] VIBRATE_DATA = new long[] {0, 100, 100, 100, 100, 300, 100, 100}; // actually morse for "F"

    private static GoogleApiClient googleApiClient;
    private static PendingIntent locationIntent;

    public FlyWithMeService() {
        super(TAG);
    }

    @Override
    public void onCreate() {
        Log.d(getClass().getName(), "onCreate()");
        super.onCreate();
        if (googleApiClient == null) {
            Intent intent = new Intent(this, FlyWithMeService.class);
            intent.setAction(ACTION_CHECK_CURRENT_LOCATION);
            locationIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            googleApiClient = new GoogleApiClient.Builder(this).addApi(LocationServices.API).addConnectionCallbacks(this).build();
            googleApiClient.connect();
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(getClass().getName(), "onConnected(" + bundle + ")");
        LocationRequest locationRequest = LocationRequest.create().setSmallestDisplacement(100).setFastestInterval(300000).setPriority(LocationRequest.PRIORITY_LOW_POWER);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return;
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, locationIntent);
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(getClass().getName(), "onHandleIntent(" + intent + ")");
        String action = intent.getAction();
        Bundle bundle = intent.getExtras();
        if (bundle == null)
            bundle = new Bundle();
        if (ACTION_CHECK_CURRENT_LOCATION.equals(action)) {
            LocationResult locationResult = LocationResult.extractResult(intent);
            if (locationResult == null)
                return;
            Location location = locationResult.getLastLocation();
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            if (!sharedPref.getBoolean("pref_near_takeoff_notifications", true))
                return; // user don't want notifications when near takeoffs
            SharedPreferences dismissedTakeoffsPref = getSharedPreferences(ACTION_DISMISS_TAKEOFF_NOTIFICATION, Context.MODE_PRIVATE);
            SharedPreferences blacklistedTakeoffsPref = getSharedPreferences(ACTION_BLACKLIST_TAKEOFF_NOTIFICATION, Context.MODE_PRIVATE);
            Database database = new Database(this);
            long takeoffMaxDistance = Long.parseLong(sharedPref.getString("pref_near_takeoff_max_distance", "500"));
            boolean vibrate = sharedPref.getBoolean("pref_near_takeoff_vibrate", true);
            boolean cancelNotification = true;
            List<net.exent.flywithme.bean.Takeoff> takeoffs = database.getTakeoffs(location.getLatitude(), location.getLongitude(), 10, false);
            for (net.exent.flywithme.bean.Takeoff takeoff : takeoffs) {
                if (location.distanceTo(takeoff.getLocation()) > takeoffMaxDistance)
                    break; // takeoff too far away (all subsequent takeoffs will be even further away)
                if (dismissedTakeoffsPref.getLong("" + takeoff.getId(), 0) + DISMISS_TIMEOUT > System.currentTimeMillis())
                    continue; // user dismissed this takeoff recently, ignore takeoff
                if (blacklistedTakeoffsPref.contains("" + takeoff.getId()))
                    continue; // user blacklisted this takeoff, ignore takeoff

                PendingIntent clickIntent = PendingIntent.getService(this, 0, new Intent(this, FlyWithMeService.class).setAction(ACTION_CLICK_TAKEOFF_NOTIFICATION).putExtra(ARG_TAKEOFF_ID, takeoff.getId()), PendingIntent.FLAG_UPDATE_CURRENT);
                PendingIntent dismissIntent = PendingIntent.getService(this, 0, new Intent(this, FlyWithMeService.class).setAction(ACTION_DISMISS_TAKEOFF_NOTIFICATION).putExtra(ARG_TAKEOFF_ID, takeoff.getId()), PendingIntent.FLAG_UPDATE_CURRENT);
                PendingIntent scheduleIntent = PendingIntent.getService(this, 0, new Intent(this, FlyWithMeService.class).setAction(ACTION_SCHEDULE_TAKEOFF_NOTIFICATION).putExtra(ARG_TAKEOFF_ID, takeoff.getId()), PendingIntent.FLAG_UPDATE_CURRENT);
                PendingIntent blacklistIntent = PendingIntent.getService(this, 0, new Intent(this, FlyWithMeService.class).setAction(ACTION_BLACKLIST_TAKEOFF_NOTIFICATION).putExtra(ARG_TAKEOFF_ID, takeoff.getId()), PendingIntent.FLAG_UPDATE_CURRENT);
                Notification.Builder notificationBuilder = new Notification.Builder(this)
                        .setSmallIcon(R.drawable.notification_icon)
                        .setContentTitle(takeoff.getName())
                        .setContentText(getString(R.string.are_you_flying))
                        .setContentIntent(clickIntent)
                        .setDeleteIntent(dismissIntent)
                        .setAutoCancel(true)
                        .addAction(android.R.drawable.ic_input_add, getString(R.string.yes), scheduleIntent)
                        .addAction(android.R.drawable.ic_dialog_alert, getString(R.string.never_notify_here), blacklistIntent);
                if (vibrate)
                    notificationBuilder.setVibrate(VIBRATE_DATA);
                Notification notification = notificationBuilder.build();
                notification.flags |= Notification.FLAG_AUTO_CANCEL;
                ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(0, notification);
                cancelNotification = false;
                break;
            }
            if (cancelNotification)
                ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(0); // we're not near any known takeoff, hide notification
        } else if (ACTION_CHECK_ACTIVITY.equals(action)) {
            String message = bundle.getString(ARG_ACTIVITY);
            if (message == null)
                return;
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            sharedPref.edit().putBoolean("pref_schedule_needs_update", true).apply();
            if (!sharedPref.getBoolean("pref_takeoff_activity_notifications", true))
                return; // user don't want notifications on activity
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                return;
            Location location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
            SharedPreferences dismissedActivityPref = getSharedPreferences(ACTION_DISMISS_ACTIVITY_NOTIFICATION, Context.MODE_PRIVATE);
            SharedPreferences blacklistedActivityPref = getSharedPreferences(ACTION_BLACKLIST_ACTIVITY_NOTIFICATION, Context.MODE_PRIVATE);
            long activityMaxDistance = Long.parseLong(sharedPref.getString("pref_takeoff_activity_max_distance", "100000"));
            boolean vibrate = sharedPref.getBoolean("pref_takeoff_activity_vibrate", true);
            String[] timestampsAndTakeoffIdsList = message.split(";");
            Database database = new Database(this);
            DateFormat dateFormat = new SimpleDateFormat("HH:mm", Locale.US);
            boolean breakLoop = false;
            for (String timestampsAndTakeoffIds : timestampsAndTakeoffIdsList) {
                String[] tmp = timestampsAndTakeoffIds.split(":");
                long timestamp = Long.parseLong(tmp[0]) * 1000;
                for (String takeoffIdString : tmp[1].split(",")) {
                    long takeoffId = Long.parseLong(takeoffIdString);
                    if (dismissedActivityPref.getLong("" + takeoffId, 0) + DISMISS_TIMEOUT > System.currentTimeMillis())
                        continue; // user dismissed activity for this takeoff recently, ignore takeoff
                    if (blacklistedActivityPref.contains("" + takeoffId))
                        continue; // user blacklisted activity for this takeoff, ignore takeoff
                    net.exent.flywithme.bean.Takeoff takeoff = database.getTakeoff(takeoffId);
                    if (location.distanceTo(takeoff.getLocation()) > activityMaxDistance)
                        continue;
                    PendingIntent clickIntent = PendingIntent.getService(this, 0, new Intent(this, FlyWithMeService.class).setAction(ACTION_CLICK_ACTIVITY_NOTIFICATION).putExtra(ARG_TAKEOFF_ID, takeoffId).putExtra(ARG_TIMESTAMP_IN_SECONDS, timestamp), PendingIntent.FLAG_UPDATE_CURRENT);
                    PendingIntent dismissIntent = PendingIntent.getService(this, 0, new Intent(this, FlyWithMeService.class).setAction(ACTION_DISMISS_ACTIVITY_NOTIFICATION).putExtra(ARG_TAKEOFF_ID, takeoffId).putExtra(ARG_TIMESTAMP_IN_SECONDS, timestamp), PendingIntent.FLAG_UPDATE_CURRENT);
                    PendingIntent scheduleIntent = PendingIntent.getService(this, 0, new Intent(this, FlyWithMeService.class).setAction(ACTION_SCHEDULE_ACTIVITY_NOTIFICATION).putExtra(ARG_TAKEOFF_ID, takeoffId).putExtra(ARG_TIMESTAMP_IN_SECONDS, timestamp), PendingIntent.FLAG_UPDATE_CURRENT);
                    PendingIntent blacklistIntent = PendingIntent.getService(this, 0, new Intent(this, FlyWithMeService.class).setAction(ACTION_BLACKLIST_ACTIVITY_NOTIFICATION).putExtra(ARG_TAKEOFF_ID, takeoffId).putExtra(ARG_TIMESTAMP_IN_SECONDS, timestamp), PendingIntent.FLAG_UPDATE_CURRENT);
                    /* XXX:
                     * 1. user receives activity for one place
                     * 2. user is not watching phone and ignores the notification
                     * 3. user receives activity for another place, further away than first place
                     * 4. notification for first place i still shown, user dismiss this notification
                     * 5. no notification will be displayed for the second place (until someone schedules something anywhere)
                     * this is probably not a big deal
                     */
                    Notification.Builder notificationBuilder = new Notification.Builder(this)
                            .setSmallIcon(R.drawable.notification_icon)
                            .setContentTitle(dateFormat.format(new Date(timestamp)) + " - " + takeoff.getName())
                            .setContentText(getString(R.string.will_you_join))
                            .setContentIntent(clickIntent)
                            .setDeleteIntent(dismissIntent)
                            .setAutoCancel(true)
                            .addAction(android.R.drawable.ic_input_add, getString(R.string.yes), scheduleIntent)
                            .addAction(android.R.drawable.ic_dialog_alert, getString(R.string.never_notify_here), blacklistIntent);
                    if (vibrate)
                        notificationBuilder.setVibrate(VIBRATE_DATA);
                    Notification notification = notificationBuilder.build();
                    notification.flags |= Notification.FLAG_AUTO_CANCEL;
                    ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(0, notification);
                    breakLoop = true;
                    break;
                }
                if (breakLoop)
                    break;
            }
        } else if (ACTION_CLICK_TAKEOFF_NOTIFICATION.equals(action)) {
            Intent showTakeoffDetailsIntent = new Intent(this, FlyWithMe.class);
            showTakeoffDetailsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            showTakeoffDetailsIntent.setAction(FlyWithMe.ACTION_SHOW_TAKEOFF_DETAILS);
            showTakeoffDetailsIntent.putExtra(FlyWithMe.ARG_TAKEOFF_ID, bundle.getLong(ARG_TAKEOFF_ID));
            startActivity(showTakeoffDetailsIntent);
        } else if (ACTION_DISMISS_TAKEOFF_NOTIFICATION.equals(action)) {
            SharedPreferences prefs = getSharedPreferences(ACTION_DISMISS_TAKEOFF_NOTIFICATION, Context.MODE_PRIVATE);
            prefs.edit().putLong("" + bundle.getLong(ARG_TAKEOFF_ID), System.currentTimeMillis()).apply();
        } else if (ACTION_SCHEDULE_TAKEOFF_NOTIFICATION.equals(action)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String pilotId = prefs.getString("token", null);
            if (pilotId == null) {
                Log.w(TAG, "Can't schedule flight, pilot not registered");
                Intent showTakeoffDetailsIntent = new Intent(this, FlyWithMe.class);
                showTakeoffDetailsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                showTakeoffDetailsIntent.setAction(FlyWithMe.ACTION_SHOW_PREFERENCES);
                startActivity(showTakeoffDetailsIntent);
                return;
            }
            long takeoffId = bundle.getLong(ARG_TAKEOFF_ID);
            try {
                getServer().scheduleFlight(pilotId, takeoffId, System.currentTimeMillis() / 900000 * 900000); // rounds down to previous 15th minute
            } catch (IOException e) {
                Log.w(TAG, "Scheduling flight failed", e);
            }
            // also add takeoff to list of dismissed takeoffs so user won't be bugged again about flying here before another 6 hours has passed
            prefs.edit().putLong("" + takeoffId, System.currentTimeMillis()).apply();
            // dismiss notification
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(0);
        } else if (ACTION_BLACKLIST_TAKEOFF_NOTIFICATION.equals(action)) {
            SharedPreferences prefs = getSharedPreferences(ACTION_BLACKLIST_TAKEOFF_NOTIFICATION, Context.MODE_PRIVATE);
            prefs.edit().putLong("" + bundle.getLong(ARG_TAKEOFF_ID), System.currentTimeMillis()).apply();
            // dismiss notification
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(0);
        } else if (ACTION_CLICK_ACTIVITY_NOTIFICATION.equals(action)) {
            Intent showTakeoffDetailsIntent = new Intent(this, FlyWithMe.class);
            showTakeoffDetailsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            showTakeoffDetailsIntent.setAction(FlyWithMe.ACTION_SHOW_TAKEOFF_DETAILS);
            showTakeoffDetailsIntent.putExtra(FlyWithMe.ARG_TAKEOFF_ID, bundle.getLong(ARG_TAKEOFF_ID));
            startActivity(showTakeoffDetailsIntent);
        } else if (ACTION_DISMISS_ACTIVITY_NOTIFICATION.equals(action)) {
            SharedPreferences prefs = getSharedPreferences(ACTION_DISMISS_ACTIVITY_NOTIFICATION, Context.MODE_PRIVATE);
            prefs.edit().putLong("" + bundle.getLong(ARG_TAKEOFF_ID), System.currentTimeMillis()).apply();
        } else if (ACTION_SCHEDULE_ACTIVITY_NOTIFICATION.equals(action)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String pilotId = prefs.getString("token", null);
            if (pilotId == null) {
                Log.w(TAG, "Can't schedule flight, pilot not registered");
                Intent showTakeoffDetailsIntent = new Intent(this, FlyWithMe.class);
                showTakeoffDetailsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                showTakeoffDetailsIntent.setAction(FlyWithMe.ACTION_SHOW_PREFERENCES);
                startActivity(showTakeoffDetailsIntent);
                return;
            }
            long takeoffId = bundle.getLong(ARG_TAKEOFF_ID);
            long timestamp = bundle.getLong(ARG_TIMESTAMP_IN_SECONDS);
            try {
                getServer().scheduleFlight(pilotId, takeoffId, timestamp).execute();
            } catch (IOException e) {
                Log.w(TAG, "Scheduling flight failed", e);
            }
            // also add takeoff to list of dismissed takeoffs so user won't be bugged again about flying here before another 6 hours has passed
            prefs.edit().putLong("" + takeoffId, System.currentTimeMillis()).apply();
            // dismiss notification
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(0);
        } else if (ACTION_BLACKLIST_ACTIVITY_NOTIFICATION.equals(action)) {
            SharedPreferences prefs = getSharedPreferences(ACTION_BLACKLIST_ACTIVITY_NOTIFICATION, Context.MODE_PRIVATE);
            prefs.edit().putLong("" + bundle.getLong(ARG_TAKEOFF_ID), System.currentTimeMillis()).apply();
            // dismiss notification
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(0);
        } else if (ACTION_REGISTER_PILOT.equals(action)) {
            boolean refreshToken = bundle.getBoolean(ARG_REFRESH_TOKEN, false);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String pilotName = prefs.getString("pref_pilot_name", "<unknown>");
            if (pilotName.trim().equals(""))
                pilotName = "<unknown>";
            String pilotPhone = prefs.getString("pref_pilot_phone", "<unknown>");
            if (pilotPhone.trim().equals(""))
                pilotPhone = "<unknown>";
            registerPilot(refreshToken, pilotName, pilotPhone);
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
                forecasts = getServer().getSounding(takeoffId, timestamp).execute().getItems();
            } catch (IOException e) {
                Log.w(TAG, "Fetching sounding failed", e);
            }
            sendDisplayForecastIntent(takeoffId, forecasts);
        } else if (ACTION_GET_SCHEDULES.equals(action)) {
            getSchedulesAndRefreshView();
        } else if (ACTION_SCHEDULE_FLIGHT.equals(action)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String pilotId = prefs.getString("token", null);
            if (pilotId == null) {
                Log.w(TAG, "Can't schedule flight, pilot not registered");
                return;
            }
            long takeoffId = bundle.getLong(ARG_TAKEOFF_ID, -1);
            long timestamp = bundle.getLong(ARG_TIMESTAMP_IN_SECONDS, -1);
            try {
                Log.i(TAG, "Scheduling: " + takeoffId + " - " + timestamp);
                getServer().scheduleFlight(pilotId, takeoffId, timestamp).execute();
            } catch (IOException e) {
                Log.w(TAG, "Scheduling flight failed", e);
            }
            getSchedulesAndRefreshView();
        } else if (ACTION_UNSCHEDULE_FLIGHT.equals(action)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String pilotId = prefs.getString("token", null);
            if (pilotId == null) {
                Log.w(TAG, "Can't unschedule flight, pilot not registered");
                return;
            }
            long takeoffId = bundle.getLong(ARG_TAKEOFF_ID, -1);
            long timestamp = bundle.getLong(ARG_TIMESTAMP_IN_SECONDS, -1);
            try {
                getServer().unscheduleFlight(pilotId, takeoffId, timestamp).execute();
            } catch (IOException e) {
                Log.w(TAG, "Unscheduling flight failed", e);
            }
        } else if (ACTION_GET_UPDATED_TAKEOFFS.equals(action)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            long timestamp = prefs.getLong("pref_last_takeoff_update_timestamp", 0);
            try {
                List<Takeoff> updatedTakeoffs = getServer().getUpdatedTakeoffs(timestamp).execute().getItems();
                Database database = new Database(this);
                long lastUpdated = timestamp;
                for (Takeoff takeoff : updatedTakeoffs) {
                    Log.i(TAG, "Updating takeoff with ID: " + takeoff.getId());
                    if (takeoff.getLastUpdated() > lastUpdated)
                        lastUpdated = takeoff.getLastUpdated();
                    net.exent.flywithme.bean.Takeoff updatedTakeoff = database.getTakeoff(takeoff.getId());
                    if (updatedTakeoff != null) {
                        updatedTakeoff.setTakeoff(takeoff);
                    } else {
                        updatedTakeoff = new net.exent.flywithme.bean.Takeoff(takeoff);
                    }
                    database.updateTakeoff(updatedTakeoff);
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
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String token = prefs.getString("token", null);
        try {
            if (refreshToken || token == null) {
                token = InstanceID.getInstance(this).getToken(PROJECT_ID, GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
                prefs.edit().putString("token", token).apply();
            }
            getServer().registerPilot(token, name, phone).execute();
        } catch (IOException e) {
            Log.w(TAG, "Registering pilot failed", e);
        }
    }

    private void getSchedulesAndRefreshView() {
        Log.i(TAG, "Fetching schedules and updating view");
        try {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            sharedPref.edit().putBoolean("pref_schedule_needs_update", false).apply();
            List<Schedule> schedules = getServer().getSchedules().execute().getItems();
            Database db = new Database(getApplicationContext());
            db.updateSchedules(schedules);
        } catch (IOException e) {
            Log.w(TAG, "Fetching schedules failed", e);
        }
        Intent showTakeoffDetailsIntent = new Intent(this, FlyWithMe.class);
        showTakeoffDetailsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        showTakeoffDetailsIntent.setAction(FlyWithMe.ACTION_UPDATE_SCHEDULE_DATA);
        startActivity(showTakeoffDetailsIntent);
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
