package net.exent.flywithme.fragment;

import net.exent.flywithme.FlyWithMe;
import net.exent.flywithme.R;
import net.exent.flywithme.bean.Takeoff;
import net.exent.flywithme.data.Database;
import net.exent.flywithme.server.flyWithMeServer.model.Pilot;
import net.exent.flywithme.service.FlyWithMeService;

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
import android.app.Fragment;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TakeoffDetails extends Fragment {
    public static final String ARG_TAKEOFF = "takeoff";
    private static final int SCHEDULE_BAR_WIDTH = 90;
    private static final int SCHEDULE_BAR_SPACE = 15;
    private static final int X_AXIS_HEIGHT = 30;
    private static final int Y_AXIS_WIDTH = 100;
    private static final int LINE_WIDTH = 3;

    private Takeoff takeoff;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        if (getArguments() != null)
            takeoff = getArguments().getParcelable(ARG_TAKEOFF);
        if (takeoff == null && bundle != null)
            takeoff = bundle.getParcelable(ARG_TAKEOFF);

        View view = inflater.inflate(R.layout.takeoff_details, container, false);
        /* windpai */
        ImageView windroseNorth = (ImageView) view.findViewById(R.id.takeoffDetailsWindroseNorth);
        ImageView windroseNorthwest = (ImageView) view.findViewById(R.id.takeoffDetailsWindroseNorthwest);
        ImageView windroseWest = (ImageView) view.findViewById(R.id.takeoffDetailsWindroseWest);
        ImageView windroseSouthwest = (ImageView) view.findViewById(R.id.takeoffDetailsWindroseSouthwest);
        ImageView windroseSouth = (ImageView) view.findViewById(R.id.takeoffDetailsWindroseSouth);
        ImageView windroseSoutheast = (ImageView) view.findViewById(R.id.takeoffDetailsWindroseSoutheast);
        ImageView windroseEast = (ImageView) view.findViewById(R.id.takeoffDetailsWindroseEast);
        ImageView windroseNortheast = (ImageView) view.findViewById(R.id.takeoffDetailsWindroseNortheast);
        windroseNorth.setVisibility(takeoff.hasNorthExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseNorthwest.setVisibility(takeoff.hasNorthwestExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseWest.setVisibility(takeoff.hasWestExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseSouthwest.setVisibility(takeoff.hasSouthwestExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseSouth.setVisibility(takeoff.hasSouthExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseSoutheast.setVisibility(takeoff.hasSoutheastExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseEast.setVisibility(takeoff.hasEastExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseNortheast.setVisibility(takeoff.hasNortheastExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);


        TextView takeoffName = (TextView) view.findViewById(R.id.takeoffDetailsName);
        TextView takeoffCoordAslHeight = (TextView) view.findViewById(R.id.takeoffDetailsCoordAslHeight);
        TextView takeoffDescription = (TextView) view.findViewById(R.id.takeoffDetailsDescription);

        takeoffName.setText(takeoff.getName());
        takeoffCoordAslHeight.setText(String.format("[%.2f,%.2f] " + getActivity().getString(R.string.asl) + ": %d " + getActivity().getString(R.string.height) + ": %d", takeoff.getLocation().getLatitude(), takeoff.getLocation().getLongitude(), takeoff.getAsl(), takeoff.getHeight()));
        takeoffDescription.setText("http://flightlog.org/fl.html?a=22&country_id=160&start_id=" + takeoff.getId() + "\n" + takeoff.getDescription());
        takeoffDescription.setMovementMethod(LinkMovementMethod.getInstance());

        final ImageButton flyScheduleButton = (ImageButton) view.findViewById(R.id.takeoffDetailsFlyScheduleChart);
        if (flyScheduleButton != null && flyScheduleButton.getViewTreeObserver() != null) {
            flyScheduleButton.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    flyScheduleButton.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    drawFlySchedule(flyScheduleButton);
                }
            });
            flyScheduleButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    Bundle args = new Bundle();
                    args.putParcelable(TakeoffSchedule.ARG_TAKEOFF, takeoff);
                    FlyWithMe.showFragment(getActivity(), "takeoffSchedule," + takeoff.getId(), TakeoffSchedule.class, args);
                }
            });
        }

        ImageButton navigationButton = (ImageButton) getActivity().findViewById(R.id.fragmentButton1);
        navigationButton.setImageResource(R.mipmap.navigation);
        navigationButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Location loc = takeoff.getLocation();
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://maps.google.com/maps?daddr=" + loc.getLatitude() + "," + loc.getLongitude())));
            }
        });
        ImageButton noaaButton = (ImageButton) getActivity().findViewById(R.id.fragmentButton2);
        noaaButton.setImageResource(R.mipmap.noaa);
        noaaButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), FlyWithMeService.class);
                intent.setAction(FlyWithMeService.ACTION_GET_METEOGRAM);
                intent.putExtra(FlyWithMeService.ARG_TAKEOFF_ID, (long) takeoff.getId());
                getActivity().startService(intent);
                // show loading animation
                ProgressBar progressBar = (ProgressBar) getActivity().findViewById(R.id.progressBar2);
                progressBar.setVisibility(View.VISIBLE);
            }
        });
        final ImageButton favouriteButton = (ImageButton) getActivity().findViewById(R.id.fragmentButton3);
        favouriteButton.setImageResource(takeoff.isFavourite() ? R.mipmap.favourite_enabled : R.mipmap.favourite_disabled);
        favouriteButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                takeoff.setFavourite(!takeoff.isFavourite());
                new Database(getActivity()).updateFavourite(takeoff);
                favouriteButton.setImageResource(takeoff.isFavourite() ? R.mipmap.favourite_enabled : R.mipmap.favourite_disabled);
            }
        });
        return view;
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

        // fetch flight schedule for takeoff
        Map<Date, Set<Pilot>> schedule = new Database(getActivity()).getTakeoffSchedule(takeoff);
        if (schedule.isEmpty()) {
            // no flights scheduled, don't draw labels, show instead a message
            paint.setColor(Color.YELLOW);
            String text = getActivity().getString(R.string.no_scheduled_flights);
            int textWidth = (int) Math.ceil(paint.measureText(text));
            canvas.drawText(text, (bitmap.getWidth() - textWidth) / 2, bitmap.getHeight() / 2 + 4, paint);
            flyScheduleButton.setImageBitmap(bitmap);
            return;
        }

        // draw labels
        paint.setColor(Color.WHITE);
        canvas.drawText(getActivity().getString(R.string.date), 4, X_AXIS_HEIGHT - LINE_WIDTH - 4, paint);
        canvas.drawText(getActivity().getString(R.string.pilots), 4, bitmap.getHeight() - X_AXIS_HEIGHT - LINE_WIDTH - 4, paint);
        canvas.drawText(getActivity().getString(R.string.time), 4, bitmap.getHeight() - 4, paint);

        String prevDate = "";
        int xPos = Y_AXIS_WIDTH - SCHEDULE_BAR_SPACE;
        Calendar today = GregorianCalendar.getInstance();
        for (Map.Entry<Date, Set<Pilot>> entry : schedule.entrySet()) {
            Calendar cal = GregorianCalendar.getInstance();
            cal.setTime(entry.getKey());
            String text;
            if (today.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR) && today.get(Calendar.YEAR) == cal.get(Calendar.YEAR)) {
                text = getActivity().getString(R.string.today);
            } else {
                SimpleDateFormat dayFormatter = new SimpleDateFormat("d. MMM", Locale.US);
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
            SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm", Locale.US);
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
