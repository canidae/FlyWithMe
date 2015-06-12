package net.exent.flywithme.server.servlet;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TaskOptions;

import net.exent.flywithme.server.bean.Takeoff;
import net.exent.flywithme.server.utils.FlightlogCrawler;

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

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        log.info("'" + req.getPathInfo() + "'");
        switch (req.getPathInfo()) {
            case "/updateTakeoff":
                try {
                    long takeoffId = Long.parseLong(req.getParameter("takeoffId"));
                    long takeoffNotFoundCounter = Long.parseLong(req.getParameter("takeoffNotFoundCounter"));
                    updateTakeoff(takeoffId, takeoffNotFoundCounter);
                } catch (Exception e) {
                    log.log(Level.WARNING, "Unable to parse required parameters", e);
                }
                break;

            default:
                break;
        }
    }

    private void updateTakeoff(long takeoffId, long takeoffNotFoundCounter) {
        try {
            Takeoff existing = ofy().load().type(Takeoff.class).filter("takeoffId", takeoffId).first().now();
            if (existing == null || existing.getLastChecked() > System.currentTimeMillis() + 86400000) {
                // we may have multiple instances, check above is to prevent multiple tasks querying flightlog for same takeoff at the same time
                Takeoff takeoff = FlightlogCrawler.fetchTakeoff(takeoffId);
                if (takeoff != null) {
                    takeoffNotFoundCounter = 0;
                    if (existing != null && takeoff.equals(existing))
                        takeoff.setLastUpdated(existing.getLastUpdated()); // data not changed, keep "lastUpdated"
                    ofy().save().entity(takeoff).now();
                } else {
                    ++takeoffNotFoundCounter;
                }
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Unable to update takeoff data", e);
        }

        // TODO: improvements:
        // - it's unlikely that new takeoffs with id lower than highest known id will appear, should only be scanned once in a blue moon
        // - update the takeoff last checked, update takeoffs about every 2nd week

        if (takeoffNotFoundCounter >= 50)
            takeoffId = 1;
        else
            ++takeoffId;
        Queue queue = QueueFactory.getDefaultQueue();
        TaskOptions task = TaskOptions.Builder.withTaskName("Takeoff-" + takeoffId)
                .countdownMillis(60000)
                .url("/task/updateTakeoff")
                .param("takeoffId", "" + takeoffId)
                .param("takeoffNotFoundCounter", "" + takeoffNotFoundCounter)
                .method(TaskOptions.Method.POST);
        TaskHandle taskHandle = queue.add(task);
        log.info("Added task: " + taskHandle);
    }
}
