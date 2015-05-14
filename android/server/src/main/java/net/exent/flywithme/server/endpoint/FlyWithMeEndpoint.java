package net.exent.flywithme.server.endpoint;

import com.google.android.gcm.server.Constants;
import com.google.android.gcm.server.Message;
import com.google.android.gcm.server.Result;
import com.google.android.gcm.server.Sender;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.response.CollectionResponse;
import com.googlecode.objectify.ObjectifyService;

import net.exent.flywithme.server.bean.Pilot;
import net.exent.flywithme.server.bean.Takeoff;

import java.io.IOException;
import java.util.List;
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
     * Api Keys can be obtained from the google cloud console
     */
    private static final String API_KEY = System.getProperty("gcm.api.key");

    static {
        ObjectifyService.register(Pilot.class);
        ObjectifyService.register(Takeoff.class);
    }

    // TODO: remove
    /**
     * Send to the first 10 devices (You can modify this to send to any number of devices or a specific device)
     *
     * @param message The message to send
     */
    public void sendMessage(@Named("message") String message) throws IOException {
        if (message == null || message.trim().length() == 0) {
            log.warning("Not sending message because it is empty");
            return;
        }
        // crop longer messages
        if (message.length() > 1000) {
            message = message.substring(0, 1000) + "[...]";
        }
        Sender sender = new Sender(API_KEY);
        Message msg = new Message.Builder().addData("message", message).build();
        List<Pilot> records = ofy().load().type(Pilot.class).limit(10).list();
        for (Pilot record : records) {
            Result result = sender.send(msg, record.getPilotId(), 5);
            if (result.getMessageId() != null) {
                log.info("Message sent to " + record.getPilotId());
                String canonicalRegId = result.getCanonicalRegistrationId();
                if (canonicalRegId != null) {
                    // if the regId changed, we have to update the datastore
                    log.info("Registration Id changed for " + record.getPilotId() + " updating to " + canonicalRegId);
                    record.setPilotId(canonicalRegId);
                    ofy().save().entity(record).now();
                }
            } else {
                String error = result.getErrorCodeName();
                if (error.equals(Constants.ERROR_NOT_REGISTERED)) {
                    log.warning("Registration Id " + record.getPilotId() + " no longer registered with GCM, removing from datastore");
                    // if the device is no longer registered with Gcm, remove it from the datastore
                    ofy().delete().entity(record).now();
                } else {
                    log.warning("Error when sending message : " + error);
                }
            }
        }
    }

    // TODO: remove
    /**
     * Return a collection of registered devices
     *
     * @param count The number of devices to list
     * @return a list of Google Cloud Messaging registration Ids
     */
    @ApiMethod(name = "listDevices")
    public CollectionResponse<Pilot> listDevices(@Named("count") int count) {
        List<Pilot> records = ofy().load().type(Pilot.class).limit(count).list();
        return CollectionResponse.<Pilot>builder().setItems(records).build();
    }

    /**
     * Register a pilot to the backend
     *
     * @param pilotId The Pilot ID to add
     */
    @ApiMethod(name = "registerPilot")
    public void registerPilot(@Named("pilotId") String pilotId) {
        // TODO: more fields (pilot name, phone, etc)
        if (fetchPilot(pilotId) != null) {
            // TODO: if pilot exist, update data
            log.info("Device " + pilotId + " already registered, skipping register");
            return;
        }
        Pilot record = new Pilot();
        record.setPilotId(pilotId);
        ofy().save().entity(record).now();
    }

    /**
     * Unregister a pilot from the backend
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
     * Schedule flight at a takeoff
     *
     * @param pilotId The Pilot ID
     * @param takeoffId The Takeoff ID
     * @param timestamp Scheduled time, in seconds since epoch
     */
    @ApiMethod(name = "scheduleFlight")
    public void scheduleFlight(@Named("pilotId") String pilotId, @Named("takeoffId") int takeoffId, @Named("timestamp") int timestamp) {
        if (fetchPilot(pilotId) == null) {
            // TODO: pilot not registered, tell device to register first?
            return;
        }
        Takeoff takeoff = fetchTakeoff((long) takeoffId);
        if (takeoff == null) {
            // TODO: takeoff not registered? what now?
            return;
        }
        takeoff.addToSchedule(timestamp, pilotId);
    }

    /**
     * Unschedule flight at a takeoff
     *
     * @param pilotId The Pilot ID
     * @param takeoffId The Takeoff ID
     * @param timestamp Scheduled time, in seconds since epoch
     */
    @ApiMethod(name = "unscheduleFlight")
    public void unscheduleFlight(@Named("pilotId") String pilotId, @Named("takeoffId") int takeoffId, @Named("timestamp") int timestamp) {
        if (fetchPilot(pilotId) == null)
            return; // pilot not registered, can't unschedule anything
        Takeoff takeoff = fetchTakeoff((long) takeoffId);
        if (takeoff == null)
            return; // takeoff not registered, can't unschedule
        takeoff.removeFromSchedule(timestamp, pilotId);
    }

    private Pilot fetchPilot(String pilotId) {
        return ofy().load().type(Pilot.class).filter("pilotId", pilotId).first().now();
    }

    private Takeoff fetchTakeoff(Long takeoffId) {
        return ofy().load().type(Takeoff.class).filter("takeoffId", takeoffId).first().now();
    }
}
