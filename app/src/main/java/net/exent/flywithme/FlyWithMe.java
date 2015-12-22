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
   - Cache forecasts locally for some few hours (fetched timestamp is returned, cache for the same amount of time as server caches the forecast)
   - Implement "Poor Man's SPOT"? Livetracking?
 */
public class FlyWithMe extends Activity implements GoogleApiClient.ConnectionCallbacks, LocationListener {
    public static final String ACTION_SHOW_FORECAST = "showForecast";
    public static final String ACTION_SHOW_TAKEOFF_DETAILS = "showTakeoffDetails";

    public static final String ARG_TAKEOFF_ID = "takeoffId";

    public static final String SERVER_URL = "http://flywithme-server.appspot.com/fwm";

    private GoogleApiClient googleApiClient;
    private Location location;

    public static void showFragment(Activity activity, String tag, Class<? extends Fragment> fragmentClass, Bundle args) {
        FragmentManager fragmentManager = activity.getFragmentManager();
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
        // reset icons
        ((ImageButton) activity.findViewById(R.id.fragmentButton1)).setImageDrawable(null);
        ((ImageButton) activity.findViewById(R.id.fragmentButton2)).setImageDrawable(null);
        ((ImageButton) activity.findViewById(R.id.fragmentButton3)).setImageDrawable(null);
        // and progress bars
        activity.findViewById(R.id.progressBar1).setVisibility(View.INVISIBLE);
        activity.findViewById(R.id.progressBar2).setVisibility(View.INVISIBLE);
        activity.findViewById(R.id.progressBar3).setVisibility(View.INVISIBLE);
    }

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
        final Activity activity = this;
        ImageButton fwmButton = (ImageButton) findViewById(R.id.fwmButton);
        fwmButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Bundle bundle = null;
                if (location != null) {
                    bundle = new Bundle();
                    bundle.putParcelable(TakeoffList.ARG_LOCATION, location);
                }
                showFragment(activity, "takeoffList", TakeoffList.class, bundle);
            }
        });
        ImageButton mapButton = (ImageButton) findViewById(R.id.mapButton);
        mapButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Bundle bundle = null;
                if (location != null) {
                    bundle = new Bundle();
                    bundle.putParcelable(TakeoffMap.ARG_LOCATION, location);
                }
                showFragment(activity, "takeoffMap", TakeoffMap.class, bundle);
            }
        });
        ImageButton settingsButton = (ImageButton) findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                showFragment(activity, "preferences", Preferences.class, null);
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
                showFragment(this, "preferences", Preferences.class, null);
            } else {
                showFragment(this, "takeoffList", TakeoffList.class, null);
            }
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
        this.location = location;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(getClass().getName(), "onNewIntent(" + intent + ")");
        super.onNewIntent(intent);
        if (ACTION_SHOW_FORECAST.equals(intent.getAction())) {
            showFragment(this, null, NoaaForecast.class, intent.getExtras());
        } else if (ACTION_SHOW_TAKEOFF_DETAILS.equals(intent.getAction())) {
            Database database = new Database(this);
            Takeoff takeoff = database.getTakeoff(intent.getLongExtra(ARG_TAKEOFF_ID, 0));
            Bundle args = new Bundle();
            args.putParcelable(TakeoffDetails.ARG_TAKEOFF, takeoff);
            FlyWithMe.showFragment(this, "takeoffDetails," + takeoff.getId(), TakeoffDetails.class, args);
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
        if (googleApiClient.isConnected())
            LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
    }

    @Override
    public void onStop() {
        super.onStop();
        googleApiClient.disconnect();
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
