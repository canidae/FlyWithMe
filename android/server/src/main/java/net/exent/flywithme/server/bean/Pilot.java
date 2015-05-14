package net.exent.flywithme.server.bean;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

/**
 * The Objectify object model for device registrations we are persisting
 */
@Entity
public class Pilot {

    /* TODO: necessary? if so, remove @Id for pilotId
    @Id
    Long id;
    */

    @Id
    @Index // TODO: necessary when we have @Id?
    private String pilotId;
    // you can add more fields...

    public String getPilotId() {
        return pilotId;
    }

    public Pilot setPilotId(String pilotId) {
        this.pilotId = pilotId;
        return this;
    }
}