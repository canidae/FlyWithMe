package net.exent.flywithme;

import java.util.ArrayList;

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
import android.support.v4.app.FragmentManager;
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

        TakeoffDetails takeoffDetails = new TakeoffDetails();
        /* pass arguments */
        Bundle args = new Bundle();
        args.putParcelable(TakeoffDetails.ARG_TAKEOFF, takeoff);
        takeoffDetails.setArguments(args);
        /* show fragment */
        showFragment(takeoffDetails, "takeoffDetails");
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

        TakeoffList takeoffList = new TakeoffList();
        /* show fragment */
        showFragment(takeoffList, "takeoffList");
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

        TakeoffMap takeoffMap = new TakeoffMap();
        /* show fragment */
        showFragment(takeoffMap, "map");
    }

    /**
     * Show settings. TODO: There's no "SupportPreferenceFragment" (yet), thus this has to be an own activity for the time being
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
    
    private void showFragment(Fragment fragment, String name) {
        Log.d(getClass().getName(), "showFragment(" + fragment + ", " + name + ")");
        /* only add fragment to backstack when it's not already there */
        FragmentManager manager = getSupportFragmentManager();
        manager.popBackStack(name, 0); // bah, this will pop all up to <name>, but we only want to pop <name>
        manager.beginTransaction().replace(R.id.fragmentContainer, fragment, name).addToBackStack(name).commit();
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
            Log.d(getClass().getSimpleName(), "doInBackground(" + contexts + ")");
            progressDialog = new ProgressDialog();
            progressDialog.show(getSupportFragmentManager(), "ProgressDialogFragment");
            publishProgress("33", getString(R.string.loading_takeoffs));
            Flightlog.init(contexts[0]);
            publishProgress("67", getString(R.string.loading_airspace));
            Airspace.init(contexts[0]);
            publishProgress("100", getString(R.string.sorting_takeoffs));
            return null;
        }

        @Override
        protected void onProgressUpdate(String... messages) {
            Log.d(getClass().getSimpleName(), "onProgressUpdate(" + messages + ")");
            progressDialog.setProgress(Integer.parseInt(messages[0]), messages[1]);
        }
        
        @Override
        protected void onPostExecute(Void nothing) {
            Log.d(getClass().getSimpleName(), "onPostExecute()");
            showTakeoffList();
            progressDialog.dismiss();
            progressDialog = null;
        }
    }
}
