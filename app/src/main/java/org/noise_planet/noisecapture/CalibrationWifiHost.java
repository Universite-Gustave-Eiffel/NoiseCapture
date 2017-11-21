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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.noise_planet.noisecapture.CalibrationService.CALIBRATION_STATE.*;


public class CalibrationWifiHost extends MainActivity implements PropertyChangeListener {
    private boolean mIsBound = false;
    private CalibrationService calibrationService;
    private TextView textDeviceLevel;
    private TextView startButton;
    private ProgressBar progressBar_wait_calibration_recording;
    private TextView textStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration_wifi_host);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        initDrawer();

        progressBar_wait_calibration_recording = (ProgressBar) findViewById(R.id.progressBar_wait_calibration_recording);
        textDeviceLevel = (TextView) findViewById(R.id.spl_ref_measured);
        textStatus = (TextView) findViewById(R.id.calibration_state);
        startButton = (TextView) findViewById(R.id.btn_start);

        doBindService();
    }

    @Override
    protected void onResume() {
        doBindService();
        super.onPostResume();    }

    @Override
    protected void onPause() {
        // Disconnect listener from measurement
        doUnbindService();
        super.onPause();
    }

    void doUnbindService() {
        if (mIsBound) {
            if(calibrationService != null) {
                calibrationService.removePropertyChangeListener(this);
            }
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        Intent intent = new Intent(this, CalibrationService.class);
        intent.putExtra(CalibrationService.EXTRA_HOST, 1);
        if(bindService(intent, mConnection, Context.BIND_AUTO_CREATE)) {
            mIsBound = true;
        }
    }

    private void initCalibration() {
        textStatus.setText(R.string.calibration_status_waiting_for_user_start);
        textDeviceLevel.setText(R.string.no_valid_dba_value);
        startButton.setEnabled(calibrationService.getState() == AWAITING_START);
        startButton.setText(R.string.calibration_button_start);
    }

    public void onStartCalibration(View v) {
        startButton.setEnabled(false);
        textStatus.setText(R.string.calibration_status_waiting_for_start_timer);
        if(calibrationService.getState() == AWAITING_START) {
            textStatus.setText(R.string.calibration_status_waiting_for_start_timer);
            // Link measurement service with gui
            if (checkAndAskPermissions()) {
                // Application have right now all permissions
                calibrationService.startCalibration();
            }
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if(AudioProcess.PROP_DELAYED_STANDART_PROCESSING.equals(event.getPropertyName()) &&
                (CalibrationService.CALIBRATION_STATE.CALIBRATION.equals(calibrationService
                        .getState()) || CalibrationService.CALIBRATION_STATE.WARMUP.equals
                        (calibrationService
                        .getState()))){
            // New leq
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    textDeviceLevel.setText(
                            String.format(Locale.getDefault(), "%.1f", calibrationService.getleq()));
                }
            });
        } else if(CalibrationService.PROP_CALIBRATION_STATE.equals(event.getPropertyName())) {
            // Calibration service state change, inform user
            CalibrationService.CALIBRATION_STATE newState =
                    (CalibrationService.CALIBRATION_STATE)event.getNewValue();
            // Change state of buttons
            switch (newState) {
                case AWAITING_START:
                    startButton.setEnabled(true);
                    break;
                default:
                    startButton.setEnabled(false);
            }
        } else if(CalibrationService.PROP_CALIBRATION_PROGRESSION.equals(event.getPropertyName())) {
            progressBar_wait_calibration_recording.setProgress((Integer)event.getNewValue());
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            calibrationService = ((CalibrationService.LocalBinder)service).getService();
            calibrationService.addPropertyChangeListener(CalibrationWifiHost.this);
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            calibrationService.removePropertyChangeListener(CalibrationWifiHost.this);
            calibrationService = null;
        }
    };

    @Override
    protected void onRestart() {
        super.onRestart();
        doBindService();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Disconnect listener from measurement
        doUnbindService();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(calibrationService != null) {
            // Disconnect listener from measurement
            doUnbindService();
        }
    }

}
