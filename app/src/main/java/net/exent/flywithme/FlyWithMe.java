package net.exent.flywithme;

import net.exent.flywithme.layout.NoaaForecast;
import net.exent.flywithme.layout.Preferences;
import net.exent.flywithme.layout.TakeoffDetails.TakeoffDetailsListener;
import net.exent.flywithme.layout.TakeoffList;
import net.exent.flywithme.layout.TakeoffList.TakeoffListListener;
import net.exent.flywithme.layout.TakeoffMap;
import net.exent.flywithme.layout.TakeoffMap.TakeoffMapListener;
import net.exent.flywithme.bean.Takeoff;
import net.exent.flywithme.data.Database;
import net.exent.flywithme.service.FlyWithMeService;
import net.exent.flywithme.service.ScheduleService;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.Fragment;
import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;

/* TODO:
   - Use endpoint API for registering planned flight
   - Use endpoint API for fetching planned flights (schedule)
   - Fix "back"-functionality, see "addToBackStack()" for FragmentTransaction
   - Display notification if user is close to takeoff ("are you flying?")
     - Must be possible to "blacklist" takeoffs, and somehow remove blacklisting later (in preference window?)
   - Notify clients when a takeoff is updated. DONE: TODO: currently disabled, also causes excessive database read operations
   - Cache forecasts locally for some few hours (fetched timestamp is returned, cache for the same amount of time as server caches the forecast)
   - Can we improve fetching location, or at least get rid of all the implemented interfaces?
   - Don't like "FlyWithMe.getInstance()", is it possible to get rid of it?
   - Implement "Poor Man's SPOT"? Livetracking?
 */
public class FlyWithMe extends Activity implements TakeoffListListener, TakeoffMapListener, TakeoffDetailsListener {
    public static final String ACTION_SHOW_FORECAST = "showForecast";

    public static final String SERVER_URL = "http://flywithme-server.appspot.com/fwm";
    //public static final String SERVER_URL = "http://192.168.1.200:8080/fwm";

    private static final int LOCATION_UPDATE_TIME = 60000; // update location every LOCATION_UPDATE_TIME millisecond
    private static final int LOCATION_UPDATE_DISTANCE = 100; // or when we've moved more than LOCATION_UPDATE_DISTANCE meters
    private static Location location = new Location(LocationManager.PASSIVE_PROVIDER);
    private static FlyWithMe instance;

    public static FlyWithMe getInstance() {
        return instance;
    }

    /**
     * Get approximate location of user.
     *
     * @return Approximate location of user.
     */
    public Location getLocation() {
        return new Location(location);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fly_with_me);

        /* setup location listener */
        LocationListener locationListener = new LocationListener() {
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }

