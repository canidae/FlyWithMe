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
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

public class FlyWithMe extends FragmentActivity {
	private static final int LOCATION_UPDATE_TIME = 300000; // update location every 5 minute
	private static final int LOCATION_UPDATE_DISTANCE = 100; // or when we've moved more than 100 meters
	private static Location location;
	private static LayoutView activeView;
	private static Takeoff activeTakeoff;
	
	private enum LayoutView {
		MAP {
			public void draw(final FlyWithMe activity) {
				Log.d("Flightlog", "MAP.draw(" + activity + ")");
				activity.setContentView(R.layout.map);
			}
		},
		
		TAKEOFF_LIST {
			public void draw(final FlyWithMe activity) {
				Log.d("Flightlog", "TAKEOFF_LIST.draw(" + activity + ")");
				activity.setContentView(R.layout.takeoff_list);

				/* set initial location & listener */
				LocationListener locationListener = new LocationListener() {
					public void onStatusChanged(String provider, int status, Bundle extras) {
					}
					
					public void onProviderEnabled(String provider) {
					}
					
					public void onProviderDisabled(String provider) {
					}
					
					public void onLocationChanged(Location newLocation) {
						activity.updateLocation(newLocation);
					}
				};
				LocationManager locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
				locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_UPDATE_TIME, LOCATION_UPDATE_DISTANCE, locationListener);
				Location newLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
				if (newLocation == null)
					newLocation = new Location(LocationManager.PASSIVE_PROVIDER); // no location set, let's pretend we're skinny dipping in the gulf of guinea
				activity.updateLocation(newLocation);

				TakeoffArrayAdapter adapter = new TakeoffArrayAdapter(activity);
				ListView takeoffsView = (ListView) activity.findViewById(R.id.takeoffList);
				takeoffsView.setAdapter(adapter);
				takeoffsView.setOnItemClickListener(new OnItemClickListener() {
					public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
						activeTakeoff = Flightlog.getTakeoffs(activity).get(position);
						activeView = LayoutView.TAKEOFF_DETAIL;
						activeView.draw(activity);
					}
				});
			}
		},
		
		TAKEOFF_DETAIL {
			public void draw(final FlyWithMe activity) {
				Log.d("Flightlog", "TAKEOFF_DETAIL.draw(" + activity + ")");
				activity.setContentView(R.layout.takeoff_detail);

				TextView takeoffName = (TextView) activity.findViewById(R.id.takeoffDetailName);
				TextView takeoffCoordAslHeight = (TextView) activity.findViewById(R.id.takeoffDetailCoordAslHeight);
				TextView takeoffDescription = (TextView) activity.findViewById(R.id.takeoffDetailDescription);
				ImageButton mapButton = (ImageButton) activity.findViewById(R.id.takeoffDetailMapButton);
				
				takeoffName.setText(activeTakeoff.getName());
				takeoffCoordAslHeight.setText(String.format("[%.2f,%.2f] " + activity.getString(R.string.asl) + ": %d " + activity.getString(R.string.height) + ": %d", activeTakeoff.getLocation().getLatitude(), activeTakeoff.getLocation().getLongitude(), activeTakeoff.getAsl(), activeTakeoff.getHeight()));
				takeoffDescription.setText(activeTakeoff.getDescription());
				takeoffDescription.setMovementMethod(new ScrollingMovementMethod());

				mapButton.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						Location loc = activeTakeoff.getLocation();
						String uri = "http://maps.google.com/maps?saddr=" + location.getLatitude() + "," + location.getLongitude() + "&daddr=" + loc.getLatitude() + "," + loc.getLongitude(); 
						Intent intent = new Intent(android.content.Intent.ACTION_VIEW, Uri.parse(uri));
						activity.startActivity(intent);
					}
				});
			}
		};
		
		public abstract void draw(final FlyWithMe activity);
	}
	
	public static Location getLocation() {
		Log.d("Flightlog", "getLocation()");
		return location;
	}
	
	@Override
	public void onBackPressed() {
		Log.d("FlightLog", "onBackPressed()");
		if (activeView == LayoutView.TAKEOFF_LIST) {
			super.onBackPressed();
		} else {
			activeView = LayoutView.TAKEOFF_LIST;
			activeView.draw(this);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d("FlyWithMe", "onCreate(" + savedInstanceState + ")");
		super.onCreate(savedInstanceState);
		if (activeView == null)
			activeView = LayoutView.TAKEOFF_LIST;
		activeView.draw(this);

	}
	
	private void updateLocation(Location newLocation) {
		Log.d("FlyWithMe", "updateLocation(" + newLocation + ")");
		if (newLocation == null)
			return;
		location = newLocation;

		ListView takeoffsView = (ListView) findViewById(R.id.takeoffList);
		if (takeoffsView != null)
			takeoffsView.invalidateViews();
	}
}
