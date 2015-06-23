package net.exent.flywithme.server.bean;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

/**
 * Information about a Takeoff.
 */
@Entity
public class Takeoff {
    @Id private long id;

    // takeoff data
    @Index private long lastUpdated;
    private long lastChecked;
    private String name;
    private String description;
    private int asl;
    private int height;
    private float latitude;
    private float longitude;
    private String windpai;

    public long getId() {
        return id;
    }

    public Takeoff setId(long id) {
        this.id = id;
        return this;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public Takeoff setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
        return this;
    }

    public long getLastChecked() {
        return lastChecked;
    }

    public Takeoff setLastChecked(long lastChecked) {
        this.lastChecked = lastChecked;
        return this;
    }

    public String getName() {
        return name;
    }

    public Takeoff setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Takeoff setDescription(String description) {
        this.description = description;
        return this;
    }

    public int getAsl() {
        return asl;
    }

    public Takeoff setAsl(int asl) {
        this.asl = asl;
        return this;
    }

    public int getHeight() {
        return height;
    }

    public Takeoff setHeight(int height) {
        this.height = height;
        return this;
    }

    public float getLatitude() {
        return latitude;
    }

    public Takeoff setLatitude(float latitude) {
        this.latitude = latitude;
        return this;
    }

    public float getLongitude() {
        return longitude;
    }

    public Takeoff setLongitude(float longitude) {
        this.longitude = longitude;
        return this;
    }

    public String getWindpai() {
        return windpai;
    }

    public Takeoff setWindpai(String windpai) {
        this.windpai = windpai;
        return this;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Takeoff))
            return false;
        Takeoff takeoff = (Takeoff) object;
        return getId() == takeoff.getId() && getAsl() == takeoff.getAsl() && getHeight() == takeoff.getHeight()
                && getLatitude() == takeoff.getLatitude() && getLongitude() == takeoff.getLongitude()
                && getWindpai().equals(takeoff.getWindpai()) && getName().equals(takeoff.getName()) && getDescription().equals(takeoff.getDescription());
    }
}