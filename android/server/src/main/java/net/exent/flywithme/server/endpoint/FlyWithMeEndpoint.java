package net.exent.flywithme.server.endpoint;

import com.google.android.gcm.server.Constants;
import com.google.android.gcm.server.Message;
import com.google.android.gcm.server.Result;
import com.google.android.gcm.server.Sender;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.googlecode.objectify.ObjectifyService;

import net.exent.flywithme.server.bean.Forecast;
import net.exent.flywithme.server.bean.Pilot;
import net.exent.flywithme.server.bean.Schedule;
import net.exent.flywithme.server.bean.Takeoff;
import net.exent.flywithme.server.utils.FlightlogCrawler;
import net.exent.flywithme.server.utils.NoaaProxy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Named;

import static com.googlecode.objectify.ObjectifyService.ofy;

/**
 * Created by canidae on 4/4/15.
 */
@Api(name = "flyWithMeServer", version = "v1", namespace = @ApiNamespace(ownerDomain = "server.flywithme.exent.net", ownerName = "server.flywithme.exent.net", packagePath = ""))
public class FlyWithMeEndpoint {
    private static final Logger log = Logger.getLogger(FlyWithMeEndpoint.class.getName());

    /**
     * Api Keys can be obtained from the google cloud console.
     */
    private static final String API_KEY = System.getProperty("gcm.api.key");
    private static final int MAX_MULTICAST_RECIPIENTS = 1000;

    static {
        ObjectifyService.register(Forecast.class);
        ObjectifyService.register(Pilot.class);
        ObjectifyService.register(Schedule.class);
        ObjectifyService.register(Takeoff.class);
    }

    /**
     * Register a pilot to the backend.
     *
     * @param pilotId The Pilot ID to add
     */
    @ApiMethod(name = "registerPilot")
    public void registerPilot(@Named("pilotId") String pilotId) {
        // TODO: more fields (pilot name, phone, etc)
        Pilot pilot = fetchPilot((pilotId));
        if (pilot == null)
            pilot = new Pilot();
        pilot.setPilotId(pilotId);
        ofy().save().entity(pilot).now();
    }

    /**
     * Unregister a pilot from the backend.
     *
     * @param pilotId The Pilot ID to remove
     */
    @ApiMethod(name = "unregisterPilot")
    public void unregisterPilot(@Named("pilotId") String pilotId) {
        Pilot pilot = fetchPilot(pilotId);
        if (pilot == null) {
            log.info("Device " + pilotId + " not registered, skipping unregister");
            return;
        }
        ofy().delete().entity(pilot).now();
    }

    /**
     * Schedule flight at a takeoff.
     *
     * @param pilotId The Pilot ID
     * @param takeoffId The Takeoff ID
     * @param timestamp Scheduled time, in seconds since epoch
     */
    @ApiMethod(name = "scheduleFlight")
    public void scheduleFlight(@Named("pilotId") String pilotId, @Named("takeoffId") int takeoffId, @Named("timestamp") int timestamp) {
        Schedule schedule = ofy().load().type(Schedule.class)
                .filter("takeoffId", takeoffId)
                .filter("timestamp", timestamp).first().now();
        if (schedule == null) {
            schedule = new Schedule();
            schedule.setTimestamp(timestamp);
            schedule.setTakeoff(takeoffId);
        }
        schedule.addPilot(pilotId);
        ofy().save().entity(schedule).now();
        sendActivityUpdate();
    }

    /**
     * Unschedule flight at a takeoff.
     *
     * @param pilotId The Pilot ID
     * @param takeoffId The Takeoff ID
     * @param timestamp Scheduled time, in seconds since epoch
     */
    @ApiMethod(name = "unscheduleFlight")
    public void unscheduleFlight(@Named("pilotId") String pilotId, @Named("takeoffId") int takeoffId, @Named("timestamp") int timestamp) {
        Schedule schedule = ofy().load().type(Schedule.class)
                .filter("takeoffId", takeoffId)
                .filter("timestamp", timestamp).first().now();
        if (schedule != null) {
            schedule.removePilot(pilotId);
            ofy().save().entity(schedule).now();
        }
    }

