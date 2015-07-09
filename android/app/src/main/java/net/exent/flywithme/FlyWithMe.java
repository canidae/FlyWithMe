package net.exent.flywithme;

import net.exent.flywithme.layout.NoaaForecast;
import net.exent.flywithme.layout.Preferences;
import net.exent.flywithme.layout.TakeoffDetails;
import net.exent.flywithme.layout.TakeoffDetails.TakeoffDetailsListener;
import net.exent.flywithme.layout.TakeoffList;
import net.exent.flywithme.layout.TakeoffList.TakeoffListListener;
import net.exent.flywithme.layout.TakeoffMap;
import net.exent.flywithme.layout.TakeoffMap.TakeoffMapListener;
import net.exent.flywithme.bean.Takeoff;
import net.exent.flywithme.data.Database;
import net.exent.flywithme.layout.TakeoffSchedule;
import net.exent.flywithme.service.FlyWithMeService;
import net.exent.flywithme.service.ScheduleService;

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
import java.util.ArrayList;
import java.util.List;

public class FlyWithMe extends Activity implements TakeoffListListener, TakeoffMapListener, TakeoffDetailsListener {
    public static final String ACTION_SHOW_FORECAST = "showForecast";

    public static final String SERVER_URL = "http://flywithme-server.appspot.com/fwm";
    //public static final String SERVER_URL = "http://192.168.1.200:8080/fwm";

    public static final String PREFERENCE_TOKEN = "token";
    public static final String PREFERENCE_PILOT_NAME = "pilotName";
    public static final String PREFERENCE_PILOT_PHONE = "pilotPhone";

    private static final int LOCATION_UPDATE_TIME = 60000; // update location every LOCATION_UPDATE_TIME millisecond
    private static final int LOCATION_UPDATE_DISTANCE = 100; // or when we've moved more than LOCATION_UPDATE_DISTANCE meters
    private static Location location = new Location(LocationManager.PASSIVE_PROVIDER);
    private static FlyWithMe instance;
    private static List<String> backstack = new ArrayList<>();

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
     * Show takeoff details in TakeoffDetails fragment.
     * @param takeoff The takeoff to display details for.
     */
    public void showTakeoffDetails(Takeoff takeoff) {
        Fragment fragment = getFragmentManager().findFragmentById(R.id.fragmentContainer);
        TakeoffDetails takeoffDetails;
        if (fragment != null && fragment instanceof TakeoffDetails) {
            takeoffDetails = (TakeoffDetails) fragment;
        } else {
            takeoffDetails = new TakeoffDetails();
            /* pass arguments */
            Bundle args = new Bundle();
            args.putParcelable(TakeoffDetails.ARG_TAKEOFF, takeoff);
            takeoffDetails.setArguments(args);
        }
        /* show fragment */
        showFragment(takeoffDetails, "takeoffDetails," + takeoff.getId());
    }

    /**
     * Show NOAA forecast for takeoff in NoaaForecast fragment.
     * @param takeoff The takeoff we wish to display the forecast for.
     */
    public void showNoaaForecast(Takeoff takeoff) {
        Fragment fragment = getFragmentManager().findFragmentById(R.id.fragmentContainer);
        NoaaForecast noaaForecast;
        if (fragment != null && fragment instanceof NoaaForecast) {
            noaaForecast = (NoaaForecast) fragment;
        } else {
            noaaForecast = new NoaaForecast();
            /* pass arguments */
            Bundle args = new Bundle();
            args.putParcelable(NoaaForecast.ARG_TAKEOFF, takeoff);
            noaaForecast.setArguments(args);
        }
        /* show fragment */
        showFragment(noaaForecast, "noaaForecast," + takeoff.getId());
    }

    public void showTakeoffSchedule(Takeoff takeoff) {
        Fragment fragment = getFragmentManager().findFragmentById(R.id.fragmentContainer);
        TakeoffSchedule takeoffSchedule;
        if (fragment != null && fragment instanceof TakeoffSchedule) {
            takeoffSchedule = (TakeoffSchedule) fragment;
        } else {
            takeoffSchedule = new TakeoffSchedule();
            /* pass arguments */
            Bundle args = new Bundle();
            args.putParcelable(TakeoffSchedule.ARG_TAKEOFF, takeoff);
            takeoffSchedule.setArguments(args);
        }
        /* show fragment */
        showFragment(takeoffSchedule, "takeoffSchedule," + takeoff.getId());
    }

