package net.exent.flywithme.server.servlet;

import com.google.android.gcm.server.Message;
import com.googlecode.objectify.ObjectifyService;

import net.exent.flywithme.server.bean.Forecast;
import net.exent.flywithme.server.bean.Takeoff;
import net.exent.flywithme.server.endpoint.FlyWithMeEndpoint;
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

    private static final int MAX_TAKEOFF_ID_GAP = 50;

    static {
        ObjectifyService.register(Forecast.class);
        ObjectifyService.register(Takeoff.class);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        switch (req.getPathInfo()) {
            case "/cleanCache":
                cleanCache();
                break;

            case "/updateNextTakeoffData":
                updateNextTakeoffData();
                break;

            default:
                log.log(Level.WARNING, "Unknown task: " + req.getPathInfo());
                break;
        }
    }

    private static void cleanCache() {
        ofy().delete().entities(ofy().load().type(Forecast.class).filter("lastUpdated <=", System.currentTimeMillis() - FlyWithMeEndpoint.FORECAST_CACHE_LIFETIME).list());
    }

    private static void updateNextTakeoffData() {
        // update the takeoff after the last checked takeoff
        Takeoff lastChecked = ofy().load().type(Takeoff.class).order("-lastChecked").first().now();
        long currentId = lastChecked == null ? 1 : lastChecked.getId() + 1;
        long maxId = currentId + MAX_TAKEOFF_ID_GAP;
        for (; currentId < maxId; ++currentId) {
            if (updateTakeoff(currentId))
                return;
        }
        // couldn't find a takeoff with ID within MAX_TAKEOFF_ID_GAP after last updated takeoff, wrap around to start from ID 1
        currentId = 1;
        maxId = currentId + MAX_TAKEOFF_ID_GAP;
        for (; currentId < maxId; ++currentId) {
            if (updateTakeoff(currentId))
                return;
        }
    }

    private static boolean updateTakeoff(long takeoffId) {
        try {
            Takeoff takeoff = FlightlogCrawler.fetchTakeoff(takeoffId);
            if (takeoff != null) {
                Takeoff existing = ofy().load().type(Takeoff.class).id(takeoffId).now(); // TODO: this increase datastore read ops, is it a problem? can we remove it? "update where new data doesn't match old data"?
                if (existing != null && takeoff.equals(existing)) {
                    takeoff.setLastUpdated(existing.getLastUpdated()); // data not changed, keep "lastUpdated"
                } else {
                    log.info("Updated data for takeoff with ID " + takeoffId);
                    // send message to clients, letting them know a takeoff was added/updated
                    Message msg = new Message.Builder()
                            .collapseKey("flywithme-takeoff-updated")
                            .delayWhileIdle(true)
                            .addData("takeoffUpdated", "" + takeoff.getId())
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
