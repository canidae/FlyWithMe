package net.exent.flywithme.fragment;

import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.maps.GoogleMap.InfoWindowAdapter;
import com.google.android.gms.maps.model.Marker;

import net.exent.flywithme.R;

/**
 * Created by canidae on 6/21/15.
 */
public class TakeoffMapMarkerInfo implements InfoWindowAdapter {
    private View infoView = null;
    private LayoutInflater inflater = null;

    public TakeoffMapMarkerInfo(LayoutInflater inflater) {
        this.inflater = inflater;
    }

    @Override
    public View getInfoWindow(Marker marker) {
        return null;
    }

    @Override
    public View getInfoContents(Marker marker) {
        if (infoView == null)
            infoView = inflater.inflate(R.layout.takeoff_map_marker_info, null);

        TextView tv = (TextView) infoView.findViewById(R.id.title);
        tv.setText(marker.getTitle());
        tv = (TextView) infoView.findViewById(R.id.snippet);
        tv.setText(Html.fromHtml(marker.getSnippet().replaceAll("\\n", "<br>")));

        return infoView;
    }
}
