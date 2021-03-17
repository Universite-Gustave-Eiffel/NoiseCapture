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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.util.Date;
import java.util.UUID;


public class Start extends Activity {
    private static final Logger LOGGER = LoggerFactory.getLogger(Start.class);
    private Thread thread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        // Upgrade DB if necessary
        SQLiteDatabase database = new Storage(this).getWritableDatabase();
        database.close();
        // If first start then create a unique identifier for this install
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        if(!sharedPref.contains(MeasurementExport.PROP_UUID)) {
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString(MeasurementExport.PROP_UUID, UUID.randomUUID().toString());
            editor.apply();
        }
    // read app version name
    try {
        TextView versionText = (TextView) findViewById(R.id.textView_appversion);
        versionText.setText(MainActivity.getVersionString(this));
    } catch (PackageManager.NameNotFoundException ex) {
        LOGGER.error(ex.getLocalizedMessage(), ex);
    }

    thread=  new Thread(){
        @Override
        public void run(){
            try {
                synchronized(this){
                    wait(3000);
                }
            }
            catch(InterruptedException ex){
            }

            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(Start.this);
            if(!sharedPref.getBoolean(PrivacyPolicyActivity.PROP_POLICY_AGREED, false)) {
                Intent i = new Intent(getApplicationContext(), PrivacyPolicyActivity.class);
                startActivity(i);
                finish();
            } else if (!sharedPref.getBoolean(LocalisationPolicyActivity.PROP_POLICY_READ, false)) {
                Intent i = new Intent(getApplicationContext(), LocalisationPolicyActivity.class);
                startActivity(i);
                finish();
            } else {
                Intent i = new Intent(getApplicationContext(), MeasurementActivity.class);
                startActivity(i);
                finish();
            }
        }
    };

    thread.start();
}

    @Override
    public boolean onTouchEvent(MotionEvent evt)
    {
        if(evt.getAction() == MotionEvent.ACTION_DOWN)
        {
            synchronized(thread){
                thread.notifyAll();
            }
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_start, menu);
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
