package net.exent.flywithme;

import android.os.Bundle;
import android.preference.PreferenceActivity;

// TODO: Replace with Fragment when less people are using android older than 3.0 (or when android-support-v4 got SupportPreferenceFragment)
public class Preferences extends PreferenceActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        String showTakeoffs = getPreferenceManager().getSharedPreferences().getString("pref_show_takeoffs", getString(R.string.show_takeoffs_default_value));
    }
}
