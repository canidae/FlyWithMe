package net.exent.flywithme;

import java.util.ArrayList;
import java.util.List;

import net.exent.flywithme.data.Takeoff;
import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.TextView;

public class TakeoffList extends Fragment {
    public interface TakeoffListListener {
        void showTakeoffDetails(Takeoff takeoff);

        Location getLocation();

        List<Takeoff> getNearbyTakeoffs();
    }

    private static final int MAX_TAKEOFFS = 100;
    private static int savedPosition;
    private static int savedListTop;
    private TakeoffListListener callback;
    private List<Takeoff> takeoffs = new ArrayList<Takeoff>();

    @Override
    public void onAttach(Activity activity) {
        Log.d(getClass().getSimpleName(), "onAttach(" + activity + ")");
        super.onAttach(activity);
        callback = (TakeoffListListener) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(getClass().getSimpleName(), "onCreateView(" + inflater + ", " + container + ", " + savedInstanceState + ")");
        return inflater.inflate(R.layout.takeoff_list, container, false);
    }

    @Override
    public void onStart() {
        Log.d(getClass().getSimpleName(), "onStart()");
        super.onStart();

        takeoffs = callback.getNearbyTakeoffs().subList(0, MAX_TAKEOFFS);
        TakeoffArrayAdapter adapter = new TakeoffArrayAdapter(getActivity());
        ListView listView = (ListView) getActivity().findViewById(R.id.takeoffListView);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                callback.showTakeoffDetails(takeoffs.get(position));
            }
        });
        /* position list */
        listView.setSelectionFromTop(savedPosition, savedListTop);
    }

    @Override
    public void onStop() {
        Log.d(getClass().getSimpleName(), "onStop()");
        super.onStop();
        /* remember position in list */
        ListView listView = (ListView) getActivity().findViewById(R.id.takeoffListView);
        savedPosition = listView.getFirstVisiblePosition();
        View firstVisibleView = listView.getChildAt(0);
        savedListTop = (firstVisibleView == null) ? 0 : firstVisibleView.getTop();
    }

    private class TakeoffArrayAdapter extends ArrayAdapter<Takeoff> {
        public TakeoffArrayAdapter(Context context) {
            super(context, R.layout.takeoff_list_entry);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Log.d(getClass().getSimpleName(), "getView(" + position + ", " + convertView + ", " + parent + ")");
            Location location = callback.getLocation();
            Takeoff takeoff = takeoffs.get(position);

            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View rowView = inflater.inflate(R.layout.takeoff_list_entry, parent, false);
            TextView takeoffName = (TextView) rowView.findViewById(R.id.takeoffListEntryName);
            TextView takeoffDistance = (TextView) rowView.findViewById(R.id.takeoffListEntryDistance);
            takeoffName.setText(takeoff.toString());
            takeoffDistance.setText(getContext().getString(R.string.geodesic_distance) + ": " + (int) location.distanceTo(takeoff.getLocation()) / 1000 + "km");
            /* windpai */
            ImageView windroseNorth = (ImageView) rowView.findViewById(R.id.takeoffListEntryWindroseNorth);
            ImageView windroseNorthwest = (ImageView) rowView.findViewById(R.id.takeoffListEntryWindroseNorthwest);
            ImageView windroseWest = (ImageView) rowView.findViewById(R.id.takeoffListEntryWindroseWest);
            ImageView windroseSouthwest = (ImageView) rowView.findViewById(R.id.takeoffListEntryWindroseSouthwest);
            ImageView windroseSouth = (ImageView) rowView.findViewById(R.id.takeoffListEntryWindroseSouth);
            ImageView windroseSoutheast = (ImageView) rowView.findViewById(R.id.takeoffListEntryWindroseSoutheast);
            ImageView windroseEast = (ImageView) rowView.findViewById(R.id.takeoffListEntryWindroseEast);
            ImageView windroseNortheast = (ImageView) rowView.findViewById(R.id.takeoffListEntryWindroseNortheast);
            windroseNorth.setVisibility(takeoff.hasNorthExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
            windroseNorthwest.setVisibility(takeoff.hasNorthwestExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
            windroseWest.setVisibility(takeoff.hasWestExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
            windroseSouthwest.setVisibility(takeoff.hasSouthwestExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
            windroseSouth.setVisibility(takeoff.hasSouthExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
            windroseSoutheast.setVisibility(takeoff.hasSoutheastExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
            windroseEast.setVisibility(takeoff.hasEastExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
            windroseNortheast.setVisibility(takeoff.hasNortheastExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
            return rowView;
        }

        @Override
        public int getCount() {
            Log.d(getClass().getSimpleName(), "getCount()");
            return takeoffs.size();
        }
    }
}
