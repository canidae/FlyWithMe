package net.exent.flywithme.task;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.exent.flywithme.FlyWithMe;
import net.exent.flywithme.dialog.ProgressDialog;
import net.exent.flywithme.R;
import net.exent.flywithme.bean.Takeoff;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;

public class NoaaForecastTask extends AsyncTask<Takeoff, String, Boolean> {
    private static final String NOAA_URL = "http://www.ready.noaa.gov";
    private static final String NOAA_METGRAM_CONF = "&metdata=GFS&mdatacfg=GFS&metext=gfsf&nhrs=96&type=user&wndtxt=2&Field1=FLAG&Level1=0&Field2=FLAG&Level2=5&Field3=FLAG&Level3=7&Field4=FLAG&Level4=9&Field5=TCLD&Level5=0&Field6=MSLP&Level6=0&Field7=T02M&Level7=0&Field8=TPP6&Level8=0&Field9=%20&Level9=0&Field10=%20&Level10=0&textonly=No&gsize=96&pdf=No";
    private static final Pattern NOAA_METCYC_PATTERN = Pattern.compile(".*</div><option value=\"(\\d+ \\d+)\">.*");
    private static final Pattern NOAA_USERID_PATTERN = Pattern.compile(".*userid=(\\d+).*");
    private static final Pattern NOAA_METDIR_PATTERN = Pattern.compile(".*<input type=\"HIDDEN\" name=\"metdir\" value=\"([^\"]+)\">.*");
    private static final Pattern NOAA_METFIL_PATTERN = Pattern.compile(".*<input type=\"HIDDEN\" name=\"metfil\" value=\"([^\"]+)\">.*");
    private static final Pattern NOAA_METDATE_PATTERN = Pattern.compile(".*<option>(.*\\(\\+ 00 Hrs\\)).*");
    private static final Pattern NOAA_PROC_PATTERN = Pattern.compile(".*<input type=\"HIDDEN\" name=\"proc\" value=\"(\\d+)\">.*");
    private static final Pattern NOAA_CAPTCHA_URL_PATTERN = Pattern.compile(".*<img src=\"([^\"]+)\" ALT=\"Security Code\".*");
    private static final Pattern NOAA_METEOGRAM_PATTERN = Pattern.compile(".*<img src=\"([^\"]+)\" ALT=\"meteorogram\">.*");
    private static String noaaUserId;
    private static String noaaMetcyc;
    private static String noaaMetdir;
    private static String noaaMetfil;
    private static String noaaMetdate;
    private static String noaaProc;
    private static String noaaCaptcha;

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private Takeoff takeoff;
    private Bitmap captchaBitmap;
    private HttpClient httpClient = new DefaultHttpClient();

