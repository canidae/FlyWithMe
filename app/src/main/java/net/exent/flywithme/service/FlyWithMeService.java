package net.exent.flywithme.service;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
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
public class FlyWithMeService extends IntentService {
    public static final String ACTION_INIT = "init";
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
    private static final long DISMISS_TIMEOUT = 21600000; // 6 hours
    private static final long CHECK_ACTIVITY_INTERVAL = 3600000; // 1 hour
    private static final long CHECK_LOCATION_INTERVAL = 900000; // 15 minutes
    private static final long[] VIBRATE_DATA = new long[] {0, 100, 100, 100, 100, 300, 100, 100}; // actually morse for "F"

    private static GoogleApiClient googleApiClient; // TODO? android goes absolutely mental if this is not static

    public FlyWithMeService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(getClass().getName(), "onHandleIntent(" + intent + ")");
        String action = intent.getAction();
        Bundle bundle = intent.getExtras();
        long now = System.currentTimeMillis();
        if (bundle == null)
            bundle = new Bundle();
        if (ACTION_INIT.equals(action)) {
            if (googleApiClient != null && (googleApiClient.isConnected() || googleApiClient.isConnecting()))
                return;
            googleApiClient = new GoogleApiClient.Builder(this).addApi(LocationServices.API).build();
            googleApiClient.blockingConnect();
            PendingIntent locationIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            LocationRequest locationRequest = LocationRequest.create()
                    .setSmallestDisplacement((float) 100.0)
                    .setInterval(CHECK_LOCATION_INTERVAL * 2)
                    .setFastestInterval(CHECK_LOCATION_INTERVAL)
                    .setPriority(LocationRequest.PRIORITY_LOW_POWER);
            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, locationIntent);
        } else if (ACTION_CHECK_CURRENT_LOCATION.equals(action)) {
            LocationResult locationResult = LocationResult.extractResult(intent);
            if (locationResult == null)
                return;
            Location location = locationResult.getLastLocation();
            checkCurrentLocation(location);
        } else if (ACTION_CHECK_ACTIVITY.equals(action)) {
            String message = bundle.getString(ARG_ACTIVITY);
            checkActivity(message);
        } else if (ACTION_CLICK_TAKEOFF_NOTIFICATION.equals(action)) {
            Intent showTakeoffDetailsIntent = new Intent(this, FlyWithMe.class);
            showTakeoffDetailsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            showTakeoffDetailsIntent.setAction(FlyWithMe.ACTION_SHOW_TAKEOFF_DETAILS);
            showTakeoffDetailsIntent.putExtra(FlyWithMe.ARG_TAKEOFF_ID, bundle.getLong(ARG_TAKEOFF_ID));
            startActivity(showTakeoffDetailsIntent);
        } else if (ACTION_DISMISS_TAKEOFF_NOTIFICATION.equals(action)) {
            SharedPreferences prefs = getSharedPreferences(ACTION_DISMISS_TAKEOFF_NOTIFICATION, Context.MODE_PRIVATE);
            prefs.edit().putLong("" + bundle.getLong(ARG_TAKEOFF_ID), now).apply();
        } else if (ACTION_SCHEDULE_TAKEOFF_NOTIFICATION.equals(action)) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            String pilotId = sharedPref.getString("pilot_id", null);
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
                getServer().scheduleFlight(pilotId, takeoffId, now / 900000 * 900000); // rounds down to previous 15th minute
            } catch (IOException e) {
                Log.w(TAG, "Scheduling flight failed", e);
            }
            // also add takeoff to list of dismissed takeoffs so user won't be bugged again about flying here before another 6 hours has passed
            sharedPref.edit().putLong("" + takeoffId, now).apply();
            // dismiss notification
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(0);
        } else if (ACTION_BLACKLIST_TAKEOFF_NOTIFICATION.equals(action)) {
            SharedPreferences prefs = getSharedPreferences(ACTION_BLACKLIST_TAKEOFF_NOTIFICATION, Context.MODE_PRIVATE);
            prefs.edit().putLong("" + bundle.getLong(ARG_TAKEOFF_ID), now).apply();
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
            prefs.edit().putLong("" + bundle.getLong(ARG_TAKEOFF_ID), now).apply();
        } else if (ACTION_SCHEDULE_ACTIVITY_NOTIFICATION.equals(action)) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            String pilotId = sharedPref.getString("pilot_id", null);
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
            sharedPref.edit().putLong("" + takeoffId, now).apply();
            // dismiss notification
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(0);
        } else if (ACTION_BLACKLIST_ACTIVITY_NOTIFICATION.equals(action)) {
            SharedPreferences prefs = getSharedPreferences(ACTION_BLACKLIST_ACTIVITY_NOTIFICATION, Context.MODE_PRIVATE);
            prefs.edit().putLong("" + bundle.getLong(ARG_TAKEOFF_ID), now).apply();
            // dismiss notification
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(0);
        } else if (ACTION_REGISTER_PILOT.equals(action)) {
            boolean refreshToken = bundle.getBoolean(ARG_REFRESH_TOKEN, false);
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            String pilotName = sharedPref.getString("pref_pilot_name", "<unknown>");
            if (pilotName.trim().equals(""))
                pilotName = "<unknown>";
            String pilotPhone = sharedPref.getString("pref_pilot_phone", "<unknown>");
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
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            String pilotId = sharedPref.getString("pilot_id", null);
            if (pilotId == null) {
                Log.w(TAG, "Can't schedule flight, pilot not registered");
                return;
            }
            long takeoffId = bundle.getLong(ARG_TAKEOFF_ID, -1);
            long timestamp = bundle.getLong(ARG_TIMESTAMP_IN_SECONDS, -1);
            try {
                getServer().scheduleFlight(pilotId, takeoffId, timestamp).execute();
            } catch (IOException e) {
                Log.w(TAG, "Scheduling flight failed", e);
            }
            getSchedulesAndRefreshView();
        } else if (ACTION_UNSCHEDULE_FLIGHT.equals(action)) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            String pilotId = sharedPref.getString("pilot_id", null);
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
            getSchedulesAndRefreshView();
        } else if (ACTION_GET_UPDATED_TAKEOFFS.equals(action)) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            long timestamp = sharedPref.getLong("takeoff_last_update_timestamp", 0);
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
                sharedPref.edit().putLong("takeoff_last_update_timestamp", lastUpdated).apply();
            } catch (IOException e) {
                Log.w(TAG, "Fetching updated takeoffs failed", e);
            }
        } else {
            Log.w(TAG, "Unknown action: " + intent.getAction());
        }
    }

    private void checkCurrentLocation(Location location) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        long now = System.currentTimeMillis();
        if (now - sharedPref.getLong("activity_last_check", 0) > CHECK_ACTIVITY_INTERVAL) {
            // periodically check activity in case of no new schedules and we're closing up on an upcoming schedule
            sharedPref.edit().putLong("activity_last_check", now).apply();
            checkActivity(null);
        }
        if (!sharedPref.getBoolean("pref_near_takeoff_notifications", true))
            return; // user don't want notifications when near takeoffs
        SharedPreferences dismissedTakeoffsPref = getSharedPreferences(ACTION_DISMISS_TAKEOFF_NOTIFICATION, Context.MODE_PRIVATE);
        SharedPreferences blacklistedTakeoffsPref = getSharedPreferences(ACTION_BLACKLIST_TAKEOFF_NOTIFICATION, Context.MODE_PRIVATE);
        Database database = new Database(this);
        long takeoffMaxDistance = Long.parseLong(sharedPref.getString("pref_near_takeoff_max_distance", "500"));
        List<net.exent.flywithme.bean.Takeoff> takeoffs = database.getTakeoffs(location.getLatitude(), location.getLongitude(), 1, false, false);
        if (takeoffs.isEmpty())
            return;
        net.exent.flywithme.bean.Takeoff takeoff = takeoffs.get(0);
        if (location.distanceTo(takeoff.getLocation()) > takeoffMaxDistance) {
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(0); // we're not near any known takeoff, hide notification
            return; // takeoff too far away (all subsequent takeoffs will be even further away)
        }
        if (dismissedTakeoffsPref.getLong("" + takeoff.getId(), 0) + DISMISS_TIMEOUT > now)
            return; // user dismissed this takeoff recently, ignore takeoff
        if (blacklistedTakeoffsPref.contains("" + takeoff.getId()))
            return; // user blacklisted this takeoff, ignore takeoff
        String pilotId = sharedPref.getString("pilot_id", null);
        if (database.isPilotScheduledToday(pilotId, takeoff.getId()))
            return; // don't show notifications for takeoff where pilot is already scheduled

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
        if (sharedPref.getBoolean("pref_near_takeoff_vibrate", true) && !database.isPilotScheduledToday(pilotId, null))
            notificationBuilder.setVibrate(VIBRATE_DATA);
        Notification notification = notificationBuilder.build();
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(0, notification);
    }

    private void checkActivity(String message) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (message == null) {
            message = sharedPref.getString("activity_last_message", null);
            if (message == null)
                return;
        } else {
            sharedPref.edit()
                    .putBoolean("activity_schedule_needs_update", true)
                    .putString("activity_last_message", message)
                    .apply();
        }
        if (!sharedPref.getBoolean("pref_takeoff_activity_notifications", true))
            return; // user don't want notifications on activity
        Location location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
        SharedPreferences dismissedActivityPref = getSharedPreferences(ACTION_DISMISS_ACTIVITY_NOTIFICATION, Context.MODE_PRIVATE);
        SharedPreferences blacklistedActivityPref = getSharedPreferences(ACTION_BLACKLIST_ACTIVITY_NOTIFICATION, Context.MODE_PRIVATE);
        long activityMaxDistance = Long.parseLong(sharedPref.getString("pref_takeoff_activity_max_distance", "100000"));
        String[] timestampsAndTakeoffIdsList = message.split(";");
        Database database = new Database(this);
        DateFormat dateFormat = new SimpleDateFormat("EEE. HH:mm", Locale.US);
        boolean breakLoop = false;
        for (String timestampsAndTakeoffIds : timestampsAndTakeoffIdsList) {
            String[] tmp = timestampsAndTakeoffIds.split(":");
            long timestamp = Long.parseLong(tmp[0]) * 1000;
            long now = System.currentTimeMillis();
            if (timestamp > now + 79200000) // 22 hours
                continue; // activity is too far into the future, don't show notification (yet)
            else if (timestamp < now - 7200000) // 2 hours
                continue; // activity is too long ago, don't show notification
            for (String takeoffIdString : tmp[1].split(",")) {
                long takeoffId = Long.parseLong(takeoffIdString);
                if (dismissedActivityPref.getLong("" + takeoffId, 0) + DISMISS_TIMEOUT > now)
                    continue; // user dismissed activity for this takeoff recently, ignore takeoff
                if (blacklistedActivityPref.contains("" + takeoffId))
                    continue; // user blacklisted activity for this takeoff, ignore takeoff
                net.exent.flywithme.bean.Takeoff takeoff = database.getTakeoff(takeoffId);
                if (location.distanceTo(takeoff.getLocation()) > activityMaxDistance)
                    continue;
                String pilotId = sharedPref.getString("pilot_id", null);
                if (database.isPilotScheduledToday(pilotId, takeoffId))
                    continue; // don't show notifications for takeoff where pilot is already scheduled
                PendingIntent clickIntent = PendingIntent.getService(this, 0, new Intent(this, FlyWithMeService.class).setAction(ACTION_CLICK_ACTIVITY_NOTIFICATION).putExtra(ARG_TAKEOFF_ID, takeoffId).putExtra(ARG_TIMESTAMP_IN_SECONDS, timestamp), PendingIntent.FLAG_UPDATE_CURRENT);
                PendingIntent dismissIntent = PendingIntent.getService(this, 0, new Intent(this, FlyWithMeService.class).setAction(ACTION_DISMISS_ACTIVITY_NOTIFICATION).putExtra(ARG_TAKEOFF_ID, takeoffId).putExtra(ARG_TIMESTAMP_IN_SECONDS, timestamp), PendingIntent.FLAG_UPDATE_CURRENT);
                PendingIntent scheduleIntent = PendingIntent.getService(this, 0, new Intent(this, FlyWithMeService.class).setAction(ACTION_SCHEDULE_ACTIVITY_NOTIFICATION).putExtra(ARG_TAKEOFF_ID, takeoffId).putExtra(ARG_TIMESTAMP_IN_SECONDS, timestamp), PendingIntent.FLAG_UPDATE_CURRENT);
                PendingIntent blacklistIntent = PendingIntent.getService(this, 0, new Intent(this, FlyWithMeService.class).setAction(ACTION_BLACKLIST_ACTIVITY_NOTIFICATION).putExtra(ARG_TAKEOFF_ID, takeoffId).putExtra(ARG_TIMESTAMP_IN_SECONDS, timestamp), PendingIntent.FLAG_UPDATE_CURRENT);
                    /* XXX:
                     * 1. user receives activity for one place
                     * 2. user is not watching phone and ignores the notification
                     * 3. user receives activity for another place, further away than first place
                     * 4. notification for first place i still shown, user dismiss this notification
                     * 5. no notification will be displayed for the second place (until someone schedules something somewhere or the periodic activity check executes)
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
                if (sharedPref.getBoolean("pref_takeoff_activity_vibrate", false) && !database.isPilotScheduledToday(pilotId, null))
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
    }

    private void registerPilot(boolean refreshToken, String name, String phone) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String pilotId = sharedPref.getString("pilot_id", null);
        try {
            if (refreshToken || pilotId == null) {
                pilotId = InstanceID.getInstance(this).getToken(PROJECT_ID, GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
                sharedPref.edit().putString("pilot_id", pilotId).apply();
            }
            getServer().registerPilot(pilotId, name, phone).execute();
        } catch (IOException e) {
            Log.w(TAG, "Registering pilot failed", e);
        }
    }

    private void getSchedulesAndRefreshView() {
        try {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            sharedPref.edit().putBoolean("activity_schedule_needs_update", false).apply();
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
