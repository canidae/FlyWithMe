package net.exent.flywithme.util;

import com.google.appengine.api.memcache.ErrorHandlers;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceException;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.googlecode.objectify.ObjectifyService;

import net.exent.flywithme.bean.Forecast;
import net.exent.flywithme.bean.Takeoff;

import java.util.ArrayList;
import java.util.List;

import static com.googlecode.objectify.ObjectifyService.ofy;

/**
 * This class handles loading and storing data to the Google Datastore.
 * It will also use Memcache to reduce amount of calls to Google Datastore (which is limited by a quota).
 */
public class DataStore {
    private static final Log log = new Log();
    private static final String TAKEOFFS_RECENTLY_UPDATED_KEY_PREFIX = "takeoffs_recently_updated_";
    private static final long FORECAST_CACHE_LIFETIME = 21600000; // 6 hours

    static {
        ObjectifyService.register(Forecast.class);
        ObjectifyService.register(Takeoff.class);
    }

    public static Takeoff loadTakeoff(long takeoffId) {
        String key = "takeoff_" + takeoffId;
        Takeoff takeoff = (Takeoff) memcacheLoad(key);
        if (takeoff == null) {
            log.i("Loading takeoff from datastore");
            takeoff = ofy().load().type(Takeoff.class).id(takeoffId).now();
            memcacheSave(key, takeoff);
        }
        return takeoff;
    }

    public static void saveTakeoff(Takeoff takeoff) {
        log.i("Saving takeoff to datastore");
        ofy().save().entity(takeoff).now();
        String key = "takeoff_" + takeoff.getId();
        memcacheSave(key, takeoff);
    }

    public static Takeoff getLastCheckedTakeoff() {
        log.i("Loading last updated takeoff from datastore");
        return ofy().load().type(Takeoff.class).order("-lastUpdated").first().now();
    }

    public static List<Takeoff> getTakeoffs(long updatedAfter) {
        String key = TAKEOFFS_RECENTLY_UPDATED_KEY_PREFIX + updatedAfter;
        List<Object> cachedTakeoffs = memcacheLoadLargeList(key);
        if (cachedTakeoffs != null) {
            try {
                List<Takeoff> takeoffs = new ArrayList<>();
                for (Object object : cachedTakeoffs)
                    takeoffs.add((Takeoff) object);
                return takeoffs;
            } catch (Exception e) {
                log.w(e, "Something's wrong with memcache entry for recently updated takeoffs");
            }
        }
        log.i("Loading recently updated takeoffs from datastore");
        List<Takeoff> takeoffs = ofy().load().type(Takeoff.class)
                .filter("lastUpdated >", updatedAfter)
                .list();
        memcacheSaveLargeList(key, takeoffs);
        return takeoffs;
    }

    public static Forecast loadForecast(long takeoffId, Forecast.ForecastType type, long validFor) {
        long now = System.currentTimeMillis();
        String key = "forecast_" + takeoffId + "_" + type + "_" + now / FORECAST_CACHE_LIFETIME + "_" + validFor;
        Forecast forecast = (Forecast) memcacheLoad(key);
        if (forecast == null) {
            log.i("Loading forecast from datastore");
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
        log.i("Saving forecast to datastore");
        ofy().save().entity(forecast).now();
        String key = "forecast_" + forecast.getTakeoffId() + "_" + forecast.getType() + "_" + System.currentTimeMillis() / FORECAST_CACHE_LIFETIME + "_" + forecast.getValidFor();
        memcacheSave(key, forecast);
    }

    public static void cleanCache() {
        // clean forecasts cached in datastore
        ofy().delete().entities(ofy().load().type(Forecast.class).filter("lastUpdated <=", (System.currentTimeMillis() - FORECAST_CACHE_LIFETIME) / 1000).list());
    }

    private static Object memcacheLoad(String key) {
        try {
            MemcacheService memcache = MemcacheServiceFactory.getMemcacheService();
            memcache.setErrorHandler(ErrorHandlers.getStrict());
            return memcache.get(key);
        } catch (Exception e) {
            log.w(e, "Unable to load object from memcache. Key: ", key);
            return null;
        }
    }

    private static void memcacheSave(String key, Object object) {
        try {
            MemcacheService memcache = MemcacheServiceFactory.getMemcacheService();
            memcache.setErrorHandler(ErrorHandlers.getStrict());
            memcache.put(key, object);
        } catch (Exception e) {
            log.w(e, "Unable to save object to memcache. Key: ", key);
        }
    }

    private static boolean memcacheDelete(String key) {
        try {
            MemcacheService memcache = MemcacheServiceFactory.getMemcacheService();
            memcache.setErrorHandler(ErrorHandlers.getStrict());
            return memcache.delete(key);
        } catch (Exception e) {
            log.w(e, "Unable to delete object from memcache. Key: ", key);
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
                    log.w("Seems like we're missing some chunks, must read again from datastore");
                    return null;
                }
                result.addAll(subList);
            }
            return result;
        } catch (Exception e) {
            log.w(e, "Unable to load large list from memcache. Key prefix: ", keyPrefix);
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
            log.w(e, "Unable to save large list to memcache. Key prefix: ", keyPrefix);
        }
    }

    private static boolean memcacheDeleteLargeList(String keyPrefix) {
        try {
            MemcacheService memcache = MemcacheServiceFactory.getMemcacheService();
            memcache.setErrorHandler(ErrorHandlers.getStrict());
            return memcache.delete(keyPrefix); // enough to just delete the entry with chunk size
        } catch (Exception e) {
            log.w(e, "Unable to delete large list from memcache. Key prefix: ", keyPrefix);
            return false;
        }
    }
}
