package net.exent.flywithme;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by canidae on 6/13/14.
 */
public class NoaaProxy {
    private static final Logger log = Logger.getLogger(NoaaProxy.class.getName());

    private static final String NOAA_URL = "http://www.ready.noaa.gov";
    private static final String NOAA_METGRAM_CONF = "&metdata=GFS&mdatacfg=GFS&metext=gfsf&nhrs=96&type=user&wndtxt=2&Field1=FLAG&Level1=0&Field2=FLAG&Level2=5&Field3=FLAG&Level3=7&Field4=FLAG&Level4=9&Field5=TCLD&Level5=0&Field6=MSLP&Level6=0&Field7=T02M&Level7=0&Field8=TPP6&Level8=0&Field9=%20&Level9=0&Field10=%20&Level10=0&textonly=No&gsize=96&pdf=No";
    private static final String NOAA_SOUNDING_CONF = "&type=0&nhrs=24&hgt=0&textonly=No&skewt=1&gsize=96&pdf=No";
    private static final Pattern NOAA_METCYC_PATTERN = Pattern.compile(".*</div><option value=\"(\\d+ \\d+)\">.*");
    private static final Pattern NOAA_USERID_PATTERN = Pattern.compile(".*userid=(\\d+).*");
    private static final Pattern NOAA_METDIR_PATTERN = Pattern.compile(".*<input type=\"HIDDEN\" name=\"metdir\" value=\"([^\"]+)\">.*");
    private static final Pattern NOAA_METFIL_PATTERN = Pattern.compile(".*<input type=\"HIDDEN\" name=\"metfil\" value=\"([^\"]+)\">.*");
    private static final Pattern NOAA_METDATE_PATTERN = Pattern.compile("<option>([^<]*\\(\\+ \\d+ Hrs\\))");
    private static final Pattern NOAA_PROC_PATTERN = Pattern.compile(".*<input type=\"HIDDEN\" name=\"proc\" value=\"(\\d+)\">.*");
    private static final Pattern NOAA_CAPTCHA_URL_PATTERN = Pattern.compile(".*<img src=\"([^\"]+)\" ALT=\"Security Code\".*");
    private static final Pattern NOAA_METEOGRAM_PATTERN = Pattern.compile(".*<img src=\"([^\"]+)\" ALT=\"meteorogram\">.*");
    private static final Pattern NOAA_SOUNDING_PATTERN = Pattern.compile(".*<IMG SRC=\"([^\"]+)\" ALT=\"Profile\">.*");

    private static SimpleDateFormat metdateFormatter = new SimpleDateFormat("MMMM dd, yyyy 'at' HH 'UTC'");

    private static String noaaUserId;
    private static String noaaMetcyc;
    private static String noaaMetdir;
    private static String noaaMetfil;
    private static List<String> noaaMetdates;
    private static String noaaProc;
    private static String noaaCaptcha;


    public static synchronized int getMeteogramAndSounding(final DataInputStream inputStream, final DataOutputStream outputStream) throws IOException {
        // read in request parameters
        float latitude = inputStream.readFloat();
        float longitude = inputStream.readFloat();
        boolean fetchMeteogram = inputStream.readBoolean();
        int soundings = inputStream.readUnsignedByte();
        long[] soundingTimestamps = new long[soundings];
        for (int i = 0; i < soundings; ++i)
            soundingTimestamps[i] = inputStream.readInt() * 1000L; // timestamps are sent as seconds since epoch, we need milliseconds

        // let's try to read in captcha data from request
        try {
            int userId = inputStream.readShort();
            int proc = inputStream.readShort();
            String captcha = inputStream.readUTF();

            List<byte[]> images = fetchForecasts("" + userId, "" + proc, captcha, latitude, longitude, fetchMeteogram, soundingTimestamps);
            if (images == null)
                throw new RuntimeException("Unable to fetch meteogram/sounding, wrong captcha?");
            noaaUserId = "" + userId;
            noaaProc = "" + proc;
            noaaCaptcha = captcha;
            return writeSuccessResponse(outputStream, images);
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Something not right with supplied captcha data", e);
        } catch (UnsupportedEncodingException e) {
            log.log(Level.WARNING, "Unexpected exception", e);
        } catch (IOException e) {
            // user did likely not send captcha data (this will happen for most of the requests), continue using cached data
            log.log(Level.INFO, "Meteogram/sounding requested, likely without sending captcha data (using cached data instead)", e);
        }

        List<byte[]> images = fetchForecasts(noaaUserId, noaaProc, noaaCaptcha, latitude, longitude, fetchMeteogram, soundingTimestamps);
        if (images != null)
            return writeSuccessResponse(outputStream, images);

        // if we've come this far, then captcha is likely expired
        // update noaa data and send captcha for user to solve (along with userId and proc, which user must return)
        String userId = getOne(fetchPageContent(NOAA_URL + "/ready2-bin/main.pl?Lat=" + latitude + "&Lon=" + longitude), NOAA_USERID_PATTERN);
        String content = fetchPageContent(NOAA_URL + "/ready2-bin/metcycle.pl?product=metgram1&userid=" + userId + "&metdata=GFS&mdatacfg=GFS&Lat=" + latitude + "&Lon=" + longitude);
        noaaMetcyc = getOne(content, NOAA_METCYC_PATTERN);
        noaaMetcyc = noaaMetcyc.replace(' ', '+');
        content = fetchPageContent(NOAA_URL + "/ready2-bin/metgram1.pl?userid=" + userId + "&metdata=GFS&mdatacfg=GFS&Lat=" + latitude + "&Lon=" + longitude + "&metext=gfsf&metcyc=" + noaaMetcyc);
        noaaMetdir = getOne(content, NOAA_METDIR_PATTERN);
        noaaMetfil = getOne(content, NOAA_METFIL_PATTERN);
        noaaMetdates = getAll(content, NOAA_METDATE_PATTERN);
        String proc = getOne(content, NOAA_PROC_PATTERN);
        byte[] captchaImage = fetchImage(NOAA_URL + getOne(content, NOAA_CAPTCHA_URL_PATTERN));
        outputStream.writeByte(0);
        outputStream.writeShort(Integer.parseInt(userId));
        outputStream.writeShort(Integer.parseInt(proc));
        outputStream.writeInt(captchaImage.length);
        outputStream.write(captchaImage);

        return HttpServletResponse.SC_OK;
    }

