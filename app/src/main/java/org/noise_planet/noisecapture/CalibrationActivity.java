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

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.orbisgis.sos.LeqStats;
import org.orbisgis.sos.ThirdOctaveBandsFiltering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Arrays;
import java.util.Locale;


public class CalibrationActivity extends MainActivity implements PropertyChangeListener, SharedPreferences.OnSharedPreferenceChangeListener {
    private enum CALIBRATION_STEP {IDLE, WARMUP, CALIBRATION, END}
    private static int[] freq_choice = {0, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};
    private ProgressBar progressBar_wait_calibration_recording;
    private TextView startButton;
    private TextView applyButton;
    private TextView resetButton;
    private CALIBRATION_STEP calibration_step = CALIBRATION_STEP.IDLE;
    private TextView textStatus;
    private TextView textDeviceLevel;
    private EditText userInput;
    private CheckBox testGainCheckBox;
    private Spinner spinner;
    private Handler timeHandler;
    private int defaultWarmupTime;
    private int defaultCalibrationTime;
    private LeqStats leqStats;
    private static final float DEFAULT_CALIBRATION_LEVEL = 94.f;
    private static final double MINIMAL_VALID_MEASURED_VALUE = 72;
    private static final double MAXIMAL_VALID_MEASURED_VALUE = 102;
    private boolean mIsBound = false;
    private MeasurementService measurementService;
    private static final Logger LOGGER = LoggerFactory.getLogger(CalibrationActivity.class);
    private static final int COUNTDOWN_STEP_MILLISECOND = 125;
    private ProgressHandler progressHandler = new ProgressHandler(this);

