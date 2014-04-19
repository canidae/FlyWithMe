import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Compile & run this class manually to crawl flightlog.org and create a data file with all takeoffs.
 * The file produced is then copied to res/raw/flightlog.dat.
 */
public class FlightlogCrawler {
    /*
     * http://flightlog.org/fl.html?l=1&a=22&country_id=160&start_id=4
     * we can set "country_id" to a fixed value, it only means that wrong country will be displayed (which we don't care about)
     */
    public static void crawl(DataOutputStream outputStream, PrintWriter kmlWriter) {
        System.out.println("Crawling...");
        Pattern namePattern = Pattern.compile(".*<title>.* - .* - .* - (.*)</title>.*", Pattern.DOTALL);
        Pattern descriptionPattern = Pattern.compile(".*Description</td>.*('right'>|'left'></a>)(.*)</td></tr>.*Coordinates</td>.*", Pattern.DOTALL);
        Pattern altitudePattern = Pattern.compile(".*Altitude</td><td bgcolor='white'>(\\d+) meters asl Top to bottom (\\d+) meters</td>.*", Pattern.DOTALL);
        Pattern coordPattern = Pattern.compile(".*Coordinates</td>.*DMS: ([NS]) (\\d+)&deg; (\\d+)&#039; (\\d+)&#039;&#039; &nbsp;([EW]) (\\d+)&deg; (\\d+)&#039; (\\d+)&#039;&#039;.*", Pattern.DOTALL);
        Pattern windpaiPattern = Pattern.compile(".*<img src='fl_b5/windpai\\.html\\?[^']*' alt='([^']*)'.*", Pattern.DOTALL);
        int takeoff = 0;
        int lastValidTakeoff = 0;
        boolean tryAgain = true;
        kmlWriter.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        kmlWriter.println("<kml xmlns=\"http://www.opengis.net/kml/2.2\">");
        kmlWriter.println("<Document>");
        while (takeoff++ < lastValidTakeoff + 50) { // when we haven't found a takeoff within the last 50 fetches from flightlog, assume all is found
            try {
                URL url = new URL("http://flightlog.org/fl.html?l=1&a=22&country_id=160&start_id=" + takeoff);
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

                        System.out.println("[" + takeoff + "] " + takeoffName + " (ASL: " + aboveSeaLevel + ", Height: " + height + ") [" + latitude + "," + longitude + "] <" + windpai + ">");
                        outputStream.writeShort(takeoff);
                        outputStream.writeUTF(takeoffName);
                        outputStream.writeUTF(description);
                        outputStream.writeShort(aboveSeaLevel);
                        outputStream.writeShort(height);
                        outputStream.writeFloat(latitude);
                        outputStream.writeFloat(longitude);
                        outputStream.writeUTF(windpai);
                        lastValidTakeoff = takeoff;

                        kmlWriter.print("<Placemark>");
                        // just in case some smartass wrote "]]>" in takeoff name
                        takeoffName = takeoffName.replace("]]>", "]] >");
                        kmlWriter.print("<name><![CDATA[" + takeoffName + "]]></name>");
                        kmlWriter.print("<description><![CDATA[");
                        kmlWriter.print("<h1>" + takeoffName + "</h1>");
                        kmlWriter.print("<h2>Takeoff directions: " + windpai + "</h2>");
                        kmlWriter.print("<h2>Above Sea Level: " + aboveSeaLevel + ", Height: " + height + "</h2>");
                        // just in case some smartass wrote "]]>" in description
                        description = description.replace("]]>", "]] >");
                        kmlWriter.print("<p>" + description + "</p>");
                        kmlWriter.print("]]></description>");
                        kmlWriter.print("<Point>");
                        // bloody americans, why do you have to do everything backwards? latitude usually comes before longitude...
                        kmlWriter.print("<coordinates>" + longitude + "," + latitude + "</coordinates>");
                        kmlWriter.print("</Point>");
                        kmlWriter.println("</Placemark>");
                    }
                    break;

                default:
                    System.out.println("Whoops, not good! Response code " + httpUrlConnection.getResponseCode() + " when fetching takeoff with ID " + takeoff);
                    break;
                }
                tryAgain = true;
            } catch (Exception e) {
                /* try one more time if we get an exception */
                if (tryAgain)
                    --takeoff;
                else
                    e.printStackTrace();
                tryAgain = false;
            }
        }
        kmlWriter.println("</Document>");
        kmlWriter.println("</kml>");
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

    public static void main(String... args) throws Exception {
        PrintWriter kmlWriter = new PrintWriter("takeoffs.kml");
        DataOutputStream outputStream = new DataOutputStream(new FileOutputStream("flywithme.dat"));
        crawl(outputStream, kmlWriter);
        outputStream.close();
        kmlWriter.close();

        /* test flywithme.dat by reading it (had some unexplainable issues where the file somehow got corrupted) */
        DataInputStream inputStream = new DataInputStream(new FileInputStream("flywithme.dat"));
        try {
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
