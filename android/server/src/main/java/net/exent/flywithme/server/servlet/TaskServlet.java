package net.exent.flywithme.server.servlet;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TaskOptions;

import net.exent.flywithme.server.bean.Property;
import net.exent.flywithme.server.bean.Takeoff;
import net.exent.flywithme.server.utils.FlightlogCrawler;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
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

    private static final long FULL_SCAN_INTERVAL = TimeUnit.MILLISECONDS.convert(73, TimeUnit.DAYS);
    private static final long NEW_SCAN_INTERVAL = TimeUnit.MILLISECONDS.convert(14, TimeUnit.DAYS);
    private static final long UPDATE_TAKEOFF_INTERVAL = TimeUnit.MILLISECONDS.convert(14, TimeUnit.DAYS);

    public static void checkTasks() {
        checkTakeoffData();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        switch (req.getPathInfo()) {
            case "/checkTasks":
                checkTasks();
                break;

            case "/scanTakeoffs":
                scanTakeoffs();
                break;

            default:
                log.log(Level.WARNING, "Unknown task: " + req.getPathInfo());
                break;
        }
    }

    private static void checkTakeoffData() {
        long currentTimeMillis = System.currentTimeMillis();
        long nextCheck = TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS);
        try {
            // check if we should do a full scan
            Property lastFullScan = ofy().load().type(Property.class).id("lastFullScan").now();
            if (lastFullScan == null)
                lastFullScan = new Property("lastFullScan", 0);
            if (lastFullScan.getValueAsLong() + FULL_SCAN_INTERVAL < currentTimeMillis) {
                log.info("Starting full scan of flightlog.org");
                // set "lastFullScan" to current timestamp to prevent multiple instances from scanning at the same time
                lastFullScan.setValue(currentTimeMillis);
                ofy().save().entity(lastFullScan).now();

                // reset properties used for scanning
                ofy().save().entity(new Property("scanCurrentId", 0)).now();
                ofy().save().entity(new Property("scanFailedInARow", 0)).now();

                // also reset lastNewScan to prevent two scans running at the same time (or shortly after)
                ofy().save().entity(new Property("lastNewScan", currentTimeMillis)).now();

                // start scanning
                scanTakeoffs();

                // no need to check for new or changed takeoffs in a while when we do a full scan
                return;
            }

            // check if we should scan for new takeoffs
            Property lastNewScan = ofy().load().type(Property.class).id("lastNewScan").now();
            if (lastNewScan == null)
                lastNewScan = new Property("lastNewScan", 0);
            if (lastNewScan.getValueAsLong() + NEW_SCAN_INTERVAL < currentTimeMillis) {
                log.info("Starting scan of new takeoffs at flightlog.org");
                // set "lastNewScan" to current timestamp to prevent multiple instances from scanning at the same time
                lastNewScan.setValue(currentTimeMillis);
                ofy().save().entity(lastNewScan).now();

                // reset properties used for scanning
                Takeoff takeoffWithHighestId = ofy().load().type(Takeoff.class).order("-takeoffId").first().now();
                long startIndex = takeoffWithHighestId == null ? 0 : takeoffWithHighestId.getId();
                ofy().save().entity(new Property("scanCurrentId", startIndex)).now();
                ofy().save().entity(new Property("scanFailedInARow", 0)).now();

                // if time to next full scan is less than time to next new scan we will set time to next full scan to the same as next new scan
                // this will prevent full scan from running just after a new scan
                if (lastNewScan.getValueAsLong() - lastFullScan.getValueAsLong() < NEW_SCAN_INTERVAL)
                    ofy().save().entity(new Property("lastFullScan", currentTimeMillis)).now();

                // start scanning
                scanTakeoffs();
            }

            Takeoff lastCheckedTakeoff = ofy().load().type(Takeoff.class).order("lastChecked").first().now();
            if (lastCheckedTakeoff != null && lastCheckedTakeoff.getLastChecked() + UPDATE_TAKEOFF_INTERVAL < currentTimeMillis)
                updateTakeoff(lastCheckedTakeoff.getId());
            lastCheckedTakeoff = ofy().load().type(Takeoff.class).order("lastChecked").first().now();
            if (lastCheckedTakeoff != null) {
                nextCheck = currentTimeMillis - (lastCheckedTakeoff.getLastChecked() + UPDATE_TAKEOFF_INTERVAL);
                if (nextCheck < 0)
                    nextCheck = 0;
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "checkTakeoffData() failed", e);
        } finally {
            // start a new task to check data again
            Queue queue = QueueFactory.getDefaultQueue();
            TaskOptions task = TaskOptions.Builder.withTaskName("CheckTakeoffData")
                    .countdownMillis(nextCheck + (long) (Math.random() * 5000.0))
                    .url("/task/checkTasks")
                    .method(TaskOptions.Method.POST);
            TaskHandle taskHandle = queue.add(task);
            log.info("Added check task: " + taskHandle);
        }
    }

    private static void scanTakeoffs() {
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

        // continue with next takeoff
        Queue queue = QueueFactory.getDefaultQueue();
        queue.deleteTask("ScanTakeoffs");
        TaskOptions task = TaskOptions.Builder.withCountdownMillis(2718)
                .url("/task/scanTakeoffs")
                .method(TaskOptions.Method.POST);
        TaskHandle taskHandle = queue.add(task);
        log.info("Added scan task: " + taskHandle);
    }

    private static boolean updateTakeoff(long takeoffId) {
        try {
            Takeoff existing = ofy().load().type(Takeoff.class).id(takeoffId).now();
            if (existing == null || existing.getLastChecked() > System.currentTimeMillis() + 86400000) {
                // we may have multiple instances, check above is to prevent multiple tasks querying flightlog for same takeoff at the same time
                Takeoff takeoff = FlightlogCrawler.fetchTakeoff(takeoffId);
                if (takeoff != null) {
                    if (existing != null && takeoff.equals(existing))
                        takeoff.setLastUpdated(existing.getLastUpdated()); // data not changed, keep "lastUpdated"
                    ofy().save().entity(takeoff).now();
                    return true;
                }
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Unable to update takeoff data", e);
        }
        return false;
    }
}
