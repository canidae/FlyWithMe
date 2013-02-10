package net.exent.flywithme;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import net.exent.flywithme.data.Takeoff;

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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class TakeoffMap extends Fragment implements OnInfoWindowClickListener {
    public interface TakeoffMapListener {
        void showTakeoffDetails(Takeoff takeoff);

        Location getLocation();

        List<Takeoff> getNearbyTakeoffs();
    }

    private static final int MAX_TAKEOFFS = 100;
    private static View view;
    private TakeoffMapListener callback;
    private Map<String, Takeoff> takeoffMarkers = new HashMap<String, Takeoff>();

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
        /* draw map */
        map.clear();
        drawTakeoffMarkers(map);
        drawAirspaceMap(map);
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
        InputStream inputStream = getActivity().getResources().openRawResource(R.raw.airspace_map);
        try {
            Log.d(getClass().getSimpleName(), "inputStream.available(): " + inputStream.available());
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(inputStream, null);
            Map<String, PolygonOptions> polygonStyles = new HashMap<String, PolygonOptions>();
            int polygons = 1000;
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                switch (parser.getEventType()) {
                case XmlPullParser.START_TAG:
                    if ("Style".equals(parser.getName())) {
                        polygonStyles.put(parser.getAttributeValue(null, "id"), fetchPolygonStyle(parser));
                    } else if ("Placemark".equals(parser.getName())) {
                        if (polygons >= 0) {
                            PolygonOptions polygon = fetchPolygon(polygonStyles, parser);
                            if (polygon != null) {
                                map.addPolygon(polygon);
                                polygons--;
                            }
                        }
                    }
                    break;
                }
            }
            inputStream.close();
        } catch (XmlPullParserException e) {
            Log.w(getClass().getSimpleName(), "Error parsing airspace map file", e);
        } catch (IOException e) {
            Log.w(getClass().getSimpleName(), "Error reading airspace map file", e);
        }
    }

    private PolygonOptions fetchPolygonStyle(XmlPullParser parser) throws XmlPullParserException, IOException {
        Log.d(getClass().getSimpleName(), "fetchPolygonStyle(" + parser + ")");
        PolygonOptions style = new PolygonOptions();
        String block = "";
        while (parser.next() != XmlPullParser.END_DOCUMENT && !"Style".equals(parser.getName())) {
            if (parser.getEventType() != XmlPullParser.START_TAG)
                continue;
            if ("LineStyle".equals(parser.getName())) {
                block = "LineStyle";
            } else if ("PolyStyle".equals(parser.getName())) {
                block = "PolyStyle";
            } else if ("width".equals(parser.getName()) && "LineStyle".equals(block)) {
                style.strokeWidth(Float.parseFloat(parser.nextText()));
            } else if ("color".equals(parser.getName())) {
                if ("LineStyle".equals(block))
                    style.strokeColor(convertColor(parser.nextText()));
                else if ("PolyStyle".equals(block))
                    style.fillColor(convertColor(parser.nextText()));
                block = null;
            }
        }
        return style;
    }

    private PolygonOptions fetchPolygon(Map<String, PolygonOptions> polygonStyles, XmlPullParser parser) throws XmlPullParserException, IOException {
        Log.d(getClass().getSimpleName(), "fetchPolygon(" + polygonStyles + ", " + parser + ")");
        String styleId = null;
        while (parser.next() != XmlPullParser.END_DOCUMENT && !"Placemark".equals(parser.getName())) {
            if (parser.getEventType() != XmlPullParser.START_TAG)
                continue;
            if ("styleUrl".equals(parser.getName())) {
                styleId = parser.nextText().substring(1);
            } else if ("coordinates".equals(parser.getName())) {
                String coordinates[] = parser.nextText().trim().split("\\r?\\n");
                if (coordinates.length <= 1) {
                    // what now?
                    continue;
                }
                PolygonOptions style = polygonStyles.get(styleId);
                // TODO: what if style is not found?
                PolygonOptions polygon = new PolygonOptions();
                polygon.strokeWidth(style.getStrokeWidth());
                polygon.strokeColor(style.getStrokeColor());
                polygon.fillColor(style.getFillColor());
                for (String coordinate : coordinates) {
                    int pos = coordinate.indexOf(',');
                    if (pos <= 0) {
                        // what now?
                        continue;
                    }
                    polygon.add(new LatLng(Double.parseDouble(coordinate.substring(pos + 1)), Double.parseDouble(coordinate.substring(0, pos))));
                }
                return polygon;
            }
        }
        return null;
    }
    
    /**
     * Convert ABGR to ARGB. KML-file use ABGR, but we need ARGB when drawing.
     * @param color 8 character string representing an ABGR color.
     * @return int value for ARGB color.
     */
    private int convertColor(String color) {
        long abgr = Long.parseLong(color, 16);
        long alpha = (abgr >> 24) & 0xFF;
        long red = (abgr >> 16) & 0xFF;
        long green = (abgr >> 8) & 0xFF;
        long blue = (abgr >> 0) & 0xFF;
        return (int) ((alpha << 24) | (blue << 16) | (green << 8) | (red << 0));
    }
}