    @Override
    protected Boolean doInBackground(Takeoff... takeoffs) {
        try {
            takeoff = takeoffs[0];
            Location loc = takeoff.getLocation();
            if (noaaCaptcha != null) {
                /* try fetching using old captcha, proc, etc */
                publishProgress("" + (int) (Math.random() * 20), FlyWithMe.getInstance().getString(R.string.attempting_forecast_shortcut));
                Bitmap bitmap = fetchMeteogram(loc);
                if (bitmap != null) {
                    takeoff.setNoaaForecast(bitmap);
                    return true;
                }
            }
            // didn't work, we'll have to go through the steps again
            noaaCaptcha = null;
            publishProgress("0", FlyWithMe.getInstance().getString(R.string.initiating_noaa_forecast));
            noaaUserId = getOne(fetchPageContent(NOAA_URL + "/ready2-bin/main.pl?Lat=" + loc.getLatitude() + "&Lon=" + loc.getLongitude()), NOAA_USERID_PATTERN);
            if (isCancelled() || noaaUserId == null)
                return false;
            publishProgress("20", FlyWithMe.getInstance().getString(R.string.initiating_noaa_forecast));
            String content = fetchPageContent(NOAA_URL + "/ready2-bin/metcycle.pl?product=metgram1&userid=" + noaaUserId + "&metdata=GFS&mdatacfg=GFS&Lat=" + loc.getLatitude() + "&Lon=" + loc.getLongitude());
            noaaMetcyc = getOne(content, NOAA_METCYC_PATTERN);
            noaaMetcyc = noaaMetcyc.replace(' ', '+');
            if (isCancelled() || noaaMetcyc == null)
                return false;
            publishProgress("40", FlyWithMe.getInstance().getString(R.string.fetching_noaa_captcha));
            content = fetchPageContent(NOAA_URL + "/ready2-bin/metgram1.pl?userid=" + noaaUserId + "&metdata=GFS&mdatacfg=GFS&Lat=" + loc.getLatitude() + "&Lon=" + loc.getLongitude() + "&metext=gfsf&metcyc=" + noaaMetcyc);
            noaaMetdir = getOne(content, NOAA_METDIR_PATTERN);
            noaaMetfil = getOne(content, NOAA_METFIL_PATTERN);
            try {
                noaaMetdate = URLEncoder.encode(getOne(content, NOAA_METDATE_PATTERN), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Log.w(getClass().getName(), "Unable to URLEncode metdate", e);
            }
            noaaProc = getOne(content, NOAA_PROC_PATTERN);
            if (isCancelled() || noaaMetdir == null || noaaMetfil == null || noaaMetdate == null || noaaProc == null)
                return false;
            noaaCaptcha = fetchCaptcha(getOne(content, NOAA_CAPTCHA_URL_PATTERN));
            if (isCancelled() || noaaCaptcha == null)
                return false;
            publishProgress("80", FlyWithMe.getInstance().getString(R.string.retrieving_noaa_forecast));
            Bitmap bitmap = fetchMeteogram(loc);
            if (bitmap == null) {
                /* hmm, wrong captcha? give user another try */
                publishProgress("40", FlyWithMe.getInstance().getString(R.string.fetching_noaa_captcha));
                content = fetchPageContent(NOAA_URL + "/ready2-bin/metgram1.pl?userid=" + noaaUserId + "&metdata=GFS&mdatacfg=GFS&Lat=" + loc.getLatitude() + "&Lon=" + loc.getLongitude() + "&metext=gfsf&metcyc=" + noaaMetcyc);
                noaaProc = getOne(content, NOAA_PROC_PATTERN);
                noaaCaptcha = fetchCaptcha(getOne(content, NOAA_CAPTCHA_URL_PATTERN));
                if (isCancelled() || noaaProc == null || noaaCaptcha == null)
                    return false;
                publishProgress("80", FlyWithMe.getInstance().getString(R.string.retrieving_noaa_forecast));
                bitmap = fetchMeteogram(loc);
                if (isCancelled() || bitmap == null)
                    return false;
            }
            takeoff.setNoaaForecast(bitmap);
        } catch (Exception e) {
            Log.w(getClass().getName(), "doInBackground() failed unexpectedly", e);
        }
        return true;
    }

    @Override
    protected void onProgressUpdate(String... messages) {
        try {
            if (isCancelled())
                return;
            int progress = Integer.parseInt(messages[0]);
            String message = messages[1];
            if (FlyWithMe.getInstance().getString(R.string.type_noaa_captcha).equals(message)) {
                showProgress(progress, message, captchaBitmap, new Runnable() {
                    public void run() {
                        lock.lock();
                        try {
                            condition.signal();
                        } finally {
                            lock.unlock();
                        }
                    }
                });
            } else {
                showProgress(progress, message, null, null);
            }
        } catch (Exception e) {
            Log.w(getClass().getName(), "onProgressUpdate() failed unexpectedly", e);
        }
    }

    @Override
    protected void onPostExecute(Boolean update) {
        if (isCancelled())
            return;
        if (update) {
            showProgress(-1, null, null, null);
            FlyWithMe.getInstance().showNoaaForecast(takeoff);
        } else {
            showProgress(100, FlyWithMe.getInstance().getString(R.string.fetching_noaa_failed), null, null);
        }
    }

    private String fetchCaptcha(String captchaUrl) {
        if (captchaUrl == null)
            return null;
        try {
            HttpResponse response = fetchPage(NOAA_URL + captchaUrl);
            captchaBitmap = BitmapFactory.decodeStream(response.getEntity().getContent());
            response.getEntity().consumeContent();
            publishProgress("60", FlyWithMe.getInstance().getString(R.string.type_noaa_captcha));
            lock.lock();
            try {
                condition.await(120000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Log.w(getClass().getName(), "Failed sleeping", e);
            } finally {
                lock.unlock();
            }
            return ProgressDialog.getInstance().getInputText();
        } catch (Exception e) {
            Log.w(getClass().getName(), "Unable to fetch CAPTCHA", e);
        }
        return null;
    }

    private Bitmap fetchMeteogram(Location loc) {
        try {
            String meteogramUrl = getOne(fetchPageContent(NOAA_URL + "/ready2-bin/metgram2.pl?userid=" + noaaUserId + "&Lat=" + loc.getLatitude() + "&Lon=" + loc.getLongitude() + "&metdir=" + noaaMetdir + "&metcyc=" + noaaMetcyc + "&metdate=" + noaaMetdate + "&metfil=" + noaaMetfil + "&password1=" + noaaCaptcha + "&proc=" + noaaProc + NOAA_METGRAM_CONF), NOAA_METEOGRAM_PATTERN);
            if (meteogramUrl == null)
                return null;
            HttpResponse response = fetchPage(NOAA_URL + meteogramUrl);
            Bitmap bitmap = BitmapFactory.decodeStream(response.getEntity().getContent());
            response.getEntity().consumeContent();
            return bitmap;
        } catch (Exception e) {
            Log.w(getClass().getName(), "Unable to fetch meteogram", e);
        }
        return null;
    }

    private HttpResponse fetchPage(String uri) {
        Log.i(getClass().getName(), "Fetching page: " + uri);
        try {
            URI website = new URI(uri);
            HttpGet request = new HttpGet();
            request.setURI(website);
            return httpClient.execute(request);
        } catch (Exception e) {
            /* try one more time before giving up */
            try {
                URI website = new URI(uri);
                HttpGet request = new HttpGet();
                request.setURI(website);
                return httpClient.execute(request);
            } catch (Exception e2) {
                Log.w(getClass().getName(), "Unable to fetch page: " + uri, e);
            }
        }
        return null;
    }

    private String fetchPageContent(String uri) {
        try {
            HttpResponse response = fetchPage(uri);
            BufferedReader in = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null)
                sb.append(line);
            response.getEntity().consumeContent();
            return sb.toString();
        } catch (Exception e) {
            Log.w(getClass().getName(), "Unable to fetch page content", e);
        }
        return null;
    }

    private String getOne(String text, Pattern pattern) {
        if (text == null || pattern == null)
            return null;
        Matcher matcher = pattern.matcher(text);
        if (matcher.matches())
            return matcher.group(1);
        return null;
    }

    private void showProgress(int progress, String text, Bitmap image, Runnable showInput) {
        ProgressDialog progressDialog = ProgressDialog.getInstance();
        if (progress >= 0) {
            if (progressDialog == null) {
                progressDialog = new ProgressDialog();
                progressDialog.setTask(this);
                progressDialog.show(FlyWithMe.getInstance().getSupportFragmentManager(), "progressDialog");
            }
            /* pass arguments */
            progressDialog.setProgress(progress, text, image, showInput);
            /* show fragment */
        } else if (progressDialog != null) {
            /* hide fragment */
            progressDialog.dismiss();
        }
    }
}
