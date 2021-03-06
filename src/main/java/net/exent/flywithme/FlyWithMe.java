package net.exent.flywithme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.googlecode.objectify.ObjectifyService;
import net.exent.flywithme.bean.Forecast;
import net.exent.flywithme.bean.Takeoff;
import net.exent.flywithme.util.DataStore;
import net.exent.flywithme.util.FlightlogProxy;
import net.exent.flywithme.util.Log;
import net.exent.flywithme.util.NoaaProxy;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@WebServlet(name = "FlyWithMe", value = "/flywithme")
public class FlyWithMe extends HttpServlet {
    private static final Log log = new Log();

    private static final Pattern takeoffsUrl = Pattern.compile("^/takeoffs$");
    private static final Pattern meteogramUrl = Pattern.compile("^/takeoffs/(\\d+)/meteogram$");
    private static final Pattern soundingUrl = Pattern.compile("^/takeoffs/(\\d+)/sounding/(\\d+)$");
    private static final Pattern updateTakeoffDataUrl = Pattern.compile("^/task/updateTakeoffData$");

    private static Gson gson;

    @Override
    public void init() {
        ObjectifyService.init();
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(byte[].class, (JsonSerializer<byte[]>) (src, typeOfSrc, context) -> new JsonPrimitive(Base64.getEncoder().encodeToString(src)));
        gson = builder.create();
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        /* TODO:
           - get takeoffs (and update takeoff list if necessary)
           - get meteogram/sounding
           - get guesstimated landing zone (heatmap layer?)
           - get thermal hotspots (heatmap layer)
         */

        /* url schemas:
           - /takeoffs
           - /takeoffs/<id>
           - /takeoffs/<id>/meteogram
           - /takeoffs/<id>/sounding/<timestamp>
           - /landings
           - /thermals
           - /task/updateTakeoffData
         */
        String path = request.getPathInfo();
        log.d("Request: ", path);
        Matcher m;
        if ((m = takeoffsUrl.matcher(path)).matches()) {
            long lastUpdated = Long.parseLong(request.getParameter("lastUpdated"));
            writeJsonResponse(response, getTakeoffs(lastUpdated));
        } else if ((m = meteogramUrl.matcher(path)).matches()) {
            writeJsonResponse(response, getMeteogram(Long.parseLong(m.group(1))));
        } else if ((m = soundingUrl.matcher(path)).matches()) {
            writeJsonResponse(response, getSounding(Long.parseLong(m.group(1)), Long.parseLong(m.group(2))));
        } else if ((m = updateTakeoffDataUrl.matcher(path)).matches()) {
            updateTakeoffData();
        }
    }

    private void writeJsonResponse(HttpServletResponse response, Object data) throws IOException {
        response.setContentType("application/json");
        response.getWriter().print(gson.toJson(data));
    }

    private List<Takeoff> getTakeoffs(long updatedAfter) {
        return DataStore.getTakeoffs(updatedAfter);
    }

    private Forecast getMeteogram(long takeoffId) {
        Forecast forecast = DataStore.loadForecast(takeoffId, Forecast.ForecastType.METEOGRAM, 0);
        if (forecast != null) {
            // return forecast to client
            return forecast;
        }
        // need to fetch forecast
        Takeoff takeoff = DataStore.loadTakeoff(takeoffId);
        if (takeoff == null) {
            log.w("Client asked for meteogram for a takeoff that doesn't seem to exist in our database: ", takeoffId);
            return null;
        }
        forecast = new Forecast();
        forecast.setTakeoffId(takeoffId);
        forecast.setType(Forecast.ForecastType.METEOGRAM);
        forecast.setUpdated(System.currentTimeMillis() / 1000);
        forecast.setImage(NoaaProxy.fetchMeteogram(takeoff.getLat(), takeoff.getLng()));
        DataStore.saveForecast(forecast);
        return forecast;
    }

