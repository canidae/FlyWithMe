package net.exent.flywithme.bean;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

import java.io.Serializable;

/**
 * Information about a Takeoff.
 */
@Entity
public class Takeoff implements Serializable {
    @Id private long id;

    // takeoff data
    @Index private long updated;
    private String name;
    private String desc;
    private int asl;
    private int height;
    private float lat;
    private float lng;
    private int exits;

    public long getId() {
        return id;
    }

    public Takeoff setId(long id) {
        this.id = id;
        return this;
    }

    public long getUpdated() {
        return updated;
    }

    public Takeoff setUpdated(long updated) {
        this.updated = updated;
        return this;
    }

    public String getName() {
        return name;
    }

    public Takeoff setName(String name) {
        this.name = name;
        return this;
    }

    public String getDesc() {
        return desc;
    }

    public Takeoff setDesc(String desc) {
        this.desc = desc;
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

    public float getLat() {
        return lat;
    }

    public Takeoff setLat(float lat) {
        this.lat = lat;
        return this;
    }

    public float getLng() {
        return lng;
    }

    public Takeoff setLng(float lng) {
        this.lng = lng;
        return this;
    }

    public int getExits() {
        return exits;
    }

    public Takeoff setExits(int exits) {
        this.exits = exits;
        return this;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Takeoff))
            return false;
        Takeoff takeoff = (Takeoff) object;
        return getId() == takeoff.getId() && getAsl() == takeoff.getAsl() && getHeight() == takeoff.getHeight()
                && getLat() == takeoff.getLat() && getLng() == takeoff.getLng()
                && getExits() == takeoff.getExits() && getName().equals(takeoff.getName()) && getDesc().equals(takeoff.getDesc());
    }
}