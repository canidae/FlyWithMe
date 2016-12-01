package net.exent.flywithme.server.util;

import net.exent.flywithme.server.util.gif.GifDecoder;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
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

/**
 * Tool for fetching data from ready.noaa.gov.
 */
public class NoaaProxy {
    private static final Logger log = Logger.getLogger(NoaaProxy.class.getName());

    private static final int CAPTCHA_Y_OFFSET = 38;
    private static final int BLACK = -16777216;

    private static final String NOAA_URL = "http://www.ready.noaa.gov";
    private static final String NOAA_METGRAM_CONF = "&metdata=GFS&mdatacfg=GFS&metext=gfsf&nhrs=96&type=user&wndtxt=2&Field1=FLAG&Level1=0&Field2=FLAG&Level2=5&Field3=FLAG&Level3=7&Field4=FLAG&Level4=9&Field5=TCLD&Level5=0&Field6=MSLP&Level6=0&Field7=T02M&Level7=0&Field8=TPP6&Level8=0&Field9=%20&Level9=0&Field10=%20&Level10=0&textonly=No&gsize=96&pdf=No";
    private static final String NOAA_SOUNDING_CONF = "&metdata=GFS&type=0&nhrs=24&hgt=0&textonly=No&skewt=3&gsize=96&pdf=No";
    private static final Pattern NOAA_METCYC_PATTERN = Pattern.compile(".*</div><option value=\"(\\d+ \\d+)\">.*");
    private static final Pattern NOAA_USERID_PATTERN = Pattern.compile(".*userid=(\\d+).*");
    private static final Pattern NOAA_METDIR_PATTERN = Pattern.compile(".*<input type=\"HIDDEN\" name=\"metdir\" value=\"([^\"]+)\">.*");
    private static final Pattern NOAA_METFIL_PATTERN = Pattern.compile(".*<input type=\"HIDDEN\" name=\"metfil\" value=\"([^\"]+)\">.*");
    private static final Pattern NOAA_METDATE_PATTERN = Pattern.compile("<option>([^<]*\\(\\+ \\d+ Hrs\\))");
    private static final Pattern NOAA_PROC_PATTERN = Pattern.compile(".*<input type=\"HIDDEN\" name=\"proc\" value=\"(\\d+)\">.*");
    private static final Pattern NOAA_CAPTCHA_URL_PATTERN = Pattern.compile(".*<img src=\"([^\"]+)\" ALT=\"Security Code\".*");
    private static final Pattern NOAA_METEOGRAM_PATTERN = Pattern.compile(".*<img src=\"([^\"]+)\" ALT=\"meteorogram\">.*");
    private static final Pattern NOAA_SOUNDING_PROFILE_PATTERN = Pattern.compile(".*<IMG SRC=\"([^\"]+)\" ALT=\"Profile\">.*");
    private static final Pattern NOAA_SOUNDING_THETA_PATTERN = Pattern.compile(".*<img src=\"([^\"]+)\" ALT=\"Theta Plot\">.*");
    private static final Pattern NOAA_SOUNDING_TEXT_PATTERN = Pattern.compile(".*<img src=\"([^\"]+)\" ALT=\"Text listing\">.*");

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
            File directory = new File("captcha_bitmaps");
            for (File file : directory.listFiles()) {
                GifDecoder.GifImage image = GifDecoder.read(new FileInputStream(file));
                int[] pixels = image.getFrame(0);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                for (int y = 0; y < image.getHeight(); ++y) {
                    for (int x = 0; x < image.getWidth(); ++x) {
                        if (pixels[y * image.getWidth() + x] == BLACK) {
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

    public static void main(String... args) {
        System.out.println(System.getProperty("user.dir"));
        fetchMeteogram(63.0f, 10.0f);
    }

    /**
     * Fetch meteogram for a given location.
     *
     * @param latitude The latitude of the location we want forecast for.
     * @param longitude The longitude of the location we want forecast for.
     * @return The meteogram image.
     */
    public static byte[] fetchMeteogram(float latitude, float longitude) {
        // TODO (medium): we need to handle when NOAA becomes unavailable much better (app just hangs with loading animation)
        if (noaaCaptcha == null)
            updateFieldsAndCaptcha(latitude, longitude);
        for (int a = 0; a < 2; ++a) {
            try {
                String meteogramUrl = getOne(fetchPageContent(NOAA_URL + "/ready2-bin/metgram2.pl?userid=" + noaaUserId + "&Lat=" + latitude + "&Lon=" + longitude
                        + "&metdir=" + noaaMetDir + "&metcyc=" + noaaMetCyc + "&metdate=" + URLEncoder.encode(noaaMetDates.get(0), "UTF-8") + "&metfil=" + noaaMetFil
                        + "&password1=" + noaaCaptcha + "&proc=" + noaaProc + NOAA_METGRAM_CONF), NOAA_METEOGRAM_PATTERN);
                byte[] meteogramImage = fetchImage(NOAA_URL + meteogramUrl);
                if (meteogramImage != null)
                    return meteogramImage;
            } catch (Exception e) {
                log.log(Level.WARNING, "Failed fetching meteogram image", e);
            }
            log.info("No meteogram image returned, updating cached data and trying to solve new captcha");
            updateFieldsAndCaptcha(latitude, longitude);
        }
        return null;
    }

    /**
     * Fetch sounding profile, theta and text for a given location and time.
     *
     * @param latitude The latitude of the location we want forecast for.
     * @param longitude The longitude of the location we want forecast for.
     * @param soundingTimestamp The timestamp we want sounding for, in milliseconds since epoch.
     * @return The sounding profile, theta and text images, in that order.
     */
    public static List<byte[]> fetchSounding(float latitude, float longitude, long soundingTimestamp) {
        if (noaaCaptcha == null)
            updateFieldsAndCaptcha(latitude, longitude);
        String metDate = metdateFormatter.format(new Date(soundingTimestamp));
        for (int a = 0; a < 2; ++a) {
            for (String noaaMetdate : noaaMetDates) {
                if (!noaaMetdate.startsWith(metDate))
                    continue;
                try {
                    String pageContent = fetchPageContent(NOAA_URL + "/ready2-bin/profile2.pl?userid=" + noaaUserId + "&Lat=" + latitude + "&Lon=" + longitude
                            + "&metdir=" + noaaMetDir + "&metcyc=" + noaaMetCyc + "&metdate=" + URLEncoder.encode(noaaMetdate, "UTF-8") + "&metfil=" + noaaMetFil
                            + "&password1=" + noaaCaptcha + "&proc=" + noaaProc + NOAA_SOUNDING_CONF);
                    String soundingUrl = getOne(pageContent, NOAA_SOUNDING_PROFILE_PATTERN);
                    byte[] profileImage = fetchImage(NOAA_URL + soundingUrl);
                    if (profileImage != null) {
                        String thetaUrl = getOne(pageContent, NOAA_SOUNDING_THETA_PATTERN);
                        byte[] thetaImage = fetchImage(NOAA_URL + thetaUrl);
                        if (thetaImage != null) {
                            String textUrl = getOne(pageContent, NOAA_SOUNDING_TEXT_PATTERN);
                            byte[] textImage = fetchImage(NOAA_URL + textUrl);
                            return Arrays.asList(profileImage, thetaImage, textImage);
                        }
                    }
                } catch (Exception e) {
                    log.log(Level.WARNING, "Failed fetching sounding profile/theta/text images", e);
                }
            }
            log.info("No sounding profile/theta/text images returned, updating cached data and trying to solve new captcha");
            updateFieldsAndCaptcha(latitude, longitude);
        }
        return null;
    }

    /**
     * Fetch CAPTCHA image to solve before fetching meteogram and sounding.
     *
     * @param latitude The latitude of the location we want forecast for.
     * @param longitude The longitude of the location we want forecast for.
     */
    private static void updateFieldsAndCaptcha(float latitude, float longitude) {
        noaaCaptcha = null;
        for (int a = 0; a < 3 && noaaCaptcha == null; ++a) {
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

    private static String solveCaptcha(byte[] captchaImage) throws Exception {
        log.info("CAPTCHA image size: " + captchaImage.length);
        GifDecoder.GifImage image = GifDecoder.read(captchaImage);
        List<CaptchaStringMatch> possibleMatches = findPossibleCaptchas(image, 0, image.getWidth() / 4, new ArrayList<CaptchaCharacterMatch>());
        Collections.sort(possibleMatches, new Comparator<CaptchaStringMatch>() {
            @Override
            public int compare(CaptchaStringMatch c1, CaptchaStringMatch c2) {
                // weight both amount and percentage of matching pixels, this weighting seems to produce very good results
                return (c2.matchingPixels + c2.matchingPixels * 10000 / c2.totalPixels) - (c1.matchingPixels + c1.matchingPixels * 10000 / c1.totalPixels);
            }
        });
        if (possibleMatches.size() > 0) {
            String captcha = possibleMatches.get(0).captcha;
            log.info("Found " + possibleMatches.size() + " possible CAPTCHA matches, I think this is correct: " + captcha);
            return captcha;
        } else {
            log.info("Unable to solve this CAPTCHA");
            return null;
        }
    }

    private static List<CaptchaStringMatch> findPossibleCaptchas(GifDecoder.GifImage image, int startX, int stopX, List<CaptchaCharacterMatch> characterMatches) {
        List<CaptchaStringMatch> matches = new ArrayList<>();
        List<CaptchaCharacterMatch> possibleCaptchaCharacters = findNextPossibleCaptchaCharacters(image, startX, stopX);
        if (possibleCaptchaCharacters.isEmpty()) {
            CaptchaStringMatch match = new CaptchaStringMatch();
            for (CaptchaCharacterMatch character : characterMatches) {
                match.captcha = match.captcha + character.character;
                match.matchingPixels += character.matchingPixels;
                match.totalPixels += character.totalPixels;
            }
            matches.add(match);
        } else {
            for (CaptchaCharacterMatch character : possibleCaptchaCharacters) {
                characterMatches.add(character);
                matches.addAll(findPossibleCaptchas(image, character.xOffset + character.width - 6, character.xOffset + character.width + 12, characterMatches));
                characterMatches.remove(characterMatches.size() - 1);
            }
        }
        return matches;
    }

    private static List<CaptchaCharacterMatch> findNextPossibleCaptchaCharacters(GifDecoder.GifImage image, int startX, int stopX) {
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
                    if (x + xOffset < image.getWidth() && image.getFrame(0)[(y + CAPTCHA_Y_OFFSET) * image.getWidth() + x + xOffset] == BLACK)
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
            // TODO (low): precalculate character width?
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
        private String captcha = "";
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
