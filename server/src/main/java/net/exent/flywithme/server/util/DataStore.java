package net.exent.flywithme.server.util;

import com.google.appengine.api.memcache.ErrorHandlers;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceException;
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
    private static final String TAKEOFFS_RECENTLY_SCHEDULED_KEY = "takeoffs_recently_scheduled";
    private static final String TAKEOFFS_RECENTLY_UPDATED_KEY_PREFIX = "takeoffs_recently_updated_";
    private static final String ALL_PILOTS_KEY = "all_pilots";
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
        ofy().save().entity(takeoff).now();
        String key = "takeoff_" + takeoff.getId();
        memcacheSave(key, takeoff);
    }

    public static Takeoff getLastCheckedTakeoff() {
        // this is only called when updating takeoffs (not very often), no need to memcache
        return ofy().load().type(Takeoff.class).order("-lastChecked").first().now();
    }

    public static List<Takeoff> getRecentlyUpdatedTakeoffs(long updatedAfter) {
        String key = TAKEOFFS_RECENTLY_UPDATED_KEY_PREFIX + updatedAfter;
        List<Object> objects = memcacheLoadLargeList(key);
        List<Takeoff> takeoffs;
        if (objects == null || objects.isEmpty()) {
            takeoffs = ofy().load().type(Takeoff.class).filter("lastUpdated >=", updatedAfter).list();
            memcacheSaveLargeList(key, takeoffs);
        } else {
            takeoffs = new ArrayList<>();
            for (Object object : objects)
                takeoffs.add((Takeoff) object);
        }
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
        memcacheDeleteLargeList(ALL_PILOTS_KEY); // NOTE: important
        ofy().save().entity(pilot).now();
        String key = "pilot_" + pilot.getId();
        memcacheSave(key, pilot);
    }

    public static void deletePilot(String pilotId) {
        memcacheDeleteLargeList(ALL_PILOTS_KEY); // NOTE: important
        Pilot pilot = loadPilot(pilotId);
        if (pilot == null) {
            return;
        }
        String key = "pilot_" + pilot.getId();
        memcacheDelete(key);
        ofy().delete().entity(pilot).now();
    }

    public static List<Pilot> getAllPilots() {
        List<Object> objects = memcacheLoadLargeList(ALL_PILOTS_KEY);
        List<Pilot> pilots;
        if (objects == null || objects.isEmpty()) {
            pilots = ofy().load().type(Pilot.class).list();
            memcacheSaveLargeList(ALL_PILOTS_KEY, pilots);
        } else {
            pilots = new ArrayList<>();
            for (Object object : objects)
                pilots.add((Pilot) object);
        }
        return pilots;
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
        memcacheDelete(TAKEOFFS_RECENTLY_SCHEDULED_KEY); // NOTE: somewhat important
    }

    public static List<Schedule> getUpcomingSchedules() {
        // NOTE: we must delete the memcache entry when we update the schedule for a takeoff
        List recentlyScheduled = (List) memcacheLoad(TAKEOFFS_RECENTLY_SCHEDULED_KEY);
        if (recentlyScheduled != null) {
            try {
                List<Schedule> schedules = new ArrayList<>();
                for (int i = 0; i < recentlyScheduled.size(); i += 2) {
                    long takeoffId = (Long) recentlyScheduled.get(i);
                    long timestamp = (Long) recentlyScheduled.get(i + 1);
                    schedules.add(loadSchedule(takeoffId, timestamp));
                }
                return schedules;
            } catch (Exception e) {
                log.log(Level.WARNING, "Something's wrong with memcache entry for recently scheduled takeoffs", e);
            }
        }

        List<Schedule> schedules = ofy().load().type(Schedule.class)
                .filter("timestamp >=", System.currentTimeMillis() - 7200000) // 2 hours
                .list();
        List<Long> newRecentlyScheduled = new ArrayList<>();
        for (Schedule schedule : schedules) {
            newRecentlyScheduled.add(schedule.getTakeoffId());
            newRecentlyScheduled.add(schedule.getTimestamp());
        }
        memcacheSave(TAKEOFFS_RECENTLY_SCHEDULED_KEY, newRecentlyScheduled);
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
        // clean schedules cached in datastore
        ofy().delete().entities(ofy().load().type(Schedule.class).filter("timestamp <=", System.currentTimeMillis() - 7200000).list());
    }

    private static Object memcacheLoad(String key) {
        try {
            MemcacheService memcache = MemcacheServiceFactory.getMemcacheService();
            memcache.setErrorHandler(ErrorHandlers.getStrict());
            return memcache.get(key);
        } catch (Exception e) {
            log.log(Level.WARNING, "Unable to load object from memcache. Key: " + key, e);
            return null;
        }
    }

    private static void memcacheSave(String key, Object object) {
        try {
            MemcacheService memcache = MemcacheServiceFactory.getMemcacheService();
            memcache.setErrorHandler(ErrorHandlers.getStrict());
            memcache.put(key, object);
        } catch (Exception e) {
            log.log(Level.WARNING, "Unable to save object to memcache. Key: " + key, e);
        }
    }

    private static boolean memcacheDelete(String key) {
        try {
            MemcacheService memcache = MemcacheServiceFactory.getMemcacheService();
            memcache.setErrorHandler(ErrorHandlers.getStrict());
            return memcache.delete(key);
        } catch (Exception e) {
            log.log(Level.WARNING, "Unable to delete object from memcache. Key: " + key, e);
            return false;
        }
    }

    private static List<Object> memcacheLoadLargeList(String keyPrefix) {
        try {
            MemcacheService memcache = MemcacheServiceFactory.getMemcacheService();
            memcache.setErrorHandler(ErrorHandlers.getStrict());
            List<Object> result = new ArrayList<>();
            int chunks = (Integer) memcache.get(keyPrefix);
            for (int chunk = 0; chunk < chunks; ++chunk)
                result.addAll((List) memcache.get(keyPrefix + chunk));
            return result;
        } catch (Exception e) {
            log.log(Level.WARNING, "Unable to load large list from memcache. Key prefix: " + keyPrefix, e);
            return null;
        }
    }

    private static void memcacheSaveLargeList(String keyPrefix, List list) {
        try {
            MemcacheService memcache = MemcacheServiceFactory.getMemcacheService();
            memcache.setErrorHandler(ErrorHandlers.getStrict());
            int chunks = 1;
            while (true) {
                try {
                    int chunkSize = list.size() / chunks;
                    for (int i = 0; i < chunks; ++i)
                        memcache.put(keyPrefix + i, list.subList(i * chunkSize, (i + 1) * chunkSize));
                    if (list.size() > chunks * chunkSize)
                        memcache.put(keyPrefix + chunks, list.subList(chunks * chunkSize, list.size()));
                    memcache.put(keyPrefix, chunks);
                    break;
                } catch (MemcacheServiceException e) {
                    chunks *= 2;
                    if (chunks >= list.size())
                        throw e;
                }
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Unable to save large list to memcache. Key prefix: " + keyPrefix, e);
        }
    }

    private static boolean memcacheDeleteLargeList(String keyPrefix) {
        try {
            MemcacheService memcache = MemcacheServiceFactory.getMemcacheService();
            memcache.setErrorHandler(ErrorHandlers.getStrict());
            return memcache.delete(keyPrefix); // enough to just delete the entry with chunk size
        } catch (Exception e) {
            log.log(Level.WARNING, "Unable to delete large list from memcache. Key prefix: " + keyPrefix, e);
            return false;
        }
    }
}
