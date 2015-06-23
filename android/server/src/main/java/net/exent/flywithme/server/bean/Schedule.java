package net.exent.flywithme.server.bean;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

import java.util.HashSet;
import java.util.Set;

/**
 * The schedule for future flights.
 */
@Entity
public class Schedule {
    @Id private Long id;
    @Index private long takeoffId;
    @Index private long timestamp;
    private Set<Pilot> pilots;

    public long getTimestamp() {
        return timestamp;
    }

    public Schedule setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public long getTakeoffId() {
        return takeoffId;
    }

    public Schedule setTakeoffId(long takeoffId) {
        this.takeoffId = takeoffId;
        return this;
    }

    public Set<Pilot> getPilots() {
        return pilots;
    }

    public Schedule addPilot(Pilot pilot) {
        if (pilots == null)
            pilots = new HashSet<>();
        pilots.add(pilot);
        return this;
    }

    public Schedule removePilot(Pilot pilot) {
        if (pilots != null)
            pilots.remove(pilot);
        return this;
    }
}
