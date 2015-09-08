package net.exent.flywithme.server.endpoint;

import com.google.android.gcm.server.Message;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.googlecode.objectify.ObjectifyService;

import net.exent.flywithme.server.bean.Forecast;
import net.exent.flywithme.server.bean.Pilot;
import net.exent.flywithme.server.bean.Schedule;
import net.exent.flywithme.server.bean.Takeoff;
import net.exent.flywithme.server.utils.GcmUtil;
import net.exent.flywithme.server.utils.NoaaProxy;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.inject.Named;

import static com.googlecode.objectify.ObjectifyService.ofy;

/**
 * Created by canidae on 4/4/15.
 */
@Api(name = "flyWithMeServer", version = "v1", namespace = @ApiNamespace(ownerDomain = "server.flywithme.exent.net", ownerName = "server.flywithme.exent.net", packagePath = ""))
public class FlyWithMeEndpoint {
    private static final Logger log = Logger.getLogger(FlyWithMeEndpoint.class.getName());

    public static final long FORECAST_CACHE_LIFETIME = TimeUnit.MILLISECONDS.convert(6, TimeUnit.HOURS);

    static {
        ObjectifyService.register(Forecast.class);
        ObjectifyService.register(Pilot.class);
        ObjectifyService.register(Schedule.class);
        ObjectifyService.register(Takeoff.class);
    }

    /**
     * Register a pilot to the backend.
     *
     * @param pilotId The pilot ID to add.
     */
    @ApiMethod(name = "registerPilot")
    public void registerPilot(@Named("pilotId") String pilotId, @Named("pilotName") String pilotName, @Named("pilotPhone") String pilotPhone) {
        Pilot pilot = fetchPilot(pilotId);
        if (pilot == null)
            pilot = new Pilot().setId(pilotId);
        pilot.setName(pilotName).setPhone(pilotPhone);
        ofy().save().entity(pilot).now();
    }

    /**
     * Unregister a pilot from the backend.
     *
     * @param pilotId The pilot ID to remove.
     */
    @ApiMethod(name = "unregisterPilot")
    public void unregisterPilot(@Named("pilotId") String pilotId) {
        Pilot pilot = fetchPilot(pilotId);
        if (pilot == null) {
            log.info("Pilot " + pilotId + " not registered, skipping unregister");
            return;
        }
        ofy().delete().entity(pilot).now();
    }

    /**
     * Schedule flight at a takeoff.
     *
     * @param pilotId The Pilot ID.
     * @param takeoffId The takeoff ID.
     * @param timestamp Scheduled time, in seconds since epoch.
     */
    @ApiMethod(name = "scheduleFlight")
    public void scheduleFlight(@Named("pilotId") String pilotId, @Named("takeoffId") long takeoffId, @Named("timestamp") long timestamp) {
        Schedule schedule = ofy().load().type(Schedule.class)
                .filter("takeoffId", takeoffId)
                .filter("timestamp", timestamp)
                .first().now();
        if (schedule == null) {
            schedule = new Schedule();
            schedule.setTimestamp(timestamp);
            schedule.setTakeoffId(takeoffId);
        }
        schedule.addPilot(fetchPilot(pilotId));
        ofy().save().entity(schedule).now();
        sendActivityUpdate();
    }

    /**
     * Unschedule flight at a takeoff.
     *
     * @param pilotId The pilot ID.
     * @param takeoffId The takeoff ID.
     * @param timestamp Scheduled time, in seconds since epoch.
     */
    @ApiMethod(name = "unscheduleFlight")
    public void unscheduleFlight(@Named("pilotId") String pilotId, @Named("takeoffId") long takeoffId, @Named("timestamp") long timestamp) {
        Schedule schedule = ofy().load().type(Schedule.class)
                .filter("takeoff", takeoffId)
                .filter("timestamp", timestamp)
                .first().now();
        if (schedule != null) {
            schedule.removePilot(fetchPilot(pilotId));
            ofy().save().entity(schedule).now();
            sendActivityUpdate();
        }
    }

    /**
     * Fetch meteogram for the given takeoff.
     *
     * @param takeoffId The takeoff ID.
     * @return Meteogram for the given takeoff.
     */
    @ApiMethod(name = "getMeteogram")
    public Forecast getMeteogram(@Named("takeoffId") long takeoffId) {
        Forecast forecast = ofy().load().type(Forecast.class)
                .filter("takeoffId", takeoffId)
                .filter("type", Forecast.ForecastType.METEOGRAM)
                .filter("lastUpdated >", System.currentTimeMillis() - FORECAST_CACHE_LIFETIME)
                .first().now();
        if (forecast != null) {
            // return forecast to client
            return forecast;
        }
        // need to fetch forecast
        Takeoff takeoff = fetchTakeoff(takeoffId);
        if (takeoff != null) {
            forecast = new Forecast();
            forecast.setTakeoffId(takeoffId);
            forecast.setType(Forecast.ForecastType.METEOGRAM);
            forecast.setLastUpdated(System.currentTimeMillis());
            forecast.setImage(NoaaProxy.fetchMeteogram(takeoff.getLatitude(), takeoff.getLongitude()));
            ofy().save().entity(forecast).now();
        }
        return forecast;
    }

