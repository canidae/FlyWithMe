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
    private static final String TAKEOFFS_RECENTLY_UPDATED_KEY_PREFIX = "takeoffs_recently_updated_";
    private static final String ALL_SCHEDULES_KEY = "all_schedules";
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
            log.info("Loading takeoff from datastore");
            takeoff = ofy().load().type(Takeoff.class).id(takeoffId).now();
            memcacheSave(key, takeoff);
        }
        return takeoff;
    }

    public static void saveTakeoff(Takeoff takeoff) {
        log.info("Saving takeoff to datastore");
        ofy().save().entity(takeoff).now();
        String key = "takeoff_" + takeoff.getId();
        memcacheSave(key, takeoff);
    }

    public static Takeoff getLastCheckedTakeoff() {
        // this is only called when updating takeoffs (not very often), no need to memcache
        log.info("Loading last checked takeoff from datastore");
        return ofy().load().type(Takeoff.class).order("-lastChecked").first().now();
    }

    public static List<Takeoff> getRecentlyUpdatedTakeoffs(long updatedAfter) {
        String key = TAKEOFFS_RECENTLY_UPDATED_KEY_PREFIX + updatedAfter;
        List<Object> cachedTakeoffs = memcacheLoadLargeList(key);
        if (cachedTakeoffs != null) {
            try {
                List<Takeoff> takeoffs = new ArrayList<>();
                for (Object object : cachedTakeoffs)
                    takeoffs.add((Takeoff) object);
                return takeoffs;
            } catch (Exception e) {
                log.log(Level.WARNING, "Something's wrong with memcache entry for recently updated takeoffs", e);
            }
        }
        log.info("Loading recently updated takeoffs from datastore");
        List<Takeoff> takeoffs = ofy().load().type(Takeoff.class)
                .filter("lastUpdated >=", updatedAfter)
                .list();
        memcacheSaveLargeList(key, takeoffs);
        return takeoffs;
    }

    public static Pilot loadPilot(String pilotId) {
        String key = "pilot_" + pilotId;
        Pilot pilot = (Pilot) memcacheLoad(key);
        if (pilot == null) {
            log.info("Loading pilot from datastore");
            pilot = ofy().load().type(Pilot.class).id(pilotId).now();
            memcacheSave(key, pilot);
        }
        return pilot;
    }

    public static void savePilot(Pilot pilot) {
        log.info("Saving pilot to datastore");
        ofy().save().entity(pilot).now();
        String key = "pilot_" + pilot.getId();
        memcacheSave(key, pilot);
        memcacheDeleteLargeList(ALL_PILOTS_KEY); // NOTE: important
    }

    public static void deletePilot(String pilotId) {
        Pilot pilot = loadPilot(pilotId);
        if (pilot == null) {
            return;
        }
        String key = "pilot_" + pilot.getId();
        log.info("Deleting pilot from datastore");
        ofy().delete().entity(pilot).now();
        memcacheDelete(key);
        memcacheDeleteLargeList(ALL_PILOTS_KEY); // NOTE: important
    }

    public static List<Pilot> getAllPilots() {
        List<Object> cachedPilots = memcacheLoadLargeList(ALL_PILOTS_KEY);
        if (cachedPilots != null) {
            try {
                List<Pilot> pilots = new ArrayList<>();
                for (Object object : cachedPilots)
                    pilots.add((Pilot) object);
                return pilots;
            } catch (Exception e) {
                log.log(Level.WARNING, "Something's wrong with memcache entry for all pilots", e);
            }
        }
        log.info("Loading all pilots from datastore");
        List<Pilot> pilots = ofy().load().type(Pilot.class).list();
        memcacheSaveLargeList(ALL_PILOTS_KEY, pilots);
        return pilots;
    }

    public static Schedule loadSchedule(long takeoffId, long timestamp) {
        String key = "schedule_" + takeoffId + "_" + timestamp;
        Schedule schedule = (Schedule) memcacheLoad(key);
        if (schedule == null) {
            log.info("Loading schedule from datastore");
            schedule = ofy().load().type(Schedule.class)
                    .filter("takeoffId", takeoffId)
                    .filter("timestamp", timestamp)
                    .first().now();
        }
        return schedule;
    }

    public static void saveSchedule(Schedule schedule) {
        log.info("Saving schedule to datastore");
        ofy().save().entity(schedule).now();
        String key = "schedule_" + schedule.getTakeoffId() + "_" + schedule.getTimestamp();
        memcacheSave(key, schedule);
        updateAllSchedulesInMemcache(schedule); // NOTE: important
    }

    public static List<Schedule> getAllSchedules() {
        // TODO: this returned an error earlier. figure out why
        List<Schedule> schedules = getAllSchedulesFromMemcache();
        if (schedules != null)
            return schedules;

        log.info("Loading all schedules from datastore");
        schedules = ofy().load().type(Schedule.class)
                .filter("timestamp >=", getScheduleExpireTime())
                .order("-timestamp")
                .list();
        memcacheSaveLargeList(ALL_SCHEDULES_KEY, schedules);
        return schedules;
    }

    public static Forecast loadForecast(long takeoffId, Forecast.ForecastType type, long validFor) {
        long now = System.currentTimeMillis();
        String key = "forecast_" + takeoffId + "_" + type + "_" + now / FORECAST_CACHE_LIFETIME + "_" + validFor;
        Forecast forecast = (Forecast) memcacheLoad(key);
        if (forecast == null) {
            log.info("Loading forecast from datastore");
            forecast = ofy().load().type(Forecast.class)
                    .filter("takeoffId", takeoffId)
                    .filter("type", type)
                    .filter("validFor", validFor)
                    .filter("lastUpdated >", (now - FORECAST_CACHE_LIFETIME / 2) / 1000)
                    .first().now();
            memcacheSave(key, forecast);
        }
        return forecast;
    }

    public static void saveForecast(Forecast forecast) {
        log.info("Saving forecast to datastore");
        ofy().save().entity(forecast).now();
        String key = "forecast_" + forecast.getTakeoffId() + "_" + forecast.getType() + "_" + System.currentTimeMillis() / FORECAST_CACHE_LIFETIME + "_" + forecast.getValidFor();
        memcacheSave(key, forecast);
    }

    public static void cleanCache() {
        // clean forecasts cached in datastore (Memcache cleans itself)
        ofy().delete().entities(ofy().load().type(Forecast.class).filter("lastUpdated <=", (System.currentTimeMillis() - FORECAST_CACHE_LIFETIME) / 1000).list());
        // clean schedules cached in datastore
        ofy().delete().entities(ofy().load().type(Schedule.class).filter("timestamp <=", getScheduleExpireTime()).list());
    }

    private static long getScheduleExpireTime() {
        return (System.currentTimeMillis() - 86400000) / 1000; // 24 hours into the past
    }

    private static List<Schedule> getAllSchedulesFromMemcache() {
        List cachedSchedules = (List) memcacheLoadLargeList(ALL_SCHEDULES_KEY);
        if (cachedSchedules != null) {
            try {
                List<Schedule> schedules = new ArrayList<>();
                for (Object object : cachedSchedules) {
                    Schedule schedule = (Schedule) object;
                    if (schedule == null || schedule.getPilots() == null || schedule.getPilots().isEmpty())
                        continue; // skip schedules where there are no pilots (also a work-around for dodgy behaviour from memcache)
                    schedules.add(schedule);
                }
                return schedules;
            } catch (Exception e) {
                log.log(Level.WARNING, "Something's wrong with memcache entry for recently scheduled takeoffs", e);
            }
        }
        return null;
    }

    private static void updateAllSchedulesInMemcache(Schedule schedule) {
        // schedule is our most complex data structure and the most used one
        // we'll try to keep memcache entry for all schedules in sync with datastore without loading from datastore each time we modify a schedule
        List<Schedule> cachedSchedules = getAllSchedulesFromMemcache();
        if (cachedSchedules != null) {
            List<Schedule> allSchedules = new ArrayList<>();
            boolean scheduleAdded = false;
            for (Schedule cachedSchedule : cachedSchedules) {
                if (cachedSchedule.getTimestamp() < getScheduleExpireTime())
                    continue; // schedule is too old, remove from memcached list of schedules
                if (cachedSchedule.getTakeoffId() == schedule.getTakeoffId() && cachedSchedule.getTimestamp() == schedule.getTimestamp()) {
                    // schedule already in list of cached schedules, replace the cached one with the new schedule
                    allSchedules.add(schedule);
                    scheduleAdded = true;
                } else if (cachedSchedule.getTimestamp() > schedule.getTimestamp()) {
                    // schedule not in list of cached schedules, add it at the right place (list is ordered by timestamp)
                    allSchedules.add(schedule);
                    allSchedules.add(cachedSchedule); // keep the cached schedule
                    scheduleAdded = true;
                } else {
                    allSchedules.add(cachedSchedule); // keep the cached schedule
                }
            }
            if (!scheduleAdded)
                allSchedules.add(schedule); // schedule not in cached list, and it is scheduled after all the cached schedules
            memcacheSaveLargeList(ALL_SCHEDULES_KEY, allSchedules);
        }
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
            Integer chunks = (Integer) memcache.get(keyPrefix);
            if (chunks == null)
                return null;
            for (int chunk = 0; chunk < chunks; ++chunk) {
                List subList = (List) memcache.get(keyPrefix + chunk);
                if (subList == null) {
                    log.warning("Seems like we're missing some chunks, must read again from datastore");
                    return null;
                }
                result.addAll(subList);
            }
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
                        memcache.put(keyPrefix + i, new ArrayList<>(list.subList(i * chunkSize, (i + 1) * chunkSize)));
                    if (list.size() > chunks * chunkSize) {
                        memcache.put(keyPrefix + chunks, new ArrayList<>(list.subList(chunks * chunkSize, list.size())));
                        ++chunks;
                    }
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
