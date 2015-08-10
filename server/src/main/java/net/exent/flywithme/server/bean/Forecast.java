package net.exent.flywithme.server.bean;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

/**
 * A forecast image, such as meteogram or sounding.
 */
@Entity
public class Forecast {
    public enum ForecastType {
        METEOGRAM, PROFILE, THETA, TEXT
    }

    @Id private Long id;
    @Index private long takeoffId;
    @Index private ForecastType type;
    @Index private long lastUpdated;
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

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
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
