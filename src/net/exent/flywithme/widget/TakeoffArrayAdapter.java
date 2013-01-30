package net.exent.flywithme.widget;

import net.exent.flywithme.FlyWithMe;
import net.exent.flywithme.R;
import net.exent.flywithme.dao.Flightlog;
import net.exent.flywithme.data.Takeoff;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class TakeoffArrayAdapter extends ArrayAdapter<Takeoff> {
	public TakeoffArrayAdapter(Context context) {
		super(context, R.layout.takeoff_list_entry);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		Takeoff takeoff = Flightlog.getTakeoffs(getContext()).get(position);
		
		LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View rowView = inflater.inflate(R.layout.takeoff_list_entry, parent, false);
		TextView takeoffName = (TextView) rowView.findViewById(R.id.takeoffListName);
		TextView takeoffDistance = (TextView) rowView.findViewById(R.id.takeoffListDistance);
		takeoffName.setText(takeoff.toString());
		takeoffDistance.setText(getContext().getString(R.string.geodesic_distance) + ": " + (int) FlyWithMe.getLocation().distanceTo(takeoff.getLocation()) / 1000 + "km");
		/* windpai */
		ImageView windroseNorth = (ImageView) rowView.findViewById(R.id.takeoffWindroseNorth);
		ImageView windroseNorthwest = (ImageView) rowView.findViewById(R.id.takeoffWindroseNorthwest);
		ImageView windroseWest = (ImageView) rowView.findViewById(R.id.takeoffWindroseWest);
		ImageView windroseSouthwest = (ImageView) rowView.findViewById(R.id.takeoffWindroseSouthwest);
		ImageView windroseSouth = (ImageView) rowView.findViewById(R.id.takeoffWindroseSouth);
		ImageView windroseSoutheast = (ImageView) rowView.findViewById(R.id.takeoffWindroseSoutheast);
		ImageView windroseEast = (ImageView) rowView.findViewById(R.id.takeoffWindroseEast);
		ImageView windroseNortheast = (ImageView) rowView.findViewById(R.id.takeoffWindroseNortheast);
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
		return Flightlog.getTakeoffs(getContext()).size();
	}
}
