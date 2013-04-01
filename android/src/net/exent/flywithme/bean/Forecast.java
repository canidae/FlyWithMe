package net.exent.flywithme.bean;

public class Forecast {
    private int timestamp;
    private float temperature;
    private float windDirection;
    private float windSpeed;
    private float humidity;
    private float pressure;
    
    public Forecast(int timestamp, double temperature, double windDirection, double windSpeed, double humidity, double pressure) {
        this.timestamp = timestamp;
        this.temperature = (float) temperature;
        this.windDirection = (float) windDirection;
        this.windSpeed = (float) windSpeed;
        this.humidity = (float) humidity;
        this.pressure = (float) pressure;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public float getTemperature() {
        return temperature;
    }

    public float getWindDirection() {
        return windDirection;
    }

    public float getWindSpeed() {
        return windSpeed;
    }

    public float getHumidity() {
        return humidity;
    }

    public float getPressure() {
        return pressure;
    }
    
    public String toString() {
        return timestamp + ": " + temperature + "°C, " + windDirection + "° & " + windSpeed + " m/s, " + humidity + "%, " + pressure + " hPa";
    }
}
