package net.exent.flywithme;

import net.exent.flywithme.bean.Takeoff;
import net.exent.flywithme.view.GestureImageView;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

public class NoaaForecast extends Fragment {
    public static final String ARG_TAKEOFF = "takeoff";
    private Takeoff takeoff;

    public void showNoaaForecast(Takeoff takeoff) {
        try {
            this.takeoff = takeoff;
            TextView noaaForecastText = (TextView) getActivity().findViewById(R.id.noaaForecastText);
            noaaForecastText.setText(takeoff.getName());
            GestureImageView noaaForecastImage = (GestureImageView) getActivity().findViewById(R.id.noaaForecastImage);
            noaaForecastImage.setBitmap(takeoff.getNoaaforecast());
        } catch (Exception e) {
            Log.w(getClass().getName(), "showNoaaForecast() failed unexpectedly", e);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (savedInstanceState != null)
            takeoff = savedInstanceState.getParcelable(ARG_TAKEOFF);
        setRetainInstance(true);
        return inflater.inflate(R.layout.noaa_forecast, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        Bundle args = getArguments();
        if (args != null)
            showNoaaForecast((Takeoff) args.getParcelable(ARG_TAKEOFF));

        ((ImageButton) getActivity().findViewById(R.id.fragmentButton1)).setImageDrawable(null);
        ((ImageButton) getActivity().findViewById(R.id.fragmentButton2)).setImageDrawable(null);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(ARG_TAKEOFF, takeoff);
    }
}
