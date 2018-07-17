package net.exent.flywithme.server.endpoint;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.config.Named;

import net.exent.flywithme.server.bean.Forecast;
import net.exent.flywithme.server.bean.Pilot;
import net.exent.flywithme.server.bean.Takeoff;
import net.exent.flywithme.server.util.DataStore;
import net.exent.flywithme.server.util.NoaaProxy;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * Endpoint that handles pilot/forecast/takeoff data transfer between server and device.
 */
@Api(name = "flyWithMeServer", version = "v1", namespace = @ApiNamespace(ownerDomain = "server.flywithme.exent.net", ownerName = "server.flywithme.exent.net"))
public class FlyWithMeEndpoint {
    private static final Logger log = Logger.getLogger(FlyWithMeEndpoint.class.getName());

    /**
     * Register a pilot to the backend.
     *
     * @param pilotId The pilot ID to add.
     */
    @ApiMethod(name = "registerPilot")
    public void registerPilot(@Named("pilotId") String pilotId) {
        Pilot pilot = DataStore.loadPilot(pilotId);
        if (pilot == null)
            pilot = new Pilot().setId(pilotId);
        DataStore.savePilot(pilot);
    }

    /**
     * Unregister a pilot from the backend.
     *
     * @param pilotId The pilot ID to remove.
     */
    @ApiMethod(name = "unregisterPilot")
    public void unregisterPilot(@Named("pilotId") String pilotId) {
        log.info("Unregistering pilot: " + pilotId);
        DataStore.deletePilot(pilotId);
    }

    /**
     * Fetch meteogram for the given takeoff.
     *
     * @param takeoffId The takeoff ID.
     * @return Meteogram for the given takeoff.
     */
    @ApiMethod(name = "getMeteogram")
    public Forecast getMeteogram(@Named("takeoffId") long takeoffId) {
        Forecast forecast = DataStore.loadForecast(takeoffId, Forecast.ForecastType.METEOGRAM, 0);
        if (forecast != null) {
            // return forecast to client
            return forecast;
        }
        // need to fetch forecast
        Takeoff takeoff = DataStore.loadTakeoff(takeoffId);
        if (takeoff == null) {
            log.warning("Client asked for meteogram for a takeoff that doesn't seem to exist in our database: " + takeoffId);
            return null;
        }
        forecast = new Forecast();
        forecast.setTakeoffId(takeoffId);
        forecast.setType(Forecast.ForecastType.METEOGRAM);
        forecast.setLastUpdated(System.currentTimeMillis() / 1000);
        forecast.setImage(NoaaProxy.fetchMeteogram(takeoff.getLatitude(), takeoff.getLongitude()));
        DataStore.saveForecast(forecast);
        return forecast;
    }

    /**
     * Fetch sounding profile, theta and text for the given takeoff and time.
     *
     * @param takeoffId The takeoff ID.
     * @param timestamp The timestamp we want sounding for, in seconds since epoch.
     * @return Sounding profile, theta and text for the given takeoff and timestamp.
     */
    @ApiMethod(name = "getSounding")
    public List<Forecast> getSounding(@Named("takeoffId") long takeoffId, @Named("timestamp") long timestamp) {
        timestamp = (timestamp / 10800) * 10800000; // aligns timestamp with valid values for sounding (sounding every 3rd hour) and converts to milliseconds
        long now = System.currentTimeMillis();
        if (timestamp < now - 86400000) { // 86400000 = 1 day
            log.info("Client tried to retrieve sounding for takeoff '" + takeoffId + "' with timestamp '" + timestamp + "', but that timestamp was a long time ago");
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
            log.warning("Client asked for sounding for a takeoff that doesn't seem to exist in our database: " + takeoffId);
            return null;
        }
        List<byte[]> images = NoaaProxy.fetchSounding(takeoff.getLatitude(), takeoff.getLongitude(), timestamp);
        if (images == null || images.size() != 3)
            return null;
        // convert "now" and "timestamp" from milliseconds to seconds
        now /= 1000;
        timestamp /= 1000;
        // profile
        profile = new Forecast();
        profile.setTakeoffId(takeoffId);
        profile.setType(Forecast.ForecastType.PROFILE);
        profile.setLastUpdated(now);
        profile.setValidFor(timestamp);
        profile.setImage(images.get(0));
        DataStore.saveForecast(profile);
        // theta
        Forecast theta = new Forecast();
        theta.setTakeoffId(takeoffId);
        theta.setType(Forecast.ForecastType.THETA);
        theta.setLastUpdated(now);
        theta.setValidFor(timestamp);
        theta.setImage(images.get(1));
        DataStore.saveForecast(theta);
        // text
        Forecast text = new Forecast();
        text.setTakeoffId(takeoffId);
        text.setType(Forecast.ForecastType.TEXT);
        text.setLastUpdated(now);
        text.setValidFor(timestamp);
        text.setImage(images.get(2));
        DataStore.saveForecast(text);
        return Arrays.asList(profile, theta, text);
    }

    /**
     * Fetch all takeoffs that have been updated after the given timestamp.
     *
     * @param updatedAfter The timestamp of the last updated takeoff cached on client.
     * @return A list of takeoffs updated after the given timestamp.
     */
    @ApiMethod(name = "getUpdatedTakeoffs")
    public List<Takeoff> getUpdatedTakeoffs(@Named("updatedAfter") long updatedAfter) {
        return DataStore.getRecentlyUpdatedTakeoffs(updatedAfter);
    }
}
