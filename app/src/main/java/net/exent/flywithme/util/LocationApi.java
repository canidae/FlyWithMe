package net.exent.flywithme.util;

import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

/**
 * Wrapper for FusedLocationApi.
 */
public class LocationApi {
    public interface Callback {
        void locationChanged(Location newLocation, Location previousLocation);
    }

    private static final String LAST_LOCATION_ACCURACY = "last_location_accuracy";
    private static final String LAST_LOCATION_ALTITUDE = "last_location_altitude";
    private static final String LAST_LOCATION_BEARING = "last_location_bearing";
    private static final String LAST_LOCATION_ELAPSED_REALTIME_NANOS = "last_location_elapsed_realtime_nanos";
    private static final String LAST_LOCATION_LATITUDE = "last_location_latitude";
    private static final String LAST_LOCATION_LONGITUDE = "last_location_longitude";
    private static final String LAST_LOCATION_PROVIDER = "last_location_provider";
    private static final String LAST_LOCATION_SPEED = "last_location_speed";
    private static final String LAST_LOCATION_TIME = "last_location_time";

    private Context context;
    private GoogleApiClient googleApiClient;
    private Callback callback;
    private LocationRequest locationRequest;
    private PendingIntent pendingIntent;
    private Location location;

    /**
     * Creates an instance of LocationApi, do note that onStart() must be called for this to start fetching locations.
     *
     * @param context The application context.
     * @param callback Callback executed when location change, may be <code>null</code>.
     * @param locationRequest Settings for location updates, may be <code>null</code> for default settings.
     * @param pendingIntent Set this to use PendingIntent rather than callback, set it to <code>null</code> for using callback.
     */
    public LocationApi(Context context, Callback callback, LocationRequest locationRequest, PendingIntent pendingIntent) {
        this.context = context;
        this.callback = callback;
        this.locationRequest = locationRequest;
        this.pendingIntent = pendingIntent;
        if (this.locationRequest == null) {
            this.locationRequest = LocationRequest.create()
                    .setSmallestDisplacement((float) 100.0)
                    .setInterval(60000)
                    .setFastestInterval(30000)
                    .setPriority(LocationRequest.PRIORITY_LOW_POWER);
        }
        googleApiClient = new GoogleApiClient.Builder(context).addApi(LocationServices.API).addConnectionCallbacks(new LocationClient()).build();
    }

    public static Location getCachedLocation(Context context) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        Location location = new Location(LocationManager.PASSIVE_PROVIDER);
        location.setAccuracy(sharedPref.getFloat(LAST_LOCATION_ACCURACY, location.getAccuracy()));
        location.setAltitude(sharedPref.getFloat(LAST_LOCATION_ALTITUDE, (float) location.getAltitude()));
        location.setBearing(sharedPref.getFloat(LAST_LOCATION_BEARING, location.getBearing()));
        location.setElapsedRealtimeNanos(sharedPref.getLong(LAST_LOCATION_ELAPSED_REALTIME_NANOS, location.getElapsedRealtimeNanos()));
        location.setLatitude(sharedPref.getFloat(LAST_LOCATION_LATITUDE, (float) location.getLatitude()));
        location.setLongitude(sharedPref.getFloat(LAST_LOCATION_LONGITUDE, (float) location.getLongitude()));
        location.setProvider(sharedPref.getString(LAST_LOCATION_PROVIDER, location.getProvider()));
        location.setSpeed(sharedPref.getFloat(LAST_LOCATION_SPEED, location.getSpeed()));
        location.setTime(sharedPref.getLong(LAST_LOCATION_TIME, location.getTime()));
        return location;
    }

    public static void setCachedLocation(Context context, Location location) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        sharedPref.edit()
                .putFloat(LAST_LOCATION_ACCURACY, location.getAccuracy())
                .putFloat(LAST_LOCATION_ACCURACY, location.getAccuracy())
                .putFloat(LAST_LOCATION_ALTITUDE, (float) location.getAltitude())
                .putFloat(LAST_LOCATION_BEARING, location.getBearing())
                .putLong(LAST_LOCATION_ELAPSED_REALTIME_NANOS, location.getElapsedRealtimeNanos())
                .putFloat(LAST_LOCATION_LATITUDE, (float) location.getLatitude())
                .putFloat(LAST_LOCATION_LONGITUDE, (float) location.getLongitude())
                .putString(LAST_LOCATION_PROVIDER, location.getProvider())
                .putFloat(LAST_LOCATION_SPEED, location.getSpeed())
                .putLong(LAST_LOCATION_TIME, location.getTime())
                .apply();
    }

    public Location getLocation() {
        return location == null ? getCachedLocation(context) : location;
    }

    public void onStart() {
        googleApiClient.connect();
    }

    public void onStop() {
        googleApiClient.disconnect();
    }

    private class LocationClient implements GoogleApiClient.ConnectionCallbacks, LocationListener {
        @Override
        public void onConnected(Bundle bundle) {
            if (pendingIntent == null)
                LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
            else
                LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, pendingIntent);
        }

        @Override
        public void onLocationChanged(Location location) {
            if (location == null)
                return;
            Location previousLocation = LocationApi.this.location;
            LocationApi.this.location = location;
            if (callback != null)
                callback.locationChanged(location, previousLocation);
        }

        @Override
        public void onConnectionSuspended(int i) {
        }
    }
}
