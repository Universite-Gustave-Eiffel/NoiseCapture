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

import android.os.Handler;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;


public class activity_calibration_start extends MainActivity {

    private int progressStatus = 0;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity_calibration_start);
        initDrawer();

        // Fill the spinner for the calibration mode selection
        Spinner spinner = (Spinner) findViewById(R.id.spinner_calibration_mode);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.choice_user_calibration_mode, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // Make START and CANCEL buttons visible/invisible
        Button button_start = (Button) findViewById(R.id.button_start_calibration);
        button_start.setEnabled(true);
        button_start.setVisibility(View.VISIBLE);
        Button button_cancel = (Button) findViewById(R.id.button_cancel_calibration);
        button_cancel.setEnabled(false);
        button_cancel.setVisibility(View.INVISIBLE);
        final ProgressBar progressBar_wait_calibration_recording = (ProgressBar) findViewById(R.id.progressBar_wait_calibration_recording);
        progressBar_wait_calibration_recording.setProgress(0);
        final ProgressBar progressBar_calibration_recording = (ProgressBar) findViewById(R.id.progressBar_calibration_recording);
        progressBar_calibration_recording.setProgress(0);

        // Action on START button:
        // Hide START button - Display CANCEL button
        // Start "Prepare..." progressbar
        // Start "Recording..." progressbar and start audiorecording...
        button_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // Make START and CANCEL buttons invisible/visible
                // Set progressbar to 0
                Button button_start = (Button) findViewById(R.id.button_start_calibration);
                button_start.setEnabled(false);
                button_start.setVisibility(View.INVISIBLE);
                Button button_cancel = (Button) findViewById(R.id.button_cancel_calibration);
                button_cancel.setEnabled(true);
                button_cancel.setVisibility(View.VISIBLE);
                final ProgressBar progressBar_wait_calibration_recording = (ProgressBar) findViewById(R.id.progressBar_wait_calibration_recording);
                progressBar_wait_calibration_recording.setProgress(0);
                final ProgressBar progressBar_calibration_recording = (ProgressBar) findViewById(R.id.progressBar_calibration_recording);
                progressBar_calibration_recording.setProgress(0);

                // Start Progressbar for waiting record
                // Source: http://www.compiletimeerror.com/2013/09/android-progress-bar-example.html#.VZZNEEa7_m4
                // Start long running operation in a background thread
                EditText delay = (EditText)findViewById(R.id.editText_calibration_delay);
                final int value_delay=Integer.parseInt(delay.getText().toString());
                progressStatus=0;
                new Thread(new Runnable() {
                    public void run() {
                        while (progressStatus < 100) {
                            progressStatus += 1;
                            // Update the progress bar and display the

                            //current value in the text view
                            handler.post(new Runnable() {
                                public void run() {
                                    progressBar_wait_calibration_recording.setProgress(progressStatus);
                                    //textView.setText(progressStatus+"/"+progressBar.getMax());
                                }
                            });
                            try {
                                //Display the progressbar with a time increment corresponding to a total duration of the delay
                                Thread.sleep(1000*value_delay/100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }).start();

                //TODO : start recording with progressbar progression

            }
        });

        // Action on CANCEL button:
        // Stop recording
        // Hide CANCEL button - Display CANCEL button
        button_cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // Make START and CANCEL buttons invisible/visible
                Button button_start = (Button) findViewById(R.id.button_start_calibration);
                button_start.setEnabled(true);
                button_start.setVisibility(View.VISIBLE);
                Button button_cancel = (Button) findViewById(R.id.button_cancel_calibration);
                button_cancel.setEnabled(false);
                button_cancel.setVisibility(View.INVISIBLE);
                final ProgressBar progressBar_wait_calibration_recording = (ProgressBar) findViewById(R.id.progressBar_wait_calibration_recording);
                progressBar_wait_calibration_recording.setProgress(0);
                final ProgressBar progressBar_calibration_recording = (ProgressBar) findViewById(R.id.progressBar_calibration_recording);
                progressBar_calibration_recording.setProgress(0);

            }
        });


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
