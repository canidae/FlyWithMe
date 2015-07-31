package net.exent.flywithme.layout;

import java.util.ArrayList;
import java.util.Collections;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.widget.ImageButton;

import net.exent.flywithme.R;
import net.exent.flywithme.data.Airspace;
import net.exent.flywithme.service.FlyWithMeService;

public class Preferences extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    private Preference.OnPreferenceChangeListener preferenceChangeListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            updateDynamicPreferenceScreen();
            return true;
        }
    };

    public static void setupDefaultPreferences(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        // setup default airspace map polygons (all shown)
        ArrayList<String> airspaceList = new ArrayList<>(Airspace.getAirspaceMap().keySet());
        for (String key : airspaceList) {
            if (key == null || "".equals(key.trim()))
                continue;
            if (!prefs.contains("pref_airspace_enabled_" + key))
                prefs.edit().putBoolean("pref_airspace_enabled_" + key, true).apply();
        }
    }

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

        // register new pilot name/phone
        Intent intent = new Intent(getActivity(), FlyWithMeService.class);
        intent.setAction(FlyWithMeService.ACTION_REGISTER_PILOT);
        getActivity().startService(intent);
    }
    /* END AAH! */

    private void updateDynamicPreferenceScreen() {
        EditTextPreference schedulePilotName = (EditTextPreference) findPreference("pref_pilot_name");
        schedulePilotName.setSummary(schedulePilotName.getText());
        EditTextPreference schedulePilotPhone = (EditTextPreference) findPreference("pref_pilot_phone");
        schedulePilotPhone.setSummary(schedulePilotPhone.getText());
        CheckBoxPreference scheduleNotification = (CheckBoxPreference) findPreference("pref_notifications");
        scheduleNotification.setOnPreferenceChangeListener(preferenceChangeListener);
    }
}
