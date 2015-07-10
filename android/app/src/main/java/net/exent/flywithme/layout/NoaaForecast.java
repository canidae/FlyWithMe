package net.exent.flywithme.layout;

import net.exent.flywithme.FlyWithMe;
import net.exent.flywithme.R;
import net.exent.flywithme.bean.Takeoff;
import net.exent.flywithme.data.Database;
import net.exent.flywithme.service.FlyWithMeService;
import net.exent.flywithme.view.GestureImageView;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Calendar;
import java.util.TimeZone;

public class NoaaForecast extends Fragment {
    public static final String ARG_IMAGE = "image";
    public static final String ARG_LAST_UPDATED = "lastUpdated";
    public static final String ARG_TAKEOFF_ID = "takeoffId";
    public static final String ARG_TYPE = "type";
    public static final String ARG_VALID_FOR = "validFor";

    private Bundle args;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(getClass().getName(), "onCreateView(" + inflater + ", " + container + ", " + savedInstanceState + ")");
        if (savedInstanceState != null)
            args = savedInstanceState;
        setRetainInstance(true);
        return inflater.inflate(R.layout.noaa_forecast, container, false);
    }

    @Override
    public void onStart() {
        Log.d(getClass().getName(), "onStart()");
        super.onStart();
        this.args = getArguments();
        if (args == null || !args.containsKey(ARG_TAKEOFF_ID)) {
            Log.w(getClass().getName(), "No arguments passed to fragment, can't display forecast");
            return;
        }

        try {
            final ListView forecastList = ((ListView) getActivity().findViewById(R.id.noaaForecastList));
            final GestureImageView forecastImage = ((GestureImageView) getActivity().findViewById(R.id.noaaForecastImage));
            final ProgressBar forecastLoadingAnimation = ((ProgressBar) getActivity().findViewById(R.id.noaaForecastLoadingAnimation));
            final TextView noaaForecastErrorMessage = (TextView) getActivity().findViewById(R.id.noaaForecastErrorMessage);
            final Takeoff takeoff = new Database(getActivity()).getTakeoff((int) args.getLong(ARG_TAKEOFF_ID));
            final byte[] imageArray = args.getByteArray(ARG_IMAGE);
            final Bitmap image = imageArray == null ? null : BitmapFactory.decodeByteArray(imageArray, 0, imageArray.length);

            ImageButton soundingButton = ((ImageButton) getActivity().findViewById(R.id.fragmentButton1));
            soundingButton.setImageResource(R.mipmap.noaa);
            soundingButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    forecastList.setVisibility(View.VISIBLE);
                    forecastImage.setVisibility(View.GONE);
                    forecastLoadingAnimation.setVisibility(View.GONE);
                    forecastList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                            forecastList.setVisibility(View.GONE);
                            forecastImage.setVisibility(View.VISIBLE);
                            forecastLoadingAnimation.setVisibility(View.VISIBLE);
                            Intent intent = new Intent(getActivity(), FlyWithMeService.class);
                            if (position == 0) {
                                // fetch meteogram
                                intent.setAction(FlyWithMeService.ACTION_GET_METEOGRAM);
                            } else if (position > 0) {
                                // fetch sounding
                                intent.setAction(FlyWithMeService.ACTION_GET_SOUNDING);
                                // set timestamp for sounding
                                int soundingHourOffset = getResources().getIntArray(R.array.meteogram_sounding_forecast_list_values)[position];
                                Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                                Log.d(getClass().getName(), "Timestamp: " + calendar.getTimeInMillis() + " - " + calendar.getTime() + " - " + soundingHourOffset);
                                calendar.set(Calendar.HOUR_OF_DAY, 0);
                                calendar.set(Calendar.MINUTE, 0);
                                calendar.set(Calendar.SECOND, 0);
                                calendar.set(Calendar.MILLISECOND, 0);
                                Log.d(getClass().getName(), "Timestamp: " + calendar.getTimeInMillis() + " - " + calendar.getTime() + " - " + soundingHourOffset);
                                calendar.add(Calendar.HOUR_OF_DAY, soundingHourOffset);
                                Log.d(getClass().getName(), "Timestamp: " + calendar.getTimeInMillis() + " - " + calendar.getTime() + " - " + soundingHourOffset);
                                intent.putExtra(FlyWithMeService.DATA_LONG_TIMESTAMP, calendar.getTimeInMillis());
                            }
                            intent.putExtra(FlyWithMeService.DATA_LONG_TAKEOFF_ID, (long) takeoff.getId());
                            getActivity().startService(intent);
                        }
                    });
                }
            });

            ImageButton shareButton = (ImageButton) getActivity().findViewById(R.id.fragmentButton2);
            if (image == null) {
                shareButton.setImageDrawable(null);
            } else {
                shareButton.setImageResource(R.mipmap.share);
                shareButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String title = takeoff.getName() + " (" + takeoff.getLocation().getLatitude() + ", " + takeoff.getLocation().getLongitude() + ")";
                        String url = MediaStore.Images.Media.insertImage(FlyWithMe.getInstance().getContentResolver(), image, title, null);

                        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
                        sharingIntent.setType("image/*");
                        sharingIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(url));
                        sharingIntent.putExtra(Intent.EXTRA_TEXT, title);
                        startActivity(Intent.createChooser(sharingIntent, null));
                    }
                });
            }

            ((ImageButton) getActivity().findViewById(R.id.fragmentButton3)).setImageDrawable(null);

            final TextView noaaForecastText = (TextView) getActivity().findViewById(R.id.noaaForecastText);
            noaaForecastText.setText(takeoff.getName());

            if (image == null) {
                if ("ERROR".equals(args.getString(ARG_TYPE))) {
                    // hmm, we couldn't load forecast for some reason, display error
                    Log.w(getClass().getName(), "Unable to fetch forecast for takeoff with ID: " + takeoff.getId());
                    forecastList.setVisibility(View.GONE);
                    forecastImage.setVisibility(View.GONE);
                    forecastLoadingAnimation.setVisibility(View.GONE);
                    noaaForecastErrorMessage.setVisibility(View.VISIBLE);
                } else {
                    Log.d(getClass().getName(), "No image passed to fragment, fetching meteogram");
                    Intent intent = new Intent(getActivity(), FlyWithMeService.class);
                    intent.setAction(FlyWithMeService.ACTION_GET_METEOGRAM);
                    intent.putExtra(FlyWithMeService.DATA_LONG_TAKEOFF_ID, (long) takeoff.getId());
                    getActivity().startService(intent);
                    // show loading animation
                    forecastList.setVisibility(View.GONE);
                    forecastImage.setVisibility(View.VISIBLE);
                    forecastLoadingAnimation.setVisibility(View.VISIBLE);
                    noaaForecastErrorMessage.setVisibility(View.GONE);
                }
            } else {
                // show image
                forecastImage.setBitmap(image);
                forecastList.setVisibility(View.GONE);
                forecastImage.setVisibility(View.VISIBLE);
                forecastLoadingAnimation.setVisibility(View.GONE);
                noaaForecastErrorMessage.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.w(getClass().getName(), "Showing NOAA forecast failed unexpectedly", e);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.d(getClass().getName(), "onSaveInstanceState(" + outState + ")");
        super.onSaveInstanceState(outState);
        outState.putAll(args);
    }
}
