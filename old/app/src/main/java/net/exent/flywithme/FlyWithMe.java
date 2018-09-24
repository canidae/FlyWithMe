package net.exent.flywithme;

import net.exent.flywithme.fragment.NoaaForecast;
import net.exent.flywithme.fragment.Preferences;
import net.exent.flywithme.fragment.TakeoffDetails;
import net.exent.flywithme.fragment.TakeoffList;
import net.exent.flywithme.fragment.TakeoffMap;
import net.exent.flywithme.bean.Takeoff;
import net.exent.flywithme.data.Database;
import net.exent.flywithme.service.FlyWithMeService;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.Fragment;
import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;

/* TODO:
   - Handle runtime permissions in android 6.0 better. It was hacked together just to make it work
   - Cache location so it won't always show Rikssenteret at start
   - NoaaForecast: Would prefer a better way to transfer data to fragment
   - Cache forecasts locally for some few hours (fetched timestamp is returned, cache for the same amount of time as server caches the forecast)
 */
public class FlyWithMe extends Activity implements GoogleApiClient.ConnectionCallbacks, LocationListener, FlyWithMeActivity {
    public static final String ACTION_SHOW_FORECAST = "showForecast";
    public static final String ACTION_SHOW_PREFERENCES = "showPreferences";
    public static final String ACTION_SHOW_TAKEOFF_DETAILS = "showTakeoffDetails";

    public static final String ARG_TAKEOFF_ID = "takeoffId";

    private GoogleApiClient googleApiClient;

    public void showFragment(String tag, Class<? extends Fragment> fragmentClass, Bundle args) {
        /* reset right menu */
        resetRightMenuButtons();
        /* then display the fragment requested */
        FragmentManager fragmentManager = getFragmentManager();
        if (tag != null && fragmentManager.findFragmentByTag(tag) != null) {
            fragmentManager.popBackStack(tag, 0);
        } else {
            try {
                Fragment fragment = fragmentClass.newInstance();
                if (args != null)
                    fragment.setArguments(args);
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                fragmentTransaction.replace(R.id.fragmentContainer, fragment, tag);
                fragmentTransaction.addToBackStack(tag);
                fragmentTransaction.commit();
            } catch (Exception e) {
                Log.w(FlyWithMe.class.getName(), "Error instantiating class", e);
            }
        }
    }

