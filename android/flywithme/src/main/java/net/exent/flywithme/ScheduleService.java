package net.exent.flywithme;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import net.exent.flywithme.bean.Takeoff;
import net.exent.flywithme.data.Database;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Created by canidae on 3/10/14.
 */
public class ScheduleService extends IntentService {
    private static final int NOTIFICATION_ID = 42;
    private static final long MS_IN_DAY = 86400000;
    private long lastUpdate = 0;

    public ScheduleService() {
        super("ScheduleService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(getClass().getName(), "onHandleIntent(" + intent.toString() + ")");

        // setup notification builder
        List<String> notificationTakeoffs = new ArrayList<>();
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this);
        notificationBuilder.setSmallIcon(R.drawable.ic_launcher);
        notificationBuilder.setContentTitle(getString(R.string.get_your_wing));
        notificationBuilder.setAutoCancel(true);
        PendingIntent notificationIntent = PendingIntent.getActivity(this, 0, new Intent(this, FlyWithMe.class), PendingIntent.FLAG_UPDATE_CURRENT);
        notificationBuilder.setContentIntent(notificationIntent);

        while (true) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            int fetchTakeoffs = Integer.parseInt(prefs.getString("pref_schedule_fetch_takeoffs", "-1"));
            int startTime = Integer.parseInt(prefs.getString("pref_schedule_start_fetch_time", "28800")) * 1000;
            int stopTime = Integer.parseInt(prefs.getString("pref_schedule_stop_fetch_time", "72000")) * 1000;
            boolean fetchFavourites = prefs.getBoolean("pref_schedule_fetch_favourites", true);
            long updateInterval = Integer.parseInt(prefs.getString("pref_schedule_update_interval", "3600")) * 1000;

            long now = System.currentTimeMillis();
            long localHour = (now + TimeZone.getDefault().getOffset(now)) % MS_IN_DAY;

            // these two values tells us whether we're between start time and stop time
            // if startTime == stopTime then we always want to update (setting maxValue to 24 hours)
            long timeValue = (localHour - startTime + MS_IN_DAY) % MS_IN_DAY;
            long maxValue = startTime == stopTime ? MS_IN_DAY : (stopTime - startTime + MS_IN_DAY) % MS_IN_DAY;

            if (fetchTakeoffs == -1 || now < lastUpdate + updateInterval || timeValue > maxValue) {
                // no update at this time, wait a minute and check again
                SystemClock.sleep(60000);
                continue;
            }
            lastUpdate = now;

            Location location = FlyWithMe.getInstance().getLocation();
            List<Takeoff> favourites = Database.getTakeoffs(location.getLatitude(), location.getLongitude(), fetchTakeoffs, fetchFavourites);
            try {
                Log.i(getClass().getName(), "Fetching schedule from server");
                HttpURLConnection con = (HttpURLConnection) new URL("http://flywithme-server.appspot.com/fwm").openConnection();
                con.setRequestMethod("POST");
                con.setDoOutput(true);
                DataOutputStream outputStream = new DataOutputStream(con.getOutputStream());
                outputStream.writeByte(0);
                outputStream.writeShort(favourites.size());
                for (Takeoff favourite : favourites)
                    outputStream.writeShort(favourite.getId());
                outputStream.close();
                int responseCode = con.getResponseCode();
                Log.d(getClass().getName(), "Response code: " + responseCode);
                DataInputStream inputStream = new DataInputStream(con.getInputStream());

                int takeoffs = inputStream.readUnsignedShort();
                Log.d(getClass().getName(), "Takeoffs: " + takeoffs);
                for (int a = 0; a < takeoffs; ++a) {
                    int takeoffId = inputStream.readUnsignedShort();
                    Log.d(getClass().getName(), "Takeoff ID: " + takeoffId);
                    int timestamps = inputStream.readUnsignedShort();
                    Log.d(getClass().getName(), "Timestamps: " + timestamps);
                    Map<Date, List<String>> schedule = new HashMap<>();
                    for (int b = 0; b < timestamps; ++b) {
                        long timestamp = inputStream.readLong();
                        Date date = new Date(timestamp * 1000);
                        List<String> pilotList = new ArrayList<>();
                        Log.d(getClass().getName(), "Timestamp: " + timestamp);
                        int pilots = inputStream.readUnsignedShort();
                        Log.d(getClass().getName(), "Pilots: " + pilots);
                        for (int c = 0; c < pilots; ++c) {
                            String pilot = inputStream.readUTF();
                            pilotList.add(pilot);
                            Log.d(getClass().getName(), "Pilot: " + pilot);
                        }
                        schedule.put(date, pilotList);
                        Database.updateTakeoffSchedule(takeoffId, schedule);
                    }
                }
                List<String> takeoffsWithScheduledFlightsToday = Database.getTakeoffsWithScheduledFlightsToday(prefs.getString("pref_schedule_pilot_name", ""));
                if (!notificationTakeoffs.containsAll(takeoffsWithScheduledFlightsToday)) {
                    // notify the user that people are planning to fly today
                    notificationTakeoffs = takeoffsWithScheduledFlightsToday;
                    String notificationText = "";
                    for (String takeoff : notificationTakeoffs)
                        notificationText += "".equals(notificationText) ? takeoff : " | " + takeoff;
                    notificationBuilder.setContentText(notificationText);
                    notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
                }
            } catch (IOException e) {
                Log.w(getClass().getName(), "Fetching flight schedule failed unexpectedly", e);
            }
        }
    }
}
