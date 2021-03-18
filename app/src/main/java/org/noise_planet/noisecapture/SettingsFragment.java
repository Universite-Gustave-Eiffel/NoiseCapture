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

import android.content.DialogInterface;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import androidx.appcompat.app.AlertDialog;

import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

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

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if("settings_user_language".equals(preference.getKey())) {

            // Ask the user to choose language
            AlertDialog.Builder builder = new AlertDialog.Builder(this.getActivity());
            builder.setTitle(R.string.title_settings_language);
            final List<CharSequence> availableLanguages = new ArrayList<CharSequence>();
            final List<Locale> availableLocales = new ArrayList<Locale>();
            Set<String> txLangs = new TreeSet<String>(Arrays.asList(BuildConfig.SUPPORTEDLOCALES.split(",")));
            for(String locale : this.getActivity().getAssets().getLocales()) {
                if (txLangs.contains(locale)) {
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            Locale locObj = Locale.forLanguageTag(locale);
                            if (locObj != null) {
                                availableLanguages.add(locObj.getDisplayLanguage(locObj));
                                availableLocales.add(locObj);
                            }
                        } else {
                            Locale locObj = new Locale(locale);
                            availableLanguages.add(locObj.getDisplayLanguage(locObj));
                            availableLocales.add(locObj);
                        }
                    } catch (NullPointerException | IllegalArgumentException ex) {
                        availableLanguages.add(locale);
                        availableLocales.add(Locale.getDefault());
                    }
                }
            }
            CharSequence[] options = availableLanguages.toArray(new CharSequence[0]);
            // Add check box before options
            for(int idOption=0; idOption < options.length; idOption++) {
                options[idOption] = "▢ " + options[idOption];
            }
            builder.setItems(options, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Resources resources = SettingsFragment.this.getActivity().getBaseContext().getResources();
                    Locale locale = availableLocales.get(which - 1);
                    Locale.setDefault(locale);
                    resources.getConfiguration().locale = locale;
                    resources.updateConfiguration(resources.getConfiguration(), resources.getDisplayMetrics());
                }
            });
            AlertDialog dialog = builder.create();
            TextView description = new TextView(this.getActivity());
            TypedArray array = this.getActivity().obtainStyledAttributes(new int[]{R.attr.dialogPreferredPadding});
            int padding = array.getDimensionPixelSize(0, 1);
            array.recycle();
            description.setPadding(padding,padding,padding,padding);
            description.setText(R.string.settings_language_summary);
            dialog.getListView().addHeaderView(description, null, false);
            dialog.show();

        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
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