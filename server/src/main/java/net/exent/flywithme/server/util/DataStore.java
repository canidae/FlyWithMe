package net.exent.flywithme.server.util;

import com.google.appengine.api.memcache.ErrorHandlers;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.googlecode.objectify.ObjectifyService;

import net.exent.flywithme.server.bean.Forecast;
import net.exent.flywithme.server.bean.Pilot;
import net.exent.flywithme.server.bean.Schedule;
import net.exent.flywithme.server.bean.Takeoff;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.googlecode.objectify.ObjectifyService.ofy;

/**
 * This class handles loading and storing data to the Google Datastore.
 * It will also use Memcache to reduce amount of calls to Google Datastore (which is limited by a quota).
 */
public class DataStore {
    private static final Logger log = Logger.getLogger(DataStore.class.getName());
    private static final String TAKEOFF_RECENTLY_UPDATED_KEY = "takeoff_recently_updated";
    private static final long FORECAST_CACHE_LIFETIME = 21600000; // 6 hours

    static {
        ObjectifyService.register(Forecast.class);
        ObjectifyService.register(Pilot.class);
        ObjectifyService.register(Schedule.class);
        ObjectifyService.register(Takeoff.class);
    }

    public static Takeoff loadTakeoff(long takeoffId) {
        String key = "takeoff_" + takeoffId;
        Takeoff takeoff = (Takeoff) memcacheLoad(key);
        if (takeoff == null) {
            takeoff = ofy().load().type(Takeoff.class).id(takeoffId).now();
            memcacheSave(key, takeoff);
        }
        return takeoff;
    }

    public static void saveTakeoff(Takeoff takeoff) {
        memcacheDelete(TAKEOFF_RECENTLY_UPDATED_KEY); // NOTE: important! or users may not receive updated takeoffs
        ofy().save().entity(takeoff).now();
        String key = "takeoff_" + takeoff.getId();
        memcacheSave(key, takeoff);
    }

    public static Takeoff getLastCheckedTakeoff() {
        // this is only called when updating takeoffs (not very often), no need to memcache
        return ofy().load().type(Takeoff.class).order("-lastChecked").first().now();
    }

    public static List<Takeoff> getRecentlyUpdatedTakeoffs(long updatedAfter) {
        // many people are likely to supply the same "updatedAfter" timestamp
        // so we'll just add a memcache entry with a list of takeoffs updated after the given timestamp
        // NOTE: we must delete the memcache entry when we update a takeoff
        List recentlyUpdated = (List) memcacheLoad(TAKEOFF_RECENTLY_UPDATED_KEY);
        if (recentlyUpdated != null) {
            try {
                List<Takeoff> takeoffs = new ArrayList<>();
                for (Object takeoffId : recentlyUpdated)
                    takeoffs.add(loadTakeoff((Long) takeoffId));
                return takeoffs;
            } catch (Exception e) {
                log.log(Level.WARNING, "Something's wrong with memcache entry for recently updated takeoffs", e);
            }
        }
        List<Takeoff> takeoffs = ofy().load().type(Takeoff.class).filter("lastUpdated >=", updatedAfter).list();
        List<Long> newRecentlyUpdated = new ArrayList<>();
        for (Takeoff takeoff : takeoffs)
            newRecentlyUpdated.add(takeoff.getId());
        memcacheSave(TAKEOFF_RECENTLY_UPDATED_KEY, newRecentlyUpdated);
        return takeoffs;
    }

    public static Pilot loadPilot(String pilotId) {
        String key = "pilot_" + pilotId;
        Pilot pilot = (Pilot) memcacheLoad(key);
        if (pilot == null) {
            pilot = ofy().load().type(Pilot.class).id(pilotId).now();
            memcacheSave(key, pilot);
        }
        return pilot;
    }

    public static void savePilot(Pilot pilot) {
        ofy().save().entity(pilot).now();
        String key = "pilot_" + pilot.getId();
        memcacheSave(key, pilot);
    }

