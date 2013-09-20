package net.exent.flywithme;

import java.util.ArrayList;

import net.exent.flywithme.TakeoffDetails.TakeoffDetailsListener;
import net.exent.flywithme.TakeoffList.TakeoffListListener;
import net.exent.flywithme.TakeoffMap.TakeoffMapListener;
import net.exent.flywithme.bean.Takeoff;
import net.exent.flywithme.data.Airspace;
import net.exent.flywithme.data.Flightlog;
import net.exent.flywithme.task.InitDataTask;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;

public class FlyWithMe extends FragmentActivity implements TakeoffListListener, TakeoffMapListener, TakeoffDetailsListener {
    private static final int LOCATION_UPDATE_TIME = 300000; // update location every LOCATION_UPDATE_TIME millisecond
    private static final int LOCATION_UPDATE_DISTANCE = 100; // or when we've moved more than LOCATION_UPDATE_DISTANCE meters
    private static final int TAKEOFFS_SORT_DISTANCE = 1000; // only sort takeoff list when we've moved more than TAKEOFFS_SORT_DISTANCE meters
    private static Location lastSortedTakeoffsLocation;
    private static Location location = new Location(LocationManager.PASSIVE_PROVIDER);
    private static FlyWithMe instance;
    private static boolean mapLastViewed = false; // false == we entered TakeoffDetails from TakeoffList, true == we entered TakeoffDetails from TakeoffMap
    
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
     * Show NOAA forecast in NoaaForecast fragment.
     * @param noaaForecastBitmap The bitmap containing the forecast.
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

    /**
     * Show TakeoffList fragment.
     */
    public void showTakeoffList() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        TakeoffList takeoffList = (fragment != null && fragment instanceof TakeoffList) ? (TakeoffList) fragment : new TakeoffList();
        /* show fragment */
        mapLastViewed = false;
        showFragment(takeoffList, "takeoffList");
    }

    /**
     * Show TakeoffMap fragment.
     */
    public void showMap() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        TakeoffMap takeoffMap = (fragment != null && fragment instanceof TakeoffMap) ? (TakeoffMap) fragment : new TakeoffMap();
        /* show fragment */
        mapLastViewed = true;
        showFragment(takeoffMap, "map");
    }

    /**
     * Show settings. TODO: There's no "SupportPreferenceFragment" (yet), thus this has to be an own activity for the time being
     */
    public void showSettings() {
        Intent preferenceIntent = new Intent(this, Preferences.class);
        preferenceIntent.putStringArrayListExtra("airspaceList", new ArrayList<String>(Airspace.getAirspaceMap().keySet()));
        startActivity(preferenceIntent);
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
                if (newLocation == null)
                    return;
                location = newLocation;
                updateTakeoffList();
            }
        };
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_UPDATE_TIME, LOCATION_UPDATE_DISTANCE, locationListener);
        location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (location == null)
            location = new Location(LocationManager.PASSIVE_PROVIDER); // no location set, let's pretend we're skinny dipping in the gulf of guinea
        
        instance = this;

        if (savedInstanceState != null || findViewById(R.id.fragmentContainer) == null)
            return;

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
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (fragment instanceof NoaaForecast) {
            /* when we're looking at a forecast, then the back button will always take us to TakeoffDetails */
            showTakeoffDetails(((NoaaForecast) fragment).getTakeoff());
        } else if (fragment instanceof TakeoffDetails) {
            /* either TakeoffMap or TakeoffList */
            if (mapLastViewed)
                showMap();
            else
                showTakeoffList();
        } else if (fragment instanceof TakeoffMap) {
            /* when we're looking at map, the back button takes us to the list */
            showTakeoffList();
        } else {
            /* looking at list, call parent onBackPressed(), which probably exits the application */
            super.onBackPressed();
        }
    }
    
    private void showFragment(Fragment fragment, String name) {
        getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, fragment, name).commit();
    }

    private synchronized boolean updateTakeoffList() {
        if (lastSortedTakeoffsLocation == null || location.distanceTo(lastSortedTakeoffsLocation) >= TAKEOFFS_SORT_DISTANCE) {
            /* moved too much, need to sort takeoff list again */
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
}
