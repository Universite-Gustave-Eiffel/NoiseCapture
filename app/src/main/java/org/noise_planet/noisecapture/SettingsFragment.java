/*
 * This file is part of the NoiseCapture application and OnoMap system.
 *
 * The 'OnoMaP' system is led by Lab-STICC and Ifsttar and generates noise maps via
 * citizen-contributed noise data.
 *
 * This application is co-funded by the ENERGIC-OD Project (European Network for
 * Redistributing Geospatial Information to user Communities - Open Data). ENERGIC-OD
 * (http://www.energic-od.eu/) is partially funded under the ICT Policy Support Programme (ICT
 * PSP) as part of the Competitiveness and Innovation Framework Programme by the European
 * Community. The application work is also supported by the French geographic portal GEOPAL of the
 * Pays de la Loire region (http://www.geopal.org).
 *
 * Copyright (C) IFSTTAR - LAE and Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
 *
 * NoiseCapture is a free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or(at your option) any later version. NoiseCapture is distributed in the hope that
 * it will be useful,but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.You should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation,Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301  USA or see For more information,  write to Ifsttar,
 * 14-20 Boulevard Newton Cite Descartes, Champs sur Marne F-77447 Marne la Vallee Cedex 2 FRANCE
 *  or write to scientific.computing@ifsttar.fr
 */

package org.noise_planet.noisecapture;

import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

/**
 * Check new values of preferences before commit in sharedPreferences
 */
public class SettingsFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.settings);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        SharedPreferenceListener sharedPreferenceListener = new SharedPreferenceListener();
        for(int idPreference = 0; idPreference < preferenceScreen.getPreferenceCount(); idPreference++) {
            Preference preference = preferenceScreen.getPreference(idPreference);
            if(preference instanceof PreferenceGroup) {
                PreferenceGroup preferenceGroup = (PreferenceGroup)preference;
                for(int IdGroupReference = 0; IdGroupReference < preferenceGroup.getPreferenceCount(); IdGroupReference++) {
                    preference = preferenceGroup.getPreference(IdGroupReference);
                    preference.setOnPreferenceChangeListener(sharedPreferenceListener);
                }
            } else {
                preference.setOnPreferenceChangeListener(sharedPreferenceListener);
            }
        }
    }

    private static final class SharedPreferenceListener implements Preference.OnPreferenceChangeListener {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if(preference instanceof EditTextPreference && newValue instanceof String) {
                EditText prefText = ((EditTextPreference) preference).getEditText();
                int editorType = prefText.getInputType();
                if(EditorInfo.TYPE_CLASS_NUMBER == (EditorInfo.TYPE_CLASS_NUMBER & editorType)) {
                    // Try to convert to double
                    double res;
                    try {
                        res = Double.valueOf((String)newValue);
                    } catch (NumberFormatException ex) {
                        return false;
                    }
                    if(EditorInfo.TYPE_NUMBER_FLAG_SIGNED != (EditorInfo.TYPE_NUMBER_FLAG_SIGNED & editorType)) {
                        // Must be superior than 0
                        if(res < 0) {
                            return false;
                        }
                    }
                    if(EditorInfo.TYPE_NUMBER_FLAG_SIGNED != (EditorInfo.TYPE_NUMBER_FLAG_SIGNED & editorType)) {
                        // Must be an integer
                        if(Double.compare(res % 1,0) != 0) {
                            return false;
                        }
                    }
                }
                return true;
            }
            return true;
        }
    }
}