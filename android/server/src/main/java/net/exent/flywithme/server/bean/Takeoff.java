package net.exent.flywithme.server.bean;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The Objectify object model for device registrations we are persisting
 */
@Entity
public class Takeoff {

    @Id private long takeoffId;

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

    // TODO: schedule
    private Map<Integer, Set<String>> schedule; // Map<timestamp, Set<pilot ID>>

    public Long getTakeoffId() {
        return takeoffId;
    }

    public Takeoff setTakeoffId(Long takeoffId) {
        this.takeoffId = takeoffId;
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

    public void addToSchedule(int timestamp, String pilotId) {
        if (schedule == null)
            schedule = new HashMap<>();
        Set<String> pilots = schedule.get(timestamp);
        if (pilots == null) {
            pilots = new HashSet<>();
            schedule.put(timestamp, pilots);
        }
        pilots.add(pilotId);
    }

    public void removeFromSchedule(int timestamp, String pilotId) {
        if (schedule == null)
            return; // no schedule for this takeoff
        Set<String> pilots = schedule.get(timestamp);
        if (pilots == null)
            return; // nothing registered at given timestamp
        pilots.remove(pilotId);
        if (pilots.isEmpty()) {
            schedule.remove(timestamp);
            if (schedule.isEmpty())
                schedule = null;
        }
    }
}