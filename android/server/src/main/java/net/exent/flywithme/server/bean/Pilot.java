package net.exent.flywithme.server.bean;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;

/**
 * Information about a pilot.
 */
@Entity
public class Pilot {
    @Id
    private String pilotId;

    public String getPilotId() {
        return pilotId;
    }

    public Pilot setPilotId(String pilotId) {
        this.pilotId = pilotId;
        return this;
    }
}