package net.exent.flywithme;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
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
            takeoff = savedInstanceState.getParcelable("takeoff");
        return inflater.inflate(R.layout.takeoff_details, container, false);
    }

    @Override
    public void onStart() {
        Log.d(getClass().getSimpleName(), "onStart()");
        super.onStart();
        Bundle args = getArguments();
        if (args != null)
            takeoff = args.getParcelable(ARG_TAKEOFF);
        showTakeoffDetails(takeoff);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.d(getClass().getSimpleName(), "onSaveInstanceState(" + outState + ")");
        super.onSaveInstanceState(outState);
        outState.putParcelable(ARG_TAKEOFF, takeoff);
    }
    
    private class NoaaForecastTask extends AsyncTask<Takeoff, String, Boolean> {
        private final Lock lock = new ReentrantLock();
        private final Condition condition = lock.newCondition();
        private ProgressDialog progressDialog;
        private int progress = 0;
        private Bitmap captchaBitmap;
        private Bitmap meteogramBitmap;
        private HttpClient httpClient = new DefaultHttpClient();
        private Pattern useridPattern = Pattern.compile(".*userid=(\\d+).*");
        private Pattern forecastCyclePattern = Pattern.compile(".*</div><option value=\"(\\d+ \\d+)\">.*");
        private Pattern captchaUrlPattern = Pattern.compile(".*<img src=\"([^\"]+)\" ALT=\"Security Code\".*");
        private Pattern procPattern = Pattern.compile(".*<input type=\"HIDDEN\" name=\"proc\" value=\"(\\d+)\">.*");
        private Pattern meteogramPattern = Pattern.compile(".*<img src=\"([^\"]+)\" ALT=\"meteorogram\">.*");
        
        @Override
        protected Boolean doInBackground(Takeoff... takeoffs) {
            Log.d(getClass().getSimpleName(), "doInBackground(" + takeoffs + ")");
            Takeoff takeoff = takeoffs[0];
            if (System.currentTimeMillis() - takeoff.getNoaaForecastUpdated() < 1000 * 60 * 60 * 6) {
                /* we fetched a forecast less than 6 hours ago */
                meteogramBitmap = takeoff.getNoaaforecast();
                if (meteogramBitmap != null)
                    return true; // and it's still cached, return it
            }
            progressDialog = new ProgressDialog();
            progressDialog.setTask(this);
            progressDialog.show(getActivity().getSupportFragmentManager(), "ProgressDialogFragment");
            Location loc = takeoff.getLocation();
            publishProgress(getString(R.string.fetching_noaa_forecast));
            String userid = getOne(fetchPageContent("http://www.ready.noaa.gov/ready2-bin/main.pl?Lat=" + loc.getLatitude() + "&Lon=" + loc.getLongitude()), useridPattern);
            Log.i(getClass().getSimpleName(), "User ID: " + userid);
            publishProgress(getString(R.string.fetching_noaa_forecast));
            String forecastCycle = getOne(fetchPageContent("http://www.ready.noaa.gov/ready2-bin/metcycle.pl?product=metgram1&userid=" + userid + "&metdata=GFS&mdatacfg=GFS&Lat=" + loc.getLatitude() + "&Lon=" + loc.getLongitude()), forecastCyclePattern);
            int pos = forecastCycle == null ? -1 : forecastCycle.indexOf(' ');
            if (pos < 0) {
                Log.i(getClass().getSimpleName(), "No forecast cycle or no space in it?");
                return false;
            }
            SimpleDateFormat forecastCycleFormatter = new SimpleDateFormat("HH yyyyMMdd", Locale.US);
            SimpleDateFormat metdateFormatter = new SimpleDateFormat("MMM dd, yyyy 'at' HH 'UTC (+ 00 Hrs)'", Locale.US);
            String longDate = null;
            try {
                Date date = forecastCycleFormatter.parse(forecastCycle);
                longDate = URLEncoder.encode(metdateFormatter.format(date), "UTF-8");;
                Log.i(getClass().getSimpleName(), "Long date format: " + longDate);
            } catch (ParseException e) {
                Log.i(getClass().getSimpleName(), "Error parsing forecastCycle", e);
            } catch (UnsupportedEncodingException e) {
                Log.w(getClass().getSimpleName(), "Unable to URL encode long date", e);
            }
            String forecastCycleShort = forecastCycle.substring(pos + 1);
            try {
                forecastCycle = URLEncoder.encode(forecastCycle, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Log.w(getClass().getSimpleName(), "Unable to URL encode forecastcycle: " + forecastCycle, e);
            }
            Log.i(getClass().getSimpleName(), "Forecast cycle: " + forecastCycle);
            Log.i(getClass().getSimpleName(), "Forecast cycle (short): " + forecastCycleShort);
            publishProgress(getString(R.string.fetching_noaa_forecast));
            String content = fetchPageContent("http://www.ready.noaa.gov/ready2-bin/metgram1.pl?userid=" + userid + "&metdata=GFS&mdatacfg=GFS&Lat=" + loc.getLatitude() + "&Lon=" + loc.getLongitude() + "&metext=gfsf&metcyc=" + forecastCycle);
            String proc = getOne(content, procPattern);
            Log.i(getClass().getSimpleName(), "Proc: " + proc);
            String captchaUrl = getOne(content, captchaUrlPattern);
            Log.i(getClass().getSimpleName(), "Captcha URL: " + captchaUrl);
            HttpResponse response = fetchPage("http://www.ready.noaa.gov" + captchaUrl);
            String captcha = null;
            try {
                captchaBitmap = BitmapFactory.decodeStream(response.getEntity().getContent());
                response.getEntity().consumeContent();
                publishProgress("show_captcha");
                publishProgress("Waiting for CAPTCHA…"); // TODO: strings.xml
                lock.lock();
                try {
                    condition.await(120000, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Log.w(getClass().getName(), "Failed sleeping", e);
                } finally {
                    lock.unlock();
                }
                captcha = progressDialog.getInputText();
            } catch (Exception e) {
                Log.w(getClass().getSimpleName(), "Unable to fetch CAPTCHA", e);
            }
            publishProgress(getString(R.string.fetching_noaa_forecast));
            String meteogramUrl = getOne(fetchPageContent("http://www.ready.noaa.gov/ready2-bin/metgram2.pl?userid=" + userid + "&metdata=GFS&mdatacfg=GFS&Lat=" + loc.getLatitude() + "&Lon=" + loc.getLongitude() + "&metdir=/pub/forecast/" + forecastCycleShort + "/&metfil=hysplit.t12z.gfsf&metcyc=" + forecastCycle + "&metext=gfsf&metdate=" + longDate + "&nhrs=72&type=user&wndtxt=2&Field1=FLAG&Level1=0&Field2=FLAG&Level2=5&Field3=FLAG&Level3=7&Field4=TCLD&Level4=0&Field5=MSLP&Level5=0&Field6=T02M&Level6=0&Field7=TPP6&Level7=0&Field8=%20&Level8=0&Field9=%20&Level9=0&Field10=%20&Level10=0&textonly=No&gsize=96&pdf=No&password1=" + captcha + "&proc=" + proc), meteogramPattern);
            Log.i(getClass().getSimpleName(), "Meteogram URL: " + meteogramUrl);
            response = fetchPage("http://www.ready.noaa.gov" + meteogramUrl);
            publishProgress(getString(R.string.fetching_noaa_forecast));
            try {
                meteogramBitmap = BitmapFactory.decodeStream(response.getEntity().getContent());
                response.getEntity().consumeContent();
                takeoff.setNoaaForecast(meteogramBitmap);
                publishProgress("show_meteogram");
                lock.lock();
                try {
                    condition.await(120000, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Log.w(getClass().getName(), "Failed sleeping", e);
                } finally {
                    lock.unlock();
                }
            } catch (Exception e) {
                Log.w(getClass().getSimpleName(), "Unable to fetch meteogram", e);
            }
            // TODO: captcha/proc can be reused.
            return true;
        }

        @Override
        protected void onProgressUpdate(String... messages) {
            Log.d(getClass().getSimpleName(), "onProgressUpdate(" + messages + ")");
            String message = messages[0];
            if ("show_captcha".equals(message)) {
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
            } else if ("show_meteogram".equals(message)) {
                progressDialog.setImage(meteogramBitmap);
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
            } else {
                progressDialog.setProgress(progress, message);
                progress += 20;
            }
        }
        
        @Override
        protected void onPostExecute(Boolean update) {
            Log.d(getClass().getSimpleName(), "onPostExecute()");
            progressDialog.dismiss();
            progressDialog = null;
            if (!update)
                return;
            //Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        }
        
        private HttpResponse fetchPage(String uri) {
            Log.i(getClass().getSimpleName(), "Fetching page: " + uri);
            try {
                URI website = new URI(uri);
                HttpGet request = new HttpGet();
                request.setURI(website);
                return httpClient.execute(request);
            } catch (Exception e) {
                Log.i(getClass().getSimpleName(), "Unable to fetch page", e);
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
                Log.i(getClass().getSimpleName(), "Unable to fetch page content", e);
            }
            return null;
        }
        
        private String getOne(String text, Pattern pattern) {
            if (text == null)
                return null;
            Matcher matcher = pattern.matcher(text);
            if (matcher.matches())
                return matcher.group(1);
            return null;
        }
    }
}