    /**
     * Fetch meteogram for the given takeoff.
     *
     * @param takeoffId The Takeoff ID.
     */
    @ApiMethod(name = "getMeteogram")
    public Forecast getMeteogram(@Named("takeoffId") long takeoffId) {
        Forecast forecast = ofy().load().type(Forecast.class)
                .filter("takeoffId", takeoffId)
                .filter("type", Forecast.ForecastType.METEOGRAM)
                .filter("lastUpdated <", System.currentTimeMillis() + 21600000)
                .first().now();
        if (forecast != null) {
            // return forecast to client
            return forecast;
        }
        // need to fetch forecast
        Takeoff takeoff = fetchTakeoff(takeoffId);
        if (takeoff == null) {
            updateTakeoff(takeoffId);
            takeoff = fetchTakeoff(takeoffId);
        }
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
     * Fetch sounding for the given takeoff and time.
     *
     * @param takeoffId The Takeoff ID.
     * @param timestamp The timestamp we want sounding for, in milliseconds since epoch.
     */
    @ApiMethod(name = "getSounding")
    public Forecast getSounding(@Named("takeoffId") long takeoffId, @Named("timestamp") long timestamp) {
        timestamp = (timestamp / 10800000) * 10800000; // aligns timestamp with valid values for sounding (sounding every 3rd hour)
        if (timestamp < System.currentTimeMillis() - 86400000) {
            log.info("Client tried to retrieve sounding for takeoff '" + takeoffId + "' with timestamp '" + timestamp + "', but that timestamp was a long time ago");
            return null;
        }
        Forecast forecast = ofy().load().type(Forecast.class)
                .filter("takeoffId", takeoffId)
                .filter("type", Forecast.ForecastType.SOUNDING)
                .filter("validFor", timestamp)
                .filter("lastUpdated <", System.currentTimeMillis() + 21600000).first().now();
        if (forecast != null) {
            // return forecast to client
            return forecast;
        }
        // need to fetch forecast
        Takeoff takeoff = fetchTakeoff(takeoffId);
        if (takeoff == null) {
            updateTakeoff(takeoffId);
            takeoff = fetchTakeoff(takeoffId);
        }
        if (takeoff != null) {
            forecast = new Forecast();
            forecast.setTakeoffId(takeoffId);
            forecast.setType(Forecast.ForecastType.SOUNDING);
            forecast.setLastUpdated(System.currentTimeMillis());
            forecast.setValidFor(timestamp);
            forecast.setImage(NoaaProxy.fetchSounding(takeoff.getLatitude(), takeoff.getLongitude(), timestamp));
            ofy().save().entity(forecast).now();
        }
        return forecast;
    }

    /**
     * Query flightlog.org for updates for the given takeoff.
     *
     * @param takeoffId The Takeoff ID
     */
    // TODO: remove? how will we start the task loop thingy?
    @ApiMethod(name = "updateTakeoff") //, path = "task/updateTakeoff")
    public void updateTakeoff(@Named("takeoffId") long takeoffId) {
        Takeoff takeoff = FlightlogCrawler.fetchTakeoff(takeoffId);
        if (takeoff != null) {
            Takeoff existing = fetchTakeoff(takeoffId);
            if (existing == null || !takeoff.equals(existing))
                ofy().save().entity(takeoff).now();
        }

        // TODO: possible problem: http://stackoverflow.com/questions/27113634/google-app-engine-task-queue-gets-a-404-when-invoking-google-cloud-endpoints-api

        ++takeoffId; // TODO: wrap around when we haven't found a valid takeoff ID in a while
        Queue queue = QueueFactory.getDefaultQueue(); // http://localhost:8080/_ah/api/flyWithMeServer/v1/forecast/4
        TaskOptions task = TaskOptions.Builder.withTaskName("Takeoff-" + takeoffId).countdownMillis(1000).url("/task/updateTakeoff").param("takeoffId", "" + takeoffId).param("takeoffNotFoundCounter", "0").method(TaskOptions.Method.POST);
        TaskHandle taskHandle = queue.add(task);
        log.info("Added task: " + taskHandle);
    }

    private void sendActivityUpdate() {
        // find takeoffs with activity from 2 hours ago until 12 hours into the future
        long currentTimeSeconds = System.currentTimeMillis();
        List<Schedule> schedules = ofy().load().type(Schedule.class)
                .filter("timestamp >=", currentTimeSeconds - 7200000)
                .filter("timestamp <", currentTimeSeconds + 43200000).list();
        StringBuilder sb = new StringBuilder();
        for (Schedule schedule : schedules)
            sb.append(schedule.getTakeoff()).append(',');
        if (sb.length() <= 0)
            return; // no activity
        sb.deleteCharAt(sb.length() - 1);

        // find all pilots (clients) we wish to send to
        List<Pilot> pilots = ofy().load().type(Pilot.class).list();
        List<String> sendTo = new ArrayList<>();
        for (Pilot pilot : pilots)
            sendTo.add(pilot.getPilotId());

        // create a message
        Sender sender = new Sender(API_KEY);
        Message msg = new Message.Builder()
                .collapseKey("flywithme-activity")
                .delayWhileIdle(true)
                .addData("activity", sb.toString())
                .build();

        // send update to clients
        List<Result> results = new ArrayList<>();
        for (int start = 0; start < sendTo.size(); start += MAX_MULTICAST_RECIPIENTS) {
            int stop = Math.min(sendTo.size() - start, MAX_MULTICAST_RECIPIENTS);
            try {
                results.addAll(sender.send(msg, sendTo.subList(start, stop), 5).getResults());
            } catch (IOException e) {
                log.log(Level.WARNING, "Failed sending activity to clients", e);
            }
        }

        // check status for messages sent
        for (int i = 0; i < results.size(); ++i) {
            Result result = results.get(i);
            Pilot pilot = pilots.get(i);
            if (result.getMessageId() != null) {
                log.info("Message sent to " + pilot.getPilotId());
                String canonicalRegId = result.getCanonicalRegistrationId();
                if (canonicalRegId != null) {
                    // if the regId changed, we have to update the datastore
                    log.info("Registration Id changed for " + pilot.getPilotId() + " updating to " + canonicalRegId);
                    pilot.setPilotId(canonicalRegId);
                    ofy().save().entity(pilot).now();
                }
            } else {
                String error = result.getErrorCodeName();
                if (error.equals(Constants.ERROR_NOT_REGISTERED)) {
                    log.warning("Registration Id " + pilot.getPilotId() + " no longer registered with GCM, removing from datastore");
                    // if the device is no longer registered with Gcm, remove it from the datastore
                    ofy().delete().entity(pilot).now();
                } else {
                    log.warning("Error when sending message : " + error);
                }
            }
        }

    }

    private Pilot fetchPilot(String pilotId) {
        return ofy().load().type(Pilot.class).filter("pilotId", pilotId).first().now();
    }

    private Takeoff fetchTakeoff(long takeoffId) {
        return ofy().load().type(Takeoff.class).filter("takeoffId", takeoffId).first().now();
    }
}
