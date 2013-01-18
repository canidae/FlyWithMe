package net.exent.flywithme.data;

import android.location.Location;

public class Takeoff {
	private int id;
	private String name;
	private String description;
	private int asl;
	private int height;
	private Location location;

	public Takeoff(int id, String name, String description, int asl, int height, Location location) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.asl = asl;
		this.height = height;
		this.location = location;
	}

	public int getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public int getAsl() {
		return asl;
	}

	public int getHeight() {
		return height;
	}

	public Location getLocation() {
		return location;
	}

	@Override
	public String toString() {
		return name; 
	}
}
