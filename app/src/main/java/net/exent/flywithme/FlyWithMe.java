package net.exent.flywithme;

import net.exent.flywithme.fragment.NoaaForecast;
import net.exent.flywithme.fragment.Preferences;
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
import android.location.Location;
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

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;

/* TODO:
   - Replace ScheduleService with FlyWithMeService (remember FlyWithMeBroadcastReceiver and AndroidManifest)
   - NoaaForecast: Would prefer a better way to transfer data to fragment
   - Use endpoint API for registering planned flight
   - Use endpoint API for fetching planned flights (schedule)
   - Display notification if user is close to takeoff ("are you flying?")
     - Must be possible to "blacklist" takeoffs, and somehow remove blacklisting later (in preference window?)
   - Cache forecasts locally for some few hours (fetched timestamp is returned, cache for the same amount of time as server caches the forecast)
   - Implement "Poor Man's SPOT"? Livetracking?
 */
public class FlyWithMe extends Activity implements GoogleApiClient.ConnectionCallbacks, LocationListener {
    public static final String ACTION_SHOW_FORECAST = "showForecast";
    public static final String SERVER_URL = "http://flywithme-server.appspot.com/fwm";

    private GoogleApiClient googleApiClient;
    private Location location;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fly_with_me);

        /* setup Google API client */
        googleApiClient = new GoogleApiClient.Builder(this).addApi(LocationServices.API).addConnectionCallbacks(this).build();

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

        /* setup any preferences that needs to be done programmatically */
        Preferences.setupDefaultPreferences(this);

        /* start background task */
        Intent flyWithMeService = new Intent(this, FlyWithMeService.class);
        /* register pilot if we haven't done so already */
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (prefs.getString("token", null) == null) {
            Intent intent = new Intent(this, FlyWithMeService.class);
            intent.setAction(FlyWithMeService.ACTION_REGISTER_PILOT);
        }
        startService(flyWithMeService);


        /* start importing takeoffs from files */
        (new ImportTakeoffTask()).execute();

        /* show preferences if we haven't set pilot name, otherwise takeoff list */
        if (savedInstanceState == null) {
            String pilotName = prefs.getString("pref_pilot_name", null);
            if (pilotName == null || pilotName.trim().equals("")) {
                replaceFragment(new Preferences(), "preferences");
            } else {
                replaceFragment(new TakeoffList(), "takeoffList");
            }
        } else {
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        googleApiClient.connect();
    }

    @Override
    public void onConnected(Bundle bundle) {
        LocationRequest locationRequest = LocationRequest.create().setInterval(10000).setFastestInterval(10000).setPriority(LocationRequest.PRIORITY_LOW_POWER);
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
        location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(getClass().getName(), "onLocationChanged(" + location + ")");
        this.location = location;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(getClass().getName(), "onNewIntent(" + intent + ")");
        super.onNewIntent(intent);
        if (ACTION_SHOW_FORECAST.equals(intent.getAction())) {
            NoaaForecast noaaForecast = new NoaaForecast();
            noaaForecast.setArguments(intent.getExtras());
            long takeoffId = intent.getExtras() != null ? intent.getExtras().getLong(NoaaForecast.ARG_TAKEOFF_ID) : -1;
            replaceFragment(noaaForecast, "noaaForecast," + takeoffId);
        }
    }

    @Override
    public void onBackPressed() {
        if (getFragmentManager().getBackStackEntryCount() <= 1)
            finish();
        else
            super.onBackPressed();
    }

    @Override
    public void onPause() {
        super.onPause();
        LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
    }

    @Override
    public void onStop() {
        super.onStop();
        googleApiClient.disconnect();
    }

    private void showTakeoffList() {
        String tag = "takeoffList";
        FragmentManager fragmentManager = getFragmentManager();
        Fragment fragment = fragmentManager.findFragmentByTag(tag);
        Bundle bundle = new Bundle();
        if (location != null)
            bundle.putParcelable(TakeoffList.ARG_LOCATION, location);
        if (fragment == null) {
            fragment = new TakeoffList();
            fragment.setArguments(bundle);
        }
        replaceFragment(fragment, tag);
    }

    private void showMap() {
        String tag = "takeoffMap";
        FragmentManager fragmentManager = getFragmentManager();
        Fragment fragment = fragmentManager.findFragmentByTag(tag);
        Bundle bundle = new Bundle();
        if (location != null)
            bundle.putParcelable(TakeoffMap.ARG_LOCATION, location);
        if (fragment == null) {
            fragment = new TakeoffMap();
            fragment.setArguments(bundle);
        }
        replaceFragment(fragment, tag);
    }

    private void showSettings() {
        String tag = "preferences";
        FragmentManager fragmentManager = getFragmentManager();
        Fragment fragment = fragmentManager.findFragmentByTag(tag);
        if (fragment == null)
            fragment = new Preferences();
        replaceFragment(fragment, tag);
    }

    private void replaceFragment(Fragment fragment, String tag) {
        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager.findFragmentByTag(tag) != null) {
            fragmentManager.popBackStack(tag, 0);
        } else {
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            fragmentTransaction.replace(R.id.fragmentContainer, fragment, tag);
            fragmentTransaction.addToBackStack(tag);
            fragmentTransaction.commit();
        }
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

        private void importTakeoffs() {
            Log.d(getClass().getName(), "Importing takeoffs from file");
            DataInputStream inputStream = null;
            try {
                Context context = getApplicationContext();
                Database database = new Database(context);
                inputStream = new DataInputStream(context.getResources().openRawResource(R.raw.flywithme));
                long importTimestamp = inputStream.readLong();
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                long previousImportTimestamp = prefs.getLong("pref_last_takeoff_update_timestamp", 0);
                if (importTimestamp <= previousImportTimestamp) {
                    Log.d(getClass().getName(), "No need to import, already up to date");
                    return; // no need to import, already updated
                }
                prefs.edit().putLong("pref_last_takeoff_update_timestamp", importTimestamp).apply();
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
    }
}
