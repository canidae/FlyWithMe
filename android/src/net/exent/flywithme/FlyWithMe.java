package net.exent.flywithme;

import java.util.ArrayList;
import java.util.Iterator;
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
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Pair;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;

public class FlyWithMe extends FragmentActivity implements TakeoffListListener, TakeoffMapListener, TakeoffDetailsListener {
    private static final int LOCATION_UPDATE_TIME = 300000; // update location every LOCATION_UPDATE_TIME millisecond
    private static final int LOCATION_UPDATE_DISTANCE = 100; // or when we've moved more than LOCATION_UPDATE_DISTANCE meters
    private static final int TAKEOFFS_SORT_DISTANCE = 1000; // only sort takeoff list when we've moved more than TAKEOFFS_SORT_DISTANCE meters
    private static List<Pair<String, Fragment>> backstack = new ArrayList<Pair<String, Fragment>>();
    private static Location lastSortedTakeoffsLocation;
    private static Location location = new Location(LocationManager.PASSIVE_PROVIDER);

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
        if (fragment != null && fragment instanceof TakeoffDetails) {
            ((TakeoffDetails) fragment).showTakeoffDetails(takeoff);
            return;
        }

        TakeoffDetails takeoffDetails = new TakeoffDetails();
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
        if (fragment != null && fragment instanceof NoaaForecast)
            return;

        NoaaForecast noaaForecast = new NoaaForecast();
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
        if (fragment != null && fragment instanceof TakeoffList)
            return;

        TakeoffList takeoffList = new TakeoffList();
        /* show fragment */
        showFragment(takeoffList, "takeoffList");
    }

    /**
     * Show TakeoffMap fragment.
     */
    public void showMap() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (fragment != null && fragment instanceof TakeoffMap)
            return;

        TakeoffMap takeoffMap = new TakeoffMap();
        /* show fragment */
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
        /* using our own backstack, because the android one is utterly on crack */
        Fragment lastVisibleFragment = null;
        if (backstack.size() > 0) {
            Pair<String, Fragment> entry = backstack.remove(backstack.size() - 1);
            lastVisibleFragment = entry.second;
            getSupportFragmentManager().beginTransaction().remove(entry.second).commit();
        }
        if (backstack.size() > 0) {
            Pair<String, Fragment> entry = backstack.get(backstack.size() - 1);
            showFragment(entry.second, entry.first);
        } else {
            /* nothing left in backstack, unless another fragment than the takeoff list is shown, then exit */
            if (lastVisibleFragment == null || lastVisibleFragment instanceof TakeoffList)
                super.onBackPressed();
            else
                showTakeoffList();
        }
    }
    
    private void showFragment(Fragment fragment, String name) {
        /* only add fragment to backstack when it's not already there */
        for (Iterator<Pair<String, Fragment>> it = backstack.iterator(); it.hasNext();) {
            Pair<String, Fragment> entry = it.next();
            if ((name == null && entry.first == null) || (name != null && name.equals(entry.first))) {
                it.remove();
                break;
            }
        }
        backstack.add(new Pair<String, Fragment>(name, fragment));
        getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, fragment, name).commit();
    }

    private synchronized boolean updateTakeoffList() {
        if (lastSortedTakeoffsLocation == null || location.distanceTo(lastSortedTakeoffsLocation) >= TAKEOFFS_SORT_DISTANCE) {
            /* moved too much, need to sort takeoff list again */
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

    private class InitDataTask extends AsyncTask<Context, String, Void> {
        private ProgressDialog progressDialog;
        
        @Override
        protected Void doInBackground(Context... contexts) {
            progressDialog = new ProgressDialog();
            progressDialog.show(getSupportFragmentManager(), "ProgressDialogFragment");
            publishProgress("" + (int) (Math.random() * 33), getString(R.string.loading_takeoffs));
            Flightlog.init(contexts[0]);
            publishProgress("" + (int) (Math.random() * 34 + 33), getString(R.string.loading_airspace));
            Airspace.init(contexts[0]);
            publishProgress("" + (int) (Math.random() * 33 + 67), getString(R.string.sorting_takeoffs));
            return null;
        }

        @Override
        protected void onProgressUpdate(String... messages) {
            progressDialog.setProgress(Integer.parseInt(messages[0]), messages[1]);
        }
        
        @Override
        protected void onPostExecute(Void nothing) {
            showTakeoffList();
            progressDialog.dismiss();
            progressDialog = null;
        }
    }
}
