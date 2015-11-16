package net.exent.flywithme.fragment;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.exent.flywithme.FlyWithMe;
import net.exent.flywithme.R;
import net.exent.flywithme.bean.Takeoff;
import net.exent.flywithme.data.Airspace;
import net.exent.flywithme.data.Database;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnCameraChangeListener;
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;

import android.app.FragmentTransaction;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.Fragment;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ImageButton;

public class TakeoffMap extends Fragment implements OnInfoWindowClickListener, OnCameraChangeListener, OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, LocationListener {
    public static final String ARG_LOCATION = "location";
    public static final String ARG_CAMERA_POSITION = "cameraPosition";

    /* we can't use Map<Marker, Takeoff> below, because the Marker may be recreated, invalidating the reference we got to the previous instantiation.
     * instead we'll have to keep the id (String) as a reference to the marker */
    private static Map<String, Pair<Marker, Takeoff>> markers = new HashMap<>();
    private static Map<Pair<Polygon, Marker>, Airspace.Zone> zones = new HashMap<>();
    private static Bitmap markerBitmap;
    private static Bitmap markerNorthBitmap;
    private static Bitmap markerNortheastBitmap;
    private static Bitmap markerEastBitmap;
    private static Bitmap markerSoutheastBitmap;
    private static Bitmap markerSouthBitmap;
    private static Bitmap markerSouthwestBitmap;
    private static Bitmap markerWestBitmap;
    private static Bitmap markerNorthwestBitmap;
    private static Bitmap markerExclamation;
    private static Bitmap markerExclamationYellow;

    private GoogleApiClient googleApiClient;
    private GoogleMap map;
    private Location location;
    private CameraPosition cameraPosition;

