package net.exent.flywithme.widget;

import net.exent.flywithme.FlyWithMe;
import net.exent.flywithme.R;
import net.exent.flywithme.data.Takeoff;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class TakeoffArrayAdapter extends ArrayAdapter<Takeoff> {
	public TakeoffArrayAdapter(Context context) {
		super(context, R.layout.takeoff_list_layout);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		Takeoff takeoff = FlyWithMe.getTakeoffs().get(position);
		
		LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View rowView = inflater.inflate(R.layout.takeoff_list_layout, parent, false);
		TextView takeoffName = (TextView) rowView.findViewById(R.id.takeoffListName);
		TextView takeoffDistance = (TextView) rowView.findViewById(R.id.takeoffListDistance);
		TextView takeoffPresent = (TextView) rowView.findViewById(R.id.takeoffListPresent);
		TextView takeoffComing = (TextView) rowView.findViewById(R.id.takeoffListComing);
		takeoffName.setText(takeoff.toString());
		takeoffDistance.setText("Distance: " + (int) FlyWithMe.getLocation().distanceTo(takeoff.getLocation()) / 1000 + "km");
		takeoffPresent.setText("Present: " + takeoff.getPilotsPresent());
		takeoffComing.setText("Coming: " + takeoff.getPilotsComing());

		return rowView;
	}
	
	@Override
	public int getCount() {
		return FlyWithMe.getTakeoffs().size();
	}
}
