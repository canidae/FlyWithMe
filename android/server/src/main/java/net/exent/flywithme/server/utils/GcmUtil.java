package net.exent.flywithme.server.utils;

import com.google.android.gcm.server.Constants;
import com.google.android.gcm.server.Message;
import com.google.android.gcm.server.Result;
import com.google.android.gcm.server.Sender;
import com.googlecode.objectify.ObjectifyService;

import net.exent.flywithme.server.bean.Pilot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.googlecode.objectify.ObjectifyService.ofy;

/**
 * Created by canidae on 7/12/15.
 */
public class GcmUtil {
    private static final Logger log = Logger.getLogger(GcmUtil.class.getName());

    private static final String API_KEY = System.getProperty("gcm.api.key");
    private static final int MAX_MULTICAST_RECIPIENTS = 1000;

    static {
        ObjectifyService.register(Pilot.class);
    }

    public static void sendToAllClients(Message message) {
        log.info("Sending message to all clients: " + message);
        // find all pilots (clients)
        List<Pilot> pilots = ofy().load().type(Pilot.class).list();
        List<String> sendTo = new ArrayList<>();
        for (Pilot pilot : pilots)
            sendTo.add(pilot.getId());

        // send update to clients
        Sender sender = new Sender(API_KEY);
        List<Result> results = new ArrayList<>();
        for (int start = 0; start < sendTo.size(); start += MAX_MULTICAST_RECIPIENTS) {
            int stop = Math.min(sendTo.size() - start, MAX_MULTICAST_RECIPIENTS);
            try {
                results.addAll(sender.send(message, sendTo.subList(start, stop), 5).getResults());
            } catch (IOException e) {
                log.log(Level.WARNING, "Failed sending activity to clients", e);
            }
        }

        // check status for messages sent
        for (int i = 0; i < results.size(); ++i) {
            Result result = results.get(i);
            Pilot pilot = pilots.get(i);
            if (result.getMessageId() != null) {
                String canonicalRegId = result.getCanonicalRegistrationId();
                if (canonicalRegId != null) {
                    // if the regId changed, we have to update the datastore
                    log.info("Registration Id changed for " + pilot.getId() + " updating to " + canonicalRegId);
                    pilot.setId(canonicalRegId);
                    ofy().save().entity(pilot).now();
                }
            } else {
                String error = result.getErrorCodeName();
                if (error.equals(Constants.ERROR_NOT_REGISTERED)) {
                    log.warning("Registration Id " + pilot.getId() + " no longer registered with GCM, removing from datastore");
                    // if the device is no longer registered with Gcm, remove it from the datastore
                    ofy().delete().entity(pilot).now();
                } else {
                    log.warning("Error when sending message : " + error);
                }
            }
        }
    }
}