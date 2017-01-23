package net.exent.flywithme.fragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceFragment;

import net.exent.flywithme.R;
import net.exent.flywithme.data.Airspace;
import net.exent.flywithme.service.FlyWithMeService;

public class Preferences extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    private String previousPilotName;
    private String previousPilotPhone;

    public static void setupDefaultPreferences(Context context) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        // setup default airspace map polygons (all shown)
        List<String> airspaceList = new ArrayList<>(Airspace.getAirspaceMap(context).keySet());
        for (String key : airspaceList) {
            if (key == null || "".equals(key.trim()))
                continue;
            if (!sharedPref.contains("pref_airspace_enabled_" + key))
                sharedPref.edit().putBoolean("pref_airspace_enabled_" + key, true).apply();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);

        // remember the previous pilot name and phone, update pilot registration if either change
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        previousPilotName = sharedPref.getString("pref_pilot_name", "<unknown>").trim();
        previousPilotPhone = sharedPref.getString("pref_pilot_phone", "<unknown>").trim();

        updateDynamicPreferenceScreen();

        // setup preferences for airspace
        PreferenceCategory showAirspaceTypesCategory = (PreferenceCategory) findPreference("pref_show_airspace_types");
        List<String> airspaceList = new ArrayList<>(Airspace.getAirspaceMap(getActivity()).keySet());
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

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        if (!previousPilotName.equals(sharedPref.getString("pref_pilot_name", "<unknown>").trim()) || !previousPilotPhone.equals(sharedPref.getString("pref_pilot_phone", "<unknown>").trim())) {
            // register new pilot name/phone
            Intent intent = new Intent(getActivity(), FlyWithMeService.class);
            intent.setAction(FlyWithMeService.ACTION_REGISTER_PILOT);
            getActivity().startService(intent);
        }
    }
    /* END AAH! */

    private void updateDynamicPreferenceScreen() {
        // pilot info
        EditTextPreference pilotName = (EditTextPreference) findPreference("pref_pilot_name");
        if (pilotName.getText() == null || pilotName.getText().trim().equals("")) {
            pilotName.setSummary(getActivity().getString(R.string.pilot_name_please_enter));
        } else {
            pilotName.setSummary(pilotName.getText());
        }
        EditTextPreference pilotPhone = (EditTextPreference) findPreference("pref_pilot_phone");
        pilotPhone.setSummary(pilotPhone.getText());
    }
}
