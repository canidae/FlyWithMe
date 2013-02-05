package net.exent.flywithme;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.exent.flywithme.dao.Flightlog;
import net.exent.flywithme.data.Takeoff;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import android.app.Activity;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class TakeoffMap extends Fragment implements OnInfoWindowClickListener {
    public interface TakeoffMapListener {
        void showTakeoffDetails(Takeoff takeoff);

        Location getLocation();
    }

    private static View view;
    private TakeoffMapListener callback;
    private Map<String, Takeoff> takeoffMarkers = new HashMap<String, Takeoff>();

    public void drawMap() {
        Log.d("TakeoffMap", "drawMap()");
        SupportMapFragment fragment = (SupportMapFragment) getFragmentManager().findFragmentById(R.id.takeoffMapFragment);
        if (fragment == null)
            return;
        GoogleMap map = fragment.getMap();
        if (map == null)
            return;
        /* TODO: draw markers and stuff */
        map.clear();
        takeoffMarkers.clear();
        List<Takeoff> takeoffs = Flightlog.getTakeoffs();
        for (int i = 0; i < takeoffs.size(); i++) {
            Takeoff takeoff = takeoffs.get(i);
            Marker marker = map.addMarker(new MarkerOptions().position(new LatLng(takeoff.getLocation().getLatitude(), takeoff.getLocation().getLongitude())).title(takeoff.getName()).snippet("Height: " + takeoff.getHeight() + ", Start: " + takeoff.getStartDirections()).icon(BitmapDescriptorFactory.defaultMarker((float) 42)));
            takeoffMarkers.put(marker.getId(), takeoff);
        }
        /* need to do this here or it'll end up with a reference to an old instance of "this", somehow */
        map.setOnInfoWindowClickListener(this);
    }

    public void onInfoWindowClick(Marker marker) {
        Log.d("TakeoffMap", "onInfoWindowClick(" + marker + ")");
        Takeoff takeoff = takeoffMarkers.get(marker.getId());

        /* tell main activity to show takeoff details */
        callback.showTakeoffDetails(takeoff);
    }

    @Override
    public void onAttach(Activity activity) {
        Log.d("TakeoffMap", "onAttach(" + activity + ")");
        super.onAttach(activity);
        callback = (TakeoffMapListener) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d("TakeoffMap", "onCreateView(" + inflater + ", " + container + ", " + savedInstanceState + ")");
        if (view != null) {
            ViewGroup parent = (ViewGroup) view.getParent();
            if (parent != null)
                parent.removeView(view);
        }
        if (getFragmentManager().findFragmentById(R.id.takeoffMapFragment) == null) {
            view = inflater.inflate(R.layout.takeoff_map, container, false);
            GoogleMap map = ((SupportMapFragment) getFragmentManager().findFragmentById(R.id.takeoffMapFragment)).getMap();
            map.setMyLocationEnabled(true);
            map.getUiSettings().setZoomControlsEnabled(false);
            Location loc = callback.getLocation();
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(loc.getLatitude(), loc.getLongitude()), (float) 10.0));
        }
        return view;
    }

    @Override
    public void onStart() {
        Log.d("TakeoffMap", "onStart()");
        super.onStart();
        drawMap();
    }
}
