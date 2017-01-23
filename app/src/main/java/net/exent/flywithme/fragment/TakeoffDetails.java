package net.exent.flywithme.fragment;

import net.exent.flywithme.R;
import net.exent.flywithme.bean.Takeoff;
import net.exent.flywithme.data.Database;
import net.exent.flywithme.service.FlyWithMeService;

import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class TakeoffDetails extends Fragment {
    public static final String ARG_TAKEOFF = "takeoff";

    private Takeoff takeoff;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        if (getArguments() != null)
            takeoff = getArguments().getParcelable(ARG_TAKEOFF);
        if (takeoff == null && bundle != null)
            takeoff = bundle.getParcelable(ARG_TAKEOFF);

        View view = inflater.inflate(R.layout.takeoff_details, container, false);
        /* exits */
        ImageView windroseNorth = (ImageView) view.findViewById(R.id.takeoffDetailsWindroseNorth);
        ImageView windroseNorthwest = (ImageView) view.findViewById(R.id.takeoffDetailsWindroseNorthwest);
        ImageView windroseWest = (ImageView) view.findViewById(R.id.takeoffDetailsWindroseWest);
        ImageView windroseSouthwest = (ImageView) view.findViewById(R.id.takeoffDetailsWindroseSouthwest);
        ImageView windroseSouth = (ImageView) view.findViewById(R.id.takeoffDetailsWindroseSouth);
        ImageView windroseSoutheast = (ImageView) view.findViewById(R.id.takeoffDetailsWindroseSoutheast);
        ImageView windroseEast = (ImageView) view.findViewById(R.id.takeoffDetailsWindroseEast);
        ImageView windroseNortheast = (ImageView) view.findViewById(R.id.takeoffDetailsWindroseNortheast);
        windroseNorth.setVisibility(takeoff.hasNorthExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseNorthwest.setVisibility(takeoff.hasNorthwestExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseWest.setVisibility(takeoff.hasWestExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseSouthwest.setVisibility(takeoff.hasSouthwestExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseSouth.setVisibility(takeoff.hasSouthExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseSoutheast.setVisibility(takeoff.hasSoutheastExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseEast.setVisibility(takeoff.hasEastExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);
        windroseNortheast.setVisibility(takeoff.hasNortheastExit() ? ImageView.VISIBLE : ImageView.INVISIBLE);

        TextView takeoffName = (TextView) view.findViewById(R.id.takeoffDetailsName);
        TextView takeoffCoordAslHeight = (TextView) view.findViewById(R.id.takeoffDetailsCoordAslHeight);
        TextView takeoffDescription = (TextView) view.findViewById(R.id.takeoffDetailsDescription);

        takeoffName.setText(takeoff.getName());
        takeoffCoordAslHeight.setText(String.format("[%.2f,%.2f] " + getActivity().getString(R.string.asl) + ": %d " + getActivity().getString(R.string.height) + ": %d", takeoff.getLocation().getLatitude(), takeoff.getLocation().getLongitude(), takeoff.getAsl(), takeoff.getHeight()));
        takeoffDescription.setText("http://flightlog.org/fl.html?a=22&country_id=160&start_id=" + takeoff.getId() + "\n" + takeoff.getDescription());
        takeoffDescription.setMovementMethod(LinkMovementMethod.getInstance());

        ImageButton navigationButton = (ImageButton) getActivity().findViewById(R.id.fragmentButton1);
        navigationButton.setImageResource(R.mipmap.navigation);
        navigationButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Location loc = takeoff.getLocation();
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://maps.google.com/maps?daddr=" + loc.getLatitude() + "," + loc.getLongitude())));
            }
        });
        ImageButton noaaButton = (ImageButton) getActivity().findViewById(R.id.fragmentButton2);
        noaaButton.setImageResource(R.mipmap.noaa);
        noaaButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), FlyWithMeService.class);
                intent.setAction(FlyWithMeService.ACTION_GET_METEOGRAM);
                intent.putExtra(FlyWithMeService.ARG_TAKEOFF_ID, takeoff.getId());
                getActivity().startService(intent);
                // show loading animation
                ProgressBar progressBar = (ProgressBar) getActivity().findViewById(R.id.progressBar2);
                progressBar.setVisibility(View.VISIBLE);
            }
        });
        final ImageButton favouriteButton = (ImageButton) getActivity().findViewById(R.id.fragmentButton3);
        favouriteButton.setImageResource(takeoff.isFavourite() ? R.mipmap.favourite_enabled : R.mipmap.favourite_disabled);
        favouriteButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                takeoff.setFavourite(!takeoff.isFavourite());
                Database.updateFavourite(getActivity(), takeoff);
                favouriteButton.setImageResource(takeoff.isFavourite() ? R.mipmap.favourite_enabled : R.mipmap.favourite_disabled);
            }
        });
        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(ARG_TAKEOFF, takeoff);
    }
}
