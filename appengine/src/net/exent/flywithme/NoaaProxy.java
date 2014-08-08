package net.exent.flywithme;

import com.google.appengine.api.datastore.*;
import com.google.apphosting.api.ApiProxy;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by canidae on 6/13/14.
 */
public class NoaaProxy {
    private static final Logger log = Logger.getLogger(NoaaProxy.class.getName());

    private static final Key DATASTORE_METEOGRAMS_KEY = KeyFactory.createKey("FlyWithMe", "Meteograms");
    private static final Key DATASTORE_SOUNDINGS_KEY = KeyFactory.createKey("FlyWithMe", "Soundings");

    private static final long FORECAST_CACHE_LIFETIME = 21600000;

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
    static {
        // strange that this isn't possible in constructor. oh well
        metdateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private static String noaaUserId = "";
    private static String noaaMetcyc = "";
    private static String noaaMetdir = "";
    private static String noaaMetfil = "";
    private static List<String> noaaMetdates = new ArrayList<>();
    private static String noaaProc = "";
    private static String noaaCaptcha = "";

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
            log.info("Meteogram/sounding requested, likely without sending captcha data (using cached data instead): " + e);
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

    public static void cleanCache() {
        log.info("Cleaning NOAA forecast cache");
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        // remove expired meteogram images
        try {
            Entity meteograms = datastore.get(DATASTORE_METEOGRAMS_KEY);
            for (String remove : removeKeys(meteograms))
                meteograms.removeProperty(remove);
            datastore.put(meteograms);
        } catch (EntityNotFoundException e) {
            log.info("Not cleaning meteogram cache, key not found in datastore: " + e);
        }

        // remove expired sounding images
        try {
            Entity soundings = datastore.get(DATASTORE_SOUNDINGS_KEY);
            for (String remove : removeKeys(soundings))
                soundings.removeProperty(remove);
            datastore.put(soundings);
        } catch (EntityNotFoundException e) {
            log.info("Not cleaning sounding cache, key not found in datastore: " + e);
        }
    }

    private static Set<String> removeKeys(Entity entity) {
        Set<String> removes = new HashSet<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Object> meteogram : entity.getProperties().entrySet()) {
            EmbeddedEntity meteogramEmbeddedEntity = (EmbeddedEntity) meteogram.getValue();
            long retrievedTimestamp = (long) meteogramEmbeddedEntity.getProperty("retrieved");
            if (retrievedTimestamp + FORECAST_CACHE_LIFETIME > now)
                removes.add(meteogram.getKey());
        }
        return removes;
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
        if (noaaMetdates.isEmpty())
            return null;
        List<byte[]> forecasts = new ArrayList<>();
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        // fetch meteogram
        if (fetchMeteogram) {
            // check if we got a recent cached version first
            Entity meteogramsEntity;
            try {
                meteogramsEntity = datastore.get(DATASTORE_METEOGRAMS_KEY);
            } catch (EntityNotFoundException e) {
                // seems like our datastore doesn't have any cached meteograms
                log.log(Level.INFO, "No meteograms entity found in datastore, creating one");
                meteogramsEntity = new Entity(DATASTORE_METEOGRAMS_KEY);
            }
            String cacheKey = latitude + "," + longitude;
            byte[] meteogramImage = null;
            EmbeddedEntity meteogramEmbeddedEntity = (EmbeddedEntity) meteogramsEntity.getProperty(cacheKey);
            if (meteogramEmbeddedEntity != null) {
                long retrievedTimestamp = (long) meteogramEmbeddedEntity.getProperty("retrieved");
                if (retrievedTimestamp + FORECAST_CACHE_LIFETIME > System.currentTimeMillis()) {
                    // cached version isn't too old, use it
                    log.info("Using cached meteogram for [" + latitude + "," + longitude + "]");
                    meteogramImage = ((Blob) meteogramEmbeddedEntity.getProperty("image")).getBytes();
                }
            }
            // if we couldn't retrieve meteogram from cache, then we'll need to fetch a new one from NOAA
            if (meteogramImage == null) {
                String meteogramUrl = getOne(fetchPageContent(NOAA_URL + "/ready2-bin/metgram2.pl?userid=" + userId + "&Lat=" + latitude + "&Lon=" + longitude + "&metdir=" + noaaMetdir + "&metcyc=" + noaaMetcyc + "&metdate=" + URLEncoder.encode(noaaMetdates.get(0), "UTF-8") + "&metfil=" + noaaMetfil + "&password1=" + captcha + "&proc=" + proc + NOAA_METGRAM_CONF), NOAA_METEOGRAM_PATTERN);
                if (meteogramUrl == null)
                    return null;
                meteogramImage = fetchImage(NOAA_URL + meteogramUrl);
                if (meteogramImage == null)
                    return null;
                meteogramEmbeddedEntity = new EmbeddedEntity();
                meteogramEmbeddedEntity.setProperty("retrieved", System.currentTimeMillis());
                meteogramEmbeddedEntity.setProperty("image", new Blob(meteogramImage));
                meteogramsEntity.setProperty(cacheKey, meteogramEmbeddedEntity);
                log.info("Storing meteogram for [" + latitude + "," + longitude + "] (size: " + meteogramImage.length + ") in datastore");
                try {
                    // TODO: entity will grow beyond 1mb after a while and cause this exception. need to fix this somehow
                    datastore.put(meteogramsEntity);
                } catch (ApiProxy.RequestTooLargeException e) {
                    log.warning("Unable to cache meteogram, peculiar 'RequestTooLargeException' exception caught");
                }
            }
            forecasts.add(meteogramImage);
        }

        // fetch soundings
        Entity soundingsEntity;
        try {
            soundingsEntity = datastore.get(DATASTORE_SOUNDINGS_KEY);
        } catch (EntityNotFoundException e) {
            // seems like our datastore doesn't have any cached soundings
            log.log(Level.INFO, "No soundings entity found in datastore, creating one");
            soundingsEntity = new Entity(DATASTORE_SOUNDINGS_KEY);
        }
        for (long soundingTimestamp : soundingTimestamps) {
            String metdate = metdateFormatter.format(new Date(soundingTimestamp));
            log.info("Asked for sounding at timestamp: " + metdate);
            for (String noaaMetdate : noaaMetdates) {
                if (noaaMetdate.startsWith(metdate)) {
                    // check cache first
                    String cacheKey = latitude + "," + longitude + "-" + noaaMetdate;
                    byte[] soundingImage = null;
                    EmbeddedEntity soundingEmbeddedEntity = (EmbeddedEntity) soundingsEntity.getProperty(cacheKey);
                    if (soundingEmbeddedEntity != null) {
                        long retrievedTimestamp = (long) soundingEmbeddedEntity.getProperty("retrieved");
                        if (retrievedTimestamp + FORECAST_CACHE_LIFETIME > System.currentTimeMillis()) {
                            // cached version isn't too old, use it
                            log.info("Using cached sounding for [" + latitude + "," + longitude + "] at " + noaaMetdate);
                            soundingImage = ((Blob) soundingEmbeddedEntity.getProperty("image")).getBytes();
                        }
                    }
                    // if we couldn't retrieve sounding from cache, then we'll need to fetch a new one from NOAA
                    if (soundingImage == null) {
                        String soundingUrl = getOne(fetchPageContent(NOAA_URL + "/ready2-bin/profile2.pl?userid=" + userId + "&Lat=" + latitude + "&Lon=" + longitude + "&metdir=" + noaaMetdir + "&metcyc=" + noaaMetcyc + "&metdate=" + URLEncoder.encode(metdate, "UTF-8") + "&metfil=" + noaaMetfil + "&password1=" + captcha + "&proc=" + proc + NOAA_SOUNDING_CONF), NOAA_SOUNDING_PATTERN);
                        if (soundingUrl == null)
                            return null;
                        soundingImage = fetchImage(NOAA_URL + soundingUrl);
                        if (soundingImage == null)
                            return null;
                        soundingEmbeddedEntity = new EmbeddedEntity();
                        soundingEmbeddedEntity.setProperty("retrieved", System.currentTimeMillis());
                        soundingEmbeddedEntity.setProperty("image", new Blob(soundingImage));
                        soundingsEntity.setProperty(cacheKey, soundingEmbeddedEntity);
                        log.info("Storing sounding for [" + latitude + "," + longitude + "] at " + noaaMetdate + " (size: " + soundingImage.length + ") in datastore");
                        try {
                            // TODO: entity will grow beyond 1mb after a while and cause this exception. need to fix this somehow
                            datastore.put(soundingsEntity);
                        } catch (ApiProxy.RequestTooLargeException e) {
                            log.warning("Unable to cache sounding, peculiar 'RequestTooLargeException' exception caught");
                        }
                    }
                    forecasts.add(soundingImage);
                }
            }
        }
        return forecasts;
    }

    private static URLConnection fetchPage(String url) {
        log.info("Fetching page: " + url);
        try {
            URLConnection urlConnection = new URL(url).openConnection();
            urlConnection.setConnectTimeout(60000);
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
