package net.exent.flywithme;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import net.exent.flywithme.R;
import net.exent.flywithme.dao.Flightlog;
import net.exent.flywithme.data.Takeoff;
import net.exent.flywithme.widget.TakeoffArrayAdapter;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.FragmentActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
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
	private static LayoutView returnToView;
	private static Takeoff activeTakeoff;
	private static boolean hasInitializedMap;
	
	private enum LayoutView {
		MAP {
			private Map<Marker, Takeoff> markerMap = new HashMap<Marker, Takeoff>();

			@Override
			public void drawView(final FlyWithMe activity) {
				Log.d("FlyWithMe", "MAP.draw(" + activity + ")");
				if (view == null) {
					view = activity.getLayoutInflater().inflate(R.layout.map, null);
					((SupportMapFragment) activity.getSupportFragmentManager().findFragmentById(R.id.takeoffMap)).setRetainInstance(true);

					GoogleMap map = ((SupportMapFragment) activity.getSupportFragmentManager().findFragmentById(R.id.takeoffMap)).getMap();
					if (map == null)
						return;

					map.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
						public void onInfoWindowClick(Marker marker) {
							Log.d("FlyWithMe", "MAP.onInfoWindowClick(" + marker + ")");
							marker.hideInfoWindow();
							activeTakeoff = markerMap.get(marker);
							
							/* hack: map crash if we try to draw directly, setting up a new thread to draw takeoff details */
							Handler handler = new Handler();
							handler.post(new Runnable() {
								public void run() {
									returnToView = LayoutView.MAP;
									activeView = LayoutView.TAKEOFF_DETAIL;
									activeView.draw(activity);
								}
							});
						}
					});

					if (!hasInitializedMap) {
						map.setMyLocationEnabled(true);
						map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), (float) 10.0));
						map.getUiSettings().setZoomControlsEnabled(false);
						hasInitializedMap = true;
					}
				}
				activity.setContentView(view);
				activeView.setupLocationListener(activity);

				updateLocation(activity);
			}
			
			@Override
			protected void updateLocation(final FlyWithMe activity) {
				Log.d("FlyWithMe", "MAP.updateLocation(" + activity + ")");
				GoogleMap map = ((SupportMapFragment) activity.getSupportFragmentManager().findFragmentById(R.id.takeoffMap)).getMap();
				if (map == null)
					return;

				map.clear();
				markerMap.clear();
				List<Takeoff> takeoffs = Flightlog.getTakeoffs(activity);
				for (int i = 0; i < takeoffs.size(); i++) {
					Takeoff takeoff = takeoffs.get(i);
					Marker marker = map.addMarker(new MarkerOptions()
                		.position(new LatLng(takeoff.getLocation().getLatitude(), takeoff.getLocation().getLongitude()))
                		.title(takeoff.getName())
                		.snippet("Height: " + takeoff.getHeight() + ", Start: " + takeoff.getStartDirections())
                		.icon(BitmapDescriptorFactory.defaultMarker((float) 42)));
					markerMap.put(marker, takeoff);
				}
			}
		},
		
		TAKEOFF_LIST {
			@Override
			public void drawView(final FlyWithMe activity) {
				Log.d("FlyWithMe", "TAKEOFF_LIST.draw(" + activity + ")");
				if (view == null)
					view = activity.getLayoutInflater().inflate(R.layout.takeoff_list, null);
				activity.setContentView(view);
				activeView.setupLocationListener(activity);
				
				ImageButton mapButton = (ImageButton) activity.findViewById(R.id.takeoffListMapButton);
				mapButton.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						activeView = LayoutView.MAP;
						activeView.draw(activity);
					}
				});

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
			
			@Override
			protected void updateLocation(final FlyWithMe activity) {
				Log.d("FlyWithMe", "TAKEOFF_LIST.updateLocation(" + activity + ")");
				ListView takeoffsView = (ListView) activity.findViewById(R.id.takeoffList);
				if (takeoffsView != null)
					takeoffsView.invalidateViews();
			}
		},
		
		TAKEOFF_DETAIL {
			public void drawView(final FlyWithMe activity) {
				Log.d("FlyWithMe", "TAKEOFF_DETAIL.draw(" + activity + ")");
				if (view == null)
					view = activity.getLayoutInflater().inflate(R.layout.takeoff_detail, null);
				activity.setContentView(view);

				ImageButton mapButton = (ImageButton) activity.findViewById(R.id.takeoffDetailMapButton);
				mapButton.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						Location loc = activeTakeoff.getLocation();
						String uri = "http://maps.google.com/maps?saddr=" + location.getLatitude() + "," + location.getLongitude() + "&daddr=" + loc.getLatitude() + "," + loc.getLongitude(); 
						Intent intent = new Intent(android.content.Intent.ACTION_VIEW, Uri.parse(uri));
						activity.startActivity(intent);
					}
				});

				ImageButton fwmButton = (ImageButton) activity.findViewById(R.id.takeoffDetailFwmButton);
				fwmButton.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						returnToView = LayoutView.TAKEOFF_LIST;
						activeView = LayoutView.TAKEOFF_LIST;
						activeView.draw(activity);
					}
				});

				TextView takeoffName = (TextView) activity.findViewById(R.id.takeoffDetailName);
				TextView takeoffCoordAslHeight = (TextView) activity.findViewById(R.id.takeoffDetailCoordAslHeight);
				TextView takeoffDescription = (TextView) activity.findViewById(R.id.takeoffDetailDescription);
				
				takeoffName.setText(activeTakeoff.getName());
				takeoffCoordAslHeight.setText(String.format("[%.2f,%.2f] " + activity.getString(R.string.asl) + ": %d " + activity.getString(R.string.height) + ": %d", activeTakeoff.getLocation().getLatitude(), activeTakeoff.getLocation().getLongitude(), activeTakeoff.getAsl(), activeTakeoff.getHeight()));
				takeoffDescription.setText(activeTakeoff.getDescription());
				takeoffDescription.setMovementMethod(new ScrollingMovementMethod());
			}
		};
		
		protected View view;
		
		/* when onCreate() is called we need to reinflate views, setting view to null forces this */
		public final void clearView() {
			view = null;
		}
		
		public final void draw(final FlyWithMe activity) {
			if (view != null) {
				ViewGroup parent = (ViewGroup) view.getParent();
				if (parent != null)
					parent.removeView(view);
			}
			drawView(activity);
		}
		
		protected abstract void drawView(final FlyWithMe activity);
		
		protected void updateLocation(final FlyWithMe activity) {
			Log.w("FlyWithMe", "LayoutView." + activeView + " has not implemented method updateLocation(), yet it receives location updates");
		}
		
		private void setupLocationListener(final FlyWithMe activity) {
			Log.d("FlyWithMe", "LayoutView.setupLocationListener(" + activity + ")");
			LocationListener locationListener = new LocationListener() {
				public void onStatusChanged(String provider, int status, Bundle extras) {
				}
				
				public void onProviderEnabled(String provider) {
				}
				
				public void onProviderDisabled(String provider) {
				}
				
				public void onLocationChanged(Location newLocation) {
					if (newLocation == null)
						return;
					location = newLocation;
					updateLocation(activity);
				}
			};
			LocationManager locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
			locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_UPDATE_TIME, LOCATION_UPDATE_DISTANCE, locationListener);
			if (location == null) {
				location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
				if (location == null)
					location = new Location(LocationManager.PASSIVE_PROVIDER); // no location set, let's pretend we're skinny dipping in the gulf of guinea
			}
		}
	}
	
	public static Location getLocation() {
		return location;
	}
	
	@Override
	public void onBackPressed() {
		Log.d("FlyWithMe", "onBackPressed()");
		if (activeView == LayoutView.TAKEOFF_LIST) {
			super.onBackPressed();
		} else {
			activeView = returnToView == null ? LayoutView.TAKEOFF_LIST : returnToView;
			returnToView = null;
			activeView.draw(this);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d("FlyWithMe", "onCreate(" + savedInstanceState + ")");
		super.onCreate(savedInstanceState);
        
		for (LayoutView layoutView : LayoutView.values())
			layoutView.clearView();

		if (activeView == null)
			activeView = LayoutView.TAKEOFF_LIST;
		activeView.draw(this);
	}
	
	@Override
	protected void onPause() {
		Log.d("FlyWithMe", "onPause()");
		super.onPause();
		hasInitializedMap = false;
	}
}
