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
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
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
    private static final int DEFAULT_MAX_TAKEOFFS = 200;
    private static Location lastSortedTakeoffsLocation;
    private static Location location = new Location(LocationManager.PASSIVE_PROVIDER);
    private static List<Takeoff> sortedTakeoffs = new ArrayList<Takeoff>();
    private static Fragment previousFragment;
    private static TakeoffDetails takeoffDetails;
    private static TakeoffList takeoffList;
    private static TakeoffMap takeoffMap;
    private static volatile boolean initializing;

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
        updateTakeoffList();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int maxTakeoffs = DEFAULT_MAX_TAKEOFFS;
        try {
            maxTakeoffs = Integer.parseInt(prefs.getString("pref_max_takeoffs", "" + DEFAULT_MAX_TAKEOFFS));
        } catch (NumberFormatException e) {
            Log.w(getClass().getSimpleName(), "Unable to parse max takeoffs setting as integer", e);
        }
        if (maxTakeoffs > 0) {
            return sortedTakeoffs.subList(0, maxTakeoffs > sortedTakeoffs.size() ? sortedTakeoffs.size() : maxTakeoffs);
        } else if (maxTakeoffs < 0) {
            /* negative maxTakeoffs means takeoffs within certain distance */
            int pos;
            for (pos = 0; pos < sortedTakeoffs.size() && location.distanceTo(sortedTakeoffs.get(pos).getLocation()) <= maxTakeoffs * -1000; ++pos)
                continue; // loop breaks when we've found the locations within set max distance
            return sortedTakeoffs.subList(0, pos);
        }
        return sortedTakeoffs;
    }

    /**
     * Fetch takeoffs near the given location. This requires takeoffs to be sorted by distance, which will cost some CPU.
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

        /* fragment not visible, do we need to create it= */
        if (takeoffDetails == null)
            takeoffDetails = new TakeoffDetails();
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

        /* fragment not visible, do we need to create it= */
        if (takeoffList == null)
            takeoffList = new TakeoffList();
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

        /* fragment not visible, do we need to create it= */
        if (takeoffMap == null)
            takeoffMap = new TakeoffMap();
        /* replace fragment container & add transaction to the back stack */
        getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, takeoffMap).commit();
    }

    /**
     * Show settings. TODO: There's no "SupportPreferenceFragment" (yet), thus this has to an own activity for the time being
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
                updateTakeoffList();
            }
        };
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_UPDATE_TIME, LOCATION_UPDATE_DISTANCE, locationListener);
        location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (location == null)
            location = new Location(LocationManager.PASSIVE_PROVIDER); // no location set, let's pretend we're skinny dipping in the gulf of guinea

        if (savedInstanceState != null || findViewById(R.id.fragmentContainer) == null)
            return;

        /* takeoff list is default view */
        takeoffList = new TakeoffList();
        getSupportFragmentManager().beginTransaction().add(R.id.fragmentContainer, takeoffList).commit();
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

    private synchronized boolean updateTakeoffList() {
        if (lastSortedTakeoffsLocation == null || location.distanceTo(lastSortedTakeoffsLocation) >= TAKEOFFS_SORT_DISTANCE) {
            /* moved too much, need to sort takeoff list again */
            lastSortedTakeoffsLocation = location;
            new UpdateTakeoffListTask().execute();
            return true;
        }
        return false;
    }

    private class UpdateTakeoffListTask extends AsyncTask<Void, Void, List<Takeoff>> {
        @Override
        protected List<Takeoff> doInBackground(Void... params) {
            return getTakeoffsAt(location);
        }

        @Override
        protected void onPostExecute(List<Takeoff> takeoffs) {
            Log.d("UpdateTakeoffListTask", "Setting sortedTakeoffs");
            if (initializing) {
                Log.i(getClass().getSimpleName(), "Still initializing, skipping updating sorted takeoff list");
                return;
            }
            sortedTakeoffs = takeoffs;
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
            if (fragment != null && fragment instanceof TakeoffMap)
                takeoffMap.drawMap();
            else if (fragment != null && fragment instanceof TakeoffList)
                takeoffList.updateList();
        }
    }

    private class InitDataTask extends AsyncTask<Context, Integer, Void> {
        @Override
        protected Void doInBackground(Context... contexts) {
            initializing = true;
            publishProgress(0);
            Flightlog.init(contexts[0]);
            publishProgress(1);
            Airspace.init(contexts[0]);
            publishProgress(2);
            initializing = false;
            new UpdateTakeoffListTask().execute();
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            String message;
            switch (progress[0]) {
            case 0:
                message = getString(R.string.loading_takeoffs);
                break;

            case 1:
                message = getString(R.string.loading_airspace);
                break;

            case 2:
                message = getString(R.string.sorting_takeoffs);
                break;

            default:
                /* not expected to happen */
                message = "Unexpected, bug developer";
                break;
            }
            Log.d(getClass().getSimpleName(), "InitDataTask: " + message);
            sortedTakeoffs.add(new Takeoff(0, message, "", 0, 0, location.getLatitude(), location.getLongitude(), ""));
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
            if (fragment != null && fragment instanceof TakeoffList)
                takeoffList.updateList();
        }
    }
}
