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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private static final int CAPTCHA_Y_OFFSET = 38;
    private static final int BLACK = -16777216;

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
    private static String noaaCaptcha;

    private static Map<Character, byte[]> captchaCharacters;
    static {
        try {
            captchaCharacters = new HashMap<>();
            //File directory = new File("server/src/main/webapp/captcha_bitmaps");
            File directory = new File("captcha_bitmaps");
            for (File file : directory.listFiles()) {
                BufferedImage image = ImageIO.read(file);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                for (int y = 0; y < image.getHeight(); ++y) {
                    for (int x = 0; x < image.getWidth(); ++x) {
                        if (image.getRGB(x, y) == BLACK) {
                            baos.write(x);
                            baos.write(y);
                        }
                    }
                }
                captchaCharacters.put(file.getName().toLowerCase().charAt(0), baos.toByteArray());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Fetch CAPTCHA image to solve before fetching meteogram and sounding.
     *
     * @param latitude The latitude of the location we want forecast for.
     * @param longitude The longitude of the location we want forecast for.
     * @return The CAPTCHA image to be solved.
     */
    public static void updateFieldsAndCaptcha(float latitude, float longitude) {
        try {
            noaaUserId = getOne(fetchPageContent(NOAA_URL + "/ready2-bin/main.pl?Lat=" + latitude + "&Lon=" + longitude), NOAA_USERID_PATTERN);
            String content = fetchPageContent(NOAA_URL + "/ready2-bin/metcycle.pl?product=metgram1&userid=" + noaaUserId + "&metdata=GFS&mdatacfg=GFS&Lat=" + latitude + "&Lon=" + longitude);
            noaaMetCyc = getOne(content, NOAA_METCYC_PATTERN).replace(' ', '+');
            content = fetchPageContent(NOAA_URL + "/ready2-bin/metgram1.pl?userid=" + noaaUserId + "&metdata=GFS&mdatacfg=GFS&Lat=" + latitude + "&Lon=" + longitude + "&metext=gfsf&metcyc=" + noaaMetCyc);
            noaaMetDir = getOne(content, NOAA_METDIR_PATTERN);
            noaaMetFil = getOne(content, NOAA_METFIL_PATTERN);
            noaaMetDates = getAll(content, NOAA_METDATE_PATTERN);
            noaaProc = getOne(content, NOAA_PROC_PATTERN);
            noaaCaptcha = solveCaptcha(fetchImage(NOAA_URL + getOne(content, NOAA_CAPTCHA_URL_PATTERN)));
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed fetching CAPTCHA image", e);
        }
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
            if (noaaCaptcha == null)
                updateFieldsAndCaptcha(latitude, longitude);
            String meteogramUrl = getOne(fetchPageContent(NOAA_URL + "/ready2-bin/metgram2.pl?userid=" + noaaUserId + "&Lat=" + latitude + "&Lon=" + longitude
                    + "&metdir=" + noaaMetDir + "&metcyc=" + noaaMetCyc + "&metdate=" + URLEncoder.encode(noaaMetDates.get(0), "UTF-8") + "&metfil=" + noaaMetFil
                    + "&password1=" + noaaCaptcha + "&proc=" + noaaProc + NOAA_METGRAM_CONF), NOAA_METEOGRAM_PATTERN);
            for (int a = 0; a < 3; ++a) {
                byte[] meteogramImage = fetchImage(NOAA_URL + meteogramUrl);
                if (meteogramImage == null) {
                    log.info("No meteogram image returned, updating cached data and trying to solve new captcha");
                    updateFieldsAndCaptcha(latitude, longitude);
                } else {
                    return meteogramImage;
                }
            }
            return null;
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed fetching meteogram image", e);
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
            if (noaaCaptcha == null)
                updateFieldsAndCaptcha(latitude, longitude);
            String metDate = metdateFormatter.format(new Date(soundingTimestamp));
            for (String noaaMetdate : noaaMetDates) {
                if (!noaaMetdate.startsWith(metDate))
                    continue;
                String soundingUrl = getOne(fetchPageContent(NOAA_URL + "/ready2-bin/profile2.pl?userid=" + noaaUserId + "&Lat=" + latitude + "&Lon=" + longitude
                        + "&metdir=" + noaaMetDir + "&metcyc=" + noaaMetCyc + "&metdate=" + URLEncoder.encode(noaaMetdate, "UTF-8") + "&metfil=" + noaaMetFil
                        + "&password1=" + noaaCaptcha + "&proc=" + noaaProc + NOAA_SOUNDING_CONF), NOAA_SOUNDING_PATTERN);
                for (int a = 0; a < 3; ++a) {
                    byte[] soundingImage = fetchImage(NOAA_URL + soundingUrl);
                    if (soundingImage == null) {
                        log.info("No sounding image returned, updating cached data and trying to solve new captcha");
                        updateFieldsAndCaptcha(latitude, longitude);
                    } else {
                        return soundingImage;
                    }
                }
                return null;
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed fetching sounding image", e);
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
        /*
        int correct = 0;
        int wrong = 0;
        File directory = new File("captchas");
        for (File file : directory.listFiles()) {
            if (file.isDirectory())
                continue;
            String captcha = solveCaptcha(Files.readAllBytes(file.toPath()));
            if (file.getName().equals(captcha)) {
                ++correct;
            } else {
                System.out.println("Wrong CAPTCHA, found '" + captcha + "', expected '" + file.getName() + "'");
                ++wrong;
            }
        }
        System.out.println("Found correct CAPTCHA in " + correct + " out of " + (correct + wrong) + " images");
        */

        float latitude = (float) 10.8;
        float longitude = (float) 63.0;
        /*
        for (int a = 0; a < 100; ++a) {
            byte[] captchaImage = updateFieldsAndCaptcha(latitude, longitude);
            Files.write(new File("captchas", "" + System.currentTimeMillis()).toPath(), captchaImage);
            Thread.sleep(100);
        }
        */

        byte[] meteogram = fetchMeteogram(latitude, longitude);
        //Files.write(new File("meteogram." + System.currentTimeMillis()).toPath(), meteogram);
        byte[] sounding = fetchSounding(latitude, longitude, 1433678400000L);
        //Files.write(new File("sounding." + System.currentTimeMillis()).toPath(), sounding);
    }

    private static String solveCaptcha(byte[] captchaImage) throws Exception { // TODO: fix exception handling
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(captchaImage));
        int startX = 0;
        int stopX = image.getWidth() / 4;
        int goBackXOffset = 6;
        int goForwardXOffset = 12;
        List<CaptchaStringMatch> possibleMatches = new ArrayList<>();
        for (CaptchaCharacterMatch c1 : findNextPossibleCaptchaCharacters(image, startX, stopX)) {
            startX = c1.xOffset + c1.width - goBackXOffset;
            stopX = c1.xOffset + c1.width + goForwardXOffset;
            for (CaptchaCharacterMatch c2 : findNextPossibleCaptchaCharacters(image, startX, stopX)) {
                startX = c2.xOffset + c2.width - goBackXOffset;
                stopX = c2.xOffset + c2.width + goForwardXOffset;
                for (CaptchaCharacterMatch c3 : findNextPossibleCaptchaCharacters(image, startX, stopX)) {
                    startX = c3.xOffset + c3.width - goBackXOffset;
                    stopX = c3.xOffset + c3.width + goForwardXOffset;
                    for (CaptchaCharacterMatch c4 : findNextPossibleCaptchaCharacters(image, startX, stopX)) {
                        startX = c4.xOffset + c4.width - goBackXOffset;
                        stopX = c4.xOffset + c4.width + goForwardXOffset;
                        for (CaptchaCharacterMatch c5 : findNextPossibleCaptchaCharacters(image, startX, stopX)) {
                            startX = c5.xOffset + c5.width - goBackXOffset;
                            stopX = c5.xOffset + c5.width + goForwardXOffset;
                            for (CaptchaCharacterMatch c6 : findNextPossibleCaptchaCharacters(image, startX, stopX)) {
                                CaptchaStringMatch match = new CaptchaStringMatch();
                                match.captcha = c1.character + "" + c2.character + "" + c3.character + "" + c4.character + "" + c5.character + "" + c6.character;
                                match.matchingPixels = c1.matchingPixels + c2.matchingPixels + c3.matchingPixels + c4.matchingPixels + c5.matchingPixels + c6.matchingPixels;
                                match.totalPixels = c1.totalPixels + c2.totalPixels + c3.totalPixels + c4.totalPixels + c5.totalPixels + c6.totalPixels;
                                possibleMatches.add(match);
                            }
                        }
                    }
                }
            }
        }
        Collections.sort(possibleMatches, new Comparator<CaptchaStringMatch>() {
            @Override
            public int compare(CaptchaStringMatch c1, CaptchaStringMatch c2) {
                //return c2.matchingPixels - c1.matchingPixels;
                //return (c2.matchingPixels * 100000 / c2.totalPixels) - (c1.matchingPixels * 100000 / c1.totalPixels);
                return (c2.matchingPixels + c2.matchingPixels * 10000 / c2.totalPixels) - (c1.matchingPixels + c1.matchingPixels * 10000 / c1.totalPixels);
            }
        });
        //System.out.println("Possible matches: " + possibleMatches.size() + " | Best: " + (possibleMatches.size() >= 1 ? possibleMatches.get(0) : null) + " | Then: "  + (possibleMatches.size() >= 2 ? possibleMatches.get(1) : null));
        //ImageIO.write(image2, "bmp", new File("captcha.bmp"));
        return (possibleMatches.size() >= 1 ? possibleMatches.get(0).captcha : "");
    }

    private static List<CaptchaCharacterMatch> findNextPossibleCaptchaCharacters(BufferedImage image, int startX, int stopX) {
        List<CaptchaCharacterMatch> possibleCharacters = new ArrayList<>();
        int minXOffset = stopX;
        for (Map.Entry<Character, byte[]> entry : captchaCharacters.entrySet()) {
            byte[] blackPixels = entry.getValue();
            CaptchaCharacterMatch bestMatch = new CaptchaCharacterMatch();
            bestMatch.totalPixels = blackPixels.length / 2;
            for (int xOffset = startX; xOffset < stopX; ++xOffset) {
                int matching = 0;
                for (int index = 0; index < blackPixels.length; index += 2) {
                    int x = blackPixels[index];
                    int y = blackPixels[index + 1];
                    if (x + xOffset < image.getWidth() && image.getRGB(x + xOffset, y + CAPTCHA_Y_OFFSET) == BLACK)
                        ++matching;
                }
                int curPercent = matching * 100 / bestMatch.totalPixels;
                if (curPercent >= 85 && curPercent > (bestMatch.matchingPixels * 100 / bestMatch.totalPixels) && (bestMatch.xOffset <= 0 || xOffset - bestMatch.xOffset < 6)) {
                    bestMatch.matchingPixels = matching;
                    bestMatch.xOffset = xOffset;
                }
            }
            if (bestMatch.xOffset <= 0)
                continue;
            bestMatch.character = entry.getKey();
            // TODO: precalculate character width?
            for (int index = 0; index < blackPixels.length; index += 2) {
                if (blackPixels[index] > bestMatch.width)
                    bestMatch.width = blackPixels[index];
            }
            possibleCharacters.add(bestMatch);
            if (bestMatch.xOffset < minXOffset)
                minXOffset = bestMatch.xOffset;
        }
        //System.out.println(possibleCharacters);
        // remove entries with too high xOffset
        for (Iterator<CaptchaCharacterMatch> iterator = possibleCharacters.iterator(); iterator.hasNext();) {
            CaptchaCharacterMatch possibleCharacter = iterator.next();
            if (possibleCharacter.xOffset > minXOffset + 6)
                iterator.remove();
        }
        return possibleCharacters;
    }

    private static class CaptchaStringMatch {
        private String captcha;
        private int matchingPixels;
        private int totalPixels;

        @Override
        public String toString() {
            return captcha + " | matching: [" + matchingPixels + "/" + totalPixels + "] (" + (matchingPixels * 100.0 / totalPixels) + "%)";
        }
    }

    private static class CaptchaCharacterMatch {
        private Character character;
        private int xOffset;
        private int matchingPixels;
        private int totalPixels;
        private int width;

        @Override
        public String toString() {
            return character + " | xOffset: " + xOffset + ", width: " + width + ", matching: [" + matchingPixels + "/" + totalPixels + "] (" + (matchingPixels * 100.0 / totalPixels) + "%)";
        }
    }
}
