package net.exent.flywithme;

import android.app.Fragment;
import android.location.Location;
import android.os.Bundle;

/**
 * Interface defining methods for communication with the FlyWithMe activity.
 */
public interface FlyWithMeActivity {
    void showFragment(String tag, Class<? extends Fragment> fragmentClass, Bundle args);
    Location getLocation();
}
