package net.exent.flywithme;

import net.exent.flywithme.TakeoffDetails.TakeoffDetailsListener;
import net.exent.flywithme.TakeoffList.TakeoffListListener;
import net.exent.flywithme.TakeoffMap.TakeoffMapListener;
import net.exent.flywithme.bean.Takeoff;
import net.exent.flywithme.data.Database;
import net.exent.flywithme.task.InitDataTask;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;

import java.util.ArrayList;
import java.util.List;

public class FlyWithMe extends FragmentActivity implements TakeoffListListener, TakeoffMapListener, TakeoffDetailsListener {
    private static final int LOCATION_UPDATE_TIME = 300000; // update location every LOCATION_UPDATE_TIME millisecond
    private static final int LOCATION_UPDATE_DISTANCE = 100; // or when we've moved more than LOCATION_UPDATE_DISTANCE meters
    private static final int TAKEOFFS_SORT_DISTANCE = 1000; // only sort takeoff list when we've moved more than TAKEOFFS_SORT_DISTANCE meters
    private static Location lastSortedTakeoffsLocation;
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
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        TakeoffDetails takeoffDetails = (fragment != null && fragment instanceof TakeoffDetails) ? (TakeoffDetails) fragment : new TakeoffDetails();
        /* pass arguments */
        Bundle args = new Bundle();
        args.putParcelable(TakeoffDetails.ARG_TAKEOFF, takeoff);
        takeoffDetails.setArguments(args);
        /* show fragment */
        showFragment(takeoffDetails, "takeoffDetails");
    }

    /**
     * Show NOAA forecast for takeoff in NoaaForecast fragment.
     * @param takeoff The takeoff we wish to display the forecast for.
     */
    public void showNoaaForecast(Takeoff takeoff) {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        NoaaForecast noaaForecast = (fragment != null && fragment instanceof NoaaForecast) ? (NoaaForecast) fragment : new NoaaForecast();
        /* pass arguments */
        Bundle args = new Bundle();
        args.putParcelable(NoaaForecast.ARG_TAKEOFF, takeoff);
        noaaForecast.setArguments(args);
        /* show fragment */
        showFragment(noaaForecast, "noaaForecast");
    }

    public void showTakeoffSchedule(Takeoff takeoff) {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        TakeoffSchedule takeoffSchedule = (fragment != null && fragment instanceof TakeoffSchedule) ? (TakeoffSchedule) fragment : new TakeoffSchedule();
        /* pass arguments */
        Bundle args = new Bundle();
        args.putParcelable(TakeoffSchedule.ARG_TAKEOFF, takeoff);
        takeoffSchedule.setArguments(args);
        /* show fragment */
        showFragment(takeoffSchedule, "takeoffSchedule");
    }

    /**
     * Show TakeoffList fragment.
     */
    public void showTakeoffList() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        TakeoffList takeoffList = (fragment != null && fragment instanceof TakeoffList) ? (TakeoffList) fragment : new TakeoffList();
        /* show fragment */
        showFragment(takeoffList, "takeoffList");
    }

    /**
     * Show TakeoffMap fragment.
     */
    public void showMap() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        TakeoffMap takeoffMap = (fragment != null && fragment instanceof TakeoffMap) ? (TakeoffMap) fragment : new TakeoffMap();
        /* show fragment */
        showFragment(takeoffMap, "map");
    }

    /**
     * Show settings.
     */
    public void showSettings() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
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
                //updateTakeoffList();
            }
        };
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_UPDATE_TIME, LOCATION_UPDATE_DISTANCE, locationListener);
        location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (location == null) {
            // no location set, let's pretend we're at the Rikssenter :)
            location = new Location(LocationManager.PASSIVE_PROVIDER);
            location.setLongitude(61.874655);
            location.setLatitude(9.154848);
        }

        instance = this;

        if (savedInstanceState != null || findViewById(R.id.fragmentContainer) == null)
            return;

        /* start background task */
        Intent scheduleService = new Intent(this, ScheduleService.class);
        scheduleService.setData(Uri.parse("10"));
        startService(scheduleService);

        /* init data */
        new InitDataTask().execute(this);
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
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
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
                showTakeoffDetails(Database.getTakeoff(Integer.parseInt(lastFragment.substring(lastFragment.indexOf(',') + 1))));
            } else if (lastFragment.startsWith("noaaForecast")) {
                showNoaaForecast(Database.getTakeoff(Integer.parseInt(lastFragment.substring(lastFragment.indexOf(',') + 1))));
            } else if (lastFragment.startsWith("takeoffSchedule")) {
                showTakeoffSchedule(Database.getTakeoff(Integer.parseInt(lastFragment.substring(lastFragment.indexOf(',') + 1))));
            }
        }
    }

    private void showFragment(Fragment fragment, String name) {
        backstack.remove(name);
        backstack.add(name);
        getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, fragment, name).commit();
    }

    /*
    private synchronized boolean updateTakeoffList() {
        if (lastSortedTakeoffsLocation == null || location.distanceTo(lastSortedTakeoffsLocation) >= TAKEOFFS_SORT_DISTANCE) {
            // moved too much, need to sort takeoff list again
            Flightlog.sortTakeoffListToLocation(Flightlog.getAllTakeoffs(), location);
            lastSortedTakeoffsLocation = location;
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
            if (fragment == null)
                return true;
            else if (fragment instanceof TakeoffMap)
                showMap();
            else if (fragment instanceof TakeoffList)
                showTakeoffList();
            return true;
        }
        return false;
    }
    */
}
