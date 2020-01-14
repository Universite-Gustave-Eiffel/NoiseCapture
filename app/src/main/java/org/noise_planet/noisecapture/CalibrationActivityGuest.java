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
import android.os.Bundle;
import android.os.IBinder;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class CalibrationActivityGuest extends MainActivity implements PropertyChangeListener {

    private boolean mIsBound = false;
    private LinearLayout pitchColorBar;
    private CalibrationService calibrationService;
    private TextView textStatus;
    private TextView textReferenceLevel;
    private TextView textMeasurementLevel;
    private ProgressBar progressBar_wait_calibration_recording;
    // Helper to switch pitch color using blue/yellow swapping
    private AtomicBoolean pairPitch = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration_guest);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        initDrawer();

        progressBar_wait_calibration_recording = (ProgressBar) findViewById(R.id.progressBar_wait_calibration_recording);
        textStatus = (TextView) findViewById(R.id.calibration_state);
        textMeasurementLevel = (TextView) findViewById(R.id.spl_measured);
        textReferenceLevel = (TextView) findViewById(R.id.spl_ref_measured);
        pitchColorBar = (LinearLayout) findViewById(R.id.pitch_notification);

        if(checkAndAskPermissions()) {
            doBindService();
        }
    }

    @Override
    public void propertyChange(final PropertyChangeEvent event) {
        if(CalibrationService.PROP_CALIBRATION_STATE.equals(event.getPropertyName())) {
            // Calibration service state change, inform user
            final CalibrationService.CALIBRATION_STATE newState =
                    (CalibrationService.CALIBRATION_STATE)event.getNewValue();
            // Change state of buttons
            if(!(newState == CalibrationService.CALIBRATION_STATE.WARMUP || newState ==
                    CalibrationService.CALIBRATION_STATE.CALIBRATION)) {
                progressBar_wait_calibration_recording.setProgress(0);
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // Change state of buttons
                    switch (newState) {
                        case AWAITING_START:
                            textStatus.setText(R.string.calibration_status_end);
                            break;
                        case WARMUP:
                            textStatus.setText(R.string.calibration_status_waiting_for_start_timer);
                            break;
                        case CALIBRATION:
                            textStatus.setText(R.string.calibration_status_on);
                            break;
                        case AWAITING_FOR_APPLY_OR_RESTART:
                            textStatus.setText(R.string.calibration_status_receive_reference);
                            break;
                    }
                }});
        } else if(CalibrationService.PROP_CALIBRATION_PROGRESSION.equals(event.getPropertyName())) {
            progressBar_wait_calibration_recording.setProgress((Integer)event.getNewValue());
        } else if(AudioProcess.PROP_SLOW_LEQ.equals(event.getPropertyName()) &&
                (CalibrationService.CALIBRATION_STATE.CALIBRATION.equals(calibrationService
                        .getState()) || CalibrationService.CALIBRATION_STATE.WARMUP.equals
                        (calibrationService
                                .getState()))){
            // New leq
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    double level;
                    if(CalibrationService.CALIBRATION_STATE.CALIBRATION.equals(calibrationService
                            .getState())) {
                        level = calibrationService.getleq();
                    } else {
                        level = ((AudioProcess.AudioMeasureResult)event.getNewValue()).getGlobaldBaValue();
                    }
                    textMeasurementLevel.setText(
                            String.format(Locale.getDefault(), "%.1f", level));
                }
            });
        } else if(CalibrationService.PROP_CALIBRATION_REF_LEVEL.equals(event.getPropertyName())) {
            // This device has been calibrated
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    float referenceLeq = (float)event.getNewValue();
                    textReferenceLevel.setText(
                            String.format(Locale.getDefault(), "%.1f", referenceLeq));
                    double gain = Math.round((referenceLeq - calibrationService.getleq()) * 100.) / 100.;
                    Toast.makeText(getApplicationContext(),
                            getString(R.string.calibrate_done, gain), Toast.LENGTH_LONG).show();
                }
            });
        } else if(CalibrationService.PROP_CALIBRATION_RECEIVE_PITCH.equals(event.getPropertyName())) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(pairPitch.getAndSet(!pairPitch.get())) {
                        pitchColorBar.setBackgroundResource(R.drawable.round_corner_opaque_yellow);
                    } else {
                        pitchColorBar.setBackgroundResource(R.drawable.round_corner_opaque_blue);
                    }
                }
            });
        } else if(CalibrationService.PROP_CALIBRATION_SEND_MESSAGE.equals(event.getPropertyName())) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    pitchColorBar.setBackgroundResource(R.drawable.round_corner_opaque_green);
                }
            });
        } else if(CalibrationService.PROP_CALIBRATION_NEW_MESSAGE.equals(event.getPropertyName())) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    pitchColorBar.setBackgroundResource(R.drawable.round_corner_opaque_green);

                }
            });
        } else if(CalibrationService.PROP_CALIBRATION_RECEIVE_ERROR.equals(event.getPropertyName())) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    pitchColorBar.setBackgroundResource(R.drawable.round_corner_opaque_red);
                }
            });
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            calibrationService = ((CalibrationService.LocalBinder)service).getService();
            calibrationService.addPropertyChangeListener(CalibrationActivityGuest.this);
            calibrationService.startCalibration();
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            calibrationService.removePropertyChangeListener(CalibrationActivityGuest.this);
            calibrationService = null;
        }
    };


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
        intent.putExtra(CalibrationService.EXTRA_HOST, 0);
        if(bindService(intent, mConnection, Context.BIND_AUTO_CREATE)) {
            mIsBound = true;
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        doBindService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        doBindService();
        super.onPostResume();
    }

    @Override
    protected void onPause() {
        // Disconnect listener from measurement
        doUnbindService();
        super.onPause();
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
