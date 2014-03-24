package net.exent.flywithme.data;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.exent.flywithme.FlyWithMe;
import net.exent.flywithme.R;
import net.exent.flywithme.bean.Takeoff;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

public class Flightlog {
    private static final int DEFAULT_MIN_TAKEOFFS = 50;
    private static List<Takeoff> takeoffs = new ArrayList<>();

    /**
     * Fetch takeoffs without sorting them first.
     * 
     * @return Takeoffs.
     */
    public static List<Takeoff> getAllTakeoffs() {
        //init(FlyWithMe.getInstance()); // TODO: if we do this, then performance of takeoff map is utterly devastated. i'm completely dumbfounded as of why
        return takeoffs;
    }

    /**
     * Fetch takeoff with given ID.
     * @param takeoffId ID of the takeoff.
     * @return The takeoff with given ID.
     */
    public static Takeoff getTakeoff(int takeoffId) {
        // TODO: optimize, this is slow (but only used when back is pressed, so far)
        init(FlyWithMe.getInstance());
        for (Takeoff takeoff : getAllTakeoffs()) {
            if (takeoff.getId() == takeoffId)
                return takeoff;
        }
        return null;
    }

    /**
     * Fetch sorted list of takeoffs near the given location.
     * @param location Location of where to look for locations.
     * @return Sorted list of takeoffs near location.
     */
    public static List<Takeoff> getTakeoffs(Location location) {
        init(FlyWithMe.getInstance());
        return getTakeoffs(location, 40000000, DEFAULT_MIN_TAKEOFFS);
    }

    public static List<Takeoff> getTakeoffs(Location location, int maxDistance) {
        init(FlyWithMe.getInstance());
        return getTakeoffs(location, maxDistance, DEFAULT_MIN_TAKEOFFS);
    }

    public static void sortTakeoffListToLocation(List<Takeoff> takeoffs, final Location location) {
        Collections.sort(takeoffs, new Comparator<Takeoff>() {
            public int compare(Takeoff lhs, Takeoff rhs) {
                if (!lhs.isFavourite() && rhs.isFavourite())
                    return 1;
                else if (lhs.isFavourite() && !rhs.isFavourite())
                    return -1;
                // both or neither are favourites, sort by distance from user
                if (location.distanceTo(lhs.getLocation()) > location.distanceTo(rhs.getLocation()))
                    return 1;
                else if (location.distanceTo(lhs.getLocation()) < location.distanceTo(rhs.getLocation()))
                    return -1;
                return 0;
            }
        });
    }

    public static void init(Context context) {
        Log.w("Flightlog", "init()");
        // TODO: even when takeoffs.isEmpty() returns "false", takeoff map performance is utterly devastated. what?
        // TODO: it seems to be memory related, but i still don't get why, this shouldn't cause a spike in memory usage
        if (takeoffs.isEmpty())
            initTakeoffList(context);
    }

    private static List<Takeoff> getTakeoffs(Location location, int maxDistance, int minTakeoffs) {
        /* limit the amount of takeoffs to sort (sorting is slow, reducing the list to sort by iterating some few times is significantly faster) */
        List<Takeoff> sortedTakeoffs;
        if (minTakeoffs < 1)
            minTakeoffs = 1;
        long takeoffsUpperLimit = minTakeoffs * 4;
        /* need to reduce amount of takeoffs to sort */
        sortedTakeoffs = new ArrayList<>();
        for (Takeoff takeoff : getAllTakeoffs()) {
            if (location.distanceTo(takeoff.getLocation()) <= maxDistance)
                sortedTakeoffs.add(takeoff);
        }
        while (sortedTakeoffs.size() > takeoffsUpperLimit) {
            maxDistance /= 2;
            List<Takeoff> tmp = new ArrayList<>();
            for (Takeoff takeoff : sortedTakeoffs) {
                if (location.distanceTo(takeoff.getLocation()) <= maxDistance)
                    tmp.add(takeoff);
            }
            if (tmp.size() < minTakeoffs)
                break; // dropped below amount of takeoffs to return, break loop to sort takeoffs from the previous reduction run
            sortedTakeoffs = tmp;
        }
        sortTakeoffListToLocation(sortedTakeoffs, location);
        return sortedTakeoffs;
    }

    /**
     * Read file with takeoff details.
     */
    private static void initTakeoffList(Context context) {
        Log.w("Flightlog", "initTakeoffList()");
        Set<Integer> favourites = Database.getFavourites();
        List<Takeoff> tmpTakeoffs = new ArrayList<>();
        try {
            DataInputStream inputStream = new DataInputStream(context.getResources().openRawResource(R.raw.flywithme));
            while (true) {
                /* loop breaks once we get an EOFException */
                int takeoff = inputStream.readShort();
                String name = inputStream.readUTF();
                String description = inputStream.readUTF();
                int asl = inputStream.readShort();
                int height = inputStream.readShort();
                Location takeoffLocation = new Location(LocationManager.PASSIVE_PROVIDER);
                takeoffLocation.setLatitude(inputStream.readFloat());
                takeoffLocation.setLongitude(inputStream.readFloat());
                String windpai = inputStream.readUTF();
                boolean favourite = favourites.contains(takeoff);

                tmpTakeoffs.add(new Takeoff(takeoff, name, description, asl, height, takeoffLocation.getLatitude(), takeoffLocation.getLongitude(), windpai, favourite));
            }
        } catch (EOFException e) {
            /* expected to happen */
            takeoffs = tmpTakeoffs;
        } catch (IOException e) {
            Log.e("Flightlog", "Error when reading file with takeoffs", e);
        }
    }
}
