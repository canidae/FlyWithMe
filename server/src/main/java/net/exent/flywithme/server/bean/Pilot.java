package net.exent.flywithme.server.bean;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;

import java.util.Objects;

/**
 * Information about a pilot.
 */
@Entity
public class Pilot {
    @Id private String id;
    private String name;
    private String phone;

    public String getId() {
        return id;
    }

    public Pilot setId(String id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public Pilot setName(String name) {
        this.name = name;
        return this;
    }

    public String getPhone() {
        return phone;
    }

    public Pilot setPhone(String phone) {
        this.phone = phone;
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