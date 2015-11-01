package net.exent.flywithme.server.util;

import net.exent.flywithme.server.bean.Takeoff;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tool for crawling flightlog.org for takeoff information.
 */
public class FlightlogCrawler {
    private static final Logger log = Logger.getLogger(FlightlogCrawler.class.getName());

    private static final String TAKEOFF_URL = "http://flightlog.org/fl.html?l=1&a=22&country_id=160&start_id=";
    private static final Pattern NAME_PATTERN = Pattern.compile(".*<title>.* - .* - .* - (.*)</title>.*", Pattern.DOTALL);
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile(".*Description</td>.*>([^>]*)</td></tr>.*Coordinates</td>.*", Pattern.DOTALL);
    private static final Pattern ALTITUDE_PATTERN = Pattern.compile(".*Altitude</td><td bgcolor='white'>(\\d+) meters asl Top to bottom (\\d+) meters</td>.*", Pattern.DOTALL);
    private static final Pattern COORD_PATTERN = Pattern.compile(".*Coordinates</td>.*DMS: ([NS]) (\\d+)&deg; (\\d+)&#039; (\\d+)&#039;&#039; &nbsp;([EW]) (\\d+)&deg; (\\d+)&#039; (\\d+)&#039;&#039;.*", Pattern.DOTALL);
    private static final Pattern WINDPAI_PATTERN = Pattern.compile(".*<img src='fl_b5/windpai\\.html\\?[^']*' alt='([^']*)'.*", Pattern.DOTALL);

    private static final String UPDATED_URL = "http://flightlog.org/?returntype=xml&rqtid=12&d=";
    private static final Pattern UPDATED_ID_PATTERN = Pattern.compile("<id>(\\d+|<!\\[CDATA\\[(\\d+)]]>)</id>", Pattern.DOTALL);