    private static int writeSuccessResponse(final DataOutputStream outputStream, List<byte[]> images) {
        try {
            outputStream.writeByte(1);
            outputStream.writeByte(images.size());
            for (byte[] image : images) {
                outputStream.writeInt(image.length);
                outputStream.write(image);
            }
            return HttpServletResponse.SC_OK;
        } catch (IOException e) {
            log.log(Level.WARNING, "Unable to write response", e);
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
    }

    private static List<byte[]> fetchForecasts(String userId, String proc, String captcha, float latitude, float longitude, boolean fetchMeteogram, long[] soundingTimestamps) throws UnsupportedEncodingException {
        // TODO: cache meteogram/sounding images
        List<byte[]> forecasts = new ArrayList<>();
        if (fetchMeteogram) {
            String meteogramUrl = getOne(fetchPageContent(NOAA_URL + "/ready2-bin/metgram2.pl?userid=" + userId + "&Lat=" + latitude + "&Lon=" + longitude + "&metdir=" + noaaMetdir + "&metcyc=" + noaaMetcyc + "&metdate=" + URLEncoder.encode(noaaMetdates.get(0), "UTF-8") + "&metfil=" + noaaMetfil + "&password1=" + captcha + "&proc=" + proc + NOAA_METGRAM_CONF), NOAA_METEOGRAM_PATTERN);
            if (meteogramUrl == null)
                return null;
            byte[] meteogram = fetchImage(NOAA_URL + meteogramUrl);
            if (meteogram == null)
                return null;
            forecasts.add(meteogram);
        }
        for (long soundingTimestamp : soundingTimestamps) {
            String metdate = metdateFormatter.format(new Date(soundingTimestamp));
            for (String noaaMetdate : noaaMetdates) {
                if (noaaMetdate.startsWith(metdate)) {
                    String soundingUrl = getOne(fetchPageContent(NOAA_URL + "/ready2-bin/profile2.pl?userid=" + userId + "&Lat=" + latitude + "&Lon=" + longitude + "&metdir=" + noaaMetdir + "&metcyc=" + noaaMetcyc + "&metdate=" + URLEncoder.encode(metdate, "UTF-8") + "&metfil=" + noaaMetfil + "&password1=" + captcha + "&proc=" + proc + "&type=0&nhrs=24&hgt=1&textonly=No&skewt=1&gsize=96&pdf=No"), NOAA_SOUNDING_PATTERN);
                    if (soundingUrl == null)
                        return null;
                    byte[] sounding = fetchImage(NOAA_URL + soundingUrl);
                    if (sounding == null)
                        return null;
                    forecasts.add(sounding);
                }
            }
        }
        return forecasts;
    }

    private static URLConnection fetchPage(String url) {
        log.info("Fetching page: " + url);
        try {
            URLConnection urlConnection = new URL(url).openConnection();
            urlConnection.connect();
            return urlConnection;
        } catch (Exception e) {
            /* try one more time before giving up */
            try {
                URLConnection urlConnection = new URL(url).openConnection();
                urlConnection.connect();
                return urlConnection;
            } catch (Exception e2) {
                log.log(Level.WARNING, "Unable to fetch page: " + url, e);
            }
        }
        return null;
    }

    private static String fetchPageContent(String url) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(fetchPage(url).getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null)
                sb.append(line);
            in.close();
            return sb.toString();
        } catch (Exception e) {
            log.log(Level.WARNING, "Unable to fetch page content", e);
        }
        return null;
    }

    private static String getOne(String text, Pattern pattern) {
        if (text == null || pattern == null)
            return null;
        Matcher matcher = pattern.matcher(text);
        if (matcher.matches())
            return matcher.group(1);
        return null;
    }

    private static List<String> getAll(String text, Pattern pattern) {
        List<String> result = new ArrayList<>();
        if (text == null || pattern == null)
            return result;
        Matcher matcher = pattern.matcher(text);
        while (matcher.find())
            result.add(matcher.group(1));
        return result;
    }

    private static byte[] fetchImage(String url) {
        try {
            URLConnection response = fetchPage(url);
            InputStream in = response.getInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1)
                baos.write(buffer, 0, read);
            return baos.toByteArray();
        } catch (Exception e) {
            log.log(Level.WARNING, "Unable to fetch image", e);
        }
        return null;
    }
}
