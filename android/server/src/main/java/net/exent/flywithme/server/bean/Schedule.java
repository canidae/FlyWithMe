package net.exent.flywithme.server.bean;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;

import java.util.HashSet;
import java.util.Set;

/**
 * The schedule for future flights.
 */
@Entity
public class Schedule {
    @Id private long timestamp;

    private long takeoffId;
    private Set<String> pilotIds;

    public long getTimestamp() {
        return timestamp;
    }

    public Schedule setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public long getTakeoff() {
        return takeoffId;
    }

    public Schedule setTakeoff(long takeoffId) {
        this.takeoffId = takeoffId;
        return this;
    }

    public Set<String> getPilotIds() {
        return pilotIds;
    }

    public Schedule addPilot(String pilotId) {
        if (pilotIds == null)
            pilotIds = new HashSet<>();
        pilotIds.add(pilotId);
        return this;
    }

    public Schedule removePilot(String pilotId) {
        if (pilotIds != null)
            pilotIds.remove(pilotId);
        return this;
    }
}
