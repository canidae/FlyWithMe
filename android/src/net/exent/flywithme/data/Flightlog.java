package net.exent.flywithme.data;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.exent.flywithme.R;
import net.exent.flywithme.bean.Takeoff;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

public class Flightlog {
    private static final int DEFAULT_MIN_TAKEOFFS = 50;
    private static List<Takeoff> takeoffs = new ArrayList<Takeoff>();

    /**
     * Fetch takeoffs without sorting them first.
     * 
     * @return Takeoffs.
     */
    public static List<Takeoff> getAllTakeoffs() {
        return takeoffs;
    }
    
    /**
     * Fetch sorted list of takeoffs near the given location.
     * @param location Location of where to look for locations.
     * @return Sorted list of takeoffs near location.
     */
    public static List<Takeoff> getTakeoffs(Location location) {
        return getTakeoffs(location, 40000000, DEFAULT_MIN_TAKEOFFS);
    }
    
    public static List<Takeoff> getTakeoffs(Location location, int maxDistance) {
        return getTakeoffs(location, maxDistance, DEFAULT_MIN_TAKEOFFS);
    }
    
    public static List<Takeoff> getTakeoffs(Location location, int maxDistance, int minTakeoffs) {
        /* limit the amount of takeoffs to sort (sorting is slow, reducing the list to sort by iterating some few times is significantly faster) */
        List<Takeoff> sortedTakeoffs;
        if (minTakeoffs < 1)
            minTakeoffs = 1;
        long takeoffsUpperLimit = minTakeoffs * 4;
        if (takeoffs.size() <= takeoffsUpperLimit) {
            /* so few takeoffs that there's no need to reduce amount to sort */
            sortedTakeoffs = takeoffs;
        } else {
            /* need to reduce amount of takeoffs to sort */
            sortedTakeoffs = new ArrayList<Takeoff>();
            for (Takeoff takeoff : takeoffs) {
                if (location.distanceTo(takeoff.getLocation()) <= maxDistance)
                    sortedTakeoffs.add(takeoff);
            }
            while (sortedTakeoffs.size() > takeoffsUpperLimit) {
                maxDistance /= 2;
                List<Takeoff> tmp = new ArrayList<Takeoff>();
                for (Takeoff takeoff : sortedTakeoffs) {
                    if (location.distanceTo(takeoff.getLocation()) <= maxDistance)
                        tmp.add(takeoff);
                }
                if (tmp.size() < minTakeoffs)
                    break; // dropped below amount of takeoffs to return, break loop to sort takeoffs from the previous reduction run 
                sortedTakeoffs = tmp;
            }
        }
        sortTakeoffListToLocation(sortedTakeoffs, location);
        return sortedTakeoffs;
    }
    
    public static void sortTakeoffListToLocation(List<Takeoff> takeoffs, final Location location) {
        Collections.sort(takeoffs, new Comparator<Takeoff>() {
            public int compare(Takeoff lhs, Takeoff rhs) {
                if (location.distanceTo(lhs.getLocation()) > location.distanceTo(rhs.getLocation()))
                    return 1;
                else if (location.distanceTo(lhs.getLocation()) < location.distanceTo(rhs.getLocation()))
                    return -1;
                return 0;
            }
        });
    }

    public static void init(Context context) {
        if (takeoffs.isEmpty())
            readTakeoffsFile(context);
    }

    /**
     * Read file with takeoff details.
     */
    private static void readTakeoffsFile(Context context) {
        List<Takeoff> tmpTakeoffs = new ArrayList<Takeoff>();
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

                tmpTakeoffs.add(new Takeoff(takeoff, name, description, asl, height, takeoffLocation.getLatitude(), takeoffLocation.getLongitude(), windpai));
            }
        } catch (EOFException e) {
            /* expected to happen */
            takeoffs = tmpTakeoffs;
        } catch (IOException e) {
            Log.e("Flightlog", "Error when reading file with takeoffs", e);
        }
    }
}
