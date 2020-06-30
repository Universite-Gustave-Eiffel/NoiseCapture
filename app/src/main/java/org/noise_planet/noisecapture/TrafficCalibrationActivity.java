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
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.noise_planet.noisecapture.util.TrafficNoiseEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;


public class TrafficCalibrationActivity extends MainActivity implements PropertyChangeListener {

    private enum CALIBRATION_STEP {IDLE, MEASUREMENT, PAUSED, END}
    private static final int EXPECTED_NOISE_PEAKS = 15;
    private static final int CACHED_LEQS = 3; // Number of leqs removed when user touch pause button
    // Delay in seconds for computing the number of vehicles passing by the device.
    public static final int CALIBRATION_REFRESH_DELAY = 5;
    private List<Double> leqMax = new ArrayList<>();
    private List<Double> leqMaxCached = new ArrayList<>();
    private List<Double> fastLeq = new ArrayList<>();
    private ProgressBar calibrationProgressBar;
    private ImageButton mainButton;
    private CALIBRATION_STEP calibration_step = CALIBRATION_STEP.IDLE;
    private TextView textStatus;
    private EditText inputSpeed;
    private EditText inputDistance;
    private Handler timeHandler;
    private ProgressHandler progressHandler = new ProgressHandler(this);
    private AudioProcess audioProcess;
    private AtomicBoolean recording = new AtomicBoolean(true);
    private AtomicBoolean canceled = new AtomicBoolean(false);
    private static final Logger LOGGER = LoggerFactory.getLogger(TrafficCalibrationActivity.class);
    private Storage.TrafficCalibrationSession trafficCalibrationSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_traffic_calibration);
        initDrawer();

        calibrationProgressBar = (ProgressBar) findViewById(R.id.progressBar_wait_calibration_recording);
        textStatus = (TextView) findViewById(R.id.textView_recording_state);
        mainButton = findViewById(R.id.mainBtn);
        inputSpeed = (EditText) findViewById(R.id.edit_text_vehicle_speed);
        inputDistance = (EditText) findViewById(R.id.edit_text_distance_vehicle);

        TextView message = findViewById(R.id.calibration_info_message);
        if(message != null) {
            // Activate links
            message.setMovementMethod(LinkMovementMethod.getInstance());
        }
        initCalibration();
    }

    public void onMainButton(View view) {
        switch(calibration_step) {
            case IDLE:
                // Calibration not started
                onCalibrationStart(view);
                break;
            case MEASUREMENT:
                onCalibrationPaused();
                break;
            case PAUSED:
                onCalibrationResume();
                break;
            case END:
                onCalibrationAdd();
                break;
        }
    }

    public void onCalibrationPaused() {
        calibration_step = CALIBRATION_STEP.PAUSED;
        mainButton.setImageResource(R.drawable.pause_pressed);
        textStatus.setText(R.string.measurement_pause);
        calibrationProgressBar.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pause_anim));
        leqMaxCached.clear(); // remove last n measured seconds
    }

    public void onCalibrationResume() {
        calibration_step = CALIBRATION_STEP.MEASUREMENT;
        calibrationProgressBar.clearAnimation();
        mainButton.setImageResource(R.drawable.pause_unpressed);
    }

    @Override
    protected void onPause() {
        super.onPause();
        onCalibrationPaused();
        canceled.set(true);
        recording.set(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        calibration_step = CALIBRATION_STEP.END;
        canceled.set(true);
        recording.set(false);
    }

    private void initCalibration() {
        textStatus.setText(R.string.calibration_status_waiting_for_user_start);
        calibration_step = CALIBRATION_STEP.IDLE;
        canceled.set(true);
        recording.set(false);
        calibrationProgressBar.setProgress(calibrationProgressBar.getMax());
        leqMax.clear();
        leqMaxCached.clear();
    }

    public  void onCalibrationCancel(View view) {
        initCalibration();
    }

    public void onCalibrationStart(View view) {
        InputMethodManager inputMethodManager = (InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);
        if(getCurrentFocus()!=null && inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
        textStatus.setText(getString(R.string.calibration_awaiting_vehicle_passby,0, EXPECTED_NOISE_PEAKS));
        mainButton.setImageResource(R.drawable.pause_unpressed);
        calibration_step = CALIBRATION_STEP.MEASUREMENT;
        calibrationProgressBar.setProgress(0);
        // Link measurement service with gui
        if (checkAndAskPermissions()) {
            // Application have right now all permissions
            initAudioProcess();
        }
        timeHandler = new Handler(Looper.getMainLooper(), progressHandler);
        progressHandler.start(CALIBRATION_REFRESH_DELAY * 1000);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_RECORD_AUDIO_AND_GPS: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initAudioProcess();
                } else {
                    // permission denied
                    // Ask again
                    checkAndAskPermissions();
                }
            }
        }
    }

    private void initAudioProcess() {
        if(audioProcess != null) {
            recording.set(false);
            canceled.set(true);
            recording = new AtomicBoolean(false);
            canceled = new AtomicBoolean(true);
        }
        canceled.set(false);
        recording.set(true);
        audioProcess = new AudioProcess(recording, canceled);
        audioProcess.setDoFastLeq(true);
        audioProcess.setDoOneSecondLeq(false);
        audioProcess.setWeightingA(true);
        audioProcess.setHannWindowOneSecond(false);
        audioProcess.setGain(1);
        audioProcess.getListeners().addPropertyChangeListener(this);
        // Start measurement
        new Thread(audioProcess).start();
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if(calibration_step == CALIBRATION_STEP.MEASUREMENT &&
                AudioProcess.PROP_FAST_LEQ.equals(event.getPropertyName())) {
            // New leq
            AudioProcess.AudioMeasureResult measure =
                    (AudioProcess.AudioMeasureResult) event.getNewValue();
            final double leq = measure.getGlobaldBaValue();
            fastLeq.add(leq);
            if(fastLeq.size() == 8) {
                // Add max lvl
                double lMax = Double.MIN_VALUE;
                for(double val : fastLeq) {
                    lMax = Math.max(lMax, val);
                }
                leqMaxCached.add(lMax);
                while(leqMaxCached.size() > CACHED_LEQS) {
                    leqMax.add(leqMaxCached.remove(0));
                }
                fastLeq.clear();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    public void onCalibrationAdd() {
        trafficCalibrationSession.setEstimatedSpeed(Double.valueOf(inputSpeed.getText().toString()));
        trafficCalibrationSession.setEstimatedDistance(Double.valueOf(inputDistance.getText().toString()));
        MeasurementManager measurementManager = new MeasurementManager(getApplicationContext());
        // Add calibration session
        measurementManager.addTrafficCalibrationSession(trafficCalibrationSession);
        // Load history page
        Intent ics = new Intent(getApplicationContext(), CalibrationHistory.class);
        mDrawerLayout.closeDrawer(mDrawerList);
        startActivity(ics);
        finish();
    }

    private void onEndMeasurement(final Storage.TrafficCalibrationSession data) {
        this.trafficCalibrationSession = data;
        calibration_step = CALIBRATION_STEP.END;
        canceled.set(true);
        recording.set(false);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mainButton.setImageResource(R.drawable.check_circle);
            }
        });
    }

    /**
     * Manage progress timer
     */
    public static final class ProgressHandler implements Handler.Callback {
        private TrafficCalibrationActivity activity;
        private int delay;
        private int lastProgress = 0;

        public ProgressHandler(TrafficCalibrationActivity activity) {
            this.activity = activity;
        }

        public void start(int delay) {
            this.delay = delay;
            activity.timeHandler.sendEmptyMessageDelayed(delay, CALIBRATION_REFRESH_DELAY * 1000);
        }

        @Override
        public boolean handleMessage(Message msg) {
            if(activity.calibration_step == CALIBRATION_STEP.MEASUREMENT) {
                // Time to count vehicles
                TrafficNoiseEstimator trafficNoiseEstimator = new TrafficNoiseEstimator();
                ArrayList<Double> leqs = new ArrayList<>(activity.leqMax);
                double[] laeq = new double[leqs.size()];
                for(int i=0; i < laeq.length; i++) {
                    laeq[i] = leqs.get(i);
                }
                TrafficNoiseEstimator.Estimation estimation = trafficNoiseEstimator.getMedianPeak(laeq);
                double percent = (estimation.numberOfPassby / (double) EXPECTED_NOISE_PEAKS) * 100.0;
                if(lastProgress < (int)percent) {
                    activity.calibrationProgressBar.setProgress((int) percent);
                    lastProgress = (int) percent;
                }
                if(estimation.numberOfPassby >= EXPECTED_NOISE_PEAKS) {
                    activity.textStatus.setText(R.string.calibration_done_vehicle_passby);
                    activity.onEndMeasurement(new Storage.TrafficCalibrationSession(0,
                            estimation.medianPeak, estimation.numberOfPassby,
                            Double.valueOf(activity.inputSpeed.getText().toString()),
                            Double.valueOf(activity.inputDistance.getText().toString()),
                            System.currentTimeMillis()));
                } else {
                    activity.textStatus.setText(activity.getString(R.string.calibration_awaiting_vehicle_passby));
                    activity.timeHandler.sendEmptyMessageDelayed(delay, CALIBRATION_REFRESH_DELAY * 1000);
                }
            } else if(activity.calibration_step == CALIBRATION_STEP.PAUSED) {
                activity.timeHandler.sendEmptyMessageDelayed(delay, 125);
            }
            return true;
        }
    }

}
