package net.exent.flywithme.fragment;

import net.exent.flywithme.R;
import net.exent.flywithme.bean.Takeoff;
import net.exent.flywithme.data.Database;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class TakeoffList extends Fragment implements GoogleApiClient.ConnectionCallbacks, LocationListener {
    public static final String ARG_LOCATION = "location";

    private static int savedPosition; // TODO: not static?
    private static int savedListTop; // TODO: not static?
    private List<Takeoff> takeoffs = new ArrayList<>();
    private TakeoffArrayAdapter takeoffArrayAdapter;

    private GoogleApiClient googleApiClient;
    private Location location;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        googleApiClient = new GoogleApiClient.Builder(getActivity()).addApi(LocationServices.API).addConnectionCallbacks(this).build();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        if (getArguments() != null) {
            Location location = getArguments().getParcelable(ARG_LOCATION);
            if (location != null)
                this.location = location;
        }
        if (bundle != null) {
            Location location = bundle.getParcelable(ARG_LOCATION);
            if (location != null)
                this.location = location;
        }

        View view = inflater.inflate(R.layout.takeoff_list, container, false);
        takeoffArrayAdapter = new TakeoffArrayAdapter(getActivity());
        ListView listView = (ListView) view.findViewById(R.id.takeoffListView);
        listView.setAdapter(takeoffArrayAdapter);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Takeoff takeoff = takeoffs.get(position);
                TakeoffDetails takeoffDetails = new TakeoffDetails();
                Bundle args = new Bundle();
                args.putParcelable(TakeoffDetails.ARG_TAKEOFF, takeoff);
                takeoffDetails.setArguments(args);
                String tag = "takeoffDetails," + takeoff.getId();
                FragmentManager fragmentManager = getFragmentManager();
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                fragmentTransaction.replace(R.id.fragmentContainer, takeoffDetails, tag);
                if (fragmentManager.findFragmentByTag(tag) == null)
                    fragmentTransaction.addToBackStack(tag);
                fragmentTransaction.commit();
            }
        });
        /* position list */
        listView.setSelectionFromTop(savedPosition, savedListTop);

        ((ImageButton) getActivity().findViewById(R.id.fragmentButton1)).setImageDrawable(null);
        ((ImageButton) getActivity().findViewById(R.id.fragmentButton2)).setImageDrawable(null);
        ((ImageButton) getActivity().findViewById(R.id.fragmentButton3)).setImageDrawable(null);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        googleApiClient.connect();
    }

    @Override
    public void onConnected(Bundle bundle) {
        LocationRequest locationRequest = LocationRequest.create().setInterval(10000).setFastestInterval(10000).setPriority(LocationRequest.PRIORITY_LOW_POWER);
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
        updateTakeoffList(LocationServices.FusedLocationApi.getLastLocation(googleApiClient));
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onLocationChanged(Location location) {
        updateTakeoffList(location);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(ARG_LOCATION, location);
    }

    @Override
    public void onPause() {
        super.onPause();
        LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
    }

    @Override
    public void onStop() {
        super.onStop();
        googleApiClient.disconnect();

        /* remember position in list */
        ListView listView = (ListView) getActivity().findViewById(R.id.takeoffListView);
        savedPosition = listView.getFirstVisiblePosition();
        View firstVisibleView = listView.getChildAt(0);
        savedListTop = (firstVisibleView == null) ? 0 : firstVisibleView.getTop();
    }

    private void updateTakeoffList(Location newLocation) {
        if (location == null && newLocation == null) {
            // no location set, let's pretend we're at the Rikssenter :)
            location = new Location(LocationManager.PASSIVE_PROVIDER);
            location.setLatitude(61.874655);
            location.setLongitude(9.154848);
        } else if (newLocation == null) {
            // null location received? that's odd, do nothing
            return;
        } else if (location != null && location.distanceTo(newLocation) < 100) {
            // we're within 100 meters from the last place we updated the list, do nothing
            return;
        } else {
            // no previous location set or we've moved 100 meters or more
            location = newLocation;
        }
        takeoffs = new Database(getActivity()).getTakeoffs(location.getLatitude(), location.getLongitude(), 100, true);
        Collections.sort(takeoffs, new Comparator<Takeoff>() {
            public int compare(Takeoff lhs, Takeoff rhs) {
                if (!lhs.isFavourite() && rhs.isFavourite())
                    return 1;
                else if (lhs.isFavourite() && !rhs.isFavourite())
                    return -1;
                // both or neither are favourites, sort those with scheduled flights first
                if (lhs.getPilotsToday() < rhs.getPilotsToday())
                    return 1;
                else if (lhs.getPilotsToday() > rhs.getPilotsToday())
                    return -1;
                // both or neither have scheduled flights today, sort by those who have flights later
                if (lhs.getPilotsLater() < rhs.getPilotsLater())
                    return 1;
                else if (lhs.getPilotsLater() > rhs.getPilotsLater())
                    return -1;
                // both or neither have scheduled flights, sort by distance from user
                if (location.distanceTo(lhs.getLocation()) > location.distanceTo(rhs.getLocation()))
                    return 1;
                else if (location.distanceTo(lhs.getLocation()) < location.distanceTo(rhs.getLocation()))
                    return -1;
                return 0;
            }
        });
        takeoffArrayAdapter.notifyDataSetChanged();
    }

    private class TakeoffArrayAdapter extends ArrayAdapter<Takeoff> {
        public TakeoffArrayAdapter(Context context) {
            super(context, R.layout.takeoff_list_entry);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Takeoff takeoff = takeoffs.get(position);

            ViewHolder viewHolder;
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.takeoff_list_entry, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.takeoffName = (TextView) convertView.findViewById(R.id.takeoffListEntryName);
                viewHolder.takeoffInfo = (TextView) convertView.findViewById(R.id.takeoffListEntryInfo);
                viewHolder.windroseNorth = (ImageView) convertView.findViewById(R.id.takeoffListEntryWindroseNorth);
                viewHolder.windroseNorthwest = (ImageView) convertView.findViewById(R.id.takeoffListEntryWindroseNorthwest);
                viewHolder.windroseWest = (ImageView) convertView.findViewById(R.id.takeoffListEntryWindroseWest);
                viewHolder.windroseSouthwest = (ImageView) convertView.findViewById(R.id.takeoffListEntryWindroseSouthwest);
                viewHolder.windroseSouth = (ImageView) convertView.findViewById(R.id.takeoffListEntryWindroseSouth);
                viewHolder.windroseSoutheast = (ImageView) convertView.findViewById(R.id.takeoffListEntryWindroseSoutheast);
                viewHolder.windroseEast = (ImageView) convertView.findViewById(R.id.takeoffListEntryWindroseEast);
                viewHolder.windroseNortheast = (ImageView) convertView.findViewById(R.id.takeoffListEntryWindroseNortheast);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            viewHolder.takeoffName.setText(takeoff.toString());
            viewHolder.takeoffName.setTextColor(takeoff.isFavourite() ? Color.CYAN : Color.WHITE);
            String takeoffIntoText = getContext().getString(R.string.distance) + ": " + (int) location.distanceTo(takeoff.getLocation()) / 1000 + "km";
            if (takeoff.getPilotsToday() > 0 || takeoff.getPilotsLater() > 0) {
                takeoffIntoText += ", " + getContext().getString(R.string.pilots) + ": ";
                if (takeoff.getPilotsToday() > 0)
                    takeoffIntoText += takeoff.getPilotsToday() + " " + getContext().getString(R.string.today);
                if (takeoff.getPilotsLater() > 0)
                    takeoffIntoText += (takeoff.getPilotsToday() > 0 ? ", " : "") + takeoff.getPilotsLater() + " " + getContext().getString(R.string.later);
            }
            viewHolder.takeoffInfo.setText(takeoffIntoText);
            /* windpai */
            viewHolder.windroseNorth.setVisibility(takeoff.hasNorthExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
            viewHolder.windroseNorthwest.setVisibility(takeoff.hasNorthwestExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
            viewHolder.windroseWest.setVisibility(takeoff.hasWestExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
            viewHolder.windroseSouthwest.setVisibility(takeoff.hasSouthwestExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
            viewHolder.windroseSouth.setVisibility(takeoff.hasSouthExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
            viewHolder.windroseSoutheast.setVisibility(takeoff.hasSoutheastExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
            viewHolder.windroseEast.setVisibility(takeoff.hasEastExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
            viewHolder.windroseNortheast.setVisibility(takeoff.hasNortheastExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
            return convertView;
        }

        @Override
        public int getCount() {
            return takeoffs.size();
        }
    }

    private class ViewHolder {
        TextView takeoffName;
        TextView takeoffInfo;
        ImageView windroseNorth;
        ImageView windroseNorthwest;
        ImageView windroseWest;
        ImageView windroseSouthwest;
        ImageView windroseSouth;
        ImageView windroseSoutheast;
        ImageView windroseEast;
        ImageView windroseNortheast;
    }
}
