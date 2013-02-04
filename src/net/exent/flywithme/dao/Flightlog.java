package net.exent.flywithme.dao;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.exent.flywithme.R;
import net.exent.flywithme.data.Takeoff;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

public class Flightlog {
    private static final int MAX_DISTANCE = 200000; // only fetch takeoffs within MAX_DISTANCE meters
    private static List<Takeoff> takeoffs = new ArrayList<Takeoff>();
    private static Location lastReadFileLocation;

    public static List<Takeoff> getTakeoffs() {
        Log.d("Flightlog", "getTakeoffs()");
        return takeoffs;
    }

    public static void updateTakeoffList(Context context, final Location location) {
        Log.d("Flightlog", "updateTakeoffList(" + context + ", " + location + ")");
        if (lastReadFileLocation != null && location.distanceTo(lastReadFileLocation) < MAX_DISTANCE * 0.2)
            return;
        takeoffs = new ArrayList<Takeoff>();
        lastReadFileLocation = location;
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

                if (location.distanceTo(takeoffLocation) <= MAX_DISTANCE)
                    takeoffs.add(new Takeoff(takeoff, name, description, asl, height, takeoffLocation.getLatitude(), takeoffLocation.getLongitude(), windpai));
            }
        } catch (EOFException e) {
            /* expected, do nothing */
            Log.i("Flightlog", "Done reading file with takeoffs");
        } catch (IOException e) {
            Log.e("Flightlog", "Error when reading file with takeoffs", e);
        }
        /* sort list by distance */
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
}
