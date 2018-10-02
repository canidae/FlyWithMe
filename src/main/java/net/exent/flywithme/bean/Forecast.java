package net.exent.flywithme.bean;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

import java.io.Serializable;

/**
 * A forecast image, such as meteogram or sounding.
 */
@Entity
public class Forecast implements Serializable {
    public enum ForecastType {
        METEOGRAM, PROFILE, THETA, TEXT
    }

    @Id private Long id;
    @Index private long takeoffId;
    @Index private ForecastType type;
    @Index private long updated;
    @Index private long validFor; // used by sounding as those are only valid for a certain time of a day
    private byte[] image;

    public long getTakeoffId() {
        return takeoffId;
    }

    public void setTakeoffId(Long takeoffId) {
        this.takeoffId = takeoffId;
    }

    public ForecastType getType() {
        return type;
    }

    public void setType(ForecastType type) {
        this.type = type;
    }

    public long getUpdated() {
        return updated;
    }

    public void setUpdated(long updated) {
        this.updated = updated;
    }

    public long getValidFor() {
        return validFor;
    }

    public void setValidFor(long validFor) {
        this.validFor = validFor;
    }

    public byte[] getImage() {
        return image;
    }

    public void setImage(byte[] image) {
        this.image = image;
    }
}
