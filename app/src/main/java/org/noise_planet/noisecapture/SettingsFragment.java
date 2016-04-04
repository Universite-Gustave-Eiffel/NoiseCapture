package org.noise_planet.noisecapture;

import android.os.Bundle;
import android.preference.PreferenceFragment;

/**
 * Created by picaut on 15/04/2015.
 */
public class SettingsFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.settings);
    }
}