package net.exent.flywithme.layout;

import java.util.ArrayList;
import java.util.Collections;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.SwitchPreference;
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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);

        updateDynamicPreferenceScreen();

        PreferenceCategory showAirspaceTypesCategory = (PreferenceCategory) findPreference("pref_show_airspace_types");
        ArrayList<String> airspaceList = new ArrayList<>(Airspace.getAirspaceMap().keySet());
        Collections.sort(airspaceList);
        for (String key : airspaceList) {
            if (key == null || "".equals(key.trim()))
                continue;
            CheckBoxPreference checkBoxPreference = new CheckBoxPreference(getActivity());
            checkBoxPreference.setKey("pref_airspace_enabled_" + key);
            checkBoxPreference.setTitle(key);
            checkBoxPreference.setChecked(true);
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
        scheduleFetchTakeoffs.setOnPreferenceChangeListener(preferenceChangeListener);
        scheduleFetchTakeoffs.setSummary(scheduleFetchTakeoffs.getEntry());

        PreferenceCategory scheduleCategory = (PreferenceCategory) findPreference("pref_schedule");
        scheduleCategory.setEnabled(!"-1".equals(scheduleFetchTakeoffs.getValue()));

        EditTextPreference schedulePilotName = (EditTextPreference) findPreference("pref_schedule_pilot_name");
        schedulePilotName.setSummary(schedulePilotName.getText());
        EditTextPreference schedulePilotPhone = (EditTextPreference) findPreference("pref_schedule_pilot_phone");
        schedulePilotPhone.setSummary(schedulePilotPhone.getText());
        SwitchPreference scheduleNotification = (SwitchPreference) findPreference("pref_schedule_notification");
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
    }
}
