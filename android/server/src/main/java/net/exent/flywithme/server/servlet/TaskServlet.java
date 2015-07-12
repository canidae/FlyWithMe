package net.exent.flywithme.server.servlet;

import com.google.android.gcm.server.Message;
import com.googlecode.objectify.ObjectifyService;

import net.exent.flywithme.server.bean.Property;
import net.exent.flywithme.server.bean.Takeoff;
import net.exent.flywithme.server.utils.FlightlogCrawler;
import net.exent.flywithme.server.utils.GcmUtil;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.googlecode.objectify.ObjectifyService.ofy;

/**
 * Servlet for handling task work.
 * This servlet added due to this: http://stackoverflow.com/questions/27113634/google-app-engine-task-queue-gets-a-404-when-invoking-google-cloud-endpoints-api
 */
public class TaskServlet extends HttpServlet {
    private static final Logger log = Logger.getLogger(TaskServlet.class.getName());

    static {
        ObjectifyService.register(Property.class);
        ObjectifyService.register(Takeoff.class);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        switch (req.getPathInfo()) {
            case "/updateNextTakeoffData":
                updateNextTakeoffData();
                break;

            default:
                log.log(Level.WARNING, "Unknown task: " + req.getPathInfo());
                break;
        }
    }

    private static void updateNextTakeoffData() {
        // increase takeoff id counter
        Property currentIdProperty = ofy().load().type(Property.class).id("scanCurrentId").now();
        if (currentIdProperty == null)
            currentIdProperty = new Property("scanCurrentId", 0);
        currentIdProperty.setValue(currentIdProperty.getValueAsLong() + 1);
        ofy().save().entity(currentIdProperty).now();

        // either reset or increase failed counter
        Property failedInARow = ofy().load().type(Property.class).id("scanFailedInARow").now();
        if (failedInARow == null)
            failedInARow = new Property("scanFailedInARow", 0);
        else if (failedInARow.getValueAsInt() > 50)
            return; // failed so many times in a row that we've either found all takeoffs or we should just give up
        failedInARow.setValue(updateTakeoff(currentIdProperty.getValueAsLong()) ? 0 : failedInARow.getValueAsInt() + 1);
        ofy().save().entity(failedInARow).now();
    }

    private static boolean updateTakeoff(long takeoffId) {
        try {
            Takeoff existing = ofy().load().type(Takeoff.class).id(takeoffId).now(); // TODO: this increase datastore read ops, is it a problem? can we remove it? "update where new data doesn't match old data"?
            Takeoff takeoff = FlightlogCrawler.fetchTakeoff(takeoffId);
            if (takeoff != null) {
                if (existing != null && takeoff.equals(existing)) {
                    takeoff.setLastUpdated(existing.getLastUpdated()); // data not changed, keep "lastUpdated"
                    log.info("Updated data for takeoff with ID " + takeoffId);
                    // send message to clients, letting them know a takeoff was updated
                    Message msg = new Message.Builder()
                            .collapseKey("flywithme-takeoff-updated")
                            .delayWhileIdle(true)
                            .build();
                    GcmUtil.sendToAllClients(msg);
                }
                ofy().save().entity(takeoff).now();
                return true;
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Unable to update takeoff data", e);
        }
        return false;
    }
}
