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
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

public class TakeoffDetails extends Fragment {
    public interface TakeoffDetailsListener {
        Location getLocation();

        void showNoaaForecast(Takeoff takeoff);
    }

    public static final String ARG_TAKEOFF = "takeoff";
    private Takeoff takeoff;
    private TakeoffDetailsListener callback;

    public void showTakeoffDetails(final Takeoff takeoff) {
        Log.d(getClass().getSimpleName(), "showTakeoffDetails(" + takeoff + ")");
        if (callback == null) {
            Log.w(getClass().getSimpleName(), "callback is null, returning");
            return;
        }
        this.takeoff = takeoff;
        if (takeoff == null)
            return;

        final Location myLocation = callback.getLocation();

        ImageButton navigationButton = (ImageButton) getActivity().findViewById(R.id.fragmentButton1);
        navigationButton.setImageResource(R.drawable.navigation);
        navigationButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Location loc = takeoff.getLocation();
                String uri = "http://maps.google.com/maps?saddr=" + myLocation.getLatitude() + "," + myLocation.getLongitude() + "&daddr=" + loc.getLatitude() + "," + loc.getLongitude();
                Intent intent = new Intent(android.content.Intent.ACTION_VIEW, Uri.parse(uri));
                getActivity().startActivity(intent);
            }
        });
        ImageButton noaaButton = (ImageButton) getActivity().findViewById(R.id.fragmentButton2);
        noaaButton.setImageResource(R.drawable.noaa);
        noaaButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                new NoaaForecastTask().execute(takeoff);
            }
        });

        /* windpai */
        ImageView windroseNorth = (ImageView) getActivity().findViewById(R.id.takeoffDetailsWindroseNorth);
        ImageView windroseNorthwest = (ImageView) getActivity().findViewById(R.id.takeoffDetailsWindroseNorthwest);
        ImageView windroseWest = (ImageView) getActivity().findViewById(R.id.takeoffDetailsWindroseWest);
        ImageView windroseSouthwest = (ImageView) getActivity().findViewById(R.id.takeoffDetailsWindroseSouthwest);
        ImageView windroseSouth = (ImageView) getActivity().findViewById(R.id.takeoffDetailsWindroseSouth);
        ImageView windroseSoutheast = (ImageView) getActivity().findViewById(R.id.takeoffDetailsWindroseSoutheast);
        ImageView windroseEast = (ImageView) getActivity().findViewById(R.id.takeoffDetailsWindroseEast);
        ImageView windroseNortheast = (ImageView) getActivity().findViewById(R.id.takeoffDetailsWindroseNortheast);
        windroseNorth.setVisibility(takeoff.hasNorthExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseNorthwest.setVisibility(takeoff.hasNorthwestExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseWest.setVisibility(takeoff.hasWestExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseSouthwest.setVisibility(takeoff.hasSouthwestExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseSouth.setVisibility(takeoff.hasSouthExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseSoutheast.setVisibility(takeoff.hasSoutheastExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseEast.setVisibility(takeoff.hasEastExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseNortheast.setVisibility(takeoff.hasNortheastExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);

        TextView takeoffName = (TextView) getActivity().findViewById(R.id.takeoffDetailsName);
        TextView takeoffCoordAslHeight = (TextView) getActivity().findViewById(R.id.takeoffDetailsCoordAslHeight);
        TextView takeoffDescription = (TextView) getActivity().findViewById(R.id.takeoffDetailsDescription);

        takeoffName.setText(takeoff.getName());
        takeoffCoordAslHeight.setText(String.format("[%.2f,%.2f] " + getActivity().getString(R.string.asl) + ": %d " + getActivity().getString(R.string.height) + ": %d", takeoff.getLocation().getLatitude(), takeoff.getLocation().getLongitude(), takeoff.getAsl(), takeoff.getHeight()));
        takeoffDescription.setText(takeoff.getDescription());
        takeoffDescription.setMovementMethod(new ScrollingMovementMethod());
    }

    @Override
    public void onAttach(Activity activity) {
        Log.d(getClass().getSimpleName(), "onAttach(" + activity + ")");
        super.onAttach(activity);
        callback = (TakeoffDetailsListener) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(getClass().getSimpleName(), "onCreateView(" + inflater + ", " + container + ", " + savedInstanceState + ")");
        if (savedInstanceState != null)
            takeoff = savedInstanceState.getParcelable(ARG_TAKEOFF);
        return inflater.inflate(R.layout.takeoff_details, container, false);
    }

    @Override
    public void onStart() {
        Log.d(getClass().getSimpleName(), "onStart()");
        super.onStart();
        Bundle args = getArguments();
        if (args != null)
            showTakeoffDetails((Takeoff) args.getParcelable(ARG_TAKEOFF));
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.d(getClass().getSimpleName(), "onSaveInstanceState(" + outState + ")");
        super.onSaveInstanceState(outState);
        outState.putParcelable(ARG_TAKEOFF, takeoff);
    }

    /* static variables for NoaaForecastTask.
     * used for caching stuff, so we don't always have to fetch them
     * can't be inside NoaaForecastTask class because that class can't be static and non-static inner classes can't have static members...
     */
    private static final String NOAA_URL = "http://www.ready.noaa.gov";
    private static final String NOAA_METGRAM_CONF = "&metdata=GFS&mdatacfg=GFS&metext=gfsf&nhrs=72&type=user&wndtxt=2&Field1=FLAG&Level1=0&Field2=FLAG&Level2=5&Field3=FLAG&Level3=7&Field4=FLAG&Level4=9&Field5=TCLD&Level5=0&Field6=MSLP&Level6=0&Field7=T02M&Level7=0&Field8=TPP6&Level8=0&Field9=%20&Level9=0&Field10=%20&Level10=0&textonly=No&gsize=96&pdf=No";
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
        private ProgressDialog progressDialog;
        private Bitmap captchaBitmap;
        private HttpClient httpClient = new DefaultHttpClient();
        
        @Override
        protected Boolean doInBackground(Takeoff... takeoffs) {
            Log.d(getClass().getSimpleName(), "doInBackground(" + takeoffs + ")");
            Takeoff takeoff = takeoffs[0];
            if (System.currentTimeMillis() - takeoff.getNoaaForecastUpdated() < 1000 * 60 * 60 * 6) {
                /* we fetched a forecast less than 6 hours ago */
                if (takeoff.getNoaaforecast() != null)
                    return true; // and it's still cached, return it
            }
            Location loc = takeoff.getLocation();
            if (noaaCaptcha != null) {
                /* try fetching using old captcha, proc, etc */
                Bitmap bitmap = fetchMeteogram(loc);
                if (bitmap != null) {
                    takeoff.setNoaaForecast(bitmap);
                    return true;
                }
            }
            // didn't work, we'll have to go through the steps again
            progressDialog = new ProgressDialog();
            progressDialog.setTask(this);
            noaaCaptcha = null;
            progressDialog.show(getActivity().getSupportFragmentManager(), "ProgressDialogFragment");
            publishProgress("8", getString(R.string.initiating_noaa_forecast));
            noaaUserId = getOne(fetchPageContent(NOAA_URL + "/ready2-bin/main.pl?Lat=" + loc.getLatitude() + "&Lon=" + loc.getLongitude()), NOAA_USERID_PATTERN);
            publishProgress("23", getString(R.string.initiating_noaa_forecast)); 
            String content = fetchPageContent(NOAA_URL + "/ready2-bin/metcycle.pl?product=metgram1&userid=" + noaaUserId + "&metdata=GFS&mdatacfg=GFS&Lat=" + loc.getLatitude() + "&Lon=" + loc.getLongitude()); 
            noaaMetcyc = getOne(content, NOAA_METCYC_PATTERN);
            noaaMetcyc = noaaMetcyc.replace(' ', '+');
            publishProgress("42", getString(R.string.fetching_noaa_captcha));
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
            publishProgress("85", getString(R.string.retrieving_noaa_forecast));
            Bitmap bitmap = fetchMeteogram(loc);
            if (bitmap == null) {
                /* hmm, wrong captcha? give user another try */
                publishProgress("42", getString(R.string.fetching_noaa_captcha));
                content = fetchPageContent(NOAA_URL + "/ready2-bin/metgram1.pl?userid=" + noaaUserId + "&metdata=GFS&mdatacfg=GFS&Lat=" + loc.getLatitude() + "&Lon=" + loc.getLongitude() + "&metext=gfsf&metcyc=" + noaaMetcyc);
                noaaProc = getOne(content, NOAA_PROC_PATTERN);
                noaaCaptcha = fetchCaptcha(getOne(content, NOAA_CAPTCHA_URL_PATTERN));
                publishProgress("85", getString(R.string.retrieving_noaa_forecast));
                bitmap = fetchMeteogram(loc);
                if (bitmap == null)
                    return false;
            }
            takeoff.setNoaaForecast(bitmap);
            return true;
        }

        @Override
        protected void onProgressUpdate(String... messages) {
            Log.d(getClass().getSimpleName(), "onProgressUpdate(" + messages + ")");
            int progress = Integer.parseInt(messages[0]);
            String message = messages[1];
            if (getString(R.string.write_noaa_captcha).equals(message)) {
                progressDialog.setImage(captchaBitmap);
                progressDialog.showInput(new Runnable() {
                    public void run() {
                        lock.lock();
                        try {
                            condition.signal();
                        } finally {
                            lock.unlock();
                        }
                    }
                });
            }
            progressDialog.setProgress(progress, message);
        }
        
        @Override
        protected void onPostExecute(Boolean update) {
            Log.d(getClass().getSimpleName(), "onPostExecute()");
            if (progressDialog != null) {
                progressDialog.dismiss();
                progressDialog = null;
            }
            if (!update)
                return;
            callback.showNoaaForecast(takeoff);
        }
        
        private String fetchCaptcha(String captchaUrl) {
            Log.d(getClass().getSimpleName(), "fetchMeteogram(" + captchaUrl + ")");
            try {
                HttpResponse response = fetchPage("http://www.ready.noaa.gov" + captchaUrl);
                captchaBitmap = BitmapFactory.decodeStream(response.getEntity().getContent());
                response.getEntity().consumeContent();
                publishProgress("65", getString(R.string.write_noaa_captcha));
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
            Log.d(getClass().getSimpleName(), "fetchMeteogram(" + loc + ")");
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
            Log.d(getClass().getSimpleName(), "fetchPage(" + uri + ")");
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
            Log.d(getClass().getSimpleName(), "fetchPageContent(" + uri + ")");
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
            Log.d(getClass().getSimpleName(), "getOne(" + text + ", " + pattern + ")");
            if (text == null)
                return null;
            Matcher matcher = pattern.matcher(text);
            if (matcher.matches())
                return matcher.group(1);
            return null;
        }
    }
}
