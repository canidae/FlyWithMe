package net.exent.flywithme.data;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.exent.flywithme.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolygonOptions;

import android.content.Context;
import android.util.Log;

public class Airspace {
    private static Map<String, List<PolygonOptions>> polygonMap = new HashMap<String, List<PolygonOptions>>();

    public static Map<String, List<PolygonOptions>> getAirspaceMap() {
        Log.d("Airspace", "getAirspaceMap()");
        return polygonMap;
    }

    public static void init(Context context) {
        Log.d("Airspace", "init(" + context + ")");
        if (polygonMap.isEmpty())
            readAirspaceMapFile(context);
    }

    private static void readAirspaceMapFile(Context context) {
        polygonMap = new HashMap<String, List<PolygonOptions>>();
        InputStream inputStream = context.getResources().openRawResource(R.raw.airspace_map);
        try {
            Log.d("Airspace", "inputStream.available(): " + inputStream.available());
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(inputStream, null);
            Map<String, PolygonOptions> polygonStyles = new HashMap<String, PolygonOptions>();
            String folder = "";
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() != XmlPullParser.START_TAG)
                    continue;

                if ("Style".equals(parser.getName())) {
                    polygonStyles.put(parser.getAttributeValue(null, "id"), fetchPolygonStyle(parser));
                } else if ("Folder".equals(parser.getName()) && parser.nextTag() == XmlPullParser.START_TAG && "name".equals(parser.getName())) {
                    folder = parser.nextText().trim();
                } else if ("Placemark".equals(parser.getName())) {
                    PolygonOptions polygon = fetchPolygon(polygonStyles, parser);
                    if (polygon != null) {
                        if (!polygonMap.containsKey(folder))
                            polygonMap.put(folder, new ArrayList<PolygonOptions>());
                        polygonMap.get(folder).add(polygon);
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

    private static PolygonOptions fetchPolygon(Map<String, PolygonOptions> polygonStyles, XmlPullParser parser) throws XmlPullParserException, IOException {
        String styleId = null;
        while (parser.next() != XmlPullParser.END_DOCUMENT && !"Placemark".equals(parser.getName())) {
            if (parser.getEventType() != XmlPullParser.START_TAG)
                continue;
            if ("styleUrl".equals(parser.getName())) {
                styleId = parser.nextText().substring(1);
            } else if ("coordinates".equals(parser.getName())) {
                String coordinates[] = parser.nextText().trim().split("\\r?\\n");
                if (coordinates.length < 3)
                    continue; // not enough coordinates to draw a polygon

                PolygonOptions style = polygonStyles.get(styleId);
                PolygonOptions polygon = new PolygonOptions();
                polygon.strokeWidth(style.getStrokeWidth());
                polygon.strokeColor(style.getStrokeColor());
                polygon.fillColor(style.getFillColor());
                for (String coordinate : coordinates) {
                    int pos = coordinate.indexOf(',');
                    if (pos <= 0) {
                        Log.w("Airspace", "Unable to parse coordinate: " + coordinate);
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
}
