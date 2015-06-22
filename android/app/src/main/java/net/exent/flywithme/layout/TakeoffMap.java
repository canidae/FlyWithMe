package net.exent.flywithme.layout;

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

import android.app.Activity;
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

public class TakeoffMap extends Fragment implements OnInfoWindowClickListener, OnCameraChangeListener, OnMapReadyCallback {

    public interface TakeoffMapListener {
        void showTakeoffDetails(Takeoff takeoff);

        Location getLocation();
    }

    private static CameraPosition cameraPosition;
    private GoogleMap map;

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
    private TakeoffMapListener callback;

    public void drawMap() {
        if (callback == null) {
            Log.w(getClass().getName(), "callback is null, returning");
            return;
        }
        try {
            //final GoogleMap map = ((MapFragment) getChildFragmentManager().findFragmentById(R.id.takeoffMapFragment)).getMap();
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
                public void onClick(View v) {
                    boolean markersEnabled = !prefs.getBoolean("pref_map_show_takeoffs", true);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean("pref_map_show_takeoffs", markersEnabled);
                    editor.commit();
                    markerButton.setImageResource(markersEnabled ? R.mipmap.takeoffs_enabled : R.mipmap.takeoffs_disabled);
                    drawOverlay(map.getCameraPosition());
                }
            });
            boolean showAirspace = prefs.getBoolean("pref_map_show_airspaces", true);
            final ImageButton polygonButton = (ImageButton) getActivity().findViewById(R.id.fragmentButton2);
            polygonButton.setImageResource(showAirspace ? R.mipmap.airspace_enabled : R.mipmap.airspace_disabled);
            polygonButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    boolean polygonsEnabled = !prefs.getBoolean("pref_map_show_airspace", true);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean("pref_map_show_airspace", polygonsEnabled);
                    editor.commit();
                    polygonButton.setImageResource(polygonsEnabled ? R.mipmap.airspace_enabled : R.mipmap.airspace_disabled);
                    drawOverlay(map.getCameraPosition());
                }
            });
            ((ImageButton) getActivity().findViewById(R.id.fragmentButton3)).setImageDrawable(null);
            /* draw overlay */
            drawOverlay(map.getCameraPosition());
        } catch (Exception e) {
            Log.w(getClass().getName(), "drawMap() task failed unexpectedly", e);
        }
    }

    @Override
    public void onMapReady(GoogleMap map) {
        this.map = map;
        this.map.setMyLocationEnabled(true);
        drawMap();
    }

    public void onInfoWindowClick(Marker marker) {
        if (callback == null) {
            Log.w(getClass().getName(), "callback is null, returning");
            return;
        }
        /* tell main activity to show takeoff details */
        Pair<Marker, Takeoff> pair = markers.get(marker.getId());
        if (pair != null)
            callback.showTakeoffDetails(pair.second);
        else
            Log.w(getClass().getName(), "Strange, could not find takeoff for marker");
    }

    public void onCameraChange(CameraPosition cameraPosition) {
        TakeoffMap.cameraPosition = cameraPosition;
        drawOverlay(cameraPosition);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        callback = (TakeoffMapListener) activity;
        if (markerBitmap == null) {
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
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.takeoff_map, container, false);
        GoogleMapOptions mapOptions = new GoogleMapOptions();
        mapOptions.zoomControlsEnabled(false);
        if (cameraPosition != null) {
            mapOptions.camera(cameraPosition);
        } else {
            Location loc = callback.getLocation();
            mapOptions.camera(new CameraPosition(new LatLng(loc.getLatitude(), loc.getLongitude()), 10.0f, 0.0f, 0.0f));
        }
        MapFragment mapFragment = MapFragment.newInstance(mapOptions);
        mapFragment.getMapAsync(this);
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.replace(R.id.takeoffMapLayout, mapFragment).commit();
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    private void drawOverlay(CameraPosition cameraPosition) {
        try {
            new DrawPolygonsTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, cameraPosition);
            new DrawMarkersTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, cameraPosition);
        } catch (Exception e) {
            Log.w(getClass().getName(), "redrawMap() failed unexpectedly", e);
        }
    }

    private class DrawMarkersTask extends AsyncTask<CameraPosition, Object, Void> {
        @Override
        protected Void doInBackground(CameraPosition... cameraPositions) {
            try {
                /* clone markers & save visible takeoffs, so we know which markers to remove later */
                Map<Takeoff, String> visibleTakeoffs = new HashMap<>();
                for (Map.Entry<String, Pair<Marker, Takeoff>> entry : markers.entrySet())
                    visibleTakeoffs.put(entry.getValue().second, entry.getKey());

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                if (prefs.getBoolean("pref_map_show_takeoffs", true)) {
                    Location location = callback.getLocation();
                    LatLng latLng = cameraPositions[0].target;
                    if (latLng.latitude != 0.0 && latLng.longitude != 0.0) {
                        location.setLatitude(latLng.latitude);
                        location.setLongitude(latLng.longitude);
                    }

                    /* get the 25 nearest takeoffs */
                    List<Takeoff> takeoffs = new Database(getActivity()).getTakeoffs(location.getLatitude(), location.getLongitude(), 25, false);

                    /* add markers */
                    for (Takeoff takeoff : takeoffs) {
                        if (visibleTakeoffs.containsKey(takeoff)) {
                            visibleTakeoffs.remove(takeoff);
                            continue; // takeoff already shown
                        }
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
                        String snippet = getString(R.string.height) + ": " + takeoff.getHeight() + "m\n" + getString(R.string.distance) + ": " + (int) FlyWithMe.getInstance().getLocation().distanceTo(takeoff.getLocation()) / 1000 + "km";
                        MarkerOptions markerOptions = new MarkerOptions().position(new LatLng(takeoff.getLocation().getLatitude(), takeoff.getLocation().getLongitude())).title(takeoff.getName()).snippet(snippet).icon(BitmapDescriptorFactory.fromBitmap(bitmap)).anchor(0.5f, 0.875f);
                        publishProgress(takeoff, markerOptions);
                    }
                }

                /* remove markers that we didn't "add" */
                for (Map.Entry<Takeoff, String> entry : visibleTakeoffs.entrySet())
                    publishProgress(entry.getValue());
            } catch (Exception e) {
                Log.w(getClass().getName(), "doInBackground() failed unexpectedly", e);
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Object... objects) {
            try {
                if (objects[0] instanceof Takeoff) {
                    /* add marker */
                    Takeoff takeoff = (Takeoff) objects[0];
                    MarkerOptions markerOptions = (MarkerOptions) objects[1];
                    //GoogleMap map = ((MapFragment) getChildFragmentManager().findFragmentById(R.id.takeoffMapLayout)).getMap();
                    Marker marker = map.addMarker(markerOptions);
                    Pair<Marker, Takeoff> pair = new Pair<>(marker, takeoff);
                    markers.put(marker.getId(), pair);
                } else {
                    /* remove marker */
                    Marker marker = (markers.remove((String) objects[0])).first;
                    marker.remove();
                }
            } catch (Exception e) {
                Log.w(getClass().getName(), "onProgressUpdate() failed unexpectedly", e);
            }
        }
    }

    private class DrawPolygonsTask extends AsyncTask<CameraPosition, Void, Runnable> {
        @Override
        protected Runnable doInBackground(CameraPosition... cameraPositions) {
            try {
                final Set<Airspace.Zone> showZones = new HashSet<>();
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                if (prefs.getBoolean("pref_map_show_airspace", true)) {
                    Location location = callback.getLocation();
                    LatLng latLng = cameraPositions[0].target;
                    if (latLng.latitude != 0.0 && latLng.longitude != 0.0) {
                        location.setLatitude(latLng.latitude);
                        location.setLongitude(latLng.longitude);
                    }
                    Location tmpLocation = new Location(location);

                    for (Map.Entry<String, List<Airspace.Zone>> entry : Airspace.getAirspaceMap().entrySet()) {
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
                //GoogleMap map = ((MapFragment) getChildFragmentManager().findFragmentById(R.id.takeoffMapLayout)).getMap();
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
