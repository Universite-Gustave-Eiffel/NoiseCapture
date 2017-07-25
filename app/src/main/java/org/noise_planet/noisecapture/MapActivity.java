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
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.JsonWriter;
import android.view.Menu;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;


public class MapActivity extends MainActivity implements MapFragment.MapFragmentAvailableListener {
    public static final String RESULTS_RECORD_ID = "RESULTS_RECORD_ID";
    private MeasurementManager measurementManager;
    private Storage.Record record;
    private boolean validBoundingBox = false;
    private WebViewContent webViewContent = new WebViewContent();

    public static String getColorFromLevel(double spl) {
        if(spl <35) {
            return "#82A6AD";
        }else if(spl <40) {
            return "#A0BABF";
        }else if(spl <45) {
            return "#B8D6D1";
        }else if(spl <50) {
            return "#CEE4CC";
        }else if(spl <55) {
            return "#E2F2BF";
        }else if(spl <60) {
            return "#F3C683";
        }else if(spl <65) {
            return "#E87E4D";
        }else if(spl <70) {
            return "#CD463E";
        }else if(spl <75) {
            return "#A11A4D";
        }else if(spl <80) {
            return "#75085C";
        } else {
            return "#430A4A";
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        initDrawer();

        this.measurementManager = new MeasurementManager(getApplicationContext());
        Intent intent = getIntent();
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

    }

    private MapFragment getMapControler() {
        return (MapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
    }

    // Disable warning as the injected object cannot communicate with the NoiseCapture application
    @SuppressLint("AddJavascriptInterface")
    @Override
    public void onMapFragmentAvailable(MapFragment mapFragment) {
        // addJavascriptInterface
        mapFragment.getWebView().addJavascriptInterface(webViewContent, "androidContent");
        mapFragment.loadUrl("file:///android_asset/html/map_result.html");
    }

    @Override
    public void onPageLoaded(MapFragment mapFragment) {

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        int measureLimitation = getInteger(sharedPref, "summary_settings_map_maxmarker", 0);

        List<MeasurementManager.LeqBatch> measurements = new ArrayList<MeasurementManager.LeqBatch>();
        // TODO Transfer all records through
        // addJavascriptInterface
        if(record != null) {
            measurements = measurementManager.getRecordLocations(record.getId() , true, measureLimitation);
            webViewContent.setSelectedMeasurements(measurements);
        }
        boolean validBoundingBox = measurements.size() > 1;
        for(int idMarker = 0; idMarker < measurements.size(); idMarker++) {
            MeasurementManager.LeqBatch leq = measurements.get(idMarker);
            MapFragment.LatLng position = new MapFragment.LatLng(leq.getLeq().getLatitude(), leq.getLeq().getLongitude());
            mapFragment.addMeasurement(position, getColorFromLevel(leq.computeGlobalLeq()));
        }
        if(validBoundingBox) {
            mapFragment.runJs("map.fitBounds(userMeasurementPoints.getBounds())");
        } else {
            Toast.makeText(getApplicationContext(), getString(R.string.no_gps_results),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    public static final class WebViewContent {
        List<MeasurementManager.LeqBatch> selectedMeasurements;
        List<MeasurementManager.LeqBatch> allMeasurements;

        public void setSelectedMeasurements(List<MeasurementManager.LeqBatch> selectedMeasurements) {
            this.selectedMeasurements = selectedMeasurements;
        }

        public void setAllMeasurements(List<MeasurementManager.LeqBatch> allMeasurements) {
            this.allMeasurements = allMeasurements;
        }

        @JavascriptInterface
        public String getSelectedMeasurementData() {
            return "";
        }
    }
}