    public static void deletePilot(String pilotId) {
        Pilot pilot = loadPilot(pilotId);
        if (pilot == null) {
            return;
        }
        String key = "pilot_" + pilot.getId();
        memcacheDelete(key);
        ofy().delete().entity(pilot).now();
    }

    public static Schedule loadSchedule(long takeoffId, long timestamp) {
        String key = "schedule_" + takeoffId + "_" + timestamp;
        Schedule schedule = (Schedule) memcacheLoad(key);
        if (schedule == null) {
            schedule = ofy().load().type(Schedule.class)
                    .filter("takeoffId", takeoffId)
                    .filter("timestamp", timestamp)
                    .first().now();
        }
        return schedule;
    }

    public static void saveSchedule(Schedule schedule) {
        ofy().save().entity(schedule).now();
        String key = "schedule_" + schedule.getTakeoffId() + "_" + schedule.getTimestamp();
        memcacheSave(key, schedule);
    }

    public static List<Schedule> getUpcomingSchedules() {
        // TODO: memcache
        List<Schedule> schedules = ofy().load().type(Schedule.class)
                .filter("timestamp >=", System.currentTimeMillis() - 7200000) // 2 hours
                .list();
        return schedules;
    }

    public static Forecast loadForecast(long takeoffId, Forecast.ForecastType type, long validFor) {
        String key = "forecast_" + takeoffId + "_" + type + "_" + System.currentTimeMillis() / FORECAST_CACHE_LIFETIME + "_" + validFor;
        Forecast forecast = (Forecast) memcacheLoad(key);
        if (forecast == null) {
            forecast = ofy().load().type(Forecast.class)
                    .filter("takeoffId", takeoffId)
                    .filter("type", type)
                    .filter("validFor", validFor)
                    .filter("lastUpdated >", System.currentTimeMillis() - FORECAST_CACHE_LIFETIME / 2)
                    .first().now();
            memcacheSave(key, forecast);
        }
        return forecast;
    }

    public static void saveForecast(Forecast forecast) {
        ofy().save().entity(forecast).now();
        String key = "forecast_" + forecast.getTakeoffId() + "_" + forecast.getType() + "_" + System.currentTimeMillis() / FORECAST_CACHE_LIFETIME + "_" + forecast.getValidFor();
        memcacheSave(key, forecast);
    }

    public static void cleanCache() {
        // clean forecasts cached in datastore (Memcache cleans itself)
        ofy().delete().entities(ofy().load().type(Forecast.class).filter("lastUpdated <=", System.currentTimeMillis() - FORECAST_CACHE_LIFETIME).list());
    }

    private static Object memcacheLoad(String key) {
        try {
            MemcacheService memcache = MemcacheServiceFactory.getMemcacheService();
            memcache.setErrorHandler(ErrorHandlers.getConsistentLogAndContinue(Level.INFO));
            return memcache.get(key);
        } catch (Exception e) {
            log.log(Level.WARNING, "Unable to load object from memcache. Key: " + key, e);
            return null;
        }
    }

    private static void memcacheSave(String key, Object object) {
        try {
            MemcacheService memcache = MemcacheServiceFactory.getMemcacheService();
            memcache.setErrorHandler(ErrorHandlers.getConsistentLogAndContinue(Level.INFO));
            memcache.put(key, object);
        } catch (Exception e) {
            log.log(Level.WARNING, "Unable to save object to memcache. Key: " + key, e);
        }
    }

    private static Object memcacheDelete(String key) {
        try {
            MemcacheService memcache = MemcacheServiceFactory.getMemcacheService();
            memcache.setErrorHandler(ErrorHandlers.getConsistentLogAndContinue(Level.INFO));
            return memcache.delete(key);
        } catch (Exception e) {
            log.log(Level.WARNING, "Unable to delete object from memcache. Key: " + key, e);
            return null;
        }
    }
}
