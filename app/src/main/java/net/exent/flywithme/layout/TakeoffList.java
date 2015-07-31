package net.exent.flywithme.layout;

import net.exent.flywithme.FlyWithMe;
import net.exent.flywithme.R;
import net.exent.flywithme.bean.Takeoff;
import net.exent.flywithme.data.Database;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.app.Fragment;
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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class TakeoffList extends Fragment {
    public interface TakeoffListListener {
        Location getLocation();
    }

    private static int savedPosition;
    private static int savedListTop;
    private TakeoffListListener callback;
    private List<Takeoff> takeoffs;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        callback = (TakeoffListListener) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setRetainInstance(true);
        return inflater.inflate(R.layout.takeoff_list, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();

        final Location location = FlyWithMe.getInstance().getLocation();
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

        ((ImageButton) getActivity().findViewById(R.id.fragmentButton1)).setImageDrawable(null);
        ((ImageButton) getActivity().findViewById(R.id.fragmentButton2)).setImageDrawable(null);
        ((ImageButton) getActivity().findViewById(R.id.fragmentButton3)).setImageDrawable(null);

        TakeoffArrayAdapter adapter = new TakeoffArrayAdapter(getActivity());
        ListView listView = (ListView) getActivity().findViewById(R.id.takeoffListView);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Takeoff takeoff = takeoffs.get(position);
                TakeoffDetails takeoffDetails = new TakeoffDetails();
                Bundle args = new Bundle();
                args.putParcelable(TakeoffDetails.ARG_TAKEOFF, takeoff);
                takeoffDetails.setArguments(args);
                getFragmentManager().beginTransaction().replace(R.id.fragmentContainer, takeoffDetails, "takeoffDetails," + takeoff.getId()).commit();
            }
        });
        /* position list */
        listView.setSelectionFromTop(savedPosition, savedListTop);
    }

    @Override
    public void onStop() {
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
            Location location = callback.getLocation();
            Takeoff takeoff = takeoffs.get(position);

            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View rowView = inflater.inflate(R.layout.takeoff_list_entry, parent, false);
            TextView takeoffName = (TextView) rowView.findViewById(R.id.takeoffListEntryName);
            TextView takeoffInfo = (TextView) rowView.findViewById(R.id.takeoffListEntryInfo);
            takeoffName.setText(takeoff.toString());
            if (takeoff.isFavourite())
                takeoffName.setTextColor(Color.CYAN);
            String takeoffIntoText = getContext().getString(R.string.distance) + ": " + (int) location.distanceTo(takeoff.getLocation()) / 1000 + "km";
            if (takeoff.getPilotsToday() > 0 || takeoff.getPilotsLater() > 0) {
                takeoffIntoText += ", " + getContext().getString(R.string.pilots) + ": ";
                if (takeoff.getPilotsToday() > 0)
                    takeoffIntoText += takeoff.getPilotsToday() + " " + getContext().getString(R.string.today);
                if (takeoff.getPilotsLater() > 0)
                    takeoffIntoText += (takeoff.getPilotsToday() > 0 ? ", " : "") + takeoff.getPilotsLater() + " " + getContext().getString(R.string.later);
            }
            takeoffInfo.setText(takeoffIntoText);
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
            return takeoffs.size();
        }
    }
}
