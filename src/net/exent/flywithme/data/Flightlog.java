package net.exent.flywithme.data;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.exent.flywithme.R;
import net.exent.flywithme.bean.Takeoff;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

public class Flightlog {
    private static List<Takeoff> takeoffs = new ArrayList<Takeoff>();
    
    public static List<Takeoff> getTakeoffs() {
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
        takeoffs = new ArrayList<Takeoff>();
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

                takeoffs.add(new Takeoff(takeoff, name, description, asl, height, takeoffLocation.getLatitude(), takeoffLocation.getLongitude(), windpai));
            }
        } catch (EOFException e) {
            /* expected, do nothing */
            Log.i("Flightlog", "Done reading file with takeoffs");
        } catch (IOException e) {
            Log.e("Flightlog", "Error when reading file with takeoffs", e);
        }
    }
}
