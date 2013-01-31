package net.exent.flywithme.data;

import android.location.Location;

public class Takeoff {
	private int id;
	private String name;
	private String description;
	private int asl;
	private int height;
	private Location location;
	private String startDirections;
	private boolean northExit;
	private boolean northwestExit;
	private boolean westExit;
	private boolean southwestExit;
	private boolean southExit;
	private boolean southeastExit;
	private boolean eastExit;
	private boolean northeastExit;

	public Takeoff(int id, String name, String description, int asl, int height, Location location, String startDirections) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.asl = asl;
		this.height = height;
		this.location = location;
		this.startDirections = startDirections;
		String[] directions = startDirections.split(" ");
		for (String direction : directions) {
			if ("N".equals(direction))
				northExit = true;
			if ("NW".equals(direction))
				northwestExit = true;
			if ("W".equals(direction))
				westExit = true;
			if ("SW".equals(direction))
				southwestExit = true;
			if ("S".equals(direction))
				southExit = true;
			if ("SE".equals(direction))
				southeastExit = true;
			if ("E".equals(direction))
				eastExit = true;
			if ("NE".equals(direction))
				northeastExit = true;
		}
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
	
	public String getStartDirections() {
		return startDirections;
	}
	
	public boolean hasNorthExit() {
		return northExit;
	}
	
	public boolean hasNorthwestExit() {
		return northwestExit;
	}
	
	public boolean hasWestExit() {
		return westExit;
	}
	
	public boolean hasSouthwestExit() {
		return southwestExit;
	}
	
	public boolean hasSouthExit() {
		return southExit;
	}
	
	public boolean hasSoutheastExit() {
		return southeastExit;
	}
	
	public boolean hasEastExit() {
		return eastExit;
	}
	
	public boolean hasNortheastExit() {
		return northeastExit;
	}

	@Override
	public String toString() {
		return name; 
	}
}
