package net.exent.flywithme.fragment;

import android.app.Fragment;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;

import net.exent.flywithme.FlyWithMeActivity;
import net.exent.flywithme.R;

public class TakeoffSearch extends Fragment {
    private SearchSettings searchSettings;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        final View view = inflater.inflate(R.layout.takeoff_search, container, false);

        searchSettings = loadSearchSettings(); // TODO: bundle, resets on screen rotation

        // set up text search and takeoff direction buttons
        EditText textSearch = (EditText) view.findViewById(R.id.searchTakeoffText);
        textSearch.setText(searchSettings.text);
        setupTakeoffDirButtons(view);

        // setup ok/reset/cancel
        Button okButton = (Button) view.findViewById(R.id.searchOkButton);
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText textSearch = (EditText) view.findViewById(R.id.searchTakeoffText);
                searchSettings.text = textSearch.getText().toString();
                saveSearchSettings(searchSettings);
                ((FlyWithMeActivity) getActivity()).showFragment("takeoffList", TakeoffList.class, null);
            }
        });
        Button resetButton = (Button) view.findViewById(R.id.searchResetButton);
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchSettings = new SearchSettings();
                EditText textSearch = (EditText) view.findViewById(R.id.searchTakeoffText);
                textSearch.setText(searchSettings.text);
                setupTakeoffDirButtons(view);
            }
        });
        Button cancelButton = (Button) view.findViewById(R.id.searchCancelButton);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((FlyWithMeActivity) getActivity()).showFragment("takeoffList", TakeoffList.class, null);
            }
        });

        return view;
    }

    private void setupTakeoffDirButtons(View view) {
        setupTakeoffDirButton(view, R.id.searchTakeoffNwButton, 0);
        setupTakeoffDirButton(view, R.id.searchTakeoffNButton, 1);
        setupTakeoffDirButton(view, R.id.searchTakeoffNeButton, 2);
        setupTakeoffDirButton(view, R.id.searchTakeoffEButton, 3);
        setupTakeoffDirButton(view, R.id.searchTakeoffNoneButton, 4);
        setupTakeoffDirButton(view, R.id.searchTakeoffWButton, 5);
        setupTakeoffDirButton(view, R.id.searchTakeoffSwButton, 6);
        setupTakeoffDirButton(view, R.id.searchTakeoffSButton, 7);
        setupTakeoffDirButton(view, R.id.searchTakeoffSeButton, 8);
    }

    private void setupTakeoffDirButton(View view, int button, final int dirIndex) {
        final ImageButton dirButton = (ImageButton) view.findViewById(button);
        dirButton.setColorFilter(searchSettings.dirEnabled[dirIndex] ? Color.argb(0, 0, 0, 0) : Color.argb(255, 64, 64, 64));
        dirButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchSettings.dirEnabled[dirIndex] = !searchSettings.dirEnabled[dirIndex];
                dirButton.setColorFilter(searchSettings.dirEnabled[dirIndex] ? Color.argb(0, 0, 0, 0) : Color.argb(255, 64, 64, 64));
            }
        });
    }

    private SearchSettings loadSearchSettings() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        SearchSettings searchSettings = new SearchSettings();
        searchSettings.text = sharedPref.getString("search_text", searchSettings.text);
        searchSettings.dirEnabled[0] = sharedPref.getBoolean("search_nw", searchSettings.dirEnabled[0]);
        searchSettings.dirEnabled[1] = sharedPref.getBoolean("search_n", searchSettings.dirEnabled[1]);
        searchSettings.dirEnabled[2] = sharedPref.getBoolean("search_ne", searchSettings.dirEnabled[2]);
        searchSettings.dirEnabled[3] = sharedPref.getBoolean("search_w", searchSettings.dirEnabled[3]);
        searchSettings.dirEnabled[4] = sharedPref.getBoolean("search_none", searchSettings.dirEnabled[4]);
        searchSettings.dirEnabled[5] = sharedPref.getBoolean("search_e", searchSettings.dirEnabled[5]);
        searchSettings.dirEnabled[6] = sharedPref.getBoolean("search_sw", searchSettings.dirEnabled[6]);
        searchSettings.dirEnabled[7] = sharedPref.getBoolean("search_s", searchSettings.dirEnabled[7]);
        searchSettings.dirEnabled[8] = sharedPref.getBoolean("search_se", searchSettings.dirEnabled[8]);
        return searchSettings;
    }

    private void saveSearchSettings(SearchSettings searchSettings) {
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putString("search_text", searchSettings.text)
                .putBoolean("search_nw", searchSettings.dirEnabled[0])
                .putBoolean("search_n", searchSettings.dirEnabled[1])
                .putBoolean("search_ne", searchSettings.dirEnabled[2])
                .putBoolean("search_w", searchSettings.dirEnabled[3])
                .putBoolean("search_none", searchSettings.dirEnabled[4])
                .putBoolean("search_e", searchSettings.dirEnabled[5])
                .putBoolean("search_sw", searchSettings.dirEnabled[6])
                .putBoolean("search_s", searchSettings.dirEnabled[7])
                .putBoolean("search_se", searchSettings.dirEnabled[8])
                .apply();
    }

    private class SearchSettings {
        String text = "";
        boolean[] dirEnabled = {true, true, true, true, true, true, true, true, true};
    }
}
