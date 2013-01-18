package net.exent.flywithme;

import java.util.ArrayList;
import java.util.List;

import net.exent.flywithme.R;
import net.exent.flywithme.dao.Flightlog;
import net.exent.flywithme.data.Takeoff;
import net.exent.flywithme.widget.TakeoffArrayAdapter;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.support.v4.app.FragmentActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.ViewSwitcher;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

public class FlyWithMe extends FragmentActivity {
	private static Location location;
	private static Flightlog flightlog;
	private static List<Takeoff> takeoffs = new ArrayList<Takeoff>();
	
	private LocationListener locationListener = new LocationListener() {
		public void onStatusChanged(String provider, int status, Bundle extras) {
		}
		
		public void onProviderEnabled(String provider) {
		}
		
		public void onProviderDisabled(String provider) {
		}
		
		public void onLocationChanged(Location newLocation) {
			updateLocation(newLocation);
		}
	};
	
	public static Location getLocation() {
		return location;
	}
	
	public static List<Takeoff> getTakeoffs() {
		return takeoffs;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.i("FlyWithMe", "onCreate()");
		// TODO: onCreate() is called all the time (like when changing orientation), must move a lot of this code somewhere else
		// TODO: this answers some things: http://stackoverflow.com/questions/456211/activity-restart-on-rotation-android
		// TODO: this too: http://developer.android.com/reference/android/R.attr.html#configChanges
		// TODO: read this too: http://developer.android.com/guide/topics/resources/runtime-changes.html
		setContentView(R.layout.fly_with_me);

		/* create our database handler */
		flightlog = new Flightlog(this);
		
		/* set initial location & listener */
		LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, /* TODO: setting */300000, /* TODO: setting */100, locationListener);
		Location newLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		if (newLocation == null)
			newLocation = new Location(LocationManager.PASSIVE_PROVIDER); // no location set, let's pretend we're skinny dipping in the gulf of guinea
		updateLocation(newLocation);

		TakeoffArrayAdapter adapter = new TakeoffArrayAdapter(this);
		ListView takeoffsView = (ListView) findViewById(R.id.takeoffs);
		takeoffsView.setAdapter(adapter);
		takeoffsView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				final Takeoff takeoff = takeoffs.get(position);

				TextView takeoffName = (TextView) findViewById(R.id.takeoffDetailName);
				TextView takeoffCoordAslHeight = (TextView) findViewById(R.id.takeoffDetailCoordAslHeight);
				TextView takeoffDescription = (TextView) findViewById(R.id.takeoffDetailDescription);
				ImageButton mapButton = (ImageButton) findViewById(R.id.takeoffDetailMapButton);
				
				takeoffName.setText(takeoff.getName());
				takeoffCoordAslHeight.setText(String.format("[%.2f,%.2f] " + getString(R.string.asl) + ": %d " + getString(R.string.height) + ": %d", takeoff.getLocation().getLatitude(), takeoff.getLocation().getLongitude(), takeoff.getAsl(), takeoff.getHeight()));
				takeoffDescription.setText(takeoff.getDescription());
				takeoffDescription.setMovementMethod(new ScrollingMovementMethod());
				mapButton.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						Location loc = takeoff.getLocation();
						String uri = "http://maps.google.com/maps?saddr=" + location.getLatitude() + "," + location.getLongitude() + "&daddr=" + loc.getLatitude() + "," + loc.getLongitude(); 
						Intent intent = new Intent(android.content.Intent.ACTION_VIEW, Uri.parse(uri));
						startActivity(intent);
					}
				});
				
				ViewSwitcher switcher = (ViewSwitcher) findViewById(R.id.mainViewSwitcher);
				switcher.showNext();
			}
		});
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
	  super.onConfigurationChanged(newConfig);
	  setContentView(R.layout.fly_with_me);
	}
	
	@Override
	public void onBackPressed() {
		ViewSwitcher switcher = (ViewSwitcher) findViewById(R.id.mainViewSwitcher);
		if (switcher.getCurrentView().getId() == R.id.takeoffDetailLayout)
			switcher.showPrevious();
		else
			super.onBackPressed();
	}
	
	private void updateLocation(Location newLocation) {
		if (newLocation == null)
			return;
		location = newLocation;
		takeoffs = flightlog.getTakeoffs();

		ListView takeoffsView = (ListView) findViewById(R.id.takeoffs);
		takeoffsView.invalidateViews();
	}
}
