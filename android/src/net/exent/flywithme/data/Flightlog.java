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
    private static final int DEFAULT_MAX_TAKEOFFS = 50;
    private static List<Takeoff> takeoffs = new ArrayList<Takeoff>();

    /**
     * Fetch takeoffs near the given location. This requires takeoffs to be sorted by distance, which will cost some CPU.
     * 
     * @param location
     *            Where to look for nearby takeoffs.
     * @return Takeoffs sorted by distance to the given location.
     */
    public static List<Takeoff> getTakeoffs(Location location) {
        Log.d("Flightlog", "getTakeoffs(" + location + ")");
        /* sort list by distance */
        /* we need to copy the location and not pass it by reference, otherwise it may be updated while sorting,
         * which will cause an exception as location affects the order in the sorted list */
        final Location locCopy = new Location(location);
        Log.d("Flightlog", "Sorting... (size: " + takeoffs.size() + ")");
        Collections.sort(takeoffs, new Comparator<Takeoff>() {
            public int compare(Takeoff lhs, Takeoff rhs) {
                if (locCopy.distanceTo(lhs.getLocation()) > locCopy.distanceTo(rhs.getLocation()))
                    return 1;
                else if (locCopy.distanceTo(lhs.getLocation()) < locCopy.distanceTo(rhs.getLocation()))
                    return -1;
                return 0;
            }
        });
        Log.d("Flightlog", "Done sorting");
        if (takeoffs.size() > DEFAULT_MAX_TAKEOFFS)
            return takeoffs.subList(0, DEFAULT_MAX_TAKEOFFS);
        return takeoffs;
    }

    public static void init(Context context) {
        if (takeoffs.isEmpty())
            readTakeoffsFile(context);
    }

    /**
     * Read file with takeoff details.
     */
    private static void readTakeoffsFile(Context context) {
        Log.d("Flightlog", "readTakeoffsFile()");
        List<Takeoff> tmpTakeoffs = new ArrayList<Takeoff>();
        try {
            Log.i("Flightlog", "Reading file with takeoffs");
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
            Log.i("Flightlog", "Done reading file with takeoffs");
        } catch (IOException e) {
            Log.e("Flightlog", "Error when reading file with takeoffs", e);
        }
    }
}
