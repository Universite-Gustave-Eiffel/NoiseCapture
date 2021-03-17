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
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

public class PrivacyPolicyActivity extends AppCompatActivity implements View.OnClickListener {
    boolean checkedLegalAge = false;
    boolean checkedAgree = false;
    public static final String PROP_POLICY_AGREED = "POLICY_AGREED";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_policy);
        final WebView myWebView = (WebView) findViewById(R.id.privacy_policy_webview);
        myWebView.loadUrl(getText(R.string.privacy_policy_webpage).toString());
        // Get background color
        TypedValue a = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.windowBackground, a, true);
        if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT && a.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            myWebView.setBackgroundColor(a.data);
        }


        CheckBox legalAgeCheckBox = (CheckBox) findViewById(R.id.policy_legal_age_checkbox);
        legalAgeCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                checkedLegalAge = isChecked;
                PrivacyPolicyActivity.this.onCheckChange();
            }
        });

        final CheckBox agreeCheckBox = (CheckBox) findViewById(R.id.policy_agree_checkbox);
        agreeCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                checkedAgree = isChecked;
                PrivacyPolicyActivity.this.onCheckChange();
            }
        });

        Button continueButton = (Button) findViewById(R.id.policy_agree_continue_button);
        continueButton.setOnClickListener(this);
    }

    private void onCheckChange() {
        Button continueButton = (Button) findViewById(R.id.policy_agree_continue_button);
        continueButton.setEnabled(checkedAgree && checkedLegalAge);
    }

    @Override
    public void onClick(View v) {
        // Ask the user to define is profile
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.title_settings_user_noise_knowledge);
        CharSequence[] options = getResources().getTextArray(R.array.knowledge);
        // Add check box before options
        for(int idOption=0; idOption < options.length; idOption++) {
            options[idOption] = "▢ " + options[idOption];
        }
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Save policy state
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences
                        (PrivacyPolicyActivity.this);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putBoolean(PROP_POLICY_AGREED, checkedAgree && checkedLegalAge);
                String[] spinnerValues = getResources().getStringArray(R.array.knowledge_values);
                if(which > 0 && which <= spinnerValues.length) {
                    editor.putString("settings_user_noise_knowledge", spinnerValues[which - 1]);
                }
                editor.apply();
                // Start localisation notice activity
                Intent i = new Intent(getApplicationContext(), LocalisationPolicyActivity.class);
                startActivity(i);
                finish();
            }
        });
        AlertDialog dialog = builder.create();
        TextView description = new TextView(this);
        int padding = obtainStyledAttributes(new int[]{R.attr.dialogPreferredPadding})
                .getDimensionPixelSize(0, 1);
        description.setPadding(padding,padding,padding,padding);
        description.setText(R.string.settings_user_noise_knowledge_description);
        dialog.getListView().addHeaderView(description, null, false);
        dialog.show();
    }
}
