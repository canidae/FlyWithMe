package net.exent.flywithme.fragment;

import net.exent.flywithme.R;
import net.exent.flywithme.bean.Takeoff;
import net.exent.flywithme.data.Database;
import net.exent.flywithme.service.FlyWithMeService;
import net.exent.flywithme.view.GestureImageView;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class NoaaForecast extends Fragment {
    public static final String ARG_IMAGE = "image";
    public static final String ARG_LAST_UPDATED = "lastUpdated";
    public static final String ARG_TAKEOFF_ID = "takeoffId";
    public static final String ARG_TYPE = "type";
    public static final String ARG_VALID_FOR = "validFor";

    private Bundle args;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        Log.d(getClass().getName(), "onCreateView(" + inflater + ", " + container + ", " + bundle + ")");
        if (getArguments() != null)
            args = getArguments();
        if (args == null && bundle != null)
            args = bundle;

        View view = inflater.inflate(R.layout.noaa_forecast, container, false);
        try {
            final ListView forecastList = ((ListView) view.findViewById(R.id.noaaForecastList));
            final GestureImageView forecastImage = ((GestureImageView) view.findViewById(R.id.noaaForecastImage));
            final TextView noaaForecastErrorMessage = (TextView) view.findViewById(R.id.noaaForecastErrorMessage);
            final Takeoff takeoff = Database.getTakeoff(getActivity(), (int) args.getLong(ARG_TAKEOFF_ID));

            int width = 0;
            int height = 0;
            List<Bitmap> images = new ArrayList<>();
            for (int i = 0; ; ++i) {
                final byte[] imageArray = args.getByteArray(ARG_IMAGE + "_" + i);
                final Bitmap image = imageArray == null ? null : BitmapFactory.decodeByteArray(imageArray, 0, imageArray.length);
                if (image == null)
                    break;
                width += image.getWidth();
                if (image.getHeight() > height)
                    height = image.getHeight();
                images.add(image);
            }
            final Bitmap image;
            if (width > 0) {
                image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(image);
                width = 0;
                for (Bitmap tmpImage : images) {
                    canvas.drawBitmap(tmpImage, width, 0, null);
                    width += tmpImage.getWidth();
                }
            } else {
                image = null;
            }

            final TextView noaaForecastText = (TextView) view.findViewById(R.id.noaaForecastText);
            noaaForecastText.setText(takeoff.getName());

            if (image == null) {
                if ("ERROR".equals(args.getString(ARG_TYPE + "_0"))) {
                    // hmm, we couldn't load forecast for some reason, display error
                    Log.w(getClass().getName(), "Unable to fetch forecast for takeoff with ID: " + takeoff.getId());
                    forecastList.setVisibility(View.GONE);
                    forecastImage.setVisibility(View.GONE);
                    noaaForecastErrorMessage.setVisibility(View.VISIBLE);
                } else {
                    Log.d(getClass().getName(), "No image passed to fragment, fetching meteogram");
                    Intent intent = new Intent(getActivity(), FlyWithMeService.class);
                    intent.setAction(FlyWithMeService.ACTION_GET_METEOGRAM);
                    intent.putExtra(FlyWithMeService.ARG_TAKEOFF_ID, takeoff.getId());
                    getActivity().startService(intent);
                    // show loading animation
                    forecastList.setVisibility(View.GONE);
                    forecastImage.setVisibility(View.VISIBLE);
                    noaaForecastErrorMessage.setVisibility(View.GONE);

                    // show loading animation
                    ProgressBar progressBar = (ProgressBar) getActivity().findViewById(R.id.progressBar1);
                    progressBar.setVisibility(View.VISIBLE);
                }
            } else {
                // show image
                forecastImage.setBitmap(image);
                forecastList.setVisibility(View.GONE);
                forecastImage.setVisibility(View.VISIBLE);
                noaaForecastErrorMessage.setVisibility(View.GONE);
            }

            ImageButton soundingButton = ((ImageButton) getActivity().findViewById(R.id.fragmentButton1));
            soundingButton.setImageResource(R.mipmap.noaa);
            soundingButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    forecastList.setVisibility(View.VISIBLE);
                    forecastImage.setVisibility(View.GONE);
                    forecastList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                            forecastList.setVisibility(View.GONE);
                            forecastImage.setVisibility(View.VISIBLE);
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
                                calendar.set(Calendar.HOUR_OF_DAY, 0);
                                calendar.set(Calendar.MINUTE, 0);
                                calendar.set(Calendar.SECOND, 0);
                                calendar.set(Calendar.MILLISECOND, 0);
                                calendar.add(Calendar.HOUR_OF_DAY, soundingHourOffset);
                                intent.putExtra(FlyWithMeService.ARG_TIMESTAMP_IN_SECONDS, calendar.getTimeInMillis() / 1000);
                            }
                            intent.putExtra(FlyWithMeService.ARG_TAKEOFF_ID, takeoff.getId());
                            getActivity().startService(intent);

                            // show loading animation
                            ProgressBar progressBar = (ProgressBar) getActivity().findViewById(R.id.progressBar1);
                            progressBar.setVisibility(View.VISIBLE);
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
                        String url = MediaStore.Images.Media.insertImage(getActivity().getContentResolver(), image, title, null);

                        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
                        sharingIntent.setType("image/*");
                        sharingIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(url));
                        sharingIntent.putExtra(Intent.EXTRA_TEXT, title);
                        startActivity(Intent.createChooser(sharingIntent, null));
                    }
                });
            }
        } catch (Exception e) {
            Log.w(getClass().getName(), "Showing NOAA forecast failed unexpectedly", e);
        }
        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.d(getClass().getName(), "onSaveInstanceState(" + outState + ")");
        super.onSaveInstanceState(outState);
        outState.putAll(args);
    }
}
