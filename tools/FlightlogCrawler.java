import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
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
	public static void crawl(DataOutputStream outputStream) {
		System.out.println("Crawling...");
		int takeoff = 0;
		int lastValidTakeoff = 0;
		boolean tryAgain = true;
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
					Pattern namePattern = Pattern.compile(".*<title>.* - .* - .* - (.*)</title>.*", Pattern.DOTALL);
					Matcher nameMatcher = namePattern.matcher(text);
					Pattern descriptionPattern = Pattern.compile(".*Description</td>.*('right'>|'left'></a>)(.*)</td></tr>.*Coordinates</td>.*", Pattern.DOTALL);
					Matcher descriptionMatcher = descriptionPattern.matcher(text);
					Pattern altitudePattern = Pattern.compile(".*Altitude</td><td bgcolor='white'>(\\d+) meters asl Top to bottom (\\d+) meters</td>.*", Pattern.DOTALL);
					Matcher altitudeMatcher = altitudePattern.matcher(text);
					Pattern coordPattern = Pattern.compile(".*Coordinates</td>.*DMS: ([NS]) (\\d+)&deg; (\\d+)&#039; (\\d+)&#039;&#039; &nbsp;([EW]) (\\d+)&deg; (\\d+)&#039; (\\d+)&#039;&#039;.*", Pattern.DOTALL);
					Matcher coordMatcher = coordPattern.matcher(text);
					
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
	
						System.out.println("[" + takeoff + "] " + takeoffName + " (ASL: " + aboveSeaLevel + ", Height: " + height + ") [" + latitude + "," + longitude + "]");
						outputStream.writeShort(takeoff);
						outputStream.writeUTF(takeoffName);
						outputStream.writeUTF(description);
						outputStream.writeShort(aboveSeaLevel);
						outputStream.writeShort(height);
						outputStream.writeFloat(latitude);
						outputStream.writeFloat(longitude);
						lastValidTakeoff = takeoff;
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
		DataOutputStream outputStream = new DataOutputStream(new FileOutputStream("flywithme.dat"));
		crawl(outputStream);
		outputStream.close();
	}
}
