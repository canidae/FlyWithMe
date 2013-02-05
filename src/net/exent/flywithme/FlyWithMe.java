package net.exent.flywithme;

import net.exent.flywithme.TakeoffDetails.TakeoffDetailsListener;
import net.exent.flywithme.TakeoffList.TakeoffListListener;
import net.exent.flywithme.TakeoffMap.TakeoffMapListener;
import net.exent.flywithme.dao.Flightlog;
import net.exent.flywithme.data.Takeoff;
import android.content.Context;
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
    private static final int LOCATION_UPDATE_TIME = 300000; // update location every 5 minute
    private static final int LOCATION_UPDATE_DISTANCE = 100; // or when we've moved more than 100 meters
    private Location location = new Location(LocationManager.PASSIVE_PROVIDER);
    private Fragment previousFragment;

    public Location getLocation() {
        Log.d("FlyWithMe", "getLocation()");
        return location;
    }

    public void showTakeoffDetails(Takeoff takeoff) {
        Log.d("FlyWithMe", "showTakeoffDetails(" + takeoff + ")");
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

    public void showTakeoffList() {
        Log.d("FlyWithMe", "showTakeoffList()");
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (fragment != null && fragment instanceof TakeoffList)
            return;

        /* fragment not visible, need to create it */
        TakeoffList takeoffList = new TakeoffList();
        /* replace fragment container & add transaction to the back stack */
        getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, takeoffList).commit();
    }

    public void showMap() {
        Log.d("FlyWithMe", "showMap()");
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (fragment != null && fragment instanceof TakeoffMap)
            return;

        /* fragment not visible, need to create it */
        TakeoffMap takeoffMap = new TakeoffMap();
        /* replace fragment container & add transaction to the back stack */
        getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, takeoffMap).commit();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d("FlyWithMe", "onCreate(" + savedInstanceState + ")");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fly_with_me);

        /* setup location listener */
        final FlyWithMe fwm = this; // can't use "this" inside the LocationListener
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
                Flightlog.updateTakeoffList(fwm, location);
            }
        };
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_UPDATE_TIME, LOCATION_UPDATE_DISTANCE, locationListener);
        location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (location == null)
            location = new Location(LocationManager.PASSIVE_PROVIDER); // no location set, let's pretend we're skinny dipping in the gulf of guinea
        Flightlog.updateTakeoffList(this, location);

        if (savedInstanceState != null || findViewById(R.id.fragmentContainer) == null)
            return;

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

        /* takeoff list is default view */
        TakeoffList takeoffList = new TakeoffList();
        getSupportFragmentManager().beginTransaction().add(R.id.fragmentContainer, takeoffList).commit();
    }

    @Override
    public void onBackPressed() {
        /* implementing my own backstack, because the one in FragmentTransaction is on acid */
        Log.d("FlyWithMe", "onBackPressed()");
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (fragment == null || fragment instanceof TakeoffList) {
            super.onBackPressed();
        } else if (fragment != null && fragment instanceof TakeoffMap) {
            showTakeoffList();
        } else if (previousFragment != null && previousFragment instanceof TakeoffList) {
            showTakeoffList();
        } else if (previousFragment != null && previousFragment instanceof TakeoffMap) {
            showMap();
        }
    }
}
