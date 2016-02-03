package net.exent.flywithme.fragment;

import net.exent.flywithme.FlyWithMeActivity;
import net.exent.flywithme.R;
import net.exent.flywithme.bean.Takeoff;
import net.exent.flywithme.data.Database;

import android.app.Fragment;
import android.content.Context;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class TakeoffList extends Fragment {
    private static int savedPosition;
    private static int savedListTop;
    private List<Takeoff> takeoffs = new ArrayList<>();
    private TakeoffArrayAdapter takeoffArrayAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        View view = inflater.inflate(R.layout.takeoff_list, container, false);
        takeoffArrayAdapter = new TakeoffArrayAdapter(getActivity());
        ListView listView = (ListView) view.findViewById(R.id.takeoffListView);
        listView.setAdapter(takeoffArrayAdapter);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Takeoff takeoff = takeoffs.get(position);
                Bundle args = new Bundle();
                args.putParcelable(TakeoffDetails.ARG_TAKEOFF, takeoff);
                ((FlyWithMeActivity) getActivity()).showFragment("takeoffDetails," + takeoff.getId(), TakeoffDetails.class, args);
            }
        });
        /* position list */
        listView.setSelectionFromTop(savedPosition, savedListTop);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        // update takeoff list
        updateTakeoffList();
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

    private void updateTakeoffList() {
        final Location location = ((FlyWithMeActivity) getActivity()).getLocation();
        takeoffs = Database.getTakeoffs(getActivity(), location.getLatitude(), location.getLongitude(), 100, true, true); // TODO: can we do this async?
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
            String takeoffIntoText = getContext().getString(R.string.distance) + ": " + (int) ((FlyWithMeActivity) getActivity()).getLocation().distanceTo(takeoff.getLocation()) / 1000 + "km";
            if (takeoff.getPilotsToday() > 0 || takeoff.getPilotsLater() > 0) {
                takeoffIntoText += ", " + getContext().getString(R.string.pilots) + ": ";
                if (takeoff.getPilotsToday() > 0)
                    takeoffIntoText += takeoff.getPilotsToday() + " " + getContext().getString(R.string.today);
                if (takeoff.getPilotsLater() > 0)
                    takeoffIntoText += (takeoff.getPilotsToday() > 0 ? ", " : "") + takeoff.getPilotsLater() + " " + getContext().getString(R.string.later);
            }
            viewHolder.takeoffInfo.setText(takeoffIntoText);
            /* exits */
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
