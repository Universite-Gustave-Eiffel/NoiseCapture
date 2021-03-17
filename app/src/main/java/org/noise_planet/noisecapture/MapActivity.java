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


import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatActivity;
import android.view.Menu;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;


public class MapActivity extends MainActivity implements MapFragment.MapFragmentAvailableListener {
    private Storage.Record record;
    private WebViewContent webViewContent;
    private long beginLoadPage = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        Intent intent = getIntent();
        initDrawer(intent != null ? intent.getIntExtra(RESULTS_RECORD_ID, -1) : null);

        MeasurementManager measurementManager = new MeasurementManager(getApplicationContext());
        webViewContent = new WebViewContent(this, measurementManager);
        if(intent != null && intent.hasExtra(RESULTS_RECORD_ID)) {
            record = measurementManager.getRecord(intent.getIntExtra(RESULTS_RECORD_ID, -1));
        } else {
            // Read the last stored record
            List<Storage.Record> recordList = measurementManager.getRecords();
            if(!recordList.isEmpty()) {
                record = recordList.get(0);
            } else {
                // Message for starting a record
                Toast.makeText(getApplicationContext(), getString(R.string.no_results),
                        Toast.LENGTH_LONG).show();
            }
        }
        MapFragment mapFragment = getMapControler();
        mapFragment.setMapFragmentAvailableListener(this);
        onMapFragmentAvailable(mapFragment);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        boolean displayNotification = sharedPref.getBoolean("settings_notification_links", true);
        if (displayNotification && CheckNbRun("NbRunMaxNoisePlanetUrl", getResources().getInteger
                (R.integer.NbRunMaxNoisePlanetUrl))) {
            displayCommunityMapNotification();
        }
    }

    private MapFragment getMapControler() {
        return (MapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Cancel loading if the UI is about to be destroyed
        if(webViewContent != null) {
            webViewContent.canceled.set(true);
        }
    }

    // Disable warning as the injected object cannot communicate with the NoiseCapture application
    @SuppressLint("AddJavascriptInterface")
    @Override
    public void onMapFragmentAvailable(MapFragment mapFragment) {
        beginLoadPage = System.currentTimeMillis();
        // addJavascriptInterface
        mapFragment.getWebView().addJavascriptInterface(webViewContent, "androidContent");
        mapFragment.loadUrl("file:///android_asset/html/map_result.html");
    }

    @Override
    public void onPageLoaded(MapFragment mapFragment) {
        long beginLoadContent = System.currentTimeMillis();
        if(BuildConfig.DEBUG) {
            System.out.println("Load leaflet in "+(beginLoadContent - beginLoadPage)+" ms");
        }
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        int measureLimitation = getInteger(sharedPref, "summary_settings_map_maxmarker", 0);
        webViewContent.setMeasureLimitation(measureLimitation);

        if(record != null) {
            webViewContent.setSelectedMeasurementRecordId(record.getId());
        }

        mapFragment.runJs("addMeasurementPoints(JSON.parse(androidContent" +
                ".getSelectedMeasurementData()))");

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    public static final class WebViewContent {
        private AppCompatActivity activity;
        private int selectedMeasurementRecordId;
        private MeasurementManager measurementManager;
        private int measureLimitation;
        private double ignoreNewPointDistanceDelta = 1;
        private AtomicBoolean canceled = new AtomicBoolean(false);

        public WebViewContent(AppCompatActivity activity, MeasurementManager measurementManager) {
            this.activity = activity;
            this.measurementManager = measurementManager;
        }

        public void setSelectedMeasurementRecordId(int selectedMeasurementRecordId) {
            this.selectedMeasurementRecordId = selectedMeasurementRecordId;
        }

        public void setMeasureLimitation(int measureLimitation) {
            this.measureLimitation = measureLimitation;
        }

        @JavascriptInterface
        public String getSelectedMeasurementData() {
            canceled.set(false);
            long beginLoadContent = System.currentTimeMillis();
            List<MeasurementManager.LeqBatch> measurements = measurementManager
                    .getRecordLocations(selectedMeasurementRecordId, true, measureLimitation,
                            new ReadRecordsProgression(activity, canceled),
                            ignoreNewPointDistanceDelta);
            if (measurements.isEmpty()) {
                Toast.makeText(activity, activity.getText(R.string.no_gps_results), Toast
                        .LENGTH_LONG).show();
            }
            if (BuildConfig.DEBUG) {
                System.out.println("Read data from db in " + (System.currentTimeMillis() -
                        beginLoadContent) + " ms");
            }
            try {
                return MeasurementExport.recordsToGeoJSON(measurements, false, false).toString();
            } catch (JSONException ex) {
                if (BuildConfig.DEBUG) {
                    System.out.println("Error while building JSON " + ex.getLocalizedMessage());
                }
                return new JSONArray().toString();
            }
        }

        @JavascriptInterface
        public String getAllMeasurementData() {
            canceled.set(false);
            long beginLoadContent = System.currentTimeMillis();
            List<MeasurementManager.LeqBatch> measurements = measurementManager
                    .getRecordLocations(-1 , true, measureLimitation, new ReadRecordsProgression
                            (activity, canceled),
                            ignoreNewPointDistanceDelta);
            if(BuildConfig.DEBUG) {
                System.out.println("Read data from db in "+(System.currentTimeMillis() - beginLoadContent)+" ms");
            }
            try {
                return MeasurementExport.recordsToGeoJSON(measurements, false, false).toString();
            } catch (JSONException ex) {
                if(BuildConfig.DEBUG) {
                    System.out.println("Error while building JSON "+ex.getLocalizedMessage());
                }
                return new JSONArray().toString();
            }
        }
    }

    private static final class ReadRecordsProgression implements MeasurementManager
            .ProgressionCallBack, View.OnClickListener {
        private AppCompatActivity activity;
        AtomicBoolean canceled;
        int recordCount = 0;
        int record = 0;
        int lastProgress = 0;
        boolean handleProgression = false;
        private static final int MINIMAL_RECORD_DISPLAY_PROGRESS = 100;
        View progressView;
        ProgressBar progressBar;
        Button button;

        public ReadRecordsProgression(AppCompatActivity activity, AtomicBoolean canceled) {
            this.activity = activity;
            this.canceled = canceled;
            progressView = activity.findViewById(R.id
                    .map_progress_layout);
            progressBar = (ProgressBar) activity.findViewById(R.id
                    .map_progress_control);
            button = (Button)activity.findViewById(R.id
                    .map_progress_cancel);
            button.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            canceled.set(true);
        }

        @Override
        public void onCreateCursor(int recordCount) {
            this.recordCount = recordCount;
            if(recordCount > MINIMAL_RECORD_DISPLAY_PROGRESS) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setProgress(0);
                        progressView.setVisibility(View.VISIBLE);
                    }
                });
                handleProgression = true;
            }
        }

        @Override
        public boolean onCursorNext() {
            if(handleProgression) {
                record++;
                final int newProgression = (int)((record / (double) recordCount) * 100);
                if(newProgression / 5 != lastProgress / 5) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            progressBar.setProgress(newProgression, true);
                        } else {
                            progressBar.setProgress(newProgression);
                        }
                        }
                    });
                    lastProgress = newProgression;
                }
            }
            return !canceled.get();
        }

        @Override
        public void onDeleteCursor() {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressView.setVisibility(View.GONE);
                }
            });
        }
    }
}
