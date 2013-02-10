package net.exent.flywithme;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.exent.flywithme.TakeoffDetails.TakeoffDetailsListener;
import net.exent.flywithme.TakeoffList.TakeoffListListener;
import net.exent.flywithme.TakeoffMap.TakeoffMapListener;
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
    private static final int LOCATION_UPDATE_TIME = 300000; // update location every LOCATION_UPDATE_TIME millisecond
    private static final int LOCATION_UPDATE_DISTANCE = 100; // or when we've moved more than LOCATION_UPDATE_DISTANCE meters
    private static final int TAKEOFFS_SORT_DISTANCE = 1000; // only sort takeoff list when we've moved more than TAKEOFFS_SORT_DISTANCE meters
    private static List<Takeoff> takeoffs = new ArrayList<Takeoff>();
    private static Location lastReadTakeoffsFileLocation;
    private Location location = new Location(LocationManager.PASSIVE_PROVIDER);
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
        if (lastReadTakeoffsFileLocation != null && location.distanceTo(lastReadTakeoffsFileLocation) < TAKEOFFS_SORT_DISTANCE)
            return takeoffs;
        lastReadTakeoffsFileLocation = location;
        takeoffs = getTakeoffsAt(location);
        return takeoffs;
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
        List<Takeoff> takeoffs = new ArrayList<Takeoff>(FlyWithMe.takeoffs);
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
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (fragment != null && fragment instanceof TakeoffMap)
            return;

        /* fragment not visible, need to create it */
        TakeoffMap takeoffMap = new TakeoffMap();
        /* replace fragment container & add transaction to the back stack */
        getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer, takeoffMap).commit();
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
        if (takeoffs.isEmpty())
            readTakeoffsFile();

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
        } else if (previousFragment != null && previousFragment instanceof TakeoffList) {
            showTakeoffList();
        } else if (previousFragment != null && previousFragment instanceof TakeoffMap) {
            showMap();
        } else {
            Log.d(getClass().getSimpleName(), "Dunno what to do in onBackPressed(), fragment = " + fragment + ", previousFragment = " + previousFragment);
        }
    }

    /**
     * Read file with takeoff details.
     */
    private void readTakeoffsFile() {
        Log.d(getClass().getSimpleName(), "readTakeoffsFile()");
        takeoffs = new ArrayList<Takeoff>();
        try {
            Log.i(getClass().getSimpleName(), "Reading file with takeoffs");
            DataInputStream inputStream = new DataInputStream(getResources().openRawResource(R.raw.flywithme));
            while (true) {
                /* loop breaks once we get an EOFException */
                int takeoff = inputStream.readShort();
                String name = inputStream.readUTF();
                String description = inputStream.readUTF();
                int asl = inputStream.readShort();
                int height = inputStream.readShort();
                Location takeoffLocation = new Location(LocationManager.PASSIVE_PROVIDER);
                takeoffLocation.setLatitude(inputStream.readFloat());
                takeoffLocation.setLongitude(inputStream.readFloat());
                String windpai = inputStream.readUTF();

                takeoffs.add(new Takeoff(takeoff, name, description, asl, height, takeoffLocation.getLatitude(), takeoffLocation.getLongitude(), windpai));
            }
        } catch (EOFException e) {
            /* expected, do nothing */
            Log.i(getClass().getSimpleName(), "Done reading file with takeoffs");
        } catch (IOException e) {
            Log.e(getClass().getSimpleName(), "Error when reading file with takeoffs", e);
        }
    }
}
