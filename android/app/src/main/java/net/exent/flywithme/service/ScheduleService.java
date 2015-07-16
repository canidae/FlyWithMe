package net.exent.flywithme.service;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import net.exent.flywithme.FlyWithMe;
import net.exent.flywithme.R;
import net.exent.flywithme.bean.Takeoff;
import net.exent.flywithme.data.Database;
import net.exent.flywithme.server.flyWithMeServer.model.Pilot;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by canidae on 3/10/14.
 */
public class ScheduleService extends IntentService implements ConnectionCallbacks, OnConnectionFailedListener, LocationListener {
    private static final int NOTIFICATION_ID = 42;
    private static final long MS_IN_DAY = 86400000;

    private GoogleApiClient locationClient;
    private long updateInterval;

    public ScheduleService() {
        super("ScheduleService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(getClass().getName(), "onHandleIntent(" + intent + ")");
        final Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
        String messageType = gcm.getMessageType(intent);
        if (messageType == null) {
            // not a GCM message
            locationClient = new GoogleApiClient.Builder(this).addApi(LocationServices.API).addConnectionCallbacks(this).build();
            locationClient.connect();
            // loop to prevent the thread from exiting
            while (locationClient != null) {
                // "locationClient != null" is essentially always "true", but just "true" makes Android Studio complain about endless loop (which is intended)
                SystemClock.sleep(MS_IN_DAY);
            }
        } else {
            if (extras != null && !extras.isEmpty()) {  // has effect of unparcelling Bundle
                // Since we're not using two way messaging, this is all we really to check for
                if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
                    Logger.getLogger("GCM_RECEIVED").log(Level.INFO, extras.toString());

                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), extras.getString("message"), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
            //FlyWithMeBroadcastReceiver.completeWakefulIntent(intent);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    public static void updateSchedule(Context context, Location location) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int fetchTakeoffs = Integer.parseInt(prefs.getString("pref_schedule_fetch_takeoffs", "-1"));
        List<Takeoff> takeoffs = new Database(context).getTakeoffs(location.getLatitude(), location.getLongitude(), fetchTakeoffs, true);
        try {
            Log.i(ScheduleService.class.getName(), "Fetching schedule from server");
            HttpURLConnection con = (HttpURLConnection) new URL(FlyWithMe.SERVER_URL).openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            DataOutputStream outputStream = new DataOutputStream(con.getOutputStream());
            outputStream.writeByte(0);
            outputStream.writeShort(takeoffs.size());
            for (Takeoff takeoff : takeoffs)
                outputStream.writeShort((int) takeoff.getId());
            outputStream.close();
            int responseCode = con.getResponseCode();
            Log.d(ScheduleService.class.getName(), "Response code: " + responseCode);
            parseScheduleResponse(context, new DataInputStream(con.getInputStream()));
        } catch (IOException e) {
            Log.w(ScheduleService.class.getName(), "Fetching flight schedule failed unexpectedly", e);
        }
    }

    public static void scheduleFlight(Context context, int takeoffId, long timestamp) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            long pilotId = prefs.getLong("pref_schedule_pilot_id", 0);
            while (pilotId == 0) {
                // generate a random ID for identifying the pilot's registrations
                pilotId = (new Random()).nextLong();
                prefs.edit().putLong("pref_schedule_pilot_id", pilotId).commit();
            }
            String pilotName = prefs.getString("pref_schedule_pilot_name", "").trim();
            String pilotPhone = prefs.getString("pref_schedule_pilot_phone", "").trim();
            HttpURLConnection con = (HttpURLConnection) new URL(FlyWithMe.SERVER_URL).openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            DataOutputStream outputStream = new DataOutputStream(con.getOutputStream());
            outputStream.writeByte(1);
            outputStream.writeShort(takeoffId);
            outputStream.writeInt((int) (timestamp / 1000)); // timestamp is sent as seconds since epoch, not ms
            outputStream.writeLong(pilotId);
            outputStream.writeUTF(pilotName);
            outputStream.writeUTF(pilotPhone);
            outputStream.close();
            int responseCode = con.getResponseCode();
            Log.d(ScheduleService.class.getName(), "Response code: " + responseCode);
        } catch (IOException e) {
            Log.w(ScheduleService.class.getName(), "Scheduling flight failed unexpectedly", e);
        }
    }