    @Override
    public Location getLocation() {
        if (checkLocationAccessPermission()) {
            Location location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
            if (location != null)
                return location;
        }
        // no known location, return location of the Rikssenter for the time being
        Location location = new Location(LocationManager.PASSIVE_PROVIDER);
        location.setLatitude(61.874655);
        location.setLongitude(9.154848);
        return location;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /* start FlyWithMeService */
        Intent intent = new Intent(this, FlyWithMeService.class);
        intent.setAction(FlyWithMeService.ACTION_INIT);
        startService(intent);

        /* setup Google API client */
        googleApiClient = new GoogleApiClient.Builder(this).addApi(LocationServices.API).addConnectionCallbacks(this).build();

        /* setup content view */
        setContentView(R.layout.fly_with_me);

        /* starting app, setup buttons */
        final Activity activity = this;
        ImageButton fwmButton = (ImageButton) findViewById(R.id.fwmButton);
        fwmButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                showFragment("takeoffList", TakeoffList.class, null);
            }
        });
        ImageButton mapButton = (ImageButton) findViewById(R.id.mapButton);
        mapButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                showFragment("takeoffMap", TakeoffMap.class, null);
            }
        });
        ImageButton settingsButton = (ImageButton) findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                showFragment("preferences", Preferences.class, null);
            }
        });
        // reset right menu icons
        ((ImageButton) activity.findViewById(R.id.fragmentButton1)).setImageDrawable(null);
        ((ImageButton) activity.findViewById(R.id.fragmentButton2)).setImageDrawable(null);
        ((ImageButton) activity.findViewById(R.id.fragmentButton3)).setImageDrawable(null);
        // and their progress bars
        activity.findViewById(R.id.progressBar1).setVisibility(View.INVISIBLE);
        activity.findViewById(R.id.progressBar2).setVisibility(View.INVISIBLE);
        activity.findViewById(R.id.progressBar3).setVisibility(View.INVISIBLE);

        /* setup any preferences that needs to be done programmatically */
        Preferences.setupDefaultPreferences(this);

        /* register pilot if we haven't done so already */
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (sharedPref.getString("pilot_id", null) == null) {
            intent = new Intent(this, FlyWithMeService.class);
            intent.setAction(FlyWithMeService.ACTION_REGISTER_PILOT);
            startService(intent);
        }

        /* start importing takeoffs from files */
        (new ImportTakeoffTask()).execute();

        /* show takeoff list */
        if (savedInstanceState == null)
                showFragment("takeoffList", TakeoffList.class, null);
    }

    @Override
    public void onStart() {
        super.onStart();
        googleApiClient.connect();
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (!checkLocationAccessPermission()) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 0);
            return;
        }
        setupLocationCallback();
    }

    @Override
    public void onLocationChanged(Location location) {
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (permissions.length == 1 && android.Manifest.permission.ACCESS_FINE_LOCATION.equals(permissions[0]) && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            setupLocationCallback();
    }

    @Override
    public void onBackPressed() {
        if (getFragmentManager().getBackStackEntryCount() <= 1) {
            finish();
        } else {
            resetRightMenuButtons();
            super.onBackPressed();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (googleApiClient != null && googleApiClient.isConnected())
            LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(getClass().getName(), "onNewIntent(" + intent + ")");
        super.onNewIntent(intent);
        if (ACTION_SHOW_FORECAST.equals(intent.getAction())) {
            showFragment(null, NoaaForecast.class, intent.getExtras());
        } else if (ACTION_SHOW_PREFERENCES.equals(intent.getAction())) {
            showFragment("preferences", Preferences.class, null);
        } else if (ACTION_SHOW_TAKEOFF_DETAILS.equals(intent.getAction())) {
            Takeoff takeoff = Database.getTakeoff(getApplicationContext(), intent.getLongExtra(ARG_TAKEOFF_ID, 0));
            Bundle args = new Bundle();
            args.putParcelable(TakeoffDetails.ARG_TAKEOFF, takeoff);
            showFragment("takeoffDetails," + takeoff.getId(), TakeoffDetails.class, args);
        }
    }

    private boolean checkLocationAccessPermission() {
        return ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void setupLocationCallback() {
        if (checkLocationAccessPermission()) {
            LocationRequest locationRequest = LocationRequest.create()
                    .setSmallestDisplacement((float) 100.0)
                    .setInterval(60000)
                    .setFastestInterval(30000)
                    .setPriority(LocationRequest.PRIORITY_LOW_POWER);
            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
            refreshCurrentFragment();
        }
    }

    private void refreshCurrentFragment() {
        try {
            Fragment fragment = getFragmentManager().findFragmentById(R.id.fragmentContainer);
            if (fragment != null)
                getFragmentManager().beginTransaction().detach(fragment).attach(fragment).commit();
        } catch (IllegalStateException e) {
            Log.i(getClass().getName(), "Attempted to refresh fragment after it was hidden (probably)", e);
        }
    }

    private void resetRightMenuButtons() {
        /* reset right menu icons */
        ((ImageButton) findViewById(R.id.fragmentButton1)).setImageDrawable(null);
        ((ImageButton) findViewById(R.id.fragmentButton2)).setImageDrawable(null);
        ((ImageButton) findViewById(R.id.fragmentButton3)).setImageDrawable(null);
        /* and their progress bars */
        findViewById(R.id.progressBar1).setVisibility(View.INVISIBLE);
        findViewById(R.id.progressBar2).setVisibility(View.INVISIBLE);
        findViewById(R.id.progressBar3).setVisibility(View.INVISIBLE);
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
            refreshCurrentFragment();
        }

        private void importTakeoffs() {
            Log.d(getClass().getName(), "Importing takeoffs from file");
            DataInputStream inputStream = null;
            Context context = getApplicationContext();
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
            long updateTimestamp = 0;
            long updateVersion = 0;
            try {
                inputStream = new DataInputStream(context.getResources().openRawResource(R.raw.flywithme));
                updateTimestamp = inputStream.readLong();
                long previousUpdateTimestamp = sharedPref.getLong("takeoff_last_update_timestamp", 0);
                PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                updateVersion = packageInfo.versionCode;
                long previousUpdateVersion = sharedPref.getLong("takeoff_last_update_version", 0);
                if (updateTimestamp <= previousUpdateTimestamp && previousUpdateVersion == updateVersion) {
                    Log.d(getClass().getName(), "No need to import, already up to date");
                    return; // no need to import, already updated
                }
                while (true) {
                    /* loop breaks once we get an EOFException */
                    int takeoffId = inputStream.readShort();
                    String name = inputStream.readUTF();
                    String description = inputStream.readUTF();
                    int asl = inputStream.readShort();
                    int height = inputStream.readShort();
                    float latitude = inputStream.readFloat();
                    float longitude = inputStream.readFloat();
                    int exits = inputStream.readShort();
                    Takeoff takeoff = new Takeoff(takeoffId, updateTimestamp, name, description, asl, height, latitude, longitude, exits, false);
                    Database.updateTakeoff(getApplicationContext(), takeoff);
                }
            } catch (EOFException e) {
                /* expected to happen when reaching end of file */
                sharedPref.edit().putLong("takeoff_last_update_timestamp", updateTimestamp).apply();
                sharedPref.edit().putLong("takeoff_last_update_version", updateVersion).apply();
            } catch (IOException e) {
                Log.e(getClass().getName(), "Error when reading file with takeoffs", e);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(getClass().getName(), "Error when fetching app version", e);
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
    }
}