    public void drawMap() {
        try {
            /* need to do this here or it'll end up with a reference to an old instance of "this", somehow */
            map.setInfoWindowAdapter(new TakeoffMapMarkerInfo(getActivity().getLayoutInflater()));
            map.setOnInfoWindowClickListener(this);
            map.setOnCameraChangeListener(this);
            /* clear map */
            map.clear();
            markers.clear();
            zones.clear();
            /* add icons */
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            boolean showTakeoffs = prefs.getBoolean("pref_map_show_takeoffs", true);
            final ImageButton markerButton = (ImageButton) getActivity().findViewById(R.id.fragmentButton1);
            markerButton.setImageResource(showTakeoffs ? R.mipmap.takeoffs_enabled : R.mipmap.takeoffs_disabled);
            markerButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean markersEnabled = !prefs.getBoolean("pref_map_show_takeoffs", true);
                    prefs.edit().putBoolean("pref_map_show_takeoffs", markersEnabled).apply();
                    markerButton.setImageResource(markersEnabled ? R.mipmap.takeoffs_enabled : R.mipmap.takeoffs_disabled);
                    drawOverlay(map.getCameraPosition());
                }
            });
            boolean showAirspace = prefs.getBoolean("pref_map_show_airspaces", true);
            final ImageButton polygonButton = (ImageButton) getActivity().findViewById(R.id.fragmentButton2);
            polygonButton.setImageResource(showAirspace ? R.mipmap.airspace_enabled : R.mipmap.airspace_disabled);
            polygonButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean polygonsEnabled = !prefs.getBoolean("pref_map_show_airspace", true);
                    prefs.edit().putBoolean("pref_map_show_airspace", polygonsEnabled).apply();
                    polygonButton.setImageResource(polygonsEnabled ? R.mipmap.airspace_enabled : R.mipmap.airspace_disabled);
                    drawOverlay(map.getCameraPosition());
                }
            });
        } catch (Exception e) {
            Log.w(getClass().getName(), "drawMap() task failed unexpectedly", e);
        }
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        googleApiClient = new GoogleApiClient.Builder(getActivity()).addApi(LocationServices.API).addConnectionCallbacks(this).build();

        markerBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.mapmarker);
        markerNorthBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.mapmarker_octant_n);
        markerNortheastBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.mapmarker_octant_ne);
        markerEastBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.mapmarker_octant_e);
        markerSoutheastBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.mapmarker_octant_se);
        markerSouthBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.mapmarker_octant_s);
        markerSouthwestBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.mapmarker_octant_sw);
        markerWestBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.mapmarker_octant_w);
        markerNorthwestBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.mapmarker_octant_nw);
        markerExclamation = BitmapFactory.decodeResource(getResources(), R.drawable.mapmarker_exclamation);
        markerExclamationYellow = BitmapFactory.decodeResource(getResources(), R.drawable.mapmarker_exclamation_yellow);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        if (getArguments() != null) {
            Location location = getArguments().getParcelable(ARG_LOCATION);
            if (location != null)
                this.location = location;
            if (this.cameraPosition == null)
                cameraPosition = getArguments().getParcelable(ARG_CAMERA_POSITION);
        }
        if (bundle != null) {
            Location location = bundle.getParcelable(ARG_LOCATION);
            if (location != null)
                this.location = location;
            if (cameraPosition == null)
                cameraPosition = bundle.getParcelable(ARG_CAMERA_POSITION);
        }

        View view = inflater.inflate(R.layout.takeoff_map, container, false);
        GoogleMapOptions mapOptions = new GoogleMapOptions();
        mapOptions.zoomControlsEnabled(false);
        if (cameraPosition != null)
            mapOptions.camera(cameraPosition);
        else if (location != null)
            mapOptions.camera(new CameraPosition(new LatLng(location.getLatitude(), location.getLongitude()), 10.0f, 0.0f, 0.0f));
        MapFragment mapFragment = MapFragment.newInstance(mapOptions);
        mapFragment.getMapAsync(this);
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.replace(R.id.takeoffMapLayout, mapFragment).commit();
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        googleApiClient.connect();
    }

    @Override
    public void onConnected(Bundle bundle) {
        LocationRequest locationRequest = LocationRequest.create().setInterval(1000).setPriority(LocationRequest.PRIORITY_LOW_POWER);
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
        location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
    }

    @Override
    public void onMapReady(GoogleMap map) {
        this.map = map;
        this.map.setMyLocationEnabled(true);
        drawMap();
    }

    @Override
    public void onLocationChanged(Location location) {
        this.location = location;
    }

    @Override
    public void onInfoWindowClick(Marker marker) {
        /* tell main activity to show takeoff details */
        Pair<Marker, Takeoff> pair = markers.get(marker.getId());
        if (pair != null) {
            Takeoff takeoff = pair.second;
            Bundle args = new Bundle();
            args.putParcelable(TakeoffDetails.ARG_TAKEOFF, takeoff);
            FlyWithMe.showFragment(getActivity(), "takeoffDetails," + takeoff.getId(), TakeoffDetails.class, args);
        } else {
            Log.w(getClass().getName(), "Strange, could not find takeoff for marker");
        }
    }

    public void onCameraChange(CameraPosition cameraPosition) {
        this.cameraPosition = cameraPosition;
        drawOverlay(cameraPosition);
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(ARG_LOCATION, location);
        outState.putParcelable(ARG_CAMERA_POSITION, cameraPosition);
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

    private void drawOverlay(CameraPosition cameraPosition) {
        try {
            new DrawPolygonsTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, cameraPosition);
            new DrawMarkersTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, cameraPosition);
        } catch (Exception e) {
            Log.w(getClass().getName(), "redrawMap() failed unexpectedly", e);
        }
    }

    private class DrawMarkersTask extends AsyncTask<CameraPosition, Object, Runnable> {
        @Override
        protected Runnable doInBackground(CameraPosition... cameraPositions) {
            try {
                final Map<Takeoff, MarkerOptions> addMarkers = new HashMap<>();

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                if (prefs.getBoolean("pref_map_show_takeoffs", true)) {
                    LatLng latLng = cameraPositions[0].target;
                    if (latLng.latitude != 0.0 && latLng.longitude != 0.0) {
                        location.setLatitude(latLng.latitude);
                        location.setLongitude(latLng.longitude);
                    }

                    /* get the 25 nearest takeoffs */
                    List<Takeoff> takeoffs = new Database(getActivity()).getTakeoffs(location.getLatitude(), location.getLongitude(), 25, false);

                    /* add markers */
                    for (Takeoff takeoff : takeoffs) {
                        Bitmap bitmap = Bitmap.createBitmap(markerBitmap.getWidth(), markerBitmap.getHeight(), Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(bitmap);
                        Paint paint = new Paint();
                        if (!takeoff.isFavourite())
                            paint.setAlpha(123);
                        canvas.drawBitmap(markerBitmap, 0, 0, paint);
                        if (takeoff.hasNorthExit())
                            canvas.drawBitmap(markerNorthBitmap, 0, 0, paint);
                        if (takeoff.hasNortheastExit())
                            canvas.drawBitmap(markerNortheastBitmap, 0, 0, paint);
                        if (takeoff.hasEastExit())
                            canvas.drawBitmap(markerEastBitmap, 0, 0, paint);
                        if (takeoff.hasSoutheastExit())
                            canvas.drawBitmap(markerSoutheastBitmap, 0, 0, paint);
                        if (takeoff.hasSouthExit())
                            canvas.drawBitmap(markerSouthBitmap, 0, 0, paint);
                        if (takeoff.hasSouthwestExit())
                            canvas.drawBitmap(markerSouthwestBitmap, 0, 0, paint);
                        if (takeoff.hasWestExit())
                            canvas.drawBitmap(markerWestBitmap, 0, 0, paint);
                        if (takeoff.hasNorthwestExit())
                            canvas.drawBitmap(markerNorthwestBitmap, 0, 0, paint);
                        if (takeoff.getPilotsToday() > 0)
                            canvas.drawBitmap(markerExclamation, 0, 0, paint);
                        else if (takeoff.getPilotsLater() > 0)
                            canvas.drawBitmap(markerExclamationYellow, 0, 0, paint);
                        String snippet = getString(R.string.height) + ": " + takeoff.getHeight() + "m\n" + getString(R.string.distance) + ": " + (int) location.distanceTo(takeoff.getLocation()) / 1000 + "km";
                        MarkerOptions markerOptions = new MarkerOptions().position(new LatLng(takeoff.getLocation().getLatitude(), takeoff.getLocation().getLongitude())).title(takeoff.getName()).snippet(snippet).icon(BitmapDescriptorFactory.fromBitmap(bitmap)).anchor(0.5f, 0.875f);
                        addMarkers.put(takeoff, markerOptions);
                    }
                }

                /* remove markers that we didn't "add" */
                return new Runnable() {
                    @Override
                    public void run() {
                        // remove markers that no longer should be visible
                        for (Iterator<Map.Entry<String, Pair<Marker, Takeoff>>> iterator = markers.entrySet().iterator(); iterator.hasNext(); ) {
                            Map.Entry<String, Pair<Marker, Takeoff>> entry = iterator.next();
                            if (!addMarkers.containsKey(entry.getValue().second)) {
                                entry.getValue().first.remove();
                                iterator.remove();
                            }
                        }
                        // add markers that should be visible
                        for (Map.Entry<Takeoff, MarkerOptions> addEntry : addMarkers.entrySet()) {
                            boolean alreadyShown = false;
                            for (Map.Entry<String, Pair<Marker, Takeoff>> entry : markers.entrySet()) {
                                if (addEntry.getKey().equals(entry.getValue().second)) {
                                    alreadyShown = true;
                                    break;
                                }
                            }
                            if (!alreadyShown) {
                                Marker marker = map.addMarker(addEntry.getValue());
                                Pair<Marker, Takeoff> pair = new Pair<>(marker, addEntry.getKey());
                                markers.put(marker.getId(), pair);
                            }
                        }
                    }
                };
            } catch (Exception e) {
                Log.w(getClass().getName(), "doInBackground() failed unexpectedly", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Runnable runnable) {
            if (runnable != null)
                runnable.run();
        }
    }

    private class DrawPolygonsTask extends AsyncTask<CameraPosition, Void, Runnable> {
        @Override
        protected Runnable doInBackground(CameraPosition... cameraPositions) {
            try {
                final Set<Airspace.Zone> showZones = new HashSet<>();
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                if (prefs.getBoolean("pref_map_show_airspace", true)) {
                    LatLng latLng = cameraPositions[0].target;
                    if (latLng.latitude != 0.0 && latLng.longitude != 0.0) {
                        location.setLatitude(latLng.latitude);
                        location.setLongitude(latLng.longitude);
                    }
                    Location tmpLocation = new Location(location);

                    for (Map.Entry<String, List<Airspace.Zone>> entry : Airspace.getAirspaceMap(getActivity()).entrySet()) {
                        if (entry.getKey() == null || !prefs.getBoolean("pref_airspace_enabled_" + entry.getKey().trim(), true))
                            continue;
                        for (Airspace.Zone zone : entry.getValue()) {
                            // show zones within (sort of) 100km
                            if (showPolygon(zone.getPolygon(), location, tmpLocation, 100000))
                                showZones.add(zone);
                        }
                    }
                }

                /* in case user disabled while we were figuring out which zones to show.
                 * it's not thread safe, so it's technically possibly to make it show zones even though it was disabled, but it's unlikely to happen */
                if (!prefs.getBoolean("pref_map_show_airspace", true))
                    showZones.clear();
                return new Runnable() {
                    @Override
                    public void run() {
                        drawAirspaceMap(showZones);
                    }
                };
            } catch (Exception e) {
                Log.w(getClass().getName(), "doInBackground() failed unexpectedly", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Runnable runnable) {
            if (runnable != null)
                runnable.run();
        }

        /**
         * Figure out whether to draw polygon or not. The parameters "myLocation" and "tmpLocation" are only used to prevent excessive allocations.
         *
         * @param polygon The polygon we want to figure out whether to draw or not.
         * @param myLocation Users current location.
         * @param tmpLocation Location object only used for determining distance from polygon points to user location.
         * @param maxAirspaceDistance User must be within a polygon or within this distance to one of the polygon points in order to be drawn.
         * @return Whether polygon should be drawn.
         */
        private boolean showPolygon(PolygonOptions polygon, Location myLocation, Location tmpLocation, int maxAirspaceDistance) {
            boolean userSouthOfNorthernmostPoint = false;
            boolean userNorthOfSouthernmostPoint = false;
            boolean userWestOfEasternmostPoint = false;
            boolean userEastOfWesternmostPoint = false;
            for (LatLng loc : polygon.getPoints()) {
                tmpLocation.setLatitude(loc.latitude);
                tmpLocation.setLongitude(loc.longitude);
                if (maxAirspaceDistance == 0 || myLocation.distanceTo(tmpLocation) < maxAirspaceDistance)
                    return true;
                if (myLocation.getLatitude() < loc.latitude)
                    userSouthOfNorthernmostPoint = true;
                else
                    userNorthOfSouthernmostPoint = true;
                if (myLocation.getLongitude() < loc.longitude)
                    userWestOfEasternmostPoint = true;
                else
                    userEastOfWesternmostPoint = true;
            }
            return userEastOfWesternmostPoint && userNorthOfSouthernmostPoint && userSouthOfNorthernmostPoint && userWestOfEasternmostPoint;
        }

        private void drawAirspaceMap(Set<Airspace.Zone> showZones) {
            try {
                /* remove zones that should not be visible */
                for (Iterator<Map.Entry<Pair<Polygon, Marker>, Airspace.Zone>> it = zones.entrySet().iterator(); it.hasNext();) {
                    Map.Entry<Pair<Polygon, Marker>, Airspace.Zone> entry = it.next();
                    if (showZones.contains(entry.getValue())) {
                        showZones.remove(entry.getValue()); // polygon already on map, no need to draw it again
                        continue;
                    }
                    // zone polygon is not to be shown, remove it
                    entry.getKey().first.remove();
                    // neither is marker for zone
                    entry.getKey().second.remove();
                    it.remove();
                }
                /* draw zones that should be visible */
                for (Airspace.Zone zone : showZones) {
                    Pair<Polygon, Marker> pair = new Pair<>(map.addPolygon(zone.getPolygon()), map.addMarker(zone.getMarker()));
                    zones.put(pair, zone);
                }
            } catch (Exception e) {
                Log.w(getClass().getName(), "drawAirspaceMap() failed unexpectedly", e);
            }
        }
    }
}
