package net.exent.flywithme;

public class Takeoff  {
    private int id;
    private String name;
    private String description;
    private int asl;
    private int height;
    private float latitude;
    private float longitude;
    private int exits;

    public Takeoff(int id, String name, String description, int asl, int height, float latitude, float longitude, String exitDirections) {
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

    public float getLatitude() {
        return latitude;
    }

    public float getLongitude() {
        return longitude;
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

    @Override
    public String toString() {
        return name;
    }
}