            public void onLocationChanged(Location newLocation) {
                /* TODO:
                   some users disable android location services.
                   this cause the map to be centered in the gulf of guinea,
                   and the list of takeoffs is sorted after distance to that location.
                   if location information is not available we should sort the list of takeoffs
                   after the location centered in the map.
                   we should also see if there's another way to find our location,
                   GPS for example apparently can be turned on, but i don't know if the location
                   data is any more accessible then.
                 */
                if (newLocation == null)
                    return;
                location = newLocation;
            }
        };
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_UPDATE_TIME, LOCATION_UPDATE_DISTANCE, locationListener);
        location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (location == null) {
            // no location set, let's pretend we're at the Rikssenter :)
            location = new Location(LocationManager.PASSIVE_PROVIDER);
            location.setLatitude(61.874655);
            location.setLongitude(9.154848);
        }

        /* set instance/context, our fragments are using this a lot */
        instance = this;

        /* setup any preferences that needs to be done programmatically */
        Preferences.setupDefaultPreferences(this);

        /* start background task */
        Intent scheduleService = new Intent(this, ScheduleService.class);
        startService(scheduleService);

        /* register pilot if we haven't done so already */
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (prefs.getString("token", null) == null) {
            Intent intent = new Intent(this, FlyWithMeService.class);
            intent.setAction(FlyWithMeService.ACTION_REGISTER_PILOT);
            startService(intent);
        }

        /* start importing takeoffs from files */
        (new ImportTakeoffTask()).execute();

        /* show preferences if we haven't set pilot name, otherwise takeoff list */
        if (savedInstanceState == null) {
            String pilotName = prefs.getString("pref_pilot_name", null);
            if (pilotName == null || pilotName.trim().equals("")) {
                replaceFragment(new Preferences(), "preferences", false);
            } else {
                replaceFragment(new TakeoffList(), "takeoffList", false);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        /* starting app, setup buttons */
        ImageButton fwmButton = (ImageButton) findViewById(R.id.fwmButton);
        fwmButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                showTakeoffList();
            }
        });
        ImageButton mapButton = (ImageButton) findViewById(R.id.mapButton);
        mapButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                showMap();
            }
        });
        ImageButton settingsButton = (ImageButton) findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                showSettings();
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(getClass().getName(), "onNewIntent(" + intent + ")");
        super.onNewIntent(intent);
        if (ACTION_SHOW_FORECAST.equals(intent.getAction())) {
            NoaaForecast noaaForecast = new NoaaForecast();
            noaaForecast.setArguments(intent.getExtras());
            long takeoffId = intent.getExtras() != null ? intent.getExtras().getLong(NoaaForecast.ARG_TAKEOFF_ID) : -1;
            replaceFragment(noaaForecast, "noaaForecast," + takeoffId, true);
        }
    }

    private void showTakeoffList() {
        String tag = "takeoffList";
        FragmentManager fragmentManager = getFragmentManager();
        Fragment fragment = fragmentManager.findFragmentByTag(tag);
        if (fragment == null)
            fragment = new TakeoffList();
        replaceFragment(fragment, tag, true);
    }

    private void showMap() {
        String tag = "takeoffMap";
        FragmentManager fragmentManager = getFragmentManager();
        Fragment fragment = fragmentManager.findFragmentByTag(tag);
        if (fragment == null)
            fragment = new TakeoffMap();
        replaceFragment(fragment, tag, true);
    }

    private void showSettings() {
        String tag = "preferences";
        FragmentManager fragmentManager = getFragmentManager();
        Fragment fragment = fragmentManager.findFragmentByTag(tag);
        if (fragment == null)
            fragment = new Preferences();
        replaceFragment(fragment, tag, true);
    }

    private void replaceFragment(Fragment fragment, String tag, boolean addToBackStack) {
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragmentContainer, fragment, tag);
        if (addToBackStack)
            fragmentTransaction.addToBackStack(tag);
        fragmentTransaction.commit();
    }

    private void importTakeoffs() {
        Log.d(getClass().getName(), "Importing takeoffs from file");
        DataInputStream inputStream = null;
        try {
            Context context = getApplicationContext();
            Database database = new Database(context);
            inputStream = new DataInputStream(context.getResources().openRawResource(R.raw.flywithme));
            long importTimestamp = inputStream.readLong();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            long previousImportTimestamp = prefs.getLong("pref_last_takeoff_update_timestamp", 0);
            if (importTimestamp <= previousImportTimestamp) {
                Log.d(getClass().getName(), "No need to import, already up to date");
                return; // no need to import, already updated
            }
            prefs.edit().putLong("pref_last_takeoff_update_timestamp", importTimestamp).apply();
            while (true) {
                /* loop breaks once we get an EOFException */
                int takeoffId = inputStream.readShort();
                String name = inputStream.readUTF();
                String description = inputStream.readUTF();
                int asl = inputStream.readShort();
                int height = inputStream.readShort();
                float latitude = inputStream.readFloat();
                float longitude = inputStream.readFloat();
                String windpai = inputStream.readUTF();
                Takeoff takeoff = new Takeoff(takeoffId, importTimestamp, name, description, asl, height, latitude, longitude, windpai, false);
                database.updateTakeoff(takeoff);
            }
        } catch (EOFException e) {
            /* expected to happen when reaching end of file */
        } catch (IOException e) {
            Log.e(getClass().getName(), "Error when reading file with takeoffs", e);
        } finally {
            try {
                if (inputStream != null)
                    inputStream.close();
            } catch (IOException e) {
                Log.w(getClass().getName(), "Unable to close file with takeoffs");
            }
        }
        Log.d(getClass().getName(), "Done importing takeoffs from file");
    }

    private class ImportTakeoffTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            importTakeoffs();
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            TextView statusText = (TextView) findViewById(R.id.fwmStatusText);
            statusText.setText(getString(R.string.importing_takeoffs));
            statusText.setVisibility(View.VISIBLE);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            TextView statusText = (TextView) findViewById(R.id.fwmStatusText);
            statusText.setVisibility(View.GONE);

            // update list/map if user is looking at either
            Fragment fragment = getFragmentManager().findFragmentById(R.id.fragmentContainer);
            if (fragment != null) {
                if (fragment instanceof TakeoffList) {
                    TakeoffList takeoffList = (TakeoffList) fragment;
                    takeoffList.onStart();
                } else if (fragment instanceof TakeoffMap) {
                    TakeoffMap takeoffMap = (TakeoffMap) fragment;
                    takeoffMap.drawMap();
                }
            }
        }
    }

}