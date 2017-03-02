package net.exent.flywithme.fragment;

import android.app.Fragment;
import android.content.Context;
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

        searchSettings = getSearchSettings(getActivity()); // TODO: bundle, resets on screen rotation

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

    public static SearchSettings getSearchSettings(Context context) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        SearchSettings searchSettings = new SearchSettings();
        searchSettings.text = sharedPref.getString("search_text", searchSettings.text);
        searchSettings.exits = sharedPref.getInt("search_exits", searchSettings.exits);
        return searchSettings;
    }

    private void setupTakeoffDirButtons(View view) {
        setupTakeoffDirButton(view, R.id.searchTakeoffNoneButton, 8);
        setupTakeoffDirButton(view, R.id.searchTakeoffNButton, 7);
        setupTakeoffDirButton(view, R.id.searchTakeoffNeButton, 6);
        setupTakeoffDirButton(view, R.id.searchTakeoffEButton, 5);
        setupTakeoffDirButton(view, R.id.searchTakeoffSwButton, 4);
        setupTakeoffDirButton(view, R.id.searchTakeoffSButton, 3);
        setupTakeoffDirButton(view, R.id.searchTakeoffSeButton, 2);
        setupTakeoffDirButton(view, R.id.searchTakeoffWButton, 1);
        setupTakeoffDirButton(view, R.id.searchTakeoffNwButton, 0);
    }

    private void setupTakeoffDirButton(View view, int button, final int dirIndex) {
        final ImageButton dirButton = (ImageButton) view.findViewById(button);
        dirButton.setColorFilter((searchSettings.exits & (1 << dirIndex)) != 0 ? Color.argb(0, 0, 0, 0) : Color.argb(255, 64, 64, 64));
        dirButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchSettings.exits ^= 1 << dirIndex;
                dirButton.setColorFilter((searchSettings.exits & (1 << dirIndex)) != 0 ? Color.argb(0, 0, 0, 0) : Color.argb(255, 64, 64, 64));
            }
        });
    }

    private void saveSearchSettings(SearchSettings searchSettings) {
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putString("search_text", searchSettings.text)
                .putInt("search_exits", searchSettings.exits)
                .apply();
    }

    public static class SearchSettings {
        String text = "";
        int exits = 0b11111111;

        public boolean isSearchEnabled() {
            return !text.isEmpty() || exits != 0b11111111;
        }
    }
}
