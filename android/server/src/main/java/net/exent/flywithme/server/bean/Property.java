package net.exent.flywithme.server.bean;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;

/**
 * A key-value property for persisting and sharing values across instances.
 */
@Entity
public class Property {
    @Id private String key;
    private String value;

    public Property() {
    }

    public Property(String key, Object value) {
        this.setKey(key);
        this.setValue(value);
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public boolean getValueAsBoolean() {
        return Boolean.parseBoolean(value);
    }

    public int getValueAsInt() {
        return Integer.parseInt(value);
    }

    public long getValueAsLong() {
        return Long.parseLong(value);
    }

    public float getValueAsFloat() {
        return Float.parseFloat(value);
    }

    public double getValueAsDouble() {
        return Double.parseDouble(value);
    }

    public void setValue(Object value) {
        this.value = "" + value;
    }
}
