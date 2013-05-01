package net.exent.flywithme;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.exent.flywithme.bean.Takeoff;
import net.exent.flywithme.data.Airspace;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ImageButton;

public class TakeoffMap extends Fragment implements OnInfoWindowClickListener {
    public interface TakeoffMapListener {
        void showTakeoffDetails(Takeoff takeoff);

        Location getLocation();

        List<Takeoff> getNearbyTakeoffs();
    }

    private static final int DEFAULT_MAX_AIRSPACE_DISTANCE = 100;
    private static View view;
    private static Map<String, Takeoff> takeoffMarkers = new HashMap<String, Takeoff>();
    private TakeoffMapListener callback;

    public void drawMap() {
        Log.d(getClass().getSimpleName(), "drawMap()");
        if (callback == null) {
            Log.w(getClass().getSimpleName(), "callback is null, returning");
            return;
        }
        SupportMapFragment fragment = (SupportMapFragment) getFragmentManager().findFragmentById(R.id.takeoffMapFragment);
        if (fragment == null)
            return;
        final GoogleMap map = fragment.getMap();
        if (map == null)
            return;
        /* need to do this here or it'll end up with a reference to an old instance of "this", somehow */
        map.setOnInfoWindowClickListener(this);
        /* add icons */
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        boolean showTakeoffs = prefs.getBoolean("pref_map_show_takeoffs", true);
        final ImageButton markerButton = (ImageButton) getActivity().findViewById(R.id.fragmentButton1);
        markerButton.setImageResource(showTakeoffs ? R.drawable.takeoffs_enabled : R.drawable.takeoffs_disabled);
        markerButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                boolean markersEnabled = !prefs.getBoolean("pref_map_show_takeoffs", true);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("pref_map_show_takeoffs", markersEnabled);
                editor.commit();
                markerButton.setImageResource(markersEnabled ? R.drawable.takeoffs_enabled : R.drawable.takeoffs_disabled);
                redrawMap(map);
            }
        });
        boolean showAirspace = prefs.getBoolean("pref_map_show_airspaces", true);
        final ImageButton polygonButton = (ImageButton) getActivity().findViewById(R.id.fragmentButton2);
        polygonButton.setImageResource(showAirspace ? R.drawable.airspace_enabled : R.drawable.airspace_disabled);
        polygonButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                boolean polygonsEnabled = !prefs.getBoolean("pref_map_show_airspace", true);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("pref_map_show_airspace", polygonsEnabled);
                editor.commit();
                polygonButton.setImageResource(polygonsEnabled ? R.drawable.airspace_enabled : R.drawable.airspace_disabled);
                redrawMap(map);
            }
        });
        /* draw map */
        redrawMap(map);
    }

    public void onInfoWindowClick(Marker marker) {
        Log.d(getClass().getSimpleName(), "onInfoWindowClick(" + marker + ")");
        if (callback == null) {
            Log.w(getClass().getSimpleName(), "callback is null, returning");
            return;
        }
        Takeoff takeoff = takeoffMarkers.get(marker.getId());

        /* tell main activity to show takeoff details */
        callback.showTakeoffDetails(takeoff);
    }

    @Override
    public void onAttach(Activity activity) {
        Log.d(getClass().getSimpleName(), "onAttach(" + activity + ")");
        super.onAttach(activity);
        callback = (TakeoffMapListener) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(getClass().getSimpleName(), "onCreateView(" + inflater + ", " + container + ", " + savedInstanceState + ")");
        if (view != null) {
            ViewGroup parent = (ViewGroup) view.getParent();
            if (parent != null)
                parent.removeView(view);
        }
        try {
            boolean zoom = getFragmentManager().findFragmentById(R.id.takeoffMapFragment) == null;
            view = inflater.inflate(R.layout.takeoff_map, container, false);
            GoogleMap map = ((SupportMapFragment) getFragmentManager().findFragmentById(R.id.takeoffMapFragment)).getMap();
            map.setMyLocationEnabled(true);
            map.getUiSettings().setZoomControlsEnabled(false);
            if (zoom) {
                Location loc = callback.getLocation();
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(loc.getLatitude(), loc.getLongitude()), (float) 10.0));
            }
        } catch (InflateException e) {
            /* map is already there, just return view as it is */
        }
        return view;
    }

    @Override
    public void onStart() {
        Log.d(getClass().getSimpleName(), "onStart()");
        super.onStart();
        drawMap();
    }
    
    private void redrawMap(GoogleMap map) {
        map.clear();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        if (prefs.getBoolean("pref_map_show_takeoffs", true))
            drawTakeoffMarkers(map);
        if (prefs.getBoolean("pref_map_show_airspace", true))
            drawAirspaceMap(map);
    }

    private void drawTakeoffMarkers(GoogleMap map) {
        Log.d(getClass().getSimpleName(), "drawTakeoffMarkers(" + map + ")");
        takeoffMarkers.clear();
        List<Takeoff> takeoffs = callback.getNearbyTakeoffs();
        Bitmap markerBitmap = BitmapFactory.decodeResource(getResources(), R.raw.mapmarker);
        Bitmap markerNorthBitmap = BitmapFactory.decodeResource(getResources(), R.raw.mapmarker_octant_n);
        Bitmap markerNortheastBitmap = BitmapFactory.decodeResource(getResources(), R.raw.mapmarker_octant_ne);
        Bitmap markerEastBitmap = BitmapFactory.decodeResource(getResources(), R.raw.mapmarker_octant_e);
        Bitmap markerSoutheastBitmap = BitmapFactory.decodeResource(getResources(), R.raw.mapmarker_octant_se);
        Bitmap markerSouthBitmap = BitmapFactory.decodeResource(getResources(), R.raw.mapmarker_octant_s);
        Bitmap markerSouthwestBitmap = BitmapFactory.decodeResource(getResources(), R.raw.mapmarker_octant_sw);
        Bitmap markerWestBitmap = BitmapFactory.decodeResource(getResources(), R.raw.mapmarker_octant_w);
        Bitmap markerNorthwestBitmap = BitmapFactory.decodeResource(getResources(), R.raw.mapmarker_octant_nw);
        for (Takeoff takeoff : takeoffs) {
            Bitmap bitmap = Bitmap.createBitmap(markerBitmap.getWidth(), markerBitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint();
            canvas.drawBitmap(markerBitmap, 0, 0, null);
            if (takeoff.hasNorthExit())
                canvas.drawBitmap(markerNorthBitmap, 0, 0, null);
            if (takeoff.hasNortheastExit())
                canvas.drawBitmap(markerNortheastBitmap, 0, 0, null);
            if (takeoff.hasEastExit())
                canvas.drawBitmap(markerEastBitmap, 0, 0, null);
            if (takeoff.hasSoutheastExit())
                canvas.drawBitmap(markerSoutheastBitmap, 0, 0, null);
            if (takeoff.hasSouthExit())
                canvas.drawBitmap(markerSouthBitmap, 0, 0, null);
            if (takeoff.hasSouthwestExit())
                canvas.drawBitmap(markerSouthwestBitmap, 0, 0, null);
            if (takeoff.hasWestExit())
                canvas.drawBitmap(markerWestBitmap, 0, 0, null);
            if (takeoff.hasNorthwestExit())
                canvas.drawBitmap(markerNorthwestBitmap, 0, 0, null);
            Marker marker = map.addMarker(new MarkerOptions().position(new LatLng(takeoff.getLocation().getLatitude(), takeoff.getLocation().getLongitude())).title(takeoff.getName()).snippet("Height: " + takeoff.getHeight()).icon(BitmapDescriptorFactory.fromBitmap(bitmap)).anchor(0.5f, 0.875f));
            //Marker marker = map.addMarker(new MarkerOptions().position(new LatLng(takeoff.getLocation().getLatitude(), takeoff.getLocation().getLongitude())).title(takeoff.getName()).snippet("Height: " + takeoff.getHeight() + ", Start: " + takeoff.getStartDirections()).icon(BitmapDescriptorFactory.defaultMarker()));
            takeoffMarkers.put(marker.getId(), takeoff);
        }
    }

    private void drawAirspaceMap(GoogleMap map) {
        Log.d(getClass().getSimpleName(), "drawAirspaceMap(" + map + ")");
        Location myLocation = callback.getLocation();
        Location tmpLocation = new Location(myLocation);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        int maxAirspaceDistance = DEFAULT_MAX_AIRSPACE_DISTANCE;
        try {
            maxAirspaceDistance = Integer.parseInt(prefs.getString("pref_max_airspace_distance", "" + DEFAULT_MAX_AIRSPACE_DISTANCE));
        } catch (NumberFormatException e) {
            Log.w(getClass().getSimpleName(), "Unable to parse max airspace distance setting as integer", e);
        }
        maxAirspaceDistance *= 1000;

        for (Map.Entry<String, List<PolygonOptions>> entry : Airspace.getAirspaceMap().entrySet()) {
            if (entry.getKey() == null || prefs.getBoolean("pref_airspace_enabled_" + entry.getKey().trim(), true) == false)
                continue;
            for (PolygonOptions polygon : entry.getValue()) {
                if (showPolygon(polygon, myLocation, tmpLocation, maxAirspaceDistance))
                    map.addPolygon(polygon);
            }
        }
    }

    /**
     * Figure out whether to draw polygon or not. The parameters "myLocation" and "tmpLocation" are only used to prevent excessive allocations.
     * 
     * @param polygon
     *            The polygon we want to figure out whether to draw or not.
     * @param myLocation
     *            Users current location.
     * @param tmpLocation
     *            Location object only used for determining distance from polygon points to user location.
     * @param maxAirspaceDistance
     *            User must be within a polygon or within this distance to one of the polygon points in order to be drawn.
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
}
