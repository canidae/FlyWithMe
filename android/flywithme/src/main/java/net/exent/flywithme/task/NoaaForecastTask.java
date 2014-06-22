package net.exent.flywithme.task;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.exent.flywithme.FlyWithMe;
import net.exent.flywithme.dialog.ProgressDialog;
import net.exent.flywithme.R;
import net.exent.flywithme.bean.Takeoff;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.location.Location;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

public class NoaaForecastTask extends AsyncTask<Takeoff, String, Boolean> {
    //private static final String SERVER_URL = "http://flywithme-server.appspot.com/fwm";
    private static final String SERVER_URL = "http://192.168.1.200:8080/fwm";

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private Takeoff takeoff;
    private Bitmap captchaBitmap;

    @Override
    protected Boolean doInBackground(Takeoff... takeoffs) {
        try {
            takeoff = takeoffs[0];
            Location loc = takeoff.getLocation();
            fetchMeteogramAndSoundingFromProxy(loc, 0, 0, null);
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

    private void fetchMeteogramAndSoundingFromProxy(Location loc, int userId, int proc, String captcha) throws IOException {
        publishProgress("30", FlyWithMe.getInstance().getString(R.string.retrieving_noaa_forecast));
        HttpURLConnection con = (HttpURLConnection) new URL(SERVER_URL).openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        DataOutputStream outputStream = new DataOutputStream(con.getOutputStream());
        outputStream.writeByte(3);
        outputStream.writeFloat((float) loc.getLatitude());
        outputStream.writeFloat((float) loc.getLongitude());
        outputStream.writeBoolean(true); // TODO: option to disable meteogram in settings view?

        // figure out how many soundings we want
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(FlyWithMe.getInstance());
        int soundingDays = Integer.parseInt(prefs.getString("pref_sounding_days", "2"));
        List<Integer> soundingTimestamps = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        // just setting some fields to 0, shouldn't really matter, though
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        for (int day = 0; day < soundingDays; ++day) {
            for (int i = 0; i <= 21; i += 3) {
                String hour = (i < 10 ? "0" + i : "" + i);
                if (!prefs.getBoolean("pref_sounding_at_" + hour, false))
                    continue;
                calendar.set(Calendar.HOUR_OF_DAY, i);
                soundingTimestamps.add((int) (calendar.getTimeInMillis() / 1000));
            }
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        outputStream.writeByte(soundingTimestamps.size());
        for (Integer timestamp : soundingTimestamps)
            outputStream.writeInt(timestamp);

        // if we got an userId, proc and captcha, add that to request
        if (userId > 0 && proc > 0 && captcha != null) {
            outputStream.writeShort(userId);
            outputStream.writeShort(proc);
            outputStream.writeUTF(captcha);
        }
        outputStream.close();

        // read response
        int responseCode = con.getResponseCode();
        DataInputStream inputStream = new DataInputStream(con.getInputStream());
        int responseType = inputStream.readUnsignedByte();
        if (responseType == 0) {
            // ask user to fill inn captcha
            // ushort: userId
            // ushort: proc
            // int: captchaSize
            // <bytes>: captchaImage
            int tmpUserId = inputStream.readUnsignedShort();
            int tmpProc = inputStream.readUnsignedShort();
            int captchaSize = inputStream.readInt();
            byte[] captchaImage = new byte[captchaSize];
            int readBytes;
            int totalRead = 0;
            while ((readBytes = inputStream.read(captchaImage, totalRead, captchaSize - totalRead)) != -1 && totalRead < captchaSize)
                totalRead += readBytes;

            captchaBitmap = BitmapFactory.decodeByteArray(captchaImage, 0, totalRead);
            publishProgress("50", FlyWithMe.getInstance().getString(R.string.type_noaa_captcha));
            lock.lock();
            try {
                condition.await(120000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Log.w(getClass().getName(), "Failed sleeping", e);
            } finally {
                lock.unlock();
            }
            String tmpCaptcha = ProgressDialog.getInstance().getInputText();
            // recurse
            fetchMeteogramAndSoundingFromProxy(loc, tmpUserId, tmpProc, tmpCaptcha);
        } else if (responseType == 1) {
            // we got meteogram/sounding, create bitmap and display it
            // ubyte: images
            //   int: imageSize
            //   <bytes>: image
            int imageCount = inputStream.readUnsignedByte();
            List<Bitmap> images = new ArrayList<>();
            for (int i = 0; i < imageCount; ++i) {
                int imageSize = inputStream.readInt();
                byte[] image = new byte[imageSize];
                int readBytes;
                int totalRead = 0;
                while ((readBytes = inputStream.read(image, totalRead, imageSize - totalRead)) != -1 && totalRead < imageSize)
                    totalRead += readBytes;
                images.add(BitmapFactory.decodeByteArray(image, 0, totalRead));
            }
            int width = 0;
            int height = 0;
            for (Bitmap image : images) {
                width += image.getWidth();
                if (image.getHeight() > height)
                    height = image.getHeight();
            }
            Bitmap forecasts = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(forecasts);
            width = 0;
            for (Bitmap image : images) {
                canvas.drawBitmap(image, width, (height - image.getHeight()) / 2, null);
                width += image.getWidth();
            }
            takeoff.setNoaaForecast(forecasts);
        } else {
            // TODO: what do? shouldn't happen, though
        }
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
