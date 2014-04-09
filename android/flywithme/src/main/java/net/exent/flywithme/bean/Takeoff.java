package net.exent.flywithme.bean;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationManager;
import android.os.Parcel;
import android.os.Parcelable;

import net.exent.flywithme.data.Database;

import java.util.HashMap;
import java.util.Map;

public class Takeoff implements Parcelable {
    public static final Parcelable.Creator<Takeoff> CREATOR = new Parcelable.Creator<Takeoff>() {
        public Takeoff createFromParcel(Parcel in) {
            return new Takeoff(in.readInt(), in.readString(), in.readString(), in.readInt(), in.readInt(), in.readDouble(), in.readDouble(), in.readInt(), in.readByte() == 1);
        }

        public Takeoff[] newArray(int size) {
            return new Takeoff[size];
        }
    };

    public static final String[] COLUMNS = {
            "takeoff_id", "name", "description", "asl", "height", "latitude", "longitude", "exits", "favourite"
    };

    // used to prevent excessive memory allocations. it means more memory used, but also that the garbage collector won't run so aggressively.
    private static Map<Integer, Takeoff> takeoffCache = new HashMap<>();

    private int id;
    private String name;
    private String description;
    private int asl;
    private int height;
    private double latitude;
    private double longitude;
    private int exits;
    private boolean favourite;
    private Bitmap noaaForecast;
    private long noaaUpdated;

    /* Should only be used by Database for importing takeoffs from file */
    public Takeoff(int id, String name, String description, int asl, int height, double latitude, double longitude, String exitDirections, boolean favourite) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.asl = asl;
        this.height = height;
        this.latitude = latitude;
        this.longitude = longitude;
        for (String direction : exitDirections.split(" ")) {
            if ("N".equals(direction))
                exits |= 1 << 8;
            if ("NE".equals(direction))
                exits |= 1 << 7;
            if ("E".equals(direction))
                exits |= 1 << 6;
            if ("SE".equals(direction))
                exits |= 1 << 5;
            if ("S".equals(direction))
                exits |= 1 << 4;
            if ("SW".equals(direction))
                exits |= 1 << 3;
            if ("W".equals(direction))
                exits |= 1 << 2;
            if ("NW".equals(direction))
                exits |= 1 << 1;
        }
        this.favourite = favourite;
    }

    private Takeoff(Database.ImprovedCursor cursor) {
        id = cursor.getIntOrThrow("takeoff_id");
        name = cursor.getString("name");
        description = cursor.getString("description");
        asl = cursor.getInt("asl");
        height = cursor.getInt("height");
        latitude = cursor.getDouble("latitude");
        longitude = cursor.getDouble("longitude");
        exits = cursor.getInt("exits");
        favourite = cursor.getInt("favourite") == 1;
    }

    private Takeoff(int id, String name, String description, int asl, int height, double latitude, double longitude, int exits, boolean favourite) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.asl = asl;
        this.height = height;
        this.latitude = latitude;
        this.longitude = longitude;
        this.exits = exits;
        this.favourite = favourite;
    }

    public static Takeoff create(Database.ImprovedCursor cursor) {
        int takeoffId = cursor.getIntOrThrow("takeoff_id");
        Takeoff takeoff = takeoffCache.get(takeoffId);
        if (takeoff != null)
            return takeoff;
        takeoff = new Takeoff(cursor);
        takeoffCache.put(takeoffId, takeoff);
        return takeoff;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getAsl() {
        return asl;
    }

    public void setAsl(int asl) {
        this.asl = asl;
    }

    public int getHeight() {
        return height;
    }

    public Location getLocation() {
        Location location = new Location(LocationManager.PASSIVE_PROVIDER);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        return location;
    }

    public int getExits() {
        return exits;
    }

    public boolean hasNorthExit() {
        return (exits & (1 << 8)) != 0;
    }

    public boolean hasNortheastExit() {
        return (exits & (1 << 7)) != 0;
    }

    public boolean hasEastExit() {
        return (exits & (1 << 6)) != 0;
    }

    public boolean hasSoutheastExit() {
        return (exits & (1 << 5)) != 0;
    }

    public boolean hasSouthExit() {
        return (exits & (1 << 4)) != 0;
    }

    public boolean hasSouthwestExit() {
        return (exits & (1 << 3)) != 0;
    }

    public boolean hasWestExit() {
        return (exits & (1 << 2)) != 0;
    }

    public boolean hasNorthwestExit() {
        return (exits & (1 << 1)) != 0;
    }

    public void setFavourite(boolean favourite) {
        this.favourite = favourite;
    }

    public boolean isFavourite() {
        return favourite;
    }

    public Bitmap getNoaaforecast() {
        return noaaForecast;
    }

    public void setNoaaForecast(Bitmap noaaForecast) {
        this.noaaForecast = noaaForecast;
        noaaUpdated = System.currentTimeMillis();
    }

    public long getNoaaUpdated() {
        return noaaUpdated;
    }

    public ContentValues getContentValues() {
        ContentValues contentValues = new ContentValues();
        contentValues.put("takeoff_id", id);
        contentValues.put("name", name);
        contentValues.put("description", description);
        contentValues.put("asl", asl);
        contentValues.put("height", height);
        double latitudeRadians = latitude * Math.PI / 180.0;
        contentValues.put("latitude", latitude);
        contentValues.put("latitude_cos", Math.cos(latitudeRadians));
        contentValues.put("latitude_sin", Math.sin(latitudeRadians));
        double longitudeRadians = longitude * Math.PI / 180.0;
        contentValues.put("longitude", longitude);
        contentValues.put("longitude_cos", Math.cos(longitudeRadians));
        contentValues.put("longitude_sin", Math.sin(longitudeRadians));
        contentValues.put("exits", exits);
        contentValues.put("favourite", favourite);
        return contentValues;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(name);
        dest.writeString(description);
        dest.writeInt(asl);
        dest.writeInt(height);
        dest.writeDouble(latitude);
        dest.writeDouble(longitude);
        dest.writeInt(exits);
        dest.writeByte((byte) (favourite ? 1 : 0));
    }

    @Override
    public String toString() {
        return name;
    }
}
