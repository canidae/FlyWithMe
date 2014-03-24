package net.exent.flywithme;

import java.util.ArrayList;
import java.util.Collections;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.support.v4.preference.PreferenceFragment;

import net.exent.flywithme.data.Airspace;

// TODO: Replace with Fragment when less people are using android older than 3.0 (or when android-support-v4 got SupportPreferenceFragment)
public class Preferences extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        PreferenceCategory showAirspaceTypes = (PreferenceCategory) findPreference("pref_show_airspace_types");
        ArrayList<String> airspaceList = new ArrayList<>(Airspace.getAirspaceMap().keySet());
        Collections.sort(airspaceList);
        for (String key : airspaceList) {
            if (key == null || "".equals(key.trim()))
                continue;
            CheckBoxPreference checkBoxPreference = new CheckBoxPreference(getActivity());
            checkBoxPreference.setKey("pref_airspace_enabled_" + key);
            checkBoxPreference.setTitle(key);
            checkBoxPreference.setChecked(true);
            showAirspaceTypes.addPreference(checkBoxPreference);
        }
    }
}