    private static final String SETTINGS_CALIBRATION_WARMUP_TIME = "settings_calibration_warmup_time";
    private static final String SETTINGS_CALIBRATION_TIME = "settings_calibration_time";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration);
        initDrawer();
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.registerOnSharedPreferenceChangeListener(this);
        defaultCalibrationTime = getInteger(sharedPref,SETTINGS_CALIBRATION_TIME, 10);
        defaultWarmupTime = getInteger(sharedPref,SETTINGS_CALIBRATION_WARMUP_TIME, 5);

        progressBar_wait_calibration_recording = (ProgressBar) findViewById(R.id.progressBar_wait_calibration_recording);
        applyButton = (TextView) findViewById(R.id.btn_apply);
        applyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onApply();
            }
        });
        textStatus = (TextView) findViewById(R.id.textView_recording_state);
        textDeviceLevel = (TextView) findViewById(R.id.textView_value_SL_i);
        testGainCheckBox = (CheckBox) findViewById(R.id.checkbox_test_gain);
        spinner = (Spinner) findViewById(R.id.spinner_calibration_mode);
        startButton = (TextView) findViewById(R.id.btn_start);
        resetButton = (TextView) findViewById(R.id.btn_reset);
        LinearLayout layout_progress = (LinearLayout) findViewById(R.id.layout_progress);
        userInput = (EditText) findViewById(R.id.edit_text_external_measured);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onCalibrationStart();
            }
        });
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onReset();
            }
        });

        layout_progress.setOnTouchListener(new hiddenCalibrationTouchEvent(this));

        // Load spinner values
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.calibrate_type_list_array, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinner.setAdapter(adapter);
        // Set default value to standard calibration mode (Global)
        spinner.setSelection(0, false);


        initCalibration();
    }

    private static final class hiddenCalibrationTouchEvent implements View.OnTouchListener {
        private CalibrationActivity calibrationActivity;
        private boolean mBooleanIsPressed = false;
        private final Handler handler = new Handler();
        private final Runnable runnable = new Runnable() {
            public void run() {
                if(mBooleanIsPressed) {
                    Intent im = new Intent(calibrationActivity, CalibrationLinearityActivity.class);
                    im.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    calibrationActivity.startActivity(im);
                    calibrationActivity.finish();
                }
            }
        };

        public hiddenCalibrationTouchEvent(CalibrationActivity calibrationActivity) {
            this.calibrationActivity = calibrationActivity;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if(event.getAction() == MotionEvent.ACTION_DOWN) {
                handler.postDelayed(runnable, 2000);
                mBooleanIsPressed = true;
            }

            if(event.getAction() == MotionEvent.ACTION_UP) {
                if(mBooleanIsPressed) {
                    mBooleanIsPressed = false;
                    handler.removeCallbacks(runnable);
                }
            }
            return false;
        }
    }

    private Number getCalibrationReferenceLevel() throws ParseException {
        // Change when https://code.google.com/p/android/issues/detail?id=2626 is fixed
        return Double.valueOf(userInput.getText().toString().trim());
        /*
        String refLevel = userInput.getText().toString().trim();
        NumberFormat format = NumberFormat.getInstance(Locale.getDefault());
        ParsePosition position = new ParsePosition(0);
        Number number = format.parse(refLevel, position);
        if (position.getIndex() != refLevel.length()) {
            // Not user lang, try US
            return Double.valueOf(refLevel);
        } else {
            return number;
        }
        */
    }

    private void onApply() {
        try {
            Number number = getCalibrationReferenceLevel();
            if(number.doubleValue() < MINIMAL_VALID_MEASURED_VALUE || number.doubleValue() > MAXIMAL_VALID_MEASURED_VALUE) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                // Add the buttons
                builder.setPositiveButton(R.string.calibration_save_change, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        CalibrationActivity.this.doApply();
                    }
                });
                builder.setNegativeButton(R.string.calibration_cancel_change, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        CalibrationActivity.this.onReset();
                    }
                });
                // Create the AlertDialog
                AlertDialog dialog = builder.create();
                dialog.setTitle(getString(R.string.calibration_out_bounds_title));
                dialog.setMessage(getString(R.string.calibration_out_bounds,
                        MINIMAL_VALID_MEASURED_VALUE,MAXIMAL_VALID_MEASURED_VALUE));
                dialog.show();
            } else {
                doApply();
            }
        } catch (ParseException ex) {
            LOGGER.error(ex.getLocalizedMessage(), ex);
            onReset();
        }
    }

    private void doApply() {
        try {
            Number number = getCalibrationReferenceLevel();
            double gain = Math.round((number.doubleValue() - leqStats.getLeqMean()) * 100.) / 100.;
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("settings_recording_gain", String.valueOf(gain));
            editor.apply();
            Toast.makeText(getApplicationContext(),
                    getString(R.string.calibrate_done, gain), Toast.LENGTH_LONG).show();
        } catch (ParseException ex) {
            LOGGER.error(ex.getLocalizedMessage(), ex);
        } finally {
            onReset();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(SETTINGS_CALIBRATION_TIME.equals(key)) {
            defaultCalibrationTime = getInteger(sharedPreferences, SETTINGS_CALIBRATION_TIME, 10);
        } else if(SETTINGS_CALIBRATION_WARMUP_TIME.equals(key)) {
            defaultWarmupTime = getInteger(sharedPreferences, SETTINGS_CALIBRATION_WARMUP_TIME, 5);
        }
    }

    private void initCalibration() {
        textStatus.setText(R.string.calibration_status_waiting_for_user_start);
        progressBar_wait_calibration_recording.setProgress(progressBar_wait_calibration_recording.getMax());
        userInput.setText("");
        userInput.setEnabled(false);
        spinner.setEnabled(true);
        textDeviceLevel.setText(R.string.no_valid_dba_value);
        startButton.setEnabled(true);
        applyButton.setEnabled(false);
        resetButton.setEnabled(false);
        testGainCheckBox.setEnabled(true);
        startButton.setText(R.string.calibration_button_start);
    }

    private void onCalibrationStart() {
        if(calibration_step == CALIBRATION_STEP.IDLE) {
            textStatus.setText(R.string.calibration_status_waiting_for_start_timer);
            calibration_step = CALIBRATION_STEP.WARMUP;
            // Link measurement service with gui
            if (checkAndAskPermissions()) {
                // Application have right now all permissions
                doBindService();
            }
            spinner.setEnabled(false);
            startButton.setText(R.string.calibration_button_cancel);
            testGainCheckBox.setEnabled(false);
            timeHandler = new Handler(Looper.getMainLooper(), progressHandler);
            progressHandler.start(defaultWarmupTime * 1000);
        } else {
            calibration_step = CALIBRATION_STEP.IDLE;
            onReset();
        }
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

                    doBindService();
                } else {
                    // permission denied
                    // Ask again
                    checkAndAskPermissions();
                }
            }
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if((calibration_step == CALIBRATION_STEP.CALIBRATION || calibration_step == CALIBRATION_STEP.WARMUP) &&
                AudioProcess.PROP_DELAYED_STANDART_PROCESSING.equals(event.getPropertyName())) {
            // New leq
            AudioProcess.AudioMeasureResult measure =
                    (AudioProcess.AudioMeasureResult) event.getNewValue();
            final double leq;
            // Use global dB value or only the selected frequency band
            if(spinner.getSelectedItemPosition() == 0) {
                leq = measure.getSignalLeq();
            } else {
                int selectFreq = freq_choice[spinner.getSelectedItemPosition()];
                int index = Arrays.binarySearch(measurementService.getAudioProcess().getDelayedCenterFrequency(), selectFreq);
                if(index < 0) {
                    index = Math.min(measure.getLeqs().length -1,  Math.max(0, -index - 1));
                }
                leq = measure.getLeqs()[index];
            }
            if(calibration_step == CALIBRATION_STEP.CALIBRATION) {
                leqStats.addLeq(leq);
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    double leqToShow;
                    if(calibration_step == CALIBRATION_STEP.CALIBRATION) {
                        leqToShow = leqStats.getLeqMean();
                    } else {
                        leqToShow = leq;
                    }
                    textDeviceLevel.setText(
                            String.format(Locale.getDefault(), "%.1f", leqToShow));
                }
            });
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            measurementService = ((MeasurementService.LocalBinder)service).getService();
            if(testGainCheckBox.isChecked()) {
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(CalibrationActivity.this);
                measurementService.setdBGain(
                        getDouble(sharedPref,"settings_recording_gain", 0));
            } else {
                measurementService.setdBGain(0);
            }
            measurementService.addPropertyChangeListener(CalibrationActivity.this);
            if(!measurementService.isRecording()) {
                measurementService.startRecording();
            }
            measurementService.getAudioProcess().setDoFastLeq(false);
            measurementService.getAudioProcess().setDoOneSecondLeq(true);
            measurementService.getAudioProcess().setWeightingA(false);
            measurementService.getAudioProcess().setHannWindowOneSecond(false);
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            measurementService.removePropertyChangeListener(CalibrationActivity.this);
            measurementService = null;
        }
    };

    void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        if(!bindService(new Intent(this, MeasurementService.class), mConnection,
                Context.BIND_AUTO_CREATE)) {
            Toast.makeText(CalibrationActivity.this, R.string.measurement_service_disconnected,
                    Toast.LENGTH_SHORT).show();
        } else {
            mIsBound = true;
        }
    }

    public void onReset() {
        initCalibration();
    }

    void doUnbindService() {
        if (mIsBound) {
            measurementService.removePropertyChangeListener(this);
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    private void onTimerEnd() {
        if(calibration_step == CALIBRATION_STEP.WARMUP) {
            calibration_step = CALIBRATION_STEP.CALIBRATION;
            textStatus.setText(R.string.calibration_status_on);
            // Start calibration
            leqStats = new LeqStats();
            progressHandler.start(defaultCalibrationTime * 1000);
        } else if(calibration_step == CALIBRATION_STEP.CALIBRATION) {
            calibration_step = CALIBRATION_STEP.END;
            textStatus.setText(R.string.calibration_status_end);
            measurementService.stopRecording();
            // Remove measurement service
            doUnbindService();
            // Activate user input
            if(!testGainCheckBox.isChecked()) {
                applyButton.setEnabled(true);
                // Change to default locale when fixed https://code.google.com/p/android/issues/detail?id=2626
                userInput.setText(String.format(Locale.US, "%.1f", DEFAULT_CALIBRATION_LEVEL));
                userInput.setEnabled(true);
            }
            resetButton.setEnabled(true);
        }
    }

    /**
     * Manage progress timer
     */
    private static final class ProgressHandler implements Handler.Callback {
        private CalibrationActivity activity;
        private int delay;
        private long beginTime;

        public ProgressHandler(CalibrationActivity activity) {
            this.activity = activity;
        }

        public void start(int delayMilliseconds) {
            delay = delayMilliseconds;
            beginTime = SystemClock.elapsedRealtime();
            activity.timeHandler.sendEmptyMessageDelayed(0, COUNTDOWN_STEP_MILLISECOND);
        }

        @Override
        public boolean handleMessage(Message msg) {
            long currentTime = SystemClock.elapsedRealtime();
            int newProg = (int)((((beginTime + delay) - currentTime) / (float)delay) *
                    activity.progressBar_wait_calibration_recording.getMax());
            activity.progressBar_wait_calibration_recording.setProgress(newProg);
            if(currentTime < beginTime + delay && activity.calibration_step != CALIBRATION_STEP.IDLE) {
                activity.timeHandler.sendEmptyMessageDelayed(0, COUNTDOWN_STEP_MILLISECOND);
            } else {
                activity.onTimerEnd();
            }
            return true;
        }
    }

}
