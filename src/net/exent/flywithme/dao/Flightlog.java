package net.exent.flywithme.dao;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.exent.flywithme.FlyWithMe;
import net.exent.flywithme.R;
import net.exent.flywithme.data.Takeoff;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

public class Flightlog {
	private Context context;
	private List<Takeoff> takeoffs = new ArrayList<Takeoff>();
	private Location lastReadFileLocation;
	private int maxDistance = 200000; // only fetch takeoffs within 200km
	
	public Flightlog(Context context) {
		this.context = context;
	}
	
	public List<Takeoff> getTakeoffs() {
		/* need to update takeoff list? */
		final Location myLocation = FlyWithMe.getLocation();
		if (myLocation != null && (lastReadFileLocation == null || myLocation.distanceTo(lastReadFileLocation) >= maxDistance * 0.2))
			updateTakeoffList(myLocation);
		/* sorting by distance */
		Collections.sort(takeoffs, new Comparator<Takeoff>() {
			public int compare(Takeoff lhs, Takeoff rhs) {
				if (myLocation.distanceTo(lhs.getLocation()) > myLocation.distanceTo(rhs.getLocation()))
					return 1;
				else if (myLocation.distanceTo(lhs.getLocation()) < myLocation.distanceTo(rhs.getLocation()))
					return -1;
				return 0;
			}
		});
		
		return takeoffs;
	}
	
	private void updateTakeoffList(Location myLocation) {
		takeoffs = new ArrayList<Takeoff>();
		lastReadFileLocation = myLocation;
		try {
			Log.i("Flightlog", "Reading file with takeoffs");
			DataInputStream inputStream = new DataInputStream(context.getResources().openRawResource(R.raw.flywithme));
			while (true) {
				/* loop breaks once we get an EOFException */
				int takeoff = inputStream.readShort();
				String name = inputStream.readUTF();
				String description = inputStream.readUTF();
				int asl = inputStream.readShort();
				int height = inputStream.readShort();
				Location takeoffLocation = new Location(LocationManager.PASSIVE_PROVIDER);
				takeoffLocation.setLatitude(inputStream.readFloat());
				takeoffLocation.setLongitude(inputStream.readFloat());

				if (myLocation.distanceTo(takeoffLocation) <= maxDistance)
					takeoffs.add(new Takeoff(takeoff, name, description, asl, height, takeoffLocation));
			}
		} catch (EOFException e) {
			/* expected, do nothing */
			Log.i("Flightlog", "Done reading file with takeoffs");
		} catch (IOException e) {
			Log.e("Flightlog", "Error when reading file with takeoffs", e);
		}
	}
}