    /**
     * Fetch sounding profile, theta and text for the given takeoff and time.
     *
     * @param takeoffId The takeoff ID.
     * @param timestamp The timestamp we want sounding for, in milliseconds since epoch.
     * @return Sounding profile, theta and text for the given takeoff and timestamp.
     */
    @ApiMethod(name = "getSounding")
    public List<Forecast> getSounding(@Named("takeoffId") long takeoffId, @Named("timestamp") long timestamp) {
        timestamp = (timestamp / 10800000) * 10800000; // aligns timestamp with valid values for sounding (sounding every 3rd hour)
        if (timestamp < System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS)) {
            log.info("Client tried to retrieve sounding for takeoff '" + takeoffId + "' with timestamp '" + timestamp + "', but that timestamp was a long time ago");
            return null;
        }
        Forecast profile = ofy().load().type(Forecast.class)
                .filter("takeoffId", takeoffId)
                .filter("type", Forecast.ForecastType.PROFILE)
                .filter("validFor", timestamp)
                .filter("lastUpdated >", System.currentTimeMillis() - FORECAST_CACHE_LIFETIME)
                .first().now();
        if (profile != null) {
            Forecast theta = ofy().load().type(Forecast.class)
                    .filter("takeoffId", takeoffId)
                    .filter("type", Forecast.ForecastType.THETA)
                    .filter("validFor", timestamp)
                    .filter("lastUpdated >", System.currentTimeMillis() - FORECAST_CACHE_LIFETIME)
                    .first().now();
            if (theta != null) {
                Forecast text = ofy().load().type(Forecast.class)
                        .filter("takeoffId", takeoffId)
                        .filter("type", Forecast.ForecastType.TEXT)
                        .filter("validFor", timestamp)
                        .filter("lastUpdated >", System.currentTimeMillis() - FORECAST_CACHE_LIFETIME)
                        .first().now();
                if (text != null) {
                    // return forecasts to client
                    return Arrays.asList(profile, theta, text);
                }
            }
        }
        // need to fetch sounding, theta and text
        Takeoff takeoff = fetchTakeoff(takeoffId);
        if (takeoff == null)
            return null;
        List<byte[]> images = NoaaProxy.fetchSounding(takeoff.getLatitude(), takeoff.getLongitude(), timestamp);
        if (images == null || images.size() != 3)
            return null;
        // profile
        profile = new Forecast();
        profile.setTakeoffId(takeoffId);
        profile.setType(Forecast.ForecastType.PROFILE);
        profile.setLastUpdated(System.currentTimeMillis());
        profile.setValidFor(timestamp);
        profile.setImage(images.get(0));
        ofy().save().entity(profile).now();
        // theta
        Forecast theta = new Forecast();
        theta.setTakeoffId(takeoffId);
        theta.setType(Forecast.ForecastType.THETA);
        theta.setLastUpdated(System.currentTimeMillis());
        theta.setValidFor(timestamp);
        theta.setImage(images.get(1));
        ofy().save().entity(theta).now();
        // text
        Forecast text = new Forecast();
        text.setTakeoffId(takeoffId);
        text.setType(Forecast.ForecastType.TEXT);
        text.setLastUpdated(System.currentTimeMillis());
        text.setValidFor(timestamp);
        text.setImage(images.get(2));
        ofy().save().entity(text).now();
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
        return ofy().load().type(Takeoff.class).filter("lastUpdated >=", updatedAfter).list();
    }

    /**
     * Fetch schedule for the given takeoff.
     *
     * @param takeoffId The takeoff ID.
     */
    @ApiMethod(name = "getTakeoffSchedules")
    public Schedule getTakeoffSchedules(@Named("takeoffId") long takeoffId) {
        return ofy().load().type(Schedule.class).filter("takeoffId", takeoffId).first().now();
    }

    private void sendActivityUpdate() {
        // find takeoffs with activity
        long currentTimeSeconds = System.currentTimeMillis();
        List<Schedule> schedules = ofy().load().type(Schedule.class)
                .filter("timestamp >=", currentTimeSeconds - TimeUnit.MILLISECONDS.convert(2, TimeUnit.HOURS))
                .list();
        StringBuilder sb = new StringBuilder();
        for (Schedule schedule : schedules)
            sb.append(schedule.getTakeoffId()).append(',');
        if (sb.length() <= 0)
            return; // no activity
        sb.setLength(sb.length() - 1);

        // send message
        Message msg = new Message.Builder()
                .collapseKey("flywithme-takeoff-activity")
                .delayWhileIdle(true)
                .addData("activity", sb.toString())
                .build();
        GcmUtil.sendToAllClients(msg);
    }

    private Pilot fetchPilot(String pilotId) {
        return ofy().load().type(Pilot.class).id(pilotId).now();
    }

    private Takeoff fetchTakeoff(long takeoffId) {
        return ofy().load().type(Takeoff.class).id(takeoffId).now();
    }
}
