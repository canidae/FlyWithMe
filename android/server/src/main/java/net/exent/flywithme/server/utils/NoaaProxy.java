package net.exent.flywithme.server.utils;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

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

    private static SimpleDateFormat metdateFormatter = new SimpleDateFormat("MMMM dd, yyyy 'at' HH 'UTC'", Locale.US);
    static {
        metdateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private static String noaaUserId = "";
    private static String noaaMetCyc = "";
    private static String noaaMetDir = "";
    private static String noaaMetFil = "";
    private static List<String> noaaMetDates = new ArrayList<>();
    private static String noaaProc = "";
    private static String noaaCaptcha = "";

    /**
     * Fetch CAPTCHA image to solve before fetching meteogram and sounding.
     *
     * @param latitude The latitude of the location we want forecast for.
     * @param longitude The longitude of the location we want forecast for.
     * @return The CAPTCHA image to be solved.
     */
    public static byte[] fetchCaptchaImage(float latitude, float longitude) {
        try {
            noaaUserId = getOne(fetchPageContent(NOAA_URL + "/ready2-bin/main.pl?Lat=" + latitude + "&Lon=" + longitude), NOAA_USERID_PATTERN);
            String content = fetchPageContent(NOAA_URL + "/ready2-bin/metcycle.pl?product=metgram1&userid=" + noaaUserId + "&metdata=GFS&mdatacfg=GFS&Lat=" + latitude + "&Lon=" + longitude);
            noaaMetCyc = getOne(content, NOAA_METCYC_PATTERN).replace(' ', '+');
            content = fetchPageContent(NOAA_URL + "/ready2-bin/metgram1.pl?userid=" + noaaUserId + "&metdata=GFS&mdatacfg=GFS&Lat=" + latitude + "&Lon=" + longitude + "&metext=gfsf&metcyc=" + noaaMetCyc);
            noaaMetDir = getOne(content, NOAA_METDIR_PATTERN);
            noaaMetFil = getOne(content, NOAA_METFIL_PATTERN);
            noaaMetDates = getAll(content, NOAA_METDATE_PATTERN);
            noaaProc = getOne(content, NOAA_PROC_PATTERN);
            return fetchImage(NOAA_URL + getOne(content, NOAA_CAPTCHA_URL_PATTERN));
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed fetching CAPTCHA", e);
        }
        return null;
    }

    /**
     * Set the CAPTCHA text, value will be cached and used when fetching meteogram & sounding.
     *
     * @param captcha The CAPTCHA text.
     */
    public static void setCaptchaText(String captcha) {
        // TODO: CAPTCHA solver so we possibly don't need this method any more.
        NoaaProxy.noaaCaptcha = captcha;
    }

    /**
     * Fetch meteogram for a given location.
     *
     * @param latitude The latitude of the location we want forecast for.
     * @param longitude The longitude of the location we want forecast for.
     * @return The meteogram image.
     */
    public static byte[] fetchMeteogram(float latitude, float longitude) {
        try {
            String meteogramUrl = getOne(fetchPageContent(NOAA_URL + "/ready2-bin/metgram2.pl?userid=" + noaaUserId + "&Lat=" + latitude + "&Lon=" + longitude
                    + "&metdir=" + noaaMetDir + "&metcyc=" + noaaMetCyc + "&metdate=" + URLEncoder.encode(noaaMetDates.get(0), "UTF-8") + "&metfil=" + noaaMetFil
                    + "&password1=" + noaaCaptcha + "&proc=" + noaaProc + NOAA_METGRAM_CONF), NOAA_METEOGRAM_PATTERN);
            return fetchImage(NOAA_URL + meteogramUrl);
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed fetching meteogram", e);
        }
        return null;
    }

    /**
     * Fetch sounding for a given location and time.
     *
     * @param latitude The latitude of the location we want forecast for.
     * @param longitude The longitude of the location we want forecast for.
     * @param soundingTimestamp The timestamp we want sounding for, in milliseconds since epoch.
     * @return The sounding image.
     */
    public static byte[] fetchSounding(float latitude, float longitude, long soundingTimestamp) {
        try {
            String metDate = metdateFormatter.format(new Date(soundingTimestamp));
            for (String noaaMetdate : noaaMetDates) {
                if (!noaaMetdate.startsWith(metDate))
                    continue;
                String soundingUrl = getOne(fetchPageContent(NOAA_URL + "/ready2-bin/profile2.pl?userid=" + noaaUserId + "&Lat=" + latitude + "&Lon=" + longitude
                        + "&metdir=" + noaaMetDir + "&metcyc=" + noaaMetCyc + "&metdate=" + URLEncoder.encode(noaaMetdate, "UTF-8") + "&metfil=" + noaaMetFil
                        + "&password1=" + noaaCaptcha + "&proc=" + noaaProc + NOAA_SOUNDING_CONF), NOAA_SOUNDING_PATTERN);
                return fetchImage(NOAA_URL + soundingUrl);
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed fetching sounding", e);
        }
        return null;
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

    // TODO: remove, used for testing
    public static void main(String... argS) throws Exception {
        //solveCaptcha(Files.readAllBytes(new File("captcha.gif").toPath()));

        float latitude = (float) 10.8;
        float longitude = (float) 63.0;
        //for (int a = 0; a < 100; ++a) {
            byte[] captchaImage = fetchCaptchaImage(latitude, longitude);
        /*
            Files.write(new File("captchas", "" + System.currentTimeMillis()).toPath(), captchaImage);
            Thread.sleep(100);
        }
        */

        // TODO: solve CAPTCHA

        /*
        BufferedReader bufferRead = new BufferedReader(new InputStreamReader(System.in));
        setCaptchaText(bufferRead.readLine());
        byte[] meteogram = fetchMeteogram(latitude, longitude);
        byte[] sounding = fetchSounding(latitude, longitude, 1433160000000L);
        */
    }

    private static void solveCaptcha(byte[] captchaImage) throws Exception { // TODO: fix exception handling
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(captchaImage));
        BufferedImage image2 = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        System.out.println(image.getHeight() + ", " + image.getWidth());
        for (int y = 1; y < image.getHeight() - 1; ++y) {
            for (int x = 1; x < image.getWidth() - 1; ++x) {
                int v1 = image.getRGB(x, y);
                image2.setRGB(x, y, Integer.MAX_VALUE);
                if (v1 == -16777216) {
                    int v2 = image.getRGB(x - 1, y);
                    int v3 = image.getRGB(x, y - 1);
                    int v4 = image.getRGB(x + 1, y);
                    int v5 = image.getRGB(x, y + 1);
                    if (v1 == v2 && v2 == v3 && v3 == v4 && v4 == v5)
                        image2.setRGB(x, y, v1);
                }
                /*
                int rgb = image.getRGB(x, y);
                int red = (rgb >> 16) & 0xff;
                int green = (rgb >> 8) & 0xff;
                int blue = rgb & 0xff;
                if (red == 0 && green == 0 && blue == 0) {
                    image2.setRGB(x, y, rgb);
                } else {
                    image2.setRGB(x, y, Integer.MAX_VALUE);
                }
                */
            }
        }
        ImageIO.write(image2, "bmp", new File("captcha.bmp"));
    }
}
