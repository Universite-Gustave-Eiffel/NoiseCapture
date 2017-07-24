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
import android.content.res.Resources;
import android.os.Bundle;
import android.view.Menu;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;


public class MapActivity extends MainActivity implements MapFragment.MapFragmentAvailableListener {
    public static final String RESULTS_RECORD_ID = "RESULTS_RECORD_ID";
    private MeasurementManager measurementManager;
    private Storage.Record record;
    private boolean validBoundingBox = false;

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

    @Override
    public void onMapFragmentAvailable(MapFragment mapFragment) {
        mapFragment.loadUrl("file:///android_asset/html/map_result.html");
    }

    @Override
    public void onPageLoaded(MapFragment mapFragment) {
        List<MeasurementManager.LeqBatch> measurements = new ArrayList<MeasurementManager.LeqBatch>();
        if(record != null) {
            measurements = measurementManager.getRecordLocations(record.getId() , true, 500);
        }
        boolean validBoundingBox = measurements.size() > 1;
        for(int idMarker = 0; idMarker < measurements.size(); idMarker++) {
            MeasurementManager.LeqBatch leq = measurements.get(idMarker);
            MapFragment.LatLng position = new MapFragment.LatLng(leq.getLeq().getLatitude(), leq.getLeq().getLongitude());
            double leqValue = leq.computeGlobalLeq();
            int nc = getNEcatColors(leqValue);    // Choose the color category in function of the sound level
            String htmlColor = String.format("#%06X",
                    (0xFFFFFF & NE_COLORS[nc]));
            mapFragment.addMeasurement(position, htmlColor);
        }
        if(validBoundingBox) {
            mapFragment.runJs("map.flyToBounds(userMeasurementPoints.getBounds(), 18)");
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

}
