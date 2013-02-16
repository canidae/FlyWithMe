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
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

public class TakeoffMap extends Fragment implements OnInfoWindowClickListener {
    public interface TakeoffMapListener {
        void showTakeoffDetails(Takeoff takeoff);

        Location getLocation();

        List<Takeoff> getNearbyTakeoffs();
    }

    private static final int MAX_TAKEOFFS = 100;
    private static View view;
    private static Map<String, Takeoff> takeoffMarkers = new HashMap<String, Takeoff>();
    private TakeoffMapListener callback;

    public void drawMap() {
        Log.d(getClass().getSimpleName(), "drawMap()");
        SupportMapFragment fragment = (SupportMapFragment) getFragmentManager().findFragmentById(R.id.takeoffMapFragment);
        if (fragment == null)
            return;
        GoogleMap map = fragment.getMap();
        if (map == null)
            return;
        /* need to do this here or it'll end up with a reference to an old instance of "this", somehow */
        map.setOnInfoWindowClickListener(this);
        /* add icons */
        ImageButton markerButton = (ImageButton) getActivity().findViewById(R.id.fragmentButton1);
        markerButton.setImageResource(R.drawable.marker_enabled);
        ImageButton polygonButton = (ImageButton) getActivity().findViewById(R.id.fragmentButton2);
        polygonButton.setImageResource(R.drawable.polygons_enabled);
        /* draw map */
        map.clear();
        drawTakeoffMarkers(map); // TODO: async?
        drawAirspaceMap(map); // TODO: async!
    }

    public void onInfoWindowClick(Marker marker) {
        Log.d(getClass().getSimpleName(), "onInfoWindowClick(" + marker + ")");
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

    private void drawTakeoffMarkers(GoogleMap map) {
        Log.d(getClass().getSimpleName(), "drawTakeoffMarkers(" + map + ")");
        takeoffMarkers.clear();
        List<Takeoff> takeoffs = callback.getNearbyTakeoffs();
        for (int i = 0; i < takeoffs.size() && i < MAX_TAKEOFFS; i++) {
            Takeoff takeoff = takeoffs.get(i);
            Marker marker = map.addMarker(new MarkerOptions().position(new LatLng(takeoff.getLocation().getLatitude(), takeoff.getLocation().getLongitude())).title(takeoff.getName()).snippet("Height: " + takeoff.getHeight() + ", Start: " + takeoff.getStartDirections()).icon(BitmapDescriptorFactory.defaultMarker((float) 42)));
            takeoffMarkers.put(marker.getId(), takeoff);
        }
    }

    private void drawAirspaceMap(GoogleMap map) {
        Log.d(getClass().getSimpleName(), "drawAirspaceMap(" + map + ")");
        Location myLocation = callback.getLocation();
        Location tmpLocation = new Location(myLocation);
        for (Map.Entry<String, List<PolygonOptions>> entry : Airspace.getAirspaceMap().entrySet()) {
            // TODO: if folder not enabled, skip
            for (PolygonOptions polygon : entry.getValue()) {
                // TODO: always draw everything or just stuff nearby?
                if (showPolygon(polygon, myLocation, tmpLocation))
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
     * @return Whether polygon should be drawn.
     */
    private boolean showPolygon(PolygonOptions polygon, Location myLocation, Location tmpLocation) {
        boolean userSouthOfNorthernmostPoint = false;
        boolean userNorthOfSouthernmostPoint = false;
        boolean userWestOfEasternmostPoint = false;
        boolean userEastOfWesternmostPoint = false;
        for (LatLng loc : polygon.getPoints()) {
            tmpLocation.setLatitude(loc.latitude);
            tmpLocation.setLongitude(loc.longitude);
            if (myLocation.distanceTo(tmpLocation) < 200000)
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
