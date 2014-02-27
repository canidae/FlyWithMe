package net.exent.flywithme;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO: maybe implement this at a later time
public class FlightlogDatabase {
    private static final Logger log = Logger.getLogger(FlightlogDatabase.class.getName());
    private static final long crawlSleepTimeMs = 2628000000L; // 1000 * 60 * 60 * 24 * 365 / 12;

    private static Map<Integer, Takeoff> takeoffs = new ConcurrentHashMap<>();
    private static long lastCrawl = System.currentTimeMillis() + crawlSleepTimeMs;
    private static volatile boolean crawling = false;

    /* Update takeoff map upon initialization. */
    static {
        updateTakeoffMap();
    }

    /* Private constructor, this is a singleton and shouldn't be instantiated. */
    private FlightlogDatabase() {
    }

    public static Takeoff getTakeoff(int takeoffId) {
        updateTakeoffMap();
        return takeoffs.get(takeoffId);
    }

    /* Update takeoff map (if necessary). */
    private static synchronized void updateTakeoffMap() {
        if (System.currentTimeMillis() < lastCrawl + crawlSleepTimeMs) {
            crawling = true;
            lastCrawl = System.currentTimeMillis();
            // takeoff map needs to be updated
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    crawl();
                    crawling = false;
                }
            });
            thread.run();
        }
    }

    /*
     * http://flightlog.org/fl.html?l=1&a=22&country_id=160&start_id=4
     * we must set "country_id" to a fixed value, it only means that wrong country will be displayed (which we don't care about)
     */
    private static void crawl() {
        log.info("Crawling...");
        Pattern namePattern = Pattern.compile(".*<title>.* - .* - .* - (.*)</title>.*", Pattern.DOTALL);
        Pattern descriptionPattern = Pattern.compile(".*Description</td>.*('right'>|'left'></a>)(.*)</td></tr>.*Coordinates</td>.*", Pattern.DOTALL);
        Pattern altitudePattern = Pattern.compile(".*Altitude</td><td bgcolor='white'>(\\d+) meters asl Top to bottom (\\d+) meters</td>.*", Pattern.DOTALL);
        Pattern coordPattern = Pattern.compile(".*Coordinates</td>.*DMS: ([NS]) (\\d+)&deg; (\\d+)&#039; (\\d+)&#039;&#039; &nbsp;([EW]) (\\d+)&deg; (\\d+)&#039; (\\d+)&#039;&#039;.*", Pattern.DOTALL);
        Pattern windpaiPattern = Pattern.compile(".*<img src='fl_b5/windpai\\.html\\?[^']*' alt='([^']*)'.*", Pattern.DOTALL);
        int takeoffId = 0;
        int lastValidTakeoff = 0;
        boolean tryAgain = true;
        while (takeoffId++ < lastValidTakeoff + 50) { // when we haven't found a takeoff within the last 50 fetches from flightlog, assume all is found
            try {
                URL url = new URL("http://flightlog.org/fl.html?l=1&a=22&country_id=160&start_id=" + takeoffId);
                HttpURLConnection httpUrlConnection = (HttpURLConnection) url.openConnection();
                switch (httpUrlConnection.getResponseCode()) {
                    case HttpURLConnection.HTTP_OK:
                        String charset = getCharsetFromHeaderValue(httpUrlConnection.getContentType());
                        StringBuilder sb = new StringBuilder();
                        BufferedReader br = new BufferedReader(new InputStreamReader(httpUrlConnection.getInputStream(), charset), 32768);
                        char[] buffer = new char[32768];
                        int read;
                        while ((read = br.read(buffer)) != -1)
                            sb.append(buffer, 0, read);
                        br.close();

                        String text = sb.toString();
                        Matcher nameMatcher = namePattern.matcher(text);
                        Matcher descriptionMatcher = descriptionPattern.matcher(text);
                        Matcher altitudeMatcher = altitudePattern.matcher(text);
                        Matcher coordMatcher = coordPattern.matcher(text);
                        Matcher windpaiMatcher = windpaiPattern.matcher(text);

                        if (nameMatcher.matches() && coordMatcher.matches()) {
                            String takeoffName = nameMatcher.group(1).trim();
                            String description = "";
                            if (descriptionMatcher.matches())
                                description = descriptionMatcher.group(2).replace("<br />", "").trim();
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
                            float latitude = 0;
                            latitude = (float) latDeg + (float) (latMin * 60 + latSec) / (float) 3600;
                            if ("S".equals(northOrSouth))
                                latitude *= -1.0;

                            String eastOrWest = coordMatcher.group(5);
                            int lonDeg = Integer.parseInt(coordMatcher.group(6));
                            int lonMin = Integer.parseInt(coordMatcher.group(7));
                            int lonSec = Integer.parseInt(coordMatcher.group(8));
                            float longitude = 0;
                            longitude = (float) lonDeg + (float) (lonMin * 60 + lonSec) / (float) 3600;
                            if ("W".equals(eastOrWest))
                                longitude *= -1.0;

                            String windpai = "";
                            if (windpaiMatcher.matches())
                                windpai = windpaiMatcher.group(1).trim();

                            log.info("[" + takeoffId + "] " + takeoffName + " (ASL: " + aboveSeaLevel + ", Height: " + height + ") [" + latitude + "," + longitude + "] <" + windpai + ">");
                            lastValidTakeoff = takeoffId;

                            // add takeoff to map of takeoffs
                            takeoffs.put(takeoffId, new Takeoff(takeoffId, takeoffName, description, aboveSeaLevel, height, latitude, longitude, windpai));
                        }
                        break;

                    default:
                        log.warning("Whoops, not good! Response code " + httpUrlConnection.getResponseCode() + " when fetching takeoffId with ID " + takeoffId);
                        break;
                }
                tryAgain = true;
            } catch (Exception e) {
                /* try one more time if we get an exception */
                if (tryAgain)
                    --takeoffId;
                else
                    log.log(Level.SEVERE, "Unexpected exception caught", e);
                tryAgain = false;
            }
        }
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
}