    private List<Forecast> getSounding(long takeoffId, long timestamp) {
        timestamp = (timestamp / 10800000) * 10800000; // aligns timestamp with valid values for sounding (sounding every 3rd hour)
        long now = System.currentTimeMillis();
        if (timestamp < now - 86400000) { // 86400000 = 1 day
            log.i("Client tried to retrieve sounding for takeoff '", takeoffId, "' with timestamp '", timestamp, "', but that timestamp was a long time ago");
            return null;
        }
        Forecast profile = DataStore.loadForecast(takeoffId, Forecast.ForecastType.PROFILE, timestamp);
        if (profile != null) {
            Forecast theta = DataStore.loadForecast(takeoffId, Forecast.ForecastType.THETA, timestamp);
            if (theta != null) {
                Forecast text = DataStore.loadForecast(takeoffId, Forecast.ForecastType.TEXT, timestamp);
                if (text != null) {
                    // return forecasts to client
                    return Arrays.asList(profile, theta, text);
                }
            }
        }
        // need to fetch sounding, theta and text
        Takeoff takeoff = DataStore.loadTakeoff(takeoffId);
        if (takeoff == null) {
            log.w("Client asked for sounding for a takeoff that doesn't seem to exist in our database: ", takeoffId);
            return null;
        }
        List<byte[]> images = NoaaProxy.fetchSounding(takeoff.getLat(), takeoff.getLng(), timestamp);
        if (images == null || images.size() != 3)
            return null;
        // convert "now" and "timestamp" from milliseconds to seconds
        now /= 1000;
        timestamp /= 1000;
        // profile
        profile = new Forecast();
        profile.setTakeoffId(takeoffId);
        profile.setType(Forecast.ForecastType.PROFILE);
        profile.setUpdated(now);
        profile.setValidFor(timestamp);
        profile.setImage(images.get(0));
        DataStore.saveForecast(profile);
        // theta
        Forecast theta = new Forecast();
        theta.setTakeoffId(takeoffId);
        theta.setType(Forecast.ForecastType.THETA);
        theta.setUpdated(now);
        theta.setValidFor(timestamp);
        theta.setImage(images.get(1));
        DataStore.saveForecast(theta);
        // text
        Forecast text = new Forecast();
        text.setTakeoffId(takeoffId);
        text.setType(Forecast.ForecastType.TEXT);
        text.setUpdated(now);
        text.setValidFor(timestamp);
        text.setImage(images.get(2));
        DataStore.saveForecast(text);
        return Arrays.asList(profile, theta, text);
    }

    private void updateTakeoffData() {
        // check for takeoffs updated after the last time we checked a takeoff
        Takeoff takeoff = DataStore.getLastCheckedTakeoff();
        long lastChecked = takeoff == null ? 0 : takeoff.getUpdated();
        long daysToCheck = Math.round((double) (System.currentTimeMillis() - lastChecked) / 86400000.0) + 1;
        if (daysToCheck <= 1)
            return; // less than a day since we last checked
        log.i("Checking for updated takeoffs within the last ", daysToCheck, " days");

        List<Takeoff> takeoffs = FlightlogProxy.fetchUpdatedTakeoffs(daysToCheck);
        if (takeoffs == null)
            return;
        log.i("Found ", takeoffs.size(), " takeoffs updated within the last ", daysToCheck, " days");
        takeoffs.stream().sorted((t1, t2) -> (int) ((t1.getUpdated() - t2.getUpdated()) / 1000)).forEach(updatedTakeoff -> {
            try {
                Takeoff existing = DataStore.loadTakeoff(updatedTakeoff.getId());
                if (!updatedTakeoff.equals(existing))
                    DataStore.saveTakeoff(updatedTakeoff);
                log.i("Updated takeoff(", updatedTakeoff.getId(), "), last updated: ", updatedTakeoff.getUpdated());
            } catch (Exception e) {
                log.w(e, "Unable to update takeoff(", updatedTakeoff.getId(), ")");
            }
        });
    }
}
