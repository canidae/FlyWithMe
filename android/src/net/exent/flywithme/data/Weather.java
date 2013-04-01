package net.exent.flywithme.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import net.exent.flywithme.bean.Forecast;
import net.exent.flywithme.bean.Takeoff;

import android.content.Context;
import android.util.Log;

public class Weather {
    public static void init(Context context) {
    }

    public static synchronized int updateForecast(List<Takeoff> takeoffs) {
        int updated = 0;
        List<Takeoff> updateTakeoffs = new ArrayList<Takeoff>();
        long currentTime = System.currentTimeMillis();
        for (Takeoff takeoff : takeoffs) {
            if (takeoff.getId() > 0 && currentTime - takeoff.getForecastUpdated() > 1000 * 60 * 60 * 3)
                updateTakeoffs.add(takeoff); // only update forecast if it's more than 3 hours since last update
        }
        if (updateTakeoffs.isEmpty())
            return updated; // no takeoffs to update
        URL url;
        HttpURLConnection urlConnection = null;
        try {
            url = new URL("http://192.168.1.200:8080/weather");
            urlConnection = (HttpURLConnection) url.openConnection();
            /* request forecast for takeoff */
            int takeoffCount = updateTakeoffs.size() > 255 ? 255 : updateTakeoffs.size();
            urlConnection.setDoOutput(true);
            urlConnection.setRequestProperty("content-type", "application/x-fwm-forecast-request");
            urlConnection.setFixedLengthStreamingMode(2 + takeoffCount * 8); // 2 bytes for protocol version and location count, then 2 floats (4 byte each) for every location 
            DataOutputStream output = new DataOutputStream(urlConnection.getOutputStream());
            output.writeByte(1);
            output.writeByte(takeoffCount);
            Log.d("Weather", "Requesting weather forecast for " + takeoffCount + "/" + takeoffs.size() + " locations");
            for (int a = 0; a < takeoffCount; ++a) {
                Log.d("Weather", "Location " + a + ": (" + (float) updateTakeoffs.get(a).getLocation().getLatitude() + "," + (float) updateTakeoffs.get(a).getLocation().getLongitude() + ")");
                output.writeFloat((float) updateTakeoffs.get(a).getLocation().getLatitude());
                output.writeFloat((float) updateTakeoffs.get(a).getLocation().getLongitude());
            }
            /* read forecast for takeoff */
            DataInputStream input = new DataInputStream(urlConnection.getInputStream());
            if (input.readUnsignedByte() == 1) { // first byte is protocol version, it should be 1
                takeoffCount = input.readUnsignedByte();
                if (takeoffCount > updateTakeoffs.size()) {
                    Log.w("Weather", "More takeoffs than requested returned, this shouldn't happen");
                    return updated;
                }
                for (int a = 0; a < takeoffCount; ++a) {
                    updateTakeoffs.get(a).setAsl(input.readShort());
                    int forecastCount = input.readUnsignedByte();
                    List<Forecast> forecasts = new ArrayList<Forecast>();
                    Log.d("Weather", "Location: " + updateTakeoffs.get(a));
                    for (int b = 0; b < forecastCount; ++b) {
                        Forecast forecast = new Forecast(input.readInt(), input.readShort() / 10.0, input.readShort() / 10.0, input.readShort() / 10.0, input.readShort() / 10.0, input.readShort() / 10.0);
                        Log.d("Weather", "  " + forecast);
                        forecasts.add(forecast);
                    }
                    updateTakeoffs.get(a).setForecast(forecasts);
                    ++updated;
                }
            }
        } catch (IOException e) {
            Log.e("Weather", "Error getting weather forecast for takeoffs", e);
        } finally {
            if (urlConnection != null)
                urlConnection.disconnect();
        }
        return updated;
    }
}
