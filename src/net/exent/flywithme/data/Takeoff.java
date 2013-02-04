package net.exent.flywithme.data;

import android.location.Location;
import android.location.LocationManager;
import android.os.Parcel;
import android.os.Parcelable;

public class Takeoff implements Parcelable {
    public static final Parcelable.Creator<Takeoff> CREATOR = new Parcelable.Creator<Takeoff>() {
        public Takeoff createFromParcel(Parcel in) {
            return new Takeoff(in.readInt(), in.readString(), in.readString(), in.readInt(), in.readInt(), in.readDouble(), in.readDouble(), in.readString());
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
    private String startDirections;
    private Location location;
    private boolean northExit;
    private boolean northwestExit;
    private boolean westExit;
    private boolean southwestExit;
    private boolean southExit;
    private boolean southeastExit;
    private boolean eastExit;
    private boolean northeastExit;

    public Takeoff(int id, String name, String description, int asl, int height, double latitude, double longitude, String startDirections) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.asl = asl;
        this.height = height;
        this.location = new Location(LocationManager.PASSIVE_PROVIDER);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        this.startDirections = startDirections;
        String[] directions = startDirections.split(" ");
        for (String direction : directions) {
            if ("N".equals(direction))
                northExit = true;
            if ("NW".equals(direction))
                northwestExit = true;
            if ("W".equals(direction))
                westExit = true;
            if ("SW".equals(direction))
                southwestExit = true;
            if ("S".equals(direction))
                southExit = true;
            if ("SE".equals(direction))
                southeastExit = true;
            if ("E".equals(direction))
                eastExit = true;
            if ("NE".equals(direction))
                northeastExit = true;
        }
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

    public int getHeight() {
        return height;
    }

    public Location getLocation() {
        return location;
    }

    public String getStartDirections() {
        return startDirections;
    }

    public boolean hasNorthExit() {
        return northExit;
    }

    public boolean hasNorthwestExit() {
        return northwestExit;
    }

    public boolean hasWestExit() {
        return westExit;
    }

    public boolean hasSouthwestExit() {
        return southwestExit;
    }

    public boolean hasSouthExit() {
        return southExit;
    }

    public boolean hasSoutheastExit() {
        return southeastExit;
    }

    public boolean hasEastExit() {
        return eastExit;
    }

    public boolean hasNortheastExit() {
        return northeastExit;
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
        dest.writeString(startDirections);
    }

    @Override
    public String toString() {
        return name;
    }
}
