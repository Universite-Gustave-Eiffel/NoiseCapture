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
import android.os.Bundle;
import android.view.View;

public class CalibrationMenu extends MainActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration_menu);
        initDrawer();
    }

    public void onManualCalibrationSonometer(View view) {
        Intent ics = new Intent(getApplicationContext(), CalibrationActivity.class);
        ics.putExtra(CalibrationActivity.INTENT_CALIBRATION_MODE, CalibrationActivity.CALIBRATION_MODE_SONOMETER);
        mDrawerLayout.closeDrawer(mDrawerList);
        startActivity(ics);
    }

    public void onManualCalibrationCalibrator(View view) {
        Intent ics = new Intent(getApplicationContext(), CalibrationActivity.class);
        ics.putExtra(CalibrationActivity.INTENT_CALIBRATION_MODE, CalibrationActivity.CALIBRATION_MODE_CALIBRATOR);
        mDrawerLayout.closeDrawer(mDrawerList);
        startActivity(ics);
    }

    public void onAutoCalibration(View view) {
        Intent ics = new Intent(getApplicationContext(), CalibrationAutoMenu.class);
        mDrawerLayout.closeDrawer(mDrawerList);
        startActivity(ics);
    }

    public void onTrafficCalibration(View view) {
        Intent ics = new Intent(getApplicationContext(), CalibrationHistory.class);
        mDrawerLayout.closeDrawer(mDrawerList);
        startActivity(ics);
    }
}
