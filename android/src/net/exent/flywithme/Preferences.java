package net.exent.flywithme;

import java.util.ArrayList;
import java.util.Collections;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.util.Log;

// TODO: Replace with Fragment when less people are using android older than 3.0 (or when android-support-v4 got SupportPreferenceFragment)
public class Preferences extends PreferenceActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        final String showTakeoffs = getString(R.string.show_takeoffs);
        final ListPreference takeoffMaxDistanceList = (ListPreference) findPreference("pref_max_takeoffs");
        takeoffMaxDistanceList.setTitle(showTakeoffs + ": " + takeoffMaxDistanceList.getEntry());
        takeoffMaxDistanceList.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue == null)
                    return false;
                preference.setTitle(showTakeoffs + ": " + takeoffMaxDistanceList.getEntries()[takeoffMaxDistanceList.findIndexOfValue(newValue.toString())]);
                return true;
            }
        });

        final String showAirspace = getString(R.string.show_airspace);
        final ListPreference airspaceMaxDistanceList = (ListPreference) findPreference("pref_max_airspace_distance");
        airspaceMaxDistanceList.setTitle(showAirspace + ": " + airspaceMaxDistanceList.getEntry());
        airspaceMaxDistanceList.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue == null)
                    return false;
                preference.setTitle(showAirspace + ": " + airspaceMaxDistanceList.getEntries()[airspaceMaxDistanceList.findIndexOfValue(newValue.toString())]);
                return true;
            }
        });

        PreferenceCategory showAirspaceTypes = (PreferenceCategory) findPreference("pref_show_airspace_types");
        Log.d(getClass().getSimpleName(), "getExtras(): " + getIntent().getExtras());
        ArrayList<String> airspaceList = getIntent().getExtras().getStringArrayList("airspaceList");
        Collections.sort(airspaceList);
        for (String key : airspaceList) {
            if (key == null || "".equals(key.trim()))
                continue;
            CheckBoxPreference checkBoxPreference = new CheckBoxPreference(this);
            checkBoxPreference.setKey("pref_airspace_enabled_" + key);
            checkBoxPreference.setTitle(key);
            checkBoxPreference.setChecked(true);
            showAirspaceTypes.addPreference(checkBoxPreference);
        }

        /*
        final String showAirspaceTypes = getString(R.string.show_airspace);
        final MultiSelectListPreference showAirspaceTypesList = (MultiSelectListPreference) findPreference("pref_show_airspace_types");
        List<String> list = new ArrayList<String>(TakeoffMap.polygonMap.keySet());
        showAirspaceTypesList.setEntries(list.toArray(new String[list.size()]));
        showAirspaceTypesList.setEntryValues(list.toArray(new String[list.size()]));
        /*
        showAirspaceTypesList.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue == null)
                    return false;
                preference.setTitle(showAirspaceTypes + ": " + showAirspaceTypesList.getEntries()[showAirspaceTypesList.findIndexOfValue(newValue.toString())]);
                return true;
            }
        });
        */
    }
}
