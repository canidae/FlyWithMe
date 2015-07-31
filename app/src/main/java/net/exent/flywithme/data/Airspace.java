package net.exent.flywithme.data;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.exent.flywithme.FlyWithMe;
import net.exent.flywithme.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;

import android.content.Context;
import android.util.Log;

/* TODO (low):
 * periodically (every month or so) fetch http://grytnes-it.no/pg/luftrom/luftrom.kml
 * ask hans cato to make this url permanently pointing to the most updated version of the overlay
 * add marker icons, but we need to figure out how to store these first
 */

public class Airspace {
    private static Map<String, List<Zone>> airspaceMap = new HashMap<>();

    public static Map<String, List<Zone>> getAirspaceMap() {
        if (airspaceMap.isEmpty())
            readAirspaceMapFile(FlyWithMe.getInstance());
        return airspaceMap;
    }

    private static void readAirspaceMapFile(Context context) {
        airspaceMap = new HashMap<>();
        InputStream inputStream = context.getResources().openRawResource(R.raw.airspace);
        try {
            MapsInitializer.initialize(context);
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(inputStream, null);
            Map<String, PolygonOptions> polygonStyles = new HashMap<>();
            String folder = "";
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() != XmlPullParser.START_TAG)
                    continue;

                if ("Style".equals(parser.getName())) {
                    polygonStyles.put(parser.getAttributeValue(null, "id"), fetchPolygonStyle(parser));
                } else if ("Folder".equals(parser.getName()) && parser.nextTag() == XmlPullParser.START_TAG && "name".equals(parser.getName())) {
                    folder = parser.nextText().trim();
                } else if ("Placemark".equals(parser.getName())) {
                    Zone zone = fetchZone(polygonStyles, parser, context);
                    if (zone != null) {
                        if (!airspaceMap.containsKey(folder))
                            airspaceMap.put(folder, new ArrayList<Zone>());
                        airspaceMap.get(folder).add(zone);
                    }
                }
            }
            inputStream.close();
        } catch (XmlPullParserException e) {
            Log.w("Airspace", "Error parsing airspace map file", e);
        } catch (IOException e) {
            Log.w("Airspace", "Error reading airspace map file", e);
        }
    }

    private static PolygonOptions fetchPolygonStyle(XmlPullParser parser) throws XmlPullParserException, IOException {
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

    private static Zone fetchZone(Map<String, PolygonOptions> polygonStyles, XmlPullParser parser, Context context) throws XmlPullParserException, IOException {
        String styleId = null;
        String zoneName = null;
        String zoneDescription = null;
        PolygonOptions polygon = null;
        MarkerOptions marker = null;
        while (parser.next() != XmlPullParser.END_DOCUMENT && !"Placemark".equals(parser.getName())) {
            if (parser.getEventType() != XmlPullParser.START_TAG)
                continue;
            if ("styleUrl".equals(parser.getName())) {
                styleId = parser.nextText().substring(1);
            } else if ("name".equals(parser.getName())) {
                zoneName = parser.nextText();
            } else if ("description".equals(parser.getName())) {
                zoneDescription = parser.nextText().replaceAll("(\\r|\\n|<a.*</a>)", "").replaceAll("<br/?>", "\n").trim();
            } else if ("coordinates".equals(parser.getName())) {
                String coordinates[] = parser.nextText().trim().split("\\r?\\n");
                if (coordinates.length == 1) {
                    String coordinate = coordinates[0];
                    int pos = coordinate.indexOf(',');
                    if (pos <= 0) {
                        Log.w("Airspace", "Unable to parse marker coordinate: " + coordinate);
                        continue;
                    }
                    marker = new MarkerOptions()
                            .position(new LatLng(Double.parseDouble(coordinate.substring(pos + 1)), Double.parseDouble(coordinate.substring(0, pos))))
                            .title(zoneName)
                            .snippet(zoneDescription)
                            .anchor(0.5f, 0.875f);
                    int iconId = context.getResources().getIdentifier("airspace_" + styleId, "drawable", context.getPackageName());
                    if (iconId > 0)
                        marker.icon(BitmapDescriptorFactory.fromResource(iconId));
                    else
                        Log.w("Airspace", "Unable to find icon for zone: " + styleId);
                }
                if (coordinates.length < 3)
                    continue; // not enough coordinates to draw a polygon

                PolygonOptions style = polygonStyles.get(styleId);
                polygon = new PolygonOptions()
                        .strokeWidth(style.getStrokeWidth())
                        .strokeColor(style.getStrokeColor())
                        .fillColor(style.getFillColor());
                for (String coordinate : coordinates) {
                    int pos = coordinate.indexOf(',');
                    if (pos <= 0) {
                        Log.w("Airspace", "Unable to parse coordinate: " + coordinate);
                        continue;
                    }
                    polygon.add(new LatLng(Double.parseDouble(coordinate.substring(pos + 1)), Double.parseDouble(coordinate.substring(0, pos))));
                }
            }
        }

        if (polygon != null && marker != null)
            return new Zone(polygon, marker);
        return null;
    }

    /**
     * Convert ABGR to ARGB. KML-file use ABGR, but we need ARGB when drawing.
     *
     * @param color
     *            8 character string representing an ABGR color.
     * @return int value for ARGB color.
     */
    private static int convertColor(String color) {
        long abgr = Long.parseLong(color, 16);
        long alpha = (abgr >> 24) & 0xff;
        long red = (abgr >> 16) & 0xff;
        long green = (abgr >> 8) & 0xff;
        long blue = (abgr >> 0) & 0xff;
        return (int) ((alpha << 24) | (blue << 16) | (green << 8) | (red << 0));
    }

    public static class Zone {
        private PolygonOptions polygon;
        private MarkerOptions marker;

        public Zone(PolygonOptions polygon, MarkerOptions marker) {
            this.polygon = polygon;
            this.marker = marker;
        }

        public PolygonOptions getPolygon() {
            return polygon;
        }

        public MarkerOptions getMarker() {
            return marker;
        }
    }
}
