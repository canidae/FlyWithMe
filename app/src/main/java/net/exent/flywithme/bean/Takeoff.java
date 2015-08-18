package net.exent.flywithme.bean;

import android.content.ContentValues;
import android.location.Location;
import android.location.LocationManager;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import net.exent.flywithme.data.Database;

import java.util.HashMap;
import java.util.Map;

public class Takeoff implements Parcelable {
    public static final Parcelable.Creator<Takeoff> CREATOR = new Parcelable.Creator<Takeoff>() {
        public Takeoff createFromParcel(Parcel in) {
            return new Takeoff(in.readLong(), in.readLong(), in.readString(), in.readString(), in.readInt(), in.readInt(), in.readFloat(), in.readFloat(), in.readInt(), in.readByte() == 1);
        }

        public Takeoff[] newArray(int size) {
            return new Takeoff[size];
        }
    };

    public static final String[] COLUMNS = {
            "takeoff_id", "last_updated", "name", "description", "asl", "height", "latitude", "longitude", "exits", "favourite"
    };

    // used to prevent excessive memory allocations. it means more memory used, but also that the garbage collector won't run so aggressively.
    private static Map<Integer, Takeoff> takeoffCache = new HashMap<>();

    private net.exent.flywithme.server.flyWithMeServer.model.Takeoff takeoff = new net.exent.flywithme.server.flyWithMeServer.model.Takeoff();

    private int exits;
    private boolean favourite;
    private int pilotsToday; // TODO: remove?
    private int pilotsLater; // TODO: remove?

    /* Should only be used for importing takeoffs from file */
    public Takeoff(long id, long lastUpdated, String name, String description, int asl, int height, float latitude, float longitude, String windpai, boolean favourite) {
        takeoff.setId(id);
        takeoff.setLastUpdated(lastUpdated);
        takeoff.setName(name);
        takeoff.setDescription(description);
        takeoff.setAsl(asl);
        takeoff.setHeight(height);
        takeoff.setLatitude(latitude);
        takeoff.setLongitude(longitude);
        setExits(windpai);
        this.favourite = favourite;
    }

    public Takeoff(net.exent.flywithme.server.flyWithMeServer.model.Takeoff takeoff) {
        this.takeoff = takeoff;
        setExits(takeoff.getWindpai());
    }

    private Takeoff(Database.ImprovedCursor cursor) {
        takeoff.setId(cursor.getLongOrThrow("takeoff_id"));
        takeoff.setLastUpdated(cursor.getLong("last_updated") * 1000);
        takeoff.setName(cursor.getString("name"));
        takeoff.setDescription(cursor.getString("description"));
        takeoff.setAsl(cursor.getInt("asl"));
        takeoff.setHeight(cursor.getInt("height"));
        takeoff.setLatitude(cursor.getFloat("latitude"));
        takeoff.setLongitude(cursor.getFloat("longitude"));
        exits = cursor.getInt("exits");
        favourite = cursor.getInt("favourite") == 1;
    }

    private Takeoff(long id, long lastUpdated, String name, String description, int asl, int height, float latitude, float longitude, int exits, boolean favourite) {
        takeoff.setId(id);
        takeoff.setLastUpdated(lastUpdated);
        takeoff.setName(name);
        takeoff.setDescription(description);
        takeoff.setAsl(asl);
        takeoff.setHeight(height);
        takeoff.setLatitude(latitude);
        takeoff.setLongitude(longitude);
        this.exits = exits;
        this.favourite = favourite;
    }

    public static Takeoff create(Database.ImprovedCursor cursor) {
        int takeoffId = cursor.getIntOrThrow("takeoff_id");
        int pilotsToday = cursor.getInt("pilots_today");
        int pilotsLater = cursor.getInt("pilots_later");
        Takeoff takeoff = takeoffCache.get(takeoffId);
        if (takeoff != null) {
            takeoff.setPilotsToday(pilotsToday);
            takeoff.setPilotsLater(pilotsLater);
            return takeoff;
        }
        takeoff = new Takeoff(cursor);
        takeoff.setPilotsToday(pilotsToday);
        takeoffCache.put(takeoffId, takeoff);
        return takeoff;
    }

    public void setTakeoff(net.exent.flywithme.server.flyWithMeServer.model.Takeoff takeoff) {
        this.takeoff = takeoff;
    }

    public long getId() {
        return takeoff.getId();
    }

    public long getLastUpdated() {
        return takeoff.getLastUpdated();
    }

    public String getName() {
        return takeoff.getName();
    }

    public String getDescription() {
        return takeoff.getDescription();
    }

    public int getAsl() {
        return takeoff.getAsl();
    }

    public int getHeight() {
        return takeoff.getHeight();
    }

    public Location getLocation() {
        Location location = new Location(LocationManager.PASSIVE_PROVIDER);
        location.setLatitude(takeoff.getLatitude());
        location.setLongitude(takeoff.getLongitude());
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

    public void setExits(String windpai) {
        for (String direction : windpai.split(" ")) {
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
    }

    public void setFavourite(boolean favourite) {
        this.favourite = favourite;
    }

    public boolean isFavourite() {
        return favourite;
    }

    public int getPilotsToday() {
        return pilotsToday;
    }

    public void setPilotsToday(int pilotsToday) {
        this.pilotsToday = pilotsToday;
    }

    public int getPilotsLater() {
        return pilotsLater;
    }

    public void setPilotsLater(int pilotsLater) {
        this.pilotsLater = pilotsLater;
    }

    public ContentValues getContentValues() {
        ContentValues contentValues = new ContentValues();
        contentValues.put("takeoff_id", takeoff.getId());
        contentValues.put("last_updated", takeoff.getLastUpdated());
        contentValues.put("name", takeoff.getName());
        contentValues.put("description", takeoff.getDescription());
        contentValues.put("asl", takeoff.getAsl());
        contentValues.put("height", takeoff.getHeight());
        double latitudeRadians = takeoff.getLatitude() * Math.PI / 180.0;
        contentValues.put("latitude", takeoff.getLatitude());
        contentValues.put("latitude_cos", Math.cos(latitudeRadians));
        contentValues.put("latitude_sin", Math.sin(latitudeRadians));
        double longitudeRadians = takeoff.getLongitude() * Math.PI / 180.0;
        contentValues.put("longitude", takeoff.getLongitude());
        contentValues.put("longitude_cos", Math.cos(longitudeRadians));
        contentValues.put("longitude_sin", Math.sin(longitudeRadians));
        contentValues.put("exits", exits);
        // NOTE! Don't add user set data (such as "favourite"). otherwise, when importing takeoffs, this information will be lost
        return contentValues;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(takeoff.getId());
        dest.writeString(takeoff.getName());
        dest.writeString(takeoff.getDescription());
        dest.writeInt(takeoff.getAsl());
        dest.writeInt(takeoff.getHeight());
        dest.writeFloat(takeoff.getLatitude());
        dest.writeFloat(takeoff.getLongitude());
        dest.writeInt(exits);
        dest.writeByte((byte) (favourite ? 1 : 0));
    }

    @Override
    public String toString() {
        return takeoff.getName();
    }
}