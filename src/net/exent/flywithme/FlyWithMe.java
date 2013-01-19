package net.exent.flywithme;

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
	private static final int LOCATION_UPDATE_TIME = 300000; // update location every 5 minute
	private static final int LOCATION_UPDATE_DISTANCE = 100; // or when we've moved more than 100 meters
	private static Location location;
	private static Takeoff showTakeoff;
	
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
	
	@Override
	public void onBackPressed() {
		ViewSwitcher switcher = (ViewSwitcher) findViewById(R.id.mainViewSwitcher);
		if (switcher.getCurrentView().getId() == R.id.takeoffDetailLayout) {
			switcher.showPrevious();
			showTakeoff = null;
		} else {
			super.onBackPressed();
		}
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

		/* set initial location & listener */
		LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_UPDATE_TIME, LOCATION_UPDATE_DISTANCE, locationListener);
		Location newLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		if (newLocation == null)
			newLocation = new Location(LocationManager.PASSIVE_PROVIDER); // no location set, let's pretend we're skinny dipping in the gulf of guinea
		updateLocation(newLocation);

		TakeoffArrayAdapter adapter = new TakeoffArrayAdapter(this);
		ListView takeoffsView = (ListView) findViewById(R.id.takeoffs);
		takeoffsView.setAdapter(adapter);
		final Context context = this;
		takeoffsView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				showTakeoff = Flightlog.getTakeoffs(context).get(position);
				showTakeoffDetails();
				
				ViewSwitcher switcher = (ViewSwitcher) findViewById(R.id.mainViewSwitcher);
				switcher.showNext();
			}
		});
		
		if (showTakeoff != null) {
			showTakeoffDetails();
			ViewSwitcher switcher = (ViewSwitcher) findViewById(R.id.mainViewSwitcher);
			switcher.showNext();
		}
	}
	
	private void showTakeoffDetails() {
		TextView takeoffName = (TextView) findViewById(R.id.takeoffDetailName);
		TextView takeoffCoordAslHeight = (TextView) findViewById(R.id.takeoffDetailCoordAslHeight);
		TextView takeoffDescription = (TextView) findViewById(R.id.takeoffDetailDescription);
		ImageButton mapButton = (ImageButton) findViewById(R.id.takeoffDetailMapButton);
		
		takeoffName.setText(showTakeoff.getName());
		takeoffCoordAslHeight.setText(String.format("[%.2f,%.2f] " + getString(R.string.asl) + ": %d " + getString(R.string.height) + ": %d", showTakeoff.getLocation().getLatitude(), showTakeoff.getLocation().getLongitude(), showTakeoff.getAsl(), showTakeoff.getHeight()));
		takeoffDescription.setText(showTakeoff.getDescription());
		takeoffDescription.setMovementMethod(new ScrollingMovementMethod());

		mapButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Location loc = showTakeoff.getLocation();
				String uri = "http://maps.google.com/maps?saddr=" + location.getLatitude() + "," + location.getLongitude() + "&daddr=" + loc.getLatitude() + "," + loc.getLongitude(); 
				Intent intent = new Intent(android.content.Intent.ACTION_VIEW, Uri.parse(uri));
				startActivity(intent);
			}
		});
	}
	
	private void updateLocation(Location newLocation) {
		if (newLocation == null)
			return;
		location = newLocation;

		ListView takeoffsView = (ListView) findViewById(R.id.takeoffs);
		takeoffsView.invalidateViews();
	}
}
