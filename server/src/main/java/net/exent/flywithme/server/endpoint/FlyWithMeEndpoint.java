package net.exent.flywithme.server.endpoint;

import com.google.android.gcm.server.Message;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;

import net.exent.flywithme.server.bean.Forecast;
import net.exent.flywithme.server.bean.Pilot;
import net.exent.flywithme.server.bean.Schedule;
import net.exent.flywithme.server.bean.Takeoff;
import net.exent.flywithme.server.util.DataStore;
import net.exent.flywithme.server.util.GcmUtil;
import net.exent.flywithme.server.util.NoaaProxy;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

import javax.inject.Named;

/**
 * Endpoint that handles pilot/schedule/forecast/takeoff data transfer between server and device.
 */
@Api(name = "flyWithMeServer", version = "v1", namespace = @ApiNamespace(ownerDomain = "server.flywithme.exent.net", ownerName = "server.flywithme.exent.net", packagePath = ""))
public class FlyWithMeEndpoint {
    private static final Logger log = Logger.getLogger(FlyWithMeEndpoint.class.getName());

    /**
     * Register a pilot to the backend.
     *
     * @param pilotId The pilot ID to add.
     */
    @ApiMethod(name = "registerPilot")
    public void registerPilot(@Named("pilotId") String pilotId, @Named("pilotName") String pilotName, @Named("pilotPhone") String pilotPhone) {
        Pilot pilot = DataStore.loadPilot(pilotId);
        if (pilot == null)
            pilot = new Pilot().setId(pilotId);
        pilot.setName(pilotName).setPhone(pilotPhone);
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

    // AAH!
    // class "Pilot" won't be in client library unless we return it directly
    // apparently it's not enough that the class is referenced in the Schedule class
    @ApiMethod(name = "anotherAndroidHack_Pilot")
    public Pilot anotherAndroidHack_Pilot() {
        return null;
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
        Schedule schedule = DataStore.loadSchedule(takeoffId, timestamp);
        if (schedule == null) {
            schedule = new Schedule();
            schedule.setTimestamp(timestamp);
            schedule.setTakeoffId(takeoffId);
        }
        schedule.addPilot(DataStore.loadPilot(pilotId));
        DataStore.saveSchedule(schedule);
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
        Schedule schedule = DataStore.loadSchedule(takeoffId, timestamp);
        if (schedule != null) {
            schedule.removePilot(DataStore.loadPilot(pilotId));
            DataStore.saveSchedule(schedule);
            sendActivityUpdate();
        }
    }

    @ApiMethod(name = "getSchedules")
    public List<Schedule> getSchedules() {
        List<Schedule> schedules = DataStore.getAllSchedules();
        // we'll scramble pilotIds, only keep the last few characters for identification
        for (Schedule schedule : schedules) {
            for (Pilot pilot : schedule.getPilots())
                pilot.setId(pilot.getId().substring(pilot.getId().length() - 6));
        }
        return schedules;
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
        if (takeoff != null) {
            forecast = new Forecast();
            forecast.setTakeoffId(takeoffId);
            forecast.setType(Forecast.ForecastType.METEOGRAM);
            forecast.setLastUpdated(System.currentTimeMillis());
            forecast.setImage(NoaaProxy.fetchMeteogram(takeoff.getLatitude(), takeoff.getLongitude()));
            DataStore.saveForecast(forecast);
        }
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
        if (takeoff == null)
            return null;
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

    /**
     * Sends a message to client about takeoffs with activity in the near future.
     */
    private void sendActivityUpdate() {
        // find takeoffs with activity
        List<Schedule> schedules = DataStore.getAllSchedules();
        Map<Long, Set<Long>> activity = new TreeMap<>(); // Map<timestamp, Set<takeoffId>>
        for (Schedule schedule : schedules) {
            Set<Long> takeoffs = activity.get(schedule.getTimestamp());
            if (takeoffs == null) {
                takeoffs = new HashSet<>();
                activity.put(schedule.getTimestamp(), takeoffs);
            }
            takeoffs.add(schedule.getTakeoffId());
        }
        // create message, format is: <timestamp>:<takeoffId>,<takeoffId>,...;<timestamp>:<takeoffId>,...;...
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Long, Set<Long>> entry : activity.entrySet()) {
            sb.append(entry.getKey()).append(':'); // "<timestamp>:"
            for (Long takeoffId : entry.getValue())
                sb.append(takeoffId).append(','); // "<takeoffId>,"
            sb.setLength(sb.length() - 1); // remove the trailing ","
            sb.append(';');
        }
        if (sb.length() <= 0)
            return; // no activity
        sb.setLength(sb.length() - 1); // remove trailing ";"
        if (sb.length() > 4000) {
            log.warning("Too much scheduled activity, trimming away activity farthest into the future");
            while (sb.length() > 4000)
                sb.setLength(sb.lastIndexOf(",")); // remove one takeoff at the time, starting from the end
        }

        // send message
        log.info("Sending activity message to clients");
        Message msg = new Message.Builder()
                .collapseKey("flywithme-takeoff-activity")
                .delayWhileIdle(true)
                .addData("activity", sb.toString())
                .build();
        GcmUtil.sendToAllClients(msg);
    }
}
