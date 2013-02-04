package net.exent.flywithme;

import net.exent.flywithme.data.Takeoff;
import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

public class TakeoffDetails extends Fragment {
    public interface TakeoffDetailsListener {
        Location getLocation();
    }

    public static final String ARG_TAKEOFF = "takeoff";
    private Takeoff takeoff;
    private TakeoffDetailsListener callback;

    public void showTakeoffDetails(final Takeoff takeoff) {
        Log.d("TakeoffDetails", "showTakeoffDetails(" + takeoff + ")");
        this.takeoff = takeoff;
        if (takeoff == null)
            return;

        final Location myLocation = callback.getLocation();

        ImageButton navigationButton = (ImageButton) getActivity().findViewById(R.id.takeoffDetailsNavigationButton);
        navigationButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Location loc = takeoff.getLocation();
                String uri = "http://maps.google.com/maps?saddr=" + myLocation.getLatitude() + "," + myLocation.getLongitude() + "&daddr=" + loc.getLatitude() + "," + loc.getLongitude();
                Intent intent = new Intent(android.content.Intent.ACTION_VIEW, Uri.parse(uri));
                getActivity().startActivity(intent);
            }
        });

        TextView takeoffName = (TextView) getActivity().findViewById(R.id.takeoffDetailsName);
        TextView takeoffCoordAslHeight = (TextView) getActivity().findViewById(R.id.takeoffDetailsCoordAslHeight);
        TextView takeoffDescription = (TextView) getActivity().findViewById(R.id.takeoffDetailsDescription);

        takeoffName.setText(takeoff.getName());
        takeoffCoordAslHeight.setText(String.format("[%.2f,%.2f] " + getActivity().getString(R.string.asl) + ": %d " + getActivity().getString(R.string.height) + ": %d", takeoff.getLocation().getLatitude(), takeoff.getLocation().getLongitude(), takeoff.getAsl(), takeoff.getHeight()));
        takeoffDescription.setText(takeoff.getDescription());
        takeoffDescription.setMovementMethod(new ScrollingMovementMethod());
    }

    @Override
    public void onAttach(Activity activity) {
        Log.d("TakeoffDetails", "onAttach(" + activity + ")");
        super.onAttach(activity);
        callback = (TakeoffDetailsListener) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d("TakeoffDetails", "onCreateView(" + inflater + ", " + container + ", " + savedInstanceState + ")");
        if (savedInstanceState != null)
            takeoff = savedInstanceState.getParcelable("takeoff");
        return inflater.inflate(R.layout.takeoff_details, container, false);
    }

    @Override
    public void onStart() {
        Log.d("TakeoffDetails", "onStart()");
        super.onStart();
        Bundle args = getArguments();
        if (args != null)
            takeoff = args.getParcelable(ARG_TAKEOFF);
        showTakeoffDetails(takeoff);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.d("TakeoffDetails", "onSaveInstanceState(" + outState + ")");
        super.onSaveInstanceState(outState);
        outState.putParcelable(ARG_TAKEOFF, takeoff);
    }
}
