package net.exent.flywithme.bean;

import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationManager;
import android.os.Parcel;
import android.os.Parcelable;

public class Takeoff implements Parcelable {
    public static final Parcelable.Creator<Takeoff> CREATOR = new Parcelable.Creator<Takeoff>() {
        public Takeoff createFromParcel(Parcel in) {
            return new Takeoff(in.readInt(), in.readString(), in.readString(), in.readInt(), in.readInt(), in.readDouble(), in.readDouble(), in.readInt(), in.readByte() == 1);
        }

        public Takeoff[] newArray(int size) {
            return new Takeoff[size];
        }
    };
    private int id;
    private String name;
    private String description;
    private int asl;
    private int height;
    private Location location;
    private int exits;
    private Bitmap noaaForecast;
    private long noaaUpdated;
    private boolean favourite;

    public Takeoff(int id, String name, String description, int asl, int height, double latitude, double longitude, String exitDirections, boolean favourite) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.asl = asl;
        this.height = height;
        this.location = new Location(LocationManager.PASSIVE_PROVIDER);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
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

    public Takeoff(int id, String name, String description, int asl, int height, double latitude, double longitude, int exits, boolean favourite) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.asl = asl;
        this.height = height;
        this.location = new Location(LocationManager.PASSIVE_PROVIDER);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
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

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(name);
        dest.writeString(description);
        dest.writeInt(asl);
        dest.writeInt(height);
        dest.writeDouble(location.getLatitude());
        dest.writeDouble(location.getLongitude());
        dest.writeInt(exits);
        dest.writeByte((byte) (favourite ? 1 : 0));
    }

    @Override
    public String toString() {
        return name;
    }
}