    /**
     * Show TakeoffList fragment.
     */
    public void showTakeoffList() {
        Fragment fragment = getFragmentManager().findFragmentById(R.id.fragmentContainer);
        TakeoffList takeoffList = (fragment != null && fragment instanceof TakeoffList) ? (TakeoffList) fragment : new TakeoffList();
        /* show fragment */
        showFragment(takeoffList, "takeoffList");
    }

    /**
     * Show TakeoffMap fragment.
     */
    public void showMap() {
        Fragment fragment = getFragmentManager().findFragmentById(R.id.fragmentContainer);
        TakeoffMap takeoffMap = (fragment != null && fragment instanceof TakeoffMap) ? (TakeoffMap) fragment : new TakeoffMap();
        /* show fragment */
        showFragment(takeoffMap, "map");
    }

    /**
     * Show settings.
     */
    public void showSettings() {
        Fragment fragment = getFragmentManager().findFragmentById(R.id.fragmentContainer);
        Preferences preferences = (fragment != null && fragment instanceof Preferences) ? (Preferences) fragment : new Preferences();
        /* show fragment */
        showFragment(preferences, "preferences");
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
        if (prefs.getString(FlyWithMe.PREFERENCE_TOKEN, null) == null) {
            Intent intent = new Intent(this, FlyWithMeService.class);
            intent.setAction(FlyWithMeService.ACTION_REGISTER_PILOT);
            startService(intent);
        }

        /* start importing takeoffs from files */
        (new ImportTakeoffTask()).execute();

        /* show takeoff list */
        if (savedInstanceState == null)
            showTakeoffList();
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
    public void onBackPressed() {
        /* using our own "backstack", because the android one is utterly on crack */
        /* last entry in backstack is currently viewed fragment, remove it */
        if (!backstack.isEmpty())
            backstack.remove(backstack.size() - 1);
        if (backstack.isEmpty()) {
            // no more entries in backstack, return to takeoff list, or exit application if we're looking at the takeoff list
            Fragment fragment = getFragmentManager().findFragmentById(R.id.fragmentContainer);
            if (fragment instanceof TakeoffList) {
                super.onBackPressed();
            } else {
                showTakeoffList();
            }
        } else {
            /* more entries in backstack, show previous entry */
            String lastFragment = backstack.remove(backstack.size() - 1);
            if (lastFragment == null) {
                // this shouldn't happen
                super.onBackPressed();
            } else if (lastFragment.equals("map")) {
                showMap();
            } else if (lastFragment.equals("preferences")) {
                showSettings();
            } else if (lastFragment.equals("takeoffList")) {
                showTakeoffList();
            } else if (lastFragment.startsWith("takeoffDetails")) {
                showTakeoffDetails(new Database(getInstance()).getTakeoff(Integer.parseInt(lastFragment.substring(lastFragment.indexOf(',') + 1))));
            } else if (lastFragment.startsWith("noaaForecast")) {
                showNoaaForecast(new Database(getInstance()).getTakeoff(Integer.parseInt(lastFragment.substring(lastFragment.indexOf(',') + 1))));
            } else if (lastFragment.startsWith("takeoffSchedule")) {
                showTakeoffSchedule(new Database(getInstance()).getTakeoff(Integer.parseInt(lastFragment.substring(lastFragment.indexOf(',') + 1))));
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // TODO: handle forecast and stuff
    }

    private void showFragment(Fragment fragment, String name) {
        backstack.remove(name);
        backstack.add(name);
        getFragmentManager().beginTransaction().replace(R.id.fragmentContainer, fragment, name).commit();
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
            long previousImportTimestamp = prefs.getLong("pref_import_timestamp", 0);
            if (importTimestamp <= previousImportTimestamp) {
                Log.d(getClass().getName(), "No need to import, already up to date");
                return; // no need to import, already updated
            }
            prefs.edit().putLong("pref_import_timestamp", importTimestamp).apply();
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
