package net.exent.flywithme.server.servlet;

import com.google.android.gcm.server.Message;
import com.googlecode.objectify.ObjectifyService;

import net.exent.flywithme.server.bean.Forecast;
import net.exent.flywithme.server.bean.Takeoff;
import net.exent.flywithme.server.util.DataStore;
import net.exent.flywithme.server.util.FlightlogCrawler;
import net.exent.flywithme.server.util.GcmUtil;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet for handling task work.
 * This servlet added due to this: http://stackoverflow.com/questions/27113634/google-app-engine-task-queue-gets-a-404-when-invoking-google-cloud-endpoints-api
 */
public class TaskServlet extends HttpServlet {
    private static final Logger log = Logger.getLogger(TaskServlet.class.getName());

    static {
        ObjectifyService.register(Forecast.class);
        ObjectifyService.register(Takeoff.class);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        switch (req.getPathInfo()) {
            case "/cleanCache":
                DataStore.cleanCache();
                break;

            case "/updateTakeoffData":
                updateTakeoffData();
                break;

            default:
                log.log(Level.WARNING, "Unknown task: " + req.getPathInfo());
                break;
        }
    }

    private static void updateTakeoffData() {
        // check for takeoffs updated after the last time we checked a takeoff
        Takeoff takeoff = DataStore.getLastCheckedTakeoff();
        long lastChecked = takeoff == null ? 0 : takeoff.getLastChecked();
        long daysToCheck = Math.round((double) (System.currentTimeMillis() - lastChecked) / 86400000.0) + 1;
        log.info("Checking for updated takeoffs within the last " + daysToCheck + " days");

        for (Long takeoffId : FlightlogCrawler.fetchUpdatedTakeoffs(daysToCheck))
            updateTakeoff(takeoffId);
    }

    private static boolean updateTakeoff(long takeoffId) {
        log.info("Attempting to update takeoff with ID " + takeoffId);
        try {
            Takeoff takeoff = FlightlogCrawler.fetchTakeoff(takeoffId);
            if (takeoff == null)
                return false;
            Takeoff existing = DataStore.loadTakeoff(takeoffId);
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
            DataStore.saveTakeoff(takeoff);
            return true;
        } catch (Exception e) {
            log.log(Level.WARNING, "Unable to update takeoff data", e);
        }
        return false;
    }
}
