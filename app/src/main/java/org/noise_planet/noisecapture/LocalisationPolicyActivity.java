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

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;

import androidx.annotation.NonNull;

public class LocalisationPolicyActivity extends MainActivity implements View.OnClickListener {
    public static final String PROP_POLICY_READ = "POLICY_LOCALISATION_READ";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_localisation_policy);
        final WebView myWebView = (WebView) findViewById(R.id.localisation_policy_webview);
        myWebView.loadUrl(getText(R.string.localisation_policy_webpage).toString());
        // Get background color
        TypedValue a = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.windowBackground, a, true);
        if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT && a.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            myWebView.setBackgroundColor(a.data);
        }

        Button continueButton = (Button) findViewById(R.id.localisation_continue_button);
        continueButton.setOnClickListener(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Start measurement activity
        Intent i = new Intent(getApplicationContext(), MeasurementActivity.class);
        startActivity(i);
        finish();
    }

    @Override
    public void onClick(View v) {
        // Save policy state
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences
                (LocalisationPolicyActivity.this);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(PROP_POLICY_READ, true);
        editor.apply();

        // Ask for gps access
        if(checkAndAskPermissions()) {
            // If app already gives the right or block, we still continue
            // as onRequestPermissionsResult will not be called
            // Start measurement activity
            Intent i = new Intent(getApplicationContext(), MeasurementActivity.class);
            startActivity(i);
            finish();
        }
    }
}
