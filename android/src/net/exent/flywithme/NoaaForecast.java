package net.exent.flywithme;

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

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import net.exent.flywithme.bean.Takeoff;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

public class NoaaForecast extends Fragment {
    public interface NoaaForecastListener {
        void showProgress(int progress, String text, Bitmap image, Runnable showInput);
    }

    public static final String ARG_TAKEOFF = "takeoff";
    private NoaaForecastListener callback;
    private Takeoff takeoff;

    public void showNoaaForecast(Takeoff takeoff) {
        try {
            this.takeoff = takeoff;
            TextView noaaForecastText = (TextView) getActivity().findViewById(R.id.noaaForecastText);
            noaaForecastText.setText(takeoff.getName());
            ImageView noaaForecastImage = (ImageView) getActivity().findViewById(R.id.noaaForecastImage);
            if (System.currentTimeMillis() - takeoff.getNoaaUpdated() < 1000 * 60 * 60 * 6) {
                /* we fetched a forecast less than 6 hours ago */
                if (takeoff.getNoaaforecast() != null) {
                    /* and it's still cached, display it */
                    noaaForecastImage.setImageBitmap(takeoff.getNoaaforecast());
                    return;
                }
            }
            /* no cached forecast, need to fetch it */
            new NoaaForecastTask().execute(takeoff);
        } catch (Exception e) {
            Log.w(getClass().getName(), "showNoaaForecast() failed unexpectedly", e);
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        callback = (NoaaForecastListener) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (savedInstanceState != null)
            takeoff = savedInstanceState.getParcelable(ARG_TAKEOFF);
        return inflater.inflate(R.layout.noaa_forecast, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        Bundle args = getArguments();
        if (args != null)
            showNoaaForecast((Takeoff) args.getParcelable(ARG_TAKEOFF));

        ((ImageButton) getActivity().findViewById(R.id.fragmentButton1)).setImageDrawable(null);
        ((ImageButton) getActivity().findViewById(R.id.fragmentButton2)).setImageDrawable(null);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(ARG_TAKEOFF, takeoff);
    }

    /* static variables for NoaaForecastTask.
     * used for caching stuff, so we don't always have to fetch them
     * can't be inside NoaaForecastTask class because that class can't be static and non-static inner classes can't have static members...
     */
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

    private class NoaaForecastTask extends AsyncTask<Takeoff, String, Boolean> {
        private final Lock lock = new ReentrantLock();
        private final Condition condition = lock.newCondition();
        private ProgressDialog progressDialog = new ProgressDialog();
        private Bitmap captchaBitmap;
        private HttpClient httpClient = new DefaultHttpClient();
        
        
        @Override
        protected Boolean doInBackground(Takeoff... takeoffs) {
            try {
                Takeoff takeoff = takeoffs[0];
                Location loc = takeoff.getLocation();
                progressDialog.setTask(this);
                progressDialog.setCancelable(true);
                progressDialog.show(getActivity().getSupportFragmentManager(), "ProgressDialogFragment");
                if (noaaCaptcha != null) {
                    /* try fetching using old captcha, proc, etc */
                    publishProgress("" + (int) (Math.random() * 20), getString(R.string.attempting_forecast_shortcut));
                    Bitmap bitmap = fetchMeteogram(loc);
                    if (bitmap != null) {
                        takeoff.setNoaaForecast(bitmap);
                        return true;
                    }
                }
                // didn't work, we'll have to go through the steps again
                noaaCaptcha = null;
                publishProgress("" + (int) (Math.random() * 20), getString(R.string.initiating_noaa_forecast));
                noaaUserId = getOne(fetchPageContent(NOAA_URL + "/ready2-bin/main.pl?Lat=" + loc.getLatitude() + "&Lon=" + loc.getLongitude()), NOAA_USERID_PATTERN);
                publishProgress("" + (int) (Math.random() * 20 + 20), getString(R.string.initiating_noaa_forecast)); 
                String content = fetchPageContent(NOAA_URL + "/ready2-bin/metcycle.pl?product=metgram1&userid=" + noaaUserId + "&metdata=GFS&mdatacfg=GFS&Lat=" + loc.getLatitude() + "&Lon=" + loc.getLongitude()); 
                noaaMetcyc = getOne(content, NOAA_METCYC_PATTERN);
                noaaMetcyc = noaaMetcyc.replace(' ', '+');
                publishProgress("" + (int) (Math.random() * 20 + 40), getString(R.string.fetching_noaa_captcha));
                content = fetchPageContent(NOAA_URL + "/ready2-bin/metgram1.pl?userid=" + noaaUserId + "&metdata=GFS&mdatacfg=GFS&Lat=" + loc.getLatitude() + "&Lon=" + loc.getLongitude() + "&metext=gfsf&metcyc=" + noaaMetcyc);
                noaaMetdir = getOne(content, NOAA_METDIR_PATTERN);
                noaaMetfil = getOne(content, NOAA_METFIL_PATTERN);
                try {
                    noaaMetdate = URLEncoder.encode(getOne(content, NOAA_METDATE_PATTERN), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    Log.w(getClass().getName(), "Unable to URLEncode metdate", e);
                }
                noaaProc = getOne(content, NOAA_PROC_PATTERN);
                noaaCaptcha = fetchCaptcha(getOne(content, NOAA_CAPTCHA_URL_PATTERN));
                publishProgress("" + (int) (Math.random() * 20 + 80), getString(R.string.retrieving_noaa_forecast));
                Bitmap bitmap = fetchMeteogram(loc);
                if (bitmap == null) {
                    /* hmm, wrong captcha? give user another try */
                    publishProgress("" + (int) (Math.random() * 20 + 40), getString(R.string.fetching_noaa_captcha));
                    content = fetchPageContent(NOAA_URL + "/ready2-bin/metgram1.pl?userid=" + noaaUserId + "&metdata=GFS&mdatacfg=GFS&Lat=" + loc.getLatitude() + "&Lon=" + loc.getLongitude() + "&metext=gfsf&metcyc=" + noaaMetcyc);
                    noaaProc = getOne(content, NOAA_PROC_PATTERN);
                    noaaCaptcha = fetchCaptcha(getOne(content, NOAA_CAPTCHA_URL_PATTERN));
                    publishProgress("" + (int) (Math.random() * 20 + 80), getString(R.string.retrieving_noaa_forecast));
                    bitmap = fetchMeteogram(loc);
                    if (bitmap == null)
                        return false;
                }
                takeoff.setNoaaForecast(bitmap);
            } catch (Exception e) {
                Log.w(getClass().getSimpleName(), "doInBackground() failed unexpectedly", e);
            }
            return true;
        }

        @Override
        protected void onProgressUpdate(String... messages) {
            try {
                int progress = Integer.parseInt(messages[0]);
                String message = messages[1];
                if (getString(R.string.write_noaa_captcha).equals(message)) {
                    progressDialog.setProgress(progress, message, captchaBitmap, new Runnable() {
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
                    progressDialog.setProgress(progress, message, null, null);
                }
            } catch (Exception e) {
                Log.w(getClass().getSimpleName(), "onProgressUpdate() failed unexpectedly", e);
            }
        }
        
        @Override
        protected void onPostExecute(Boolean update) {
            progressDialog.dismiss();
            progressDialog = null;
            if (!update) {
                getActivity().onBackPressed();
                return;
            }
            showNoaaForecast(takeoff);
        }
        
        private String fetchCaptcha(String captchaUrl) {
            try {
                HttpResponse response = fetchPage("http://www.ready.noaa.gov" + captchaUrl);
                captchaBitmap = BitmapFactory.decodeStream(response.getEntity().getContent());
                response.getEntity().consumeContent();
                publishProgress("" + (int) (Math.random() * 20 + 60), getString(R.string.write_noaa_captcha));
                lock.lock();
                try {
                    condition.await(120000, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Log.w(getClass().getName(), "Failed sleeping", e);
                } finally {
                    lock.unlock();
                }
                return progressDialog.getInputText();
            } catch (Exception e) {
                Log.w(getClass().getSimpleName(), "Unable to fetch CAPTCHA", e);
            }
            return null;
        }
        
        private Bitmap fetchMeteogram(Location loc) {
            try {
                String meteogramUrl = getOne(fetchPageContent(NOAA_URL + "/ready2-bin/metgram2.pl?userid=" + noaaUserId + "&Lat=" + loc.getLatitude() + "&Lon=" + loc.getLongitude() + "&metdir=" + noaaMetdir + "&metcyc=" + noaaMetcyc + "&metdate=" + noaaMetdate + "&metfil=" + noaaMetfil + "&password1=" + noaaCaptcha + "&proc=" + noaaProc + NOAA_METGRAM_CONF), NOAA_METEOGRAM_PATTERN);
                HttpResponse response = fetchPage(NOAA_URL + meteogramUrl);
                Bitmap bitmap = BitmapFactory.decodeStream(response.getEntity().getContent());
                response.getEntity().consumeContent();
                return bitmap;
            } catch (Exception e) {
                Log.w(getClass().getSimpleName(), "Unable to fetch meteogram", e);
            }
            return null;
        }
        
        private HttpResponse fetchPage(String uri) {
            try {
                URI website = new URI(uri);
                HttpGet request = new HttpGet();
                request.setURI(website);
                return httpClient.execute(request);
            } catch (Exception e) {
                Log.w(getClass().getSimpleName(), "Unable to fetch page", e);
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
                Log.w(getClass().getSimpleName(), "Unable to fetch page content", e);
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
    }
}
