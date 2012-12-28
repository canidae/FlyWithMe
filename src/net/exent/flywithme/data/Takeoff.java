package net.exent.flywithme.data;

import android.location.Location;
import android.location.LocationManager;

public class Takeoff {
	private int id;
	private String name;
	private Location location;
	private int pilotsPresent = 0; // TODO: a list of pilots present, fetched from a server (and stored in local database?)
	private int pilotsComing = 0; // TODO: a list of pilots coming, fetched from a server (and stored in local database?)
	
	public Takeoff(int id, String name, double latitude, double longitude) {
		this.id = id;
		this.name = name;
		this.location = new Location(LocationManager.PASSIVE_PROVIDER);
		this.location.setLatitude(latitude);
		this.location.setLongitude(longitude);
	}
	
	public int getId() {
		return id;
	}
	
	public String getName() {
		return name;
	}
	
	public Location getLocation() {
		return location;
	}
	
	public int getPilotsPresent() {
		return pilotsPresent;
	}
	
	public int getPilotsComing() {
		return pilotsComing;
	}

	@Override
	public String toString() {
		return name; 
	}
}
