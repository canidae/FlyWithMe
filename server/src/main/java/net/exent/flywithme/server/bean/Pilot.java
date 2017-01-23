package net.exent.flywithme.server.bean;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;

import java.io.Serializable;
import java.util.Objects;

/**
 * Information about a pilot.
 */
@Entity
public class Pilot implements Serializable {
    @Id private String id;

    public String getId() {
        return id;
    }

    public Pilot setId(String id) {
        this.id = id;
        return this;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof Pilot && Objects.equals(id, ((Pilot) object).getId());
    }
}