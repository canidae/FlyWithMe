package net.exent.flywithme.fragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceFragment;

import net.exent.flywithme.R;
import net.exent.flywithme.data.Airspace;

public class Preferences extends PreferenceFragment {

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
}
