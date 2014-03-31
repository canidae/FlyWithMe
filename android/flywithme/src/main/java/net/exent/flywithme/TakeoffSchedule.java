package net.exent.flywithme;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TextView;

import net.exent.flywithme.bean.Takeoff;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;

public class TakeoffSchedule extends Fragment {
    public static final String ARG_TAKEOFF = "takeoff";
    private Takeoff takeoff;
    private Calendar calendar = GregorianCalendar.getInstance();

    public void showTakeoffSchedule(Takeoff takeoff) {
        try {
            this.takeoff = takeoff;

            // setup day buttons
            final Button dayPlus = (Button) getActivity().findViewById(R.id.scheduleDayPlus);
            dayPlus.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    updateCalendar(Calendar.DAY_OF_YEAR, 1);
                }
            });
            final Button dayMinus = (Button) getActivity().findViewById(R.id.scheduleDayMinus);
            dayMinus.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    updateCalendar(Calendar.DAY_OF_YEAR, -1);
                }
            });

            // setup month buttons
            final Button monthPlus = (Button) getActivity().findViewById(R.id.scheduleMonthPlus);
            monthPlus.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    updateCalendar(Calendar.MONTH, 1);
                }
            });
            final Button monthMinus = (Button) getActivity().findViewById(R.id.scheduleMonthMinus);
            monthMinus.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    updateCalendar(Calendar.MONTH, -1);
                }
            });

            // setup hour buttons
            final Button hourPlus = (Button) getActivity().findViewById(R.id.scheduleHourPlus);
            hourPlus.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    updateCalendar(Calendar.HOUR, 1);
                }
            });
            final Button hourMinus = (Button) getActivity().findViewById(R.id.scheduleHourMinus);
            hourMinus.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    updateCalendar(Calendar.HOUR, -1);
                }
            });

            // setup minute buttons
            final Button minutePlus = (Button) getActivity().findViewById(R.id.scheduleMinutePlus);
            minutePlus.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    updateCalendar(Calendar.MINUTE, 15);
                }
            });
            final Button minuteMinus = (Button) getActivity().findViewById(R.id.scheduleMinuteMinus);
            minuteMinus.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    updateCalendar(Calendar.MINUTE, -15);
                }
            });

            // this serves two purposes: guesstimate when we're gonna fly and set the text for day/month/hour/year
            // TODO: if someone have scheduled today, use the next time, otherwise guesstimate how long it'll take us to get there
            updateCalendar(Calendar.MILLISECOND, 0);

        } catch (Exception e) {
            Log.w(getClass().getName(), "showTakeoffSchedule() failed unexpectedly", e);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (savedInstanceState != null)
            takeoff = savedInstanceState.getParcelable(ARG_TAKEOFF);
        setRetainInstance(true);
        return inflater.inflate(R.layout.takeoff_schedule, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        Bundle args = getArguments();
        if (args != null)
            showTakeoffSchedule((Takeoff) args.getParcelable(ARG_TAKEOFF));

        ((ImageButton) getActivity().findViewById(R.id.fragmentButton1)).setImageDrawable(null);
        ((ImageButton) getActivity().findViewById(R.id.fragmentButton2)).setImageDrawable(null);
        ((ImageButton) getActivity().findViewById(R.id.fragmentButton3)).setImageDrawable(null);

        // don't show buttons for registering flight if user haven't set a name
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String name = prefs.getString("pref_schedule_pilot_name", null);
        TextView mayNotRegister = (TextView) getActivity().findViewById(R.id.scheduleMayNotRegister);
        TableLayout registerTime = (TableLayout) getActivity().findViewById(R.id.scheduleRegisterTime);
        if (name == null || "".equals(name.trim())) {
            mayNotRegister.setVisibility(View.VISIBLE);
            registerTime.setVisibility(View.GONE);
        } else {
            mayNotRegister.setVisibility(View.GONE);
            registerTime.setVisibility(View.VISIBLE);
        }

        // TODO: accordion list (ExpandableListView) with takeoff schedule
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(ARG_TAKEOFF, takeoff);
        // TODO: save calendar too?
    }

    private void updateCalendar(int field, int value) {
        calendar.add(field, value);
        Calendar tmpCal = GregorianCalendar.getInstance();
        tmpCal.add(Calendar.HOUR, -6); // we'll allow people to register up to 6 hours into the past
        if (calendar.before(tmpCal)) {
            // given time is too far back in time, user probably means next year
            calendar.add(Calendar.YEAR, 1);
        } else {
            tmpCal.add(Calendar.YEAR, 1);
            if (calendar.after(tmpCal)) {
                // given time is too far into the future, user probably means the previous year
                calendar.add(Calendar.YEAR, -1);
            }
        }

        // update labels
        TextView dayText = (TextView) getActivity().findViewById(R.id.scheduleDay);
        dayText.setText(new SimpleDateFormat("d.").format(calendar.getTime()));
        TextView monthText = (TextView) getActivity().findViewById(R.id.scheduleMonth);
        monthText.setText(new SimpleDateFormat("MMM").format(calendar.getTime()));
        TextView hourText = (TextView) getActivity().findViewById(R.id.scheduleHour);
        hourText.setText(new SimpleDateFormat("HH").format(calendar.getTime()));
        TextView minuteText = (TextView) getActivity().findViewById(R.id.scheduleMinute);
        minuteText.setText(new SimpleDateFormat("mm").format(calendar.getTime()));
    }
}
