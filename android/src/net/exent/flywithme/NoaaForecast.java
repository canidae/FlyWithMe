package net.exent.flywithme;

import net.exent.flywithme.bean.Takeoff;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

public class NoaaForecast extends Fragment {
    public static final String ARG_TAKEOFF = "takeoff";
    private Takeoff takeoff;

    public void showNoaaForecast(Takeoff takeoff) {
        this.takeoff = takeoff;
        TextView noaaForecastText = (TextView) getActivity().findViewById(R.id.noaaForecastText);
        noaaForecastText.setText(takeoff.getName());
        ImageView noaaForecastImage = (ImageView) getActivity().findViewById(R.id.noaaForecastImage);
        noaaForecastImage.setImageBitmap(takeoff.getNoaaforecast());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(getClass().getSimpleName(), "onCreateView(" + inflater + ", " + container + ", " + savedInstanceState + ")");
        if (savedInstanceState != null)
            takeoff = savedInstanceState.getParcelable(ARG_TAKEOFF);
        return inflater.inflate(R.layout.noaa_forecast, container, false);
    }

    @Override
    public void onStart() {
        Log.d(getClass().getSimpleName(), "onStart()");
        super.onStart();
        Bundle args = getArguments();
        if (args != null)
            showNoaaForecast((Takeoff) args.getParcelable(ARG_TAKEOFF));
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.d(getClass().getSimpleName(), "onSaveInstanceState(" + outState + ")");
        super.onSaveInstanceState(outState);
        outState.putParcelable(ARG_TAKEOFF, takeoff);
    }
}
