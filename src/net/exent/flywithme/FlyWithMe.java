package net.exent.flywithme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.exent.flywithme.TakeoffDetails.TakeoffDetailsListener;
import net.exent.flywithme.TakeoffList.TakeoffListListener;
import net.exent.flywithme.TakeoffMap.TakeoffMapListener;
import net.exent.flywithme.bean.Takeoff;
import net.exent.flywithme.data.Airspace;
import net.exent.flywithme.data.Flightlog;
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
    private Location location = new Location(LocationManager.PASSIVE_PROVIDER);
    private List<Takeoff> sortedTakeoffs;
    private Fragment previousFragment;

    /**
     * Get approximate location of user.
     * 
     * @return Approximate location of user.
     */
    public Location getLocation() {
        Log.d(getClass().getSimpleName(), "getLocation()");
        return location;
    }

    /**
     * Fetch takeoffs near user (list is cached to prevent excessive sorting).
     * 
     * @return Takeoffs near user.
     */
    public List<Takeoff> getNearbyTakeoffs() {
        Log.d(getClass().getSimpleName(), "getNearbyTakeoffs()");
        if (lastSortedTakeoffsLocation != null && location.distanceTo(lastSortedTakeoffsLocation) < TAKEOFFS_SORT_DISTANCE)
            return sortedTakeoffs;
        lastSortedTakeoffsLocation = location;
        sortedTakeoffs = getTakeoffsAt(location);
        return sortedTakeoffs;
    }

    /**
     * Fetch takeoffs near the given location. This requires takeoffs to be sorted by distance, which may cost some CPU.
     * 
     * @param location
     *            Where to look for nearby takeoffs.
     * @return Takeoffs near the given location.
     */
    public List<Takeoff> getTakeoffsAt(final Location location) {
        Log.d(getClass().getSimpleName(), "getTakeoffsAt(" + location + ")");
        List<Takeoff> takeoffs = new ArrayList<Takeoff>(Flightlog.getTakeoffs());
        /* sort list by distance */
        Log.d(getClass().getSimpleName(), "Sorting...");
        Collections.sort(takeoffs, new Comparator<Takeoff>() {
            public int compare(Takeoff lhs, Takeoff rhs) {
                if (location.distanceTo(lhs.getLocation()) > location.distanceTo(rhs.getLocation()))
                    return 1;
                else if (location.distanceTo(lhs.getLocation()) < location.distanceTo(rhs.getLocation()))
                    return -1;
                return 0;
            }
        });
        Log.d(getClass().getSimpleName(), "Done sorting");
        return takeoffs;
    }

    /**
     * Show takeoff details in TakeoffDetails fragment.
     */
    public void showTakeoffDetails(Takeoff takeoff) {
        Log.d(getClass().getSimpleName(), "showTakeoffDetails(" + takeoff + ")");
        hideFragmentButtons();
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (fragment != null && fragment instanceof TakeoffDetails) {
            ((TakeoffDetails) fragment).showTakeoffDetails(takeoff);
            return;
        }
        previousFragment = fragment;

        /* fragment not visible, need to create it */
        TakeoffDetails takeoffDetails = new TakeoffDetails();
        /* pass arguments */
        Bundle args = new Bundle();
        args.putParcelable(TakeoffDetails.ARG_TAKEOFF, takeoff);
        takeoffDetails.setArguments(args);
        /* replace fragment container & add transaction to the back stack */
        getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, takeoffDetails).commit();
    }

    /**
     * Show TakeoffList fragment.
     */
    public void showTakeoffList() {
        Log.d(getClass().getSimpleName(), "showTakeoffList()");
        hideFragmentButtons();
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (fragment != null && fragment instanceof TakeoffList)
            return;

        /* fragment not visible, need to create it */
        TakeoffList takeoffList = new TakeoffList();
        /* replace fragment container & add transaction to the back stack */
        getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, takeoffList).commit();
    }

    /**
     * Show TakeoffMap fragment.
     */
    public void showMap() {
        Log.d(getClass().getSimpleName(), "showMap()");
        hideFragmentButtons();
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (fragment != null && fragment instanceof TakeoffMap)
            return;

        /* fragment not visible, need to create it */
        TakeoffMap takeoffMap = new TakeoffMap();
        /* replace fragment container & add transaction to the back stack */
        getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, takeoffMap).commit();
    }
    
    /**
     * Show settings.
     * TODO: There's no "SupportPreferenceFragment" (yet), thus this has to an own activity for the time being
     */
    public void showSettings() {
        Log.d(getClass().getSimpleName(), "showSettings()");
        Intent preferenceIntent = new Intent(this, Preferences.class);
        preferenceIntent.putStringArrayListExtra("airspaceList", new ArrayList<String>(Airspace.getAirspaceMap().keySet()));
        startActivity(preferenceIntent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(getClass().getSimpleName(), "onCreate(" + savedInstanceState + ")");
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
            }
        };
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_UPDATE_TIME, LOCATION_UPDATE_DISTANCE, locationListener);
        location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (location == null)
            location = new Location(LocationManager.PASSIVE_PROVIDER); // no location set, let's pretend we're skinny dipping in the gulf of guinea

        /* init data */
        Flightlog.init(this);
        Airspace.init(this);

        if (savedInstanceState != null || findViewById(R.id.fragmentContainer) == null)
            return;

        /* takeoff list is default view */
        TakeoffList takeoffList = new TakeoffList();
        getSupportFragmentManager().beginTransaction().add(R.id.fragmentContainer, takeoffList).commit();
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void onBackPressed() {
        /* implementing my own backstack, because the one in FragmentTransaction is on acid */
        Log.d(getClass().getSimpleName(), "onBackPressed()");
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (fragment == null || fragment instanceof TakeoffList) {
            Log.d(getClass().getSimpleName(), "super.onBackPressed()");
            super.onBackPressed();
        } else if (fragment != null && fragment instanceof TakeoffMap) {
            showTakeoffList();
        } else if (previousFragment != null && previousFragment instanceof TakeoffMap) {
            showMap();
        } else {
            showTakeoffList();
        }
    }

    /**
     * Hide buttons unique for a fragment.
     */
    private void hideFragmentButtons() {
        ImageButton fragmentButton1 = (ImageButton) findViewById(R.id.fragmentButton1);
        fragmentButton1.setImageDrawable(null);
        ImageButton fragmentButton2 = (ImageButton) findViewById(R.id.fragmentButton2);
        fragmentButton2.setImageDrawable(null);
    }
}
