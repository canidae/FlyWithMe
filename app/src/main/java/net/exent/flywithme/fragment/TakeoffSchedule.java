package net.exent.flywithme.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TextView;

import net.exent.flywithme.R;
import net.exent.flywithme.bean.Takeoff;
import net.exent.flywithme.data.Database;
import net.exent.flywithme.server.flyWithMeServer.model.Pilot;
import net.exent.flywithme.service.FlyWithMeService;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TakeoffSchedule extends Fragment {
    public static final String ARG_TAKEOFF = "takeoff";
    private Takeoff takeoff;
    private Calendar calendar = GregorianCalendar.getInstance();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (savedInstanceState != null)
            takeoff = savedInstanceState.getParcelable(ARG_TAKEOFF);
        return inflater.inflate(R.layout.takeoff_schedule, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();

        Bundle args = getArguments();
        if (args != null)
            takeoff = args.getParcelable(ARG_TAKEOFF);
        if (takeoff == null) {
            Log.w(getClass().getName(), "Unable to show schedule, no takeoff supplied");
            return;
        }

        // don't show buttons for registering flight if user haven't set a name
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String pilotName = prefs.getString("pref_pilot_name", "").trim();
        TextView mayNotRegister = (TextView) getActivity().findViewById(R.id.scheduleMayNotRegister);
        TableLayout flightTime = (TableLayout) getActivity().findViewById(R.id.scheduleFlightTime);
        Button scheduleFlight = (Button) getActivity().findViewById(R.id.scheduleFlightButton);
        if ("".equals(pilotName)) {
            mayNotRegister.setVisibility(View.VISIBLE);
            flightTime.setVisibility(View.GONE);
            scheduleFlight.setVisibility(View.GONE);
        } else {
            showTakeoffSchedule();
            mayNotRegister.setVisibility(View.GONE);
            flightTime.setVisibility(View.VISIBLE);
            scheduleFlight.setVisibility(View.VISIBLE);
        }

        ExpandableListView scheduleList = (ExpandableListView) getActivity().findViewById(R.id.scheduleRegisteredFlights);
        TakeoffScheduleAdapter scheduleAdapter = new TakeoffScheduleAdapter();
        scheduleList.setAdapter(scheduleAdapter);
        // expand all groups by default
        for (int i = 0; i < scheduleAdapter.getGroupCount(); ++i)
            scheduleList.expandGroup(i);
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(ARG_TAKEOFF, takeoff);
    }

    private void showTakeoffSchedule() {
        try {
            TextView takeoffName = (TextView) getActivity().findViewById(R.id.scheduleTakeoffName);
            takeoffName.setText(takeoff.getName());

            // setup day buttons
            Button dayPlus = (Button) getActivity().findViewById(R.id.scheduleDayPlus);
            dayPlus.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    updateCalendar(Calendar.DAY_OF_YEAR, 1);
                }
            });
            Button dayMinus = (Button) getActivity().findViewById(R.id.scheduleDayMinus);
            dayMinus.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    updateCalendar(Calendar.DAY_OF_YEAR, -1);
                }
            });

            // setup month buttons
            Button monthPlus = (Button) getActivity().findViewById(R.id.scheduleMonthPlus);
            monthPlus.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    updateCalendar(Calendar.MONTH, 1);
                }
            });
            Button monthMinus = (Button) getActivity().findViewById(R.id.scheduleMonthMinus);
            monthMinus.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    updateCalendar(Calendar.MONTH, -1);
                }
            });

            // setup hour buttons
            Button hourPlus = (Button) getActivity().findViewById(R.id.scheduleHourPlus);
            hourPlus.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    updateCalendar(Calendar.HOUR, 1);
                }
            });
            Button hourMinus = (Button) getActivity().findViewById(R.id.scheduleHourMinus);
            hourMinus.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    updateCalendar(Calendar.HOUR, -1);
                }
            });

            // setup minute buttons
            Button minutePlus = (Button) getActivity().findViewById(R.id.scheduleMinutePlus);
            minutePlus.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    updateCalendar(Calendar.MINUTE, 15);
                }
            });
            Button minuteMinus = (Button) getActivity().findViewById(R.id.scheduleMinuteMinus);
            minuteMinus.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    updateCalendar(Calendar.MINUTE, -15);
                }
            });

            // setup register schedule button
            final Button scheduleFlight = (Button) getActivity().findViewById(R.id.scheduleFlightButton);
            scheduleFlight.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    scheduleFlight(calendar.getTimeInMillis());
                }
            });

            // set minutes to the last half hour
            calendar.set(Calendar.MINUTE, calendar.get(Calendar.MINUTE) / 30 * 30);
            // set seconds and milliseconds to 0
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            // add 30 minutes to the calendar
            updateCalendar(Calendar.MINUTE, 30);
        } catch (Exception e) {
            Log.w(getClass().getName(), "showTakeoffSchedule() failed unexpectedly", e);
        }
    }

    private void updateCalendar(int field, int value) {
        calendar.add(field, value);
        Calendar tmpCal = GregorianCalendar.getInstance();
        tmpCal.add(Calendar.HOUR, -24); // we'll allow people to register up to 24 hours into the past
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
        dayText.setText(new SimpleDateFormat("d.", Locale.US).format(calendar.getTime()));
        TextView monthText = (TextView) getActivity().findViewById(R.id.scheduleMonth);
        monthText.setText(new SimpleDateFormat("MMM", Locale.US).format(calendar.getTime()));
        TextView hourText = (TextView) getActivity().findViewById(R.id.scheduleHour);
        hourText.setText(new SimpleDateFormat("HH", Locale.US).format(calendar.getTime()));
        TextView minuteText = (TextView) getActivity().findViewById(R.id.scheduleMinute);
        minuteText.setText(new SimpleDateFormat("mm", Locale.US).format(calendar.getTime()));
    }

    private void scheduleFlight(long timestamp) {
        Button scheduleFlight = (Button) getActivity().findViewById(R.id.scheduleFlightButton);
        scheduleFlight.setText(getString(R.string.scheduling_flight));
        scheduleFlight.setEnabled(false);

        Intent intent = new Intent(getActivity(), FlyWithMeService.class);
        intent.setAction(FlyWithMeService.ACTION_SCHEDULE_FLIGHT);
        intent.putExtra(FlyWithMeService.ARG_TAKEOFF_ID, takeoff.getId());
        intent.putExtra(FlyWithMeService.ARG_TIMESTAMP_IN_SECONDS, timestamp / 1000);
        getActivity().startService(intent);
        // TODO: progress bar?
    }

    private void unscheduleFlight(long timestamp) {
        Button scheduleFlight = (Button) getActivity().findViewById(R.id.scheduleFlightButton);
        scheduleFlight.setText(getString(R.string.scheduling_flight));
        scheduleFlight.setEnabled(false);

        Intent intent = new Intent(getActivity(), FlyWithMeService.class);
        intent.setAction(FlyWithMeService.ACTION_UNSCHEDULE_FLIGHT);
        intent.putExtra(FlyWithMeService.ARG_TAKEOFF_ID, takeoff.getId());
        intent.putExtra(FlyWithMeService.ARG_TIMESTAMP_IN_SECONDS, timestamp / 1000);
        getActivity().startService(intent);
        // TODO: progress bar?
    }

    private class TakeoffScheduleAdapter extends BaseExpandableListAdapter {
        private Map<Date, Set<Pilot>> schedule;
        private SimpleDateFormat dateFormatter = new SimpleDateFormat("EEE dd. MMM, HH:mm", Locale.US);

        public TakeoffScheduleAdapter() {
            updateData();
        }

        public void updateData() {
            schedule = new Database(getActivity()).getTakeoffSchedule(takeoff);
            notifyDataSetChanged();
        }

        @Override
        public int getGroupCount() {
            return schedule.size();
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return getEntryGroup(groupPosition).getValue().size();
        }

        @Override
        public Object getGroup(int groupPosition) {
            return getEntryGroup(groupPosition).getKey();
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            return getEntryGroupChild(groupPosition, childPosition);
        }

        @Override
        public long getGroupId(int groupPosition) {
            return 0;
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return 0;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getGroupView(final int groupPosition, boolean isExpanded, View convertView, final ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.takeoff_schedule_group, null);
            }
            /* AAH! Adding a button to the ExpandableListView removes expansion/collapse functionality of group as well as background color when clicked */
            convertView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    ExpandableListView listView = ((ExpandableListView) parent);
                    if (listView.isGroupExpanded(groupPosition))
                        listView.collapseGroup(groupPosition);
                    else
                        listView.expandGroup(groupPosition, true);
                }
            });
            /* END AAH! */
            TextView groupTime = (TextView) convertView.findViewById(R.id.scheduleGroupTime);
            Map.Entry<Date, Set<Pilot>> entryGroup = getEntryGroup(groupPosition);
            final Date date = entryGroup.getKey();
            groupTime.setText(dateFormatter.format(date));
            TextView groupPilots = (TextView) convertView.findViewById(R.id.scheduleGroupPilots);
            groupPilots.setText(getString(R.string.pilots) + ": " + entryGroup.getValue().size());
            final ImageButton joinOrLeave = (ImageButton) convertView.findViewById(R.id.joinOrLeaveButton);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String pilotName = prefs.getString("pref_schedule_pilot_name", "").trim();
            boolean foundPilot = false;
            for (Pilot pilot : entryGroup.getValue()) {
                if (pilotName.equals(pilot.getName())) {
                    foundPilot = true;
                    break;
                }
            }
            if ("".equals(pilotName)) {
                joinOrLeave.setVisibility(View.GONE);
            } else {
                joinOrLeave.setVisibility(View.VISIBLE);
                if (foundPilot) {
                    joinOrLeave.setTag(Boolean.TRUE); // used in onClick below to call unschedule instead of schedule
                    joinOrLeave.setImageResource(android.R.drawable.ic_delete);
                } else {
                    joinOrLeave.setTag(null); // used in onClick below to call schedule instead of unschedule
                    joinOrLeave.setImageResource(android.R.drawable.ic_input_add);
                }

                joinOrLeave.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        if (joinOrLeave.getTag() != null) {
                            unscheduleFlight(date.getTime());
                        } else {
                            scheduleFlight(date.getTime());
                        }
                    }
                });
            }
            return convertView;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.takeoff_schedule_entry, null);
            }
            final Pilot pilot = getEntryGroupChild(groupPosition, childPosition);
            TextView entryPilotName = (TextView) convertView.findViewById(R.id.scheduleEntryPilotName);
            entryPilotName.setText(pilot.getName());
            TextView entryPilotPhone = (TextView) convertView.findViewById(R.id.scheduleEntryPilotPhone);
            entryPilotPhone.setText(pilot.getPhone());
            ImageButton entryCallButton = (ImageButton) convertView.findViewById(R.id.scheduleEntryCallButton);
            if ("".equals(pilot.getPhone()) || pilot.getPhone().equals(PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("pref_schedule_pilot_phone", null))) {
                entryCallButton.setVisibility(View.INVISIBLE);
            } else {
                entryCallButton.setVisibility(View.VISIBLE);
                entryCallButton.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        Intent callIntent = new Intent(Intent.ACTION_DIAL);
                        callIntent.setData(Uri.parse("tel:" + pilot.getPhone()));
                        startActivity(callIntent);
                    }
                });
            }
            return convertView;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return false;
        }

        private Map.Entry<Date, Set<Pilot>> getEntryGroup(int group) {
            for (Map.Entry<Date, Set<Pilot>> entry : schedule.entrySet()) {
                if (group-- <= 0)
                    return entry;
            }
            return null;
        }

        private Pilot getEntryGroupChild(int group, int child) {
            for (Pilot pilot : getEntryGroup(group).getValue()) {
                if (child-- <= 0)
                    return pilot;
            }
            return null;
        }
    }
}
