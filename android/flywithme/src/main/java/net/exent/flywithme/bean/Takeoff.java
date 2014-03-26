package net.exent.flywithme.bean;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationManager;
import android.os.Parcel;
import android.os.Parcelable;

import net.exent.flywithme.data.Database;

import java.nio.ByteBuffer;

public class Takeoff implements Parcelable {
    public static final Parcelable.Creator<Takeoff> CREATOR = new Parcelable.Creator<Takeoff>() {
        public Takeoff createFromParcel(Parcel in) {
            return new Takeoff(in.readInt(), in.readString(), in.readString(), in.readInt(), in.readInt(), in.readDouble(), in.readDouble(), in.readInt(), in.readByte() == 1);
        }

        public Takeoff[] newArray(int size) {
            return new Takeoff[size];
        }
    };

    public static final String TAKEOFF_ID = "takeoff_id";
    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String ASL = "asl";
    public static final String HEIGHT = "height";
    public static final String LATITUDE = "latitude";
    public static final String LONGITUDE = "longitude";
    public static final String EXITS = "exits";
    public static final String FAVOURITE = "favourite";
    public static final String NOAA_FORECAST = "noaa_forecast";
    public static final String NOAA_UPDATED = "noaa_updated";

    public static final String[] COLUMNS = {
            TAKEOFF_ID, NAME, DESCRIPTION, ASL, HEIGHT, LATITUDE, LONGITUDE, EXITS, FAVOURITE, NOAA_FORECAST, NOAA_UPDATED
    };

    private int id;
    private String name;
    private String description;
    private int asl;
    private int height;
    private double latitude;
    private double longitude;
    private int exits;
    private Bitmap noaaForecast;
    private long noaaUpdated;
    private boolean favourite;

    public Takeoff(Database.ImprovedCursor cursor) {
        id = cursor.getIntOrThrow(TAKEOFF_ID);
        name = cursor.getString(NAME);
        description = cursor.getString(DESCRIPTION);
        asl = cursor.getInt(ASL);
        height = cursor.getInt(HEIGHT);
        latitude = cursor.getDouble(LATITUDE);
        longitude = cursor.getDouble(LONGITUDE);
        exits = cursor.getInt(EXITS);
        // TODO: noaaForecast = geCursorBitmap(cursor, NOAA_FORECAST);
        noaaUpdated = cursor.getLong(NOAA_UPDATED);
        favourite = cursor.getInt(FAVOURITE) == 1;
    }


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

    // TODO: remove? along with Parcel? fetch from database instead?
    public Takeoff(int id, String name, String description, int asl, int height, double latitude, double longitude, int exits, boolean favourite) {
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

    public void setFavourite(boolean favourite) {
        this.favourite = favourite;
    }

    public boolean isFavourite() {
        return favourite;
    }

    public ContentValues getContentValues() {
        ContentValues contentValues = new ContentValues();
        contentValues.put(TAKEOFF_ID, id);
        contentValues.put(NAME, name);
        contentValues.put(DESCRIPTION, description);
        contentValues.put(ASL, asl);
        contentValues.put(HEIGHT, height);
        double latitudeRadians = latitude * Math.PI / 180.0;
        contentValues.put(LATITUDE, latitude);
        contentValues.put(LATITUDE + "_cos", Math.cos(latitudeRadians));
        contentValues.put(LATITUDE + "_sin", Math.sin(latitudeRadians));
        double longitudeRadians = longitude * Math.PI / 180.0;
        contentValues.put(LONGITUDE, longitude);
        contentValues.put(LONGITUDE + "_cos", Math.cos(longitudeRadians));
        contentValues.put(LONGITUDE + "_sin", Math.sin(longitudeRadians));
        contentValues.put(EXITS, exits);
        contentValues.put(FAVOURITE, favourite);
        Bitmap bitmap = noaaForecast;
        if (bitmap != null) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(bitmap.getRowBytes() * bitmap.getHeight());
            bitmap.copyPixelsToBuffer(byteBuffer);
            contentValues.put(NOAA_FORECAST, byteBuffer.array());
        }
        contentValues.put(NOAA_UPDATED, noaaUpdated);
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
