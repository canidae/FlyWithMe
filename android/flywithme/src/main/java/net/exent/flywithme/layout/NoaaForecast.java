package net.exent.flywithme.layout;

import net.exent.flywithme.FlyWithMe;
import net.exent.flywithme.R;
import net.exent.flywithme.bean.Takeoff;
import net.exent.flywithme.view.GestureImageView;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
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

    public void showNoaaForecast(final Takeoff takeoff) {
        try {
            this.takeoff = takeoff;
            TextView noaaForecastText = (TextView) getActivity().findViewById(R.id.noaaForecastText);
            noaaForecastText.setText(takeoff.getName());
            GestureImageView noaaForecastImage = (GestureImageView) getActivity().findViewById(R.id.noaaForecastImage);
            noaaForecastImage.setBitmap(takeoff.getNoaaforecast());

            ImageButton navigationButton = (ImageButton) getActivity().findViewById(R.id.fragmentButton1);
            navigationButton.setImageResource(R.drawable.share);
            navigationButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    String title = takeoff.getName() + " (" + takeoff.getLocation().getLatitude() + ", " + takeoff.getLocation().getLongitude() + ")";
                    String url = MediaStore.Images.Media.insertImage(FlyWithMe.getInstance().getContentResolver(), takeoff.getNoaaforecast(), title, null);

                    Intent sharingIntent = new Intent(Intent.ACTION_SEND);
                    sharingIntent.setType("image/*");
                    sharingIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(url));
                    sharingIntent.putExtra(Intent.EXTRA_TEXT, title);
                    startActivity(Intent.createChooser(sharingIntent, null));
                }
            });
            ((ImageButton) getActivity().findViewById(R.id.fragmentButton2)).setImageDrawable(null);
            ((ImageButton) getActivity().findViewById(R.id.fragmentButton3)).setImageDrawable(null);
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
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(ARG_TAKEOFF, takeoff);
    }
}