    public static void unscheduleFlight(Context context, int takeoffId, long timestamp) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            long pilotId = prefs.getLong("pref_schedule_pilot_id", 0);
            HttpURLConnection con = (HttpURLConnection) new URL(FlyWithMe.SERVER_URL).openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            DataOutputStream outputStream = new DataOutputStream(con.getOutputStream());
            outputStream.writeByte(2);
            outputStream.writeShort(takeoffId);
            outputStream.writeInt((int) (timestamp / 1000)); // timestamp is sent as seconds since epoch, not ms
            outputStream.writeLong(pilotId);
            outputStream.close();
            int responseCode = con.getResponseCode();
            Log.d(ScheduleService.class.getName(), "Response code: " + responseCode);
        } catch (IOException e) {
            Log.w(ScheduleService.class.getName(), "Unscheduling flight failed unexpectedly", e);
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(getClass().getName(), "onConnected(" + bundle + ")");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        updateInterval = Integer.parseInt(prefs.getString("pref_schedule_update_interval", "3600")) * 1000;
        LocationRequest locationRequest = LocationRequest.create().setInterval(updateInterval).setFastestInterval(300000).setPriority(LocationRequest.PRIORITY_NO_POWER);
        LocationServices.FusedLocationApi.requestLocationUpdates(locationClient, locationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(getClass().getName(), "onConnectionSuspended(" + i + ")");
    }

//    @Override
//    public void onDisconnected() {
//        Log.d(getClass().getName(), "onDisconnected()");
        // restart service on disconnect
        //locationClient = new LocationClient(this, this, this);
        //locationClient.connect();

        /* TODO: in case the code above doesn't work (which someone claim: http://stackoverflow.com/questions/19373972/locationclient-auto-reconnect-at-ondisconnect)
        final ScheduleService scheduleService = this;
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                locationClient = new LocationClient(scheduleService, scheduleService, scheduleService);
                locationClient.connect();
            }
        });
        */
//    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.w(getClass().getName(), "onConnectionFailed(" + connectionResult + ")");
    }

    @Override
    public void onLocationChanged(Location location) {
        long now = System.currentTimeMillis();
        long localHour = (now + TimeZone.getDefault().getOffset(now)) % MS_IN_DAY;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int fetchTakeoffs = Integer.parseInt(prefs.getString("pref_schedule_fetch_takeoffs", "-1"));
        int startTime = Integer.parseInt(prefs.getString("pref_schedule_start_fetch_time", "28800")) * 1000;
        int stopTime = Integer.parseInt(prefs.getString("pref_schedule_stop_fetch_time", "72000")) * 1000;

        int tmpUpdateInterval = Integer.parseInt(prefs.getString("pref_schedule_update_interval", "3600")) * 1000;
        if (tmpUpdateInterval != updateInterval) {
            // a new interval has been set, update locationRequest
            updateInterval = tmpUpdateInterval;
            LocationRequest locationRequest = LocationRequest.create().setInterval(updateInterval).setFastestInterval(300000).setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
            LocationServices.FusedLocationApi.requestLocationUpdates(locationClient, locationRequest, this);
        }

        // these two values tells us whether we're between start time and stop time
        // if startTime == stopTime then we always want to update (setting maxValue to 24 hours)
        long timeValue = (localHour - startTime + MS_IN_DAY) % MS_IN_DAY;
        long maxValue = startTime == stopTime ? MS_IN_DAY : (stopTime - startTime + MS_IN_DAY) % MS_IN_DAY;

        long lastUpdate = prefs.getLong("pref_schedule_last_update", 0);
        if (fetchTakeoffs == -1 || now < lastUpdate + updateInterval || timeValue > maxValue) {
            // no update at this time
            return;
        }
        prefs.edit().putLong("pref_schedule_last_update", now).commit();

        // update schedule in background
        (new UpdateScheduleTask(getApplicationContext(), location)).execute();
    }

    private static void parseScheduleResponse(Context context, DataInputStream inputStream) {
        try {
            while (true) {
                int takeoffId = inputStream.readUnsignedShort();
                if (takeoffId == 0)
                    break; // no more data to be read
                int timestamps = inputStream.readUnsignedShort();
                Map<Long, List<Pilot>> schedule = new HashMap<>();
                for (int b = 0; b < timestamps; ++b) {
                    long timestamp = (long) inputStream.readInt() * 1000L; // timestamp is received as seconds since epoch, not ms
                    List<Pilot> pilotList = new ArrayList<>();
                    int pilots = inputStream.readUnsignedShort();
                    for (int c = 0; c < pilots; ++c) {
                        String pilotName = inputStream.readUTF();
                        String pilotPhone = inputStream.readUTF();
                        Pilot pilot = new Pilot();
                        pilot.setName(pilotName);
                        pilot.setPhone(pilotPhone);
                        pilotList.add(pilot);
                    }
                    schedule.put(timestamp, pilotList);
                }
                new Database(context).updateTakeoffSchedule(takeoffId, schedule);
            }
        } catch (IOException e) {
            Log.w(ScheduleService.class.getName(), "Fetching flight schedule failed unexpectedly", e);
        }
    }

    private static class UpdateScheduleTask extends AsyncTask<Void, Void, Void> {
        private Context context;
        private Location location;

        public UpdateScheduleTask(Context context, Location location) {
            this.context = context;
            this.location = location;
        }

        @Override
        protected Void doInBackground(Void... params) {
            updateSchedule(context, location);
            return null;
        }

        @Override
        protected void onPostExecute(Void param) {
            // show/update notification
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (!prefs.getBoolean("pref_schedule_notification", true))
                return;
            String pilotName = prefs.getString("pref_schedule_pilot_name", "").trim();
            List<String> takeoffsWithUpcomingFlights = new Database(context.getApplicationContext()).getTakeoffsWithUpcomingFlights(pilotName);
            String notificationText = "";
            for (String takeoff : takeoffsWithUpcomingFlights)
                notificationText += "".equals(notificationText) ? takeoff : " | " + takeoff;
            if (notificationText.equals(prefs.getString("pref_schedule_last_notification_text", "")))
                return; // no changes in upcoming activity
            prefs.edit().putString("pref_schedule_last_notification_text", notificationText).commit();
            if ("".equals(notificationText))
                return; // no upcoming activity
            // notify the user that people are planning to fly
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
            Notification.Builder notificationBuilder = new Notification.Builder(context);
            notificationBuilder.setSmallIcon(R.mipmap.ic_launcher);
            notificationBuilder.setContentTitle(context.getString(R.string.get_your_wing));
            notificationBuilder.setAutoCancel(true);
            PendingIntent notificationIntent = PendingIntent.getActivity(context, 0, new Intent(context, FlyWithMe.class), PendingIntent.FLAG_UPDATE_CURRENT);
            notificationBuilder.setContentIntent(notificationIntent);
            notificationBuilder.setContentText(notificationText);
            notificationBuilder.setWhen(System.currentTimeMillis());
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
        }
    }
}