    public static List<Long> fetchUpdatedTakeoffs(long days) {
        List<Long> takeoffIds = new ArrayList<>();
        try {
            URL url = new URL(UPDATED_URL + days);
            HttpURLConnection httpUrlConnection = (HttpURLConnection) url.openConnection();
            if (httpUrlConnection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                log.warning("Whoops, not good! Response code " + httpUrlConnection.getResponseCode() + " when fetching list of takeoffs updated within the last " + days + " days");
                return takeoffIds;
            }
            String text = getPageContent(httpUrlConnection);
            Matcher idMatcher = UPDATED_ID_PATTERN.matcher(text);
            while (idMatcher.find()) {
                Long id = null;
                try {
                    id = Long.parseLong(idMatcher.group(1));
                } catch (NumberFormatException e) {
                    try {
                        id = Long.parseLong(idMatcher.group(2));
                    } catch (NumberFormatException e2) {
                        log.warning("Unable to retrive takeoff ID from string: " + idMatcher.group());
                    }
                }
                if (id != null)
                    takeoffIds.add(id);
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Unable to fetch list of updated takeoffs within the last " + days + " days", e);
        }
        return takeoffIds;
    }

    public static Takeoff fetchTakeoff(long takeoffId) {
        try {
            URL url = new URL(TAKEOFF_URL + takeoffId);
            HttpURLConnection httpUrlConnection = (HttpURLConnection) url.openConnection();
            if (httpUrlConnection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                log.warning("Whoops, not good! Response code " + httpUrlConnection.getResponseCode() + " when fetching takeoff with ID " + takeoffId);
                return null;
            }
            String text = getPageContent(httpUrlConnection);
            Matcher nameMatcher = NAME_PATTERN.matcher(text);
            Matcher descriptionMatcher = DESCRIPTION_PATTERN.matcher(text);
            Matcher altitudeMatcher = ALTITUDE_PATTERN.matcher(text);
            Matcher coordMatcher = COORD_PATTERN.matcher(text);
            Matcher windpaiMatcher = WINDPAI_PATTERN.matcher(text);

            if (nameMatcher.matches() && coordMatcher.matches()) {
                String takeoffName = nameMatcher.group(1).trim();
                String description = "";
                if (descriptionMatcher.matches())
                    description = descriptionMatcher.group(1).replace("<br />", "").trim();
                int aboveSeaLevel = 0;
                int height = 0;
                if (altitudeMatcher.matches()) {
                    aboveSeaLevel = Integer.parseInt(altitudeMatcher.group(1).trim());
                    height = Integer.parseInt(altitudeMatcher.group(2).trim());
                }

                String northOrSouth = coordMatcher.group(1);
                int latDeg = Integer.parseInt(coordMatcher.group(2));
                int latMin = Integer.parseInt(coordMatcher.group(3));
                int latSec = Integer.parseInt(coordMatcher.group(4));
                float latitude;
                latitude = (float) latDeg + (float) (latMin * 60 + latSec) / (float) 3600;
                if ("S".equals(northOrSouth))
                    latitude *= -1.0;

                String eastOrWest = coordMatcher.group(5);
                int lonDeg = Integer.parseInt(coordMatcher.group(6));
                int lonMin = Integer.parseInt(coordMatcher.group(7));
                int lonSec = Integer.parseInt(coordMatcher.group(8));
                float longitude;
                longitude = (float) lonDeg + (float) (lonMin * 60 + lonSec) / (float) 3600;
                if ("W".equals(eastOrWest))
                    longitude *= -1.0;

                String windpai = "";
                if (windpaiMatcher.matches())
                    windpai = windpaiMatcher.group(1).trim();

                long currentTime = System.currentTimeMillis();
                Takeoff takeoff = new Takeoff();
                takeoff.setId(takeoffId).setName(takeoffName).setAsl(aboveSeaLevel).setHeight(height).setLatitude(latitude).setLongitude(longitude);
                takeoff.setDescription(description).setWindpai(windpai).setLastUpdated(currentTime).setLastChecked(currentTime);
                return takeoff;
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Unable to fetch takeoff with ID " + takeoffId, e);
        }
        return null;
    }

    private static String getPageContent(HttpURLConnection httpUrlConnection) throws IOException {
        String charset = getCharsetFromHeaderValue(httpUrlConnection.getContentType());
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(httpUrlConnection.getInputStream(), charset), 32768);
        char[] buffer = new char[32768];
        int read;
        while ((read = br.read(buffer)) != -1)
            sb.append(buffer, 0, read);
        br.close();

        return sb.toString();
    }

    private static String getCharsetFromHeaderValue(String text) {
        int start = text.indexOf("charset=");
        if (start >= 0) {
            start += 8;
            int end = text.indexOf(";", start);
            int pos = text.indexOf(" ", start);
            if (end == -1 || (pos != -1 && pos < end))
                end = pos;
            pos = text.indexOf("\n", start);
            if (end == -1 || (pos != -1 && pos < end))
                end = pos;
            if (end == -1)
                end = text.length();
            if (text.charAt(start) == '"' && text.charAt(end - 1) == '"') {
                ++start;
                --end;
            }
            return text.substring(start, end);
        }
        return "iso-8859-1";
    }

    /*
     * http://flightlog.org/fl.html?l=1&a=22&country_id=160&start_id=4
     * we can set "country_id" to a fixed value, it only means that wrong country will be displayed (which we don't care about)
     */
    private static void crawl(DataOutputStream outputStream, PrintWriter kmlWriter) throws IOException {
        System.out.println("Crawling...");
        int takeoffId = 0;
        int lastValidTakeoff = 0;
        kmlWriter.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        kmlWriter.println("<kml xmlns=\"http://www.opengis.net/kml/2.2\">");
        kmlWriter.println("<Document>");
        outputStream.writeLong(System.currentTimeMillis());
        while (takeoffId++ < lastValidTakeoff + 50) { // when we haven't found a takeoff within the last 50 fetches from flightlog, assume all is found
            Takeoff takeoff = fetchTakeoff((long) takeoffId);
            if (takeoff == null)
                takeoff = fetchTakeoff((long) takeoffId); // try again if it failed
            if (takeoff == null)
                continue;
            System.out.println("[" + takeoffId + "] " + takeoff.getName() + " (ASL: " + takeoff.getAsl() + ", Height: " + takeoff.getHeight() + ") [" + takeoff.getLatitude() + "," + takeoff.getLongitude() + "] <" + takeoff.getWindpai() + ">");
            outputStream.writeShort(takeoffId);
            outputStream.writeUTF(takeoff.getName());
            outputStream.writeUTF(takeoff.getDescription());
            outputStream.writeShort(takeoff.getAsl());
            outputStream.writeShort(takeoff.getHeight());
            outputStream.writeFloat(takeoff.getLatitude());
            outputStream.writeFloat(takeoff.getLongitude());
            outputStream.writeUTF(takeoff.getWindpai());
            lastValidTakeoff = takeoffId;

            kmlWriter.print("<Placemark>");
            // just in case some smartass wrote "]]>" in takeoff name
            kmlWriter.print("<name><![CDATA[" + takeoff.getName().replace("]]>", "]] >") + "]]></name>");
            kmlWriter.print("<description><![CDATA[");
            kmlWriter.print("<h1>" + takeoff.getName().replace("]]>", "]] >") + "</h1>");
            kmlWriter.print("<h2>Takeoff directions: " + takeoff.getWindpai() + "</h2>");
            kmlWriter.print("<h2>Above Sea Level: " + takeoff.getAsl() + ", Height: " + takeoff.getHeight() + "</h2>");
            // just in case some smartass wrote "]]>" in description
            kmlWriter.print("<p>" + takeoff.getDescription().replace("]]>", "]] >") + "</p>");
            kmlWriter.print("]]></description>");
            kmlWriter.print("<Point>");
            // bloody americans, why do you have to do everything backwards? latitude usually comes before longitude...
            kmlWriter.print("<coordinates>" + takeoff.getLongitude() + "," + takeoff.getLatitude() + "</coordinates>");
            kmlWriter.print("</Point>");
            kmlWriter.println("</Placemark>");
        }
        kmlWriter.println("</Document>");
        kmlWriter.println("</kml>");
        System.out.println("Done crawling");
    }

    public static void main(String... args) throws Exception {
        PrintWriter kmlWriter = new PrintWriter("takeoffs.kml");
        DataOutputStream outputStream = new DataOutputStream(new FileOutputStream("flywithme.dat"));
        crawl(outputStream, kmlWriter);
        outputStream.close();
        kmlWriter.close();

        /* test flywithme.dat by reading it (had some unexplainable issues where the file somehow got corrupted) */
        DataInputStream inputStream = new DataInputStream(new FileInputStream("flywithme.dat"));
        try {
            long imported = inputStream.readLong();
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
            }
        } catch (EOFException e) {
            // expected
        }
    }
}
