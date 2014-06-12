package net.exent.flywithme.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.TimeZone;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.support.v4.preference.PreferenceFragment;
import android.util.Log;
import android.widget.ImageButton;

import net.exent.flywithme.R;
import net.exent.flywithme.data.Airspace;

public class Preferences extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    private Preference.OnPreferenceChangeListener preferenceChangeListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            updateDynamicPreferenceScreen();
            return true;
        }
    };

    public static void setupDefaultPreferences(Context context) {
        // setup default sounding, two days in advantage, sounding nearest 12 local time (ignoring DST, which is an idiotic practice)
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        TimeZone tz = TimeZone.getDefault();
        int tzOffsetIgnoringDst = (tz.getOffset(System.currentTimeMillis()) - (tz.inDaylightTime(new Date()) ? tz.getDSTSavings() : 0)) / 3600000;
        for (int i = 0; i <= 21; i += 3) {
            String hour = (i < 10 ? "0" + i : "" + i);
            if (!prefs.contains("pref_sounding_at_" + hour))
                prefs.edit().putBoolean("pref_sounding_at_" + hour, Math.abs(12 - tzOffsetIgnoringDst - i) <= 1).commit();
        }

        // setup default airspace map polygons (all shown)
        ArrayList<String> airspaceList = new ArrayList<>(Airspace.getAirspaceMap().keySet());
        for (String key : airspaceList) {
            if (key == null || "".equals(key.trim()))
                continue;
            if (!prefs.contains("pref_airspace_enabled_" + key))
                prefs.edit().putBoolean("pref_airspace_enabled_" + key, true).commit();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);

        updateDynamicPreferenceScreen();

        PreferenceCategory meteogramAndSounding = (PreferenceCategory) findPreference("pref_meteogram_and_sounding");
        for (int i = 0; i <= 21; i += 3) {
            String hour = (i < 10 ? "0" + i : "" + i);
            CheckBoxPreference checkBoxPreference = new CheckBoxPreference(getActivity());
            checkBoxPreference.setKey("pref_sounding_at_" + hour);
            checkBoxPreference.setTitle("Fetch sounding at " + hour + " UTC");
            meteogramAndSounding.addPreference(checkBoxPreference);
        }

        PreferenceCategory showAirspaceTypesCategory = (PreferenceCategory) findPreference("pref_show_airspace_types");
        ArrayList<String> airspaceList = new ArrayList<>(Airspace.getAirspaceMap().keySet());
        Collections.sort(airspaceList);
        for (String key : airspaceList) {
            if (key == null || "".equals(key.trim()))
                continue;
            CheckBoxPreference checkBoxPreference = new CheckBoxPreference(getActivity());
            checkBoxPreference.setKey("pref_airspace_enabled_" + key);
            checkBoxPreference.setTitle(key);
            showAirspaceTypesCategory.addPreference(checkBoxPreference);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        setRetainInstance(true);

        ((ImageButton) getActivity().findViewById(R.id.fragmentButton1)).setImageDrawable(null);
        ((ImageButton) getActivity().findViewById(R.id.fragmentButton2)).setImageDrawable(null);
        ((ImageButton) getActivity().findViewById(R.id.fragmentButton3)).setImageDrawable(null);
    }

    /* AAH! (Another Android Hack!): Setting a preference value only updates the preference, not the PreferenceFragment view */
    /* http://stackoverflow.com/a/15329652/2040995 */
    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updateDynamicPreferenceScreen();
    }
    /* END AAH! */

    private void updateDynamicPreferenceScreen() {
        ListPreference scheduleFetchTakeoffs = (ListPreference) findPreference("pref_schedule_fetch_takeoffs");
        if (scheduleFetchTakeoffs == null) {
            // this shouldn't really happen, but apparently it does
            // for now, just return and hope that the method will be called again soon enough
            Log.w(getClass().getName(), "Unable to find preference widgets, can't update preference screen");
            return;
        }
        scheduleFetchTakeoffs.setOnPreferenceChangeListener(preferenceChangeListener);
        scheduleFetchTakeoffs.setSummary(scheduleFetchTakeoffs.getEntry());

        PreferenceCategory scheduleCategory = (PreferenceCategory) findPreference("pref_schedule");
        scheduleCategory.setEnabled(!"-1".equals(scheduleFetchTakeoffs.getValue()));

        EditTextPreference schedulePilotName = (EditTextPreference) findPreference("pref_schedule_pilot_name");
        schedulePilotName.setSummary(schedulePilotName.getText());
        EditTextPreference schedulePilotPhone = (EditTextPreference) findPreference("pref_schedule_pilot_phone");
        schedulePilotPhone.setSummary(schedulePilotPhone.getText());
        CheckBoxPreference scheduleNotification = (CheckBoxPreference) findPreference("pref_schedule_notification");
        scheduleNotification.setOnPreferenceChangeListener(preferenceChangeListener);
        ListPreference scheduleFetchInterval = (ListPreference) findPreference("pref_schedule_update_interval");
        scheduleFetchInterval.setOnPreferenceChangeListener(preferenceChangeListener);
        scheduleFetchInterval.setSummary(scheduleFetchInterval.getEntry());
        ListPreference scheduleFetchStartTime = (ListPreference) findPreference("pref_schedule_start_fetch_time");
        scheduleFetchStartTime.setOnPreferenceChangeListener(preferenceChangeListener);
        scheduleFetchStartTime.setSummary(scheduleFetchStartTime.getEntry());
        ListPreference scheduleFetchStopTime = (ListPreference) findPreference("pref_schedule_stop_fetch_time");
        scheduleFetchStopTime.setOnPreferenceChangeListener(preferenceChangeListener);
        scheduleFetchStopTime.setSummary(scheduleFetchStopTime.getEntry());

        ListPreference prefSoundingDays = (ListPreference) findPreference("pref_sounding_days");
        prefSoundingDays.setOnPreferenceChangeListener(preferenceChangeListener);
        prefSoundingDays.setSummary(prefSoundingDays.getEntry());
    }
}
