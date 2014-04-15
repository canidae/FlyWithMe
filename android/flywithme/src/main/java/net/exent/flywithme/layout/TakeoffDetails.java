package net.exent.flywithme.layout;

import net.exent.flywithme.R;
import net.exent.flywithme.bean.Takeoff;
import net.exent.flywithme.data.Database;
import net.exent.flywithme.task.NoaaForecastTask;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TakeoffDetails extends Fragment {
    public interface TakeoffDetailsListener {
        Location getLocation();

        void showNoaaForecast(Takeoff takeoff);

        void showTakeoffSchedule(Takeoff takeoff);
    }

    public static final String ARG_TAKEOFF = "takeoff";
    private static final int SCHEDULE_BAR_WIDTH = 90;
    private static final int SCHEDULE_BAR_SPACE = 15;
    private static final int X_AXIS_HEIGHT = 30;
    private static final int Y_AXIS_WIDTH = 100;
    private static final int LINE_WIDTH = 3;
    private Takeoff takeoff;
    private TakeoffDetailsListener callback;

    public void showTakeoffDetails(final Takeoff takeoff) {
        if (callback == null) {
            Log.w(getClass().getName(), "callback is null, returning");
            return;
        }
        this.takeoff = takeoff;
        if (takeoff == null)
            return;

        final Location myLocation = callback.getLocation();

        ImageButton navigationButton = (ImageButton) getActivity().findViewById(R.id.fragmentButton1);
        navigationButton.setImageResource(R.drawable.navigation);
        navigationButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Location loc = takeoff.getLocation();
                String uri = "http://maps.google.com/maps?saddr=" + myLocation.getLatitude() + "," + myLocation.getLongitude() + "&daddr=" + loc.getLatitude() + "," + loc.getLongitude();
                Intent intent = new Intent(android.content.Intent.ACTION_VIEW, Uri.parse(uri));
                getActivity().startActivity(intent);
            }
        });
        ImageButton noaaButton = (ImageButton) getActivity().findViewById(R.id.fragmentButton2);
        noaaButton.setImageResource(R.drawable.noaa);
        noaaButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (System.currentTimeMillis() - takeoff.getNoaaUpdated() < 1000 * 60 * 60 * 6) {
                    /* we fetched a forecast less than 6 hours ago */
                    if (takeoff.getNoaaforecast() != null) {
                        /* and it's still cached, display it */
                        callback.showNoaaForecast(takeoff);
                        return;
                    }
                }
                /* no cached forecast, need to fetch it */
                new NoaaForecastTask().execute(takeoff);
            }
        });
        final ImageButton favouriteButton = (ImageButton) getActivity().findViewById(R.id.fragmentButton3);
        favouriteButton.setImageResource(takeoff.isFavourite() ? R.drawable.favourite_enabled : R.drawable.favourite_disabled);
        favouriteButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                takeoff.setFavourite(!takeoff.isFavourite());
                Database.updateFavourite(takeoff);
                favouriteButton.setImageResource(takeoff.isFavourite() ? R.drawable.favourite_enabled : R.drawable.favourite_disabled);
            }
        });

        /* windpai */
        ImageView windroseNorth = (ImageView) getActivity().findViewById(R.id.takeoffDetailsWindroseNorth);
        ImageView windroseNorthwest = (ImageView) getActivity().findViewById(R.id.takeoffDetailsWindroseNorthwest);
        ImageView windroseWest = (ImageView) getActivity().findViewById(R.id.takeoffDetailsWindroseWest);
        ImageView windroseSouthwest = (ImageView) getActivity().findViewById(R.id.takeoffDetailsWindroseSouthwest);
        ImageView windroseSouth = (ImageView) getActivity().findViewById(R.id.takeoffDetailsWindroseSouth);
        ImageView windroseSoutheast = (ImageView) getActivity().findViewById(R.id.takeoffDetailsWindroseSoutheast);
        ImageView windroseEast = (ImageView) getActivity().findViewById(R.id.takeoffDetailsWindroseEast);
        ImageView windroseNortheast = (ImageView) getActivity().findViewById(R.id.takeoffDetailsWindroseNortheast);
        windroseNorth.setVisibility(takeoff.hasNorthExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseNorthwest.setVisibility(takeoff.hasNorthwestExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseWest.setVisibility(takeoff.hasWestExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseSouthwest.setVisibility(takeoff.hasSouthwestExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseSouth.setVisibility(takeoff.hasSouthExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseSoutheast.setVisibility(takeoff.hasSoutheastExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseEast.setVisibility(takeoff.hasEastExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseNortheast.setVisibility(takeoff.hasNortheastExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);


        TextView takeoffName = (TextView) getActivity().findViewById(R.id.takeoffDetailsName);
        TextView takeoffCoordAslHeight = (TextView) getActivity().findViewById(R.id.takeoffDetailsCoordAslHeight);
        TextView takeoffDescription = (TextView) getActivity().findViewById(R.id.takeoffDetailsDescription);

        takeoffName.setText(takeoff.getName());
        takeoffCoordAslHeight.setText(String.format("[%.2f,%.2f] " + getActivity().getString(R.string.asl) + ": %d " + getActivity().getString(R.string.height) + ": %d", takeoff.getLocation().getLatitude(), takeoff.getLocation().getLongitude(), takeoff.getAsl(), takeoff.getHeight()));
        takeoffDescription.setText("http://flightlog.org/fl.html?a=22&country_id=160&start_id=" + takeoff.getId() + "\n" + takeoff.getDescription());
        takeoffDescription.setMovementMethod(LinkMovementMethod.getInstance());

        final ImageButton flyScheduleButton = (ImageButton) getActivity().findViewById(R.id.takeoffDetailsFlyScheduleChart);
        if (flyScheduleButton != null && flyScheduleButton.getViewTreeObserver() != null) {
            flyScheduleButton.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    flyScheduleButton.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    drawFlySchedule(flyScheduleButton);
                }
            });
            flyScheduleButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    // show takeoff schedule details
                    callback.showTakeoffSchedule(takeoff);
                }
            });
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        callback = (TakeoffDetailsListener) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (savedInstanceState != null)
            takeoff = savedInstanceState.getParcelable(ARG_TAKEOFF);
        setRetainInstance(true);
        return inflater.inflate(R.layout.takeoff_details, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        Bundle args = getArguments();
        if (args != null)
            showTakeoffDetails((Takeoff) args.getParcelable(ARG_TAKEOFF));
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(ARG_TAKEOFF, takeoff);
    }

    private void drawFlySchedule(ImageButton flyScheduleButton) {
        Bitmap bitmap = Bitmap.createBitmap(flyScheduleButton.getWidth(), flyScheduleButton.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        // our "brush"
        Paint paint = new Paint();
        paint.setTextSize(26.0f);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));

        // draw vertical and horizontal axis
        paint.setColor(Color.CYAN);
        canvas.drawRect(0, X_AXIS_HEIGHT - LINE_WIDTH, bitmap.getWidth(), X_AXIS_HEIGHT, paint); // upper horizontal axis
        canvas.drawRect(0, bitmap.getHeight() - X_AXIS_HEIGHT + LINE_WIDTH, bitmap.getWidth(), bitmap.getHeight() - X_AXIS_HEIGHT, paint); // lower horizontal axis
        // draw labels
        paint.setColor(Color.WHITE);
        canvas.drawText(getActivity().getString(R.string.date), 4, X_AXIS_HEIGHT - LINE_WIDTH - 4, paint);
        canvas.drawText(getActivity().getString(R.string.pilots), 4, bitmap.getHeight() - X_AXIS_HEIGHT - LINE_WIDTH - 4, paint);
        canvas.drawText(getActivity().getString(R.string.time), 4, bitmap.getHeight() - 4, paint);

        // TODO: temporary to add some flights to the schedule
        Map<Date, List<String>> tmpSchedule = new HashMap<>();
        for (int a = 0; a < 10; ++a) {
            Date date = new Date(System.currentTimeMillis() + a * 1000 * 60 * 60 * 7);
            List<String> list = new ArrayList<>();
            for (int b = 0; b < Math.random() * 10; ++b)
                list.add("Vidar Wahlberg,+4795728262");
            tmpSchedule.put(date, list);
        }
        Database.updateTakeoffSchedule(takeoff.getId(), tmpSchedule);
        // TODO: END

        // fetch flight schedule for takeoff
        Map<Date, Set<String>> schedule = Database.getTakeoffSchedule(takeoff);

        String prevDate = "";
        int xPos = Y_AXIS_WIDTH - SCHEDULE_BAR_SPACE;
        Calendar today = GregorianCalendar.getInstance();
        for (Map.Entry<Date, Set<String>> entry : schedule.entrySet()) {
            Calendar cal = GregorianCalendar.getInstance();
            cal.setTime(entry.getKey());
            String text;
            if (today.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR) && today.get(Calendar.YEAR) == cal.get(Calendar.YEAR)) {
                text = getActivity().getString(R.string.today);
            } else {
                SimpleDateFormat dayFormatter = new SimpleDateFormat("d. MMM");
                text = dayFormatter.format(cal.getTime());
            }
            if (!prevDate.equals(text)) {
                /* this scheduled flight is on another day than today or the previous scheduled flight */
                paint.setColor(Color.YELLOW);
                // draw day separator
                xPos += SCHEDULE_BAR_SPACE;
                canvas.drawRect(xPos, 0, xPos + LINE_WIDTH, bitmap.getHeight(), paint);
                xPos += LINE_WIDTH;
                // draw date
                canvas.drawText(text, xPos + 4, X_AXIS_HEIGHT - LINE_WIDTH - 4, paint);
            }
            // set prevDate
            prevDate = text;

            // draw amount of pilots bar
            int pilots = entry.getValue().size();
            int barTop = (int) (X_AXIS_HEIGHT + LINE_WIDTH + (bitmap.getHeight() - (X_AXIS_HEIGHT + LINE_WIDTH) * 2) / (pilots + 0.5));
            paint.setColor(Color.GREEN);
            xPos += SCHEDULE_BAR_SPACE;
            canvas.drawRect(xPos, bitmap.getHeight() - X_AXIS_HEIGHT, xPos + SCHEDULE_BAR_WIDTH, barTop, paint);
            // draw amount of pilots number
            text = "" + pilots;
            int textWidth = (int) Math.ceil(paint.measureText(text));
            paint.setColor(Color.BLACK);
            canvas.drawText(text, xPos + (SCHEDULE_BAR_WIDTH - textWidth) / 2, bitmap.getHeight() - X_AXIS_HEIGHT - LINE_WIDTH - 4, paint);
            // draw time
            SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm");
            text = timeFormatter.format(cal.getTime());
            textWidth = (int) Math.ceil(paint.measureText(text));
            paint.setColor(Color.LTGRAY);
            canvas.drawText(text, xPos + (SCHEDULE_BAR_WIDTH - textWidth) / 2, bitmap.getHeight() - 4, paint);
            xPos += SCHEDULE_BAR_WIDTH;
        }
        // set bitmap as image for button
        flyScheduleButton.setImageBitmap(bitmap);
    }
}
