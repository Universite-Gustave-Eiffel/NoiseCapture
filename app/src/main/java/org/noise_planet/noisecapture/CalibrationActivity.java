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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.orbisgis.sos.LeqStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;


public class CalibrationActivity extends MainActivity implements PropertyChangeListener, SharedPreferences.OnSharedPreferenceChangeListener {
    private enum CALIBRATION_STEP {IDLE, WARMUP, CALIBRATION, END}
    public static final String CALIBRATION_MODE_SONOMETER = "SONOMETER";
    public static final String CALIBRATION_MODE_CALIBRATOR = "CALIBRATOR";
    public static final String INTENT_CALIBRATION_MODE = "calibrationmode";
    private static final double CALIBRATION_PRECISION_CLASS_STEP = 0.01;
    private static int[] freq_choice = {0, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};
    private ProgressBar progressBar_wait_calibration_recording;
    private TextView startButton;
    private TextView applyButton;
    private TextView resetButton;
    private String calibration_mode;
    private CALIBRATION_STEP calibration_step = CALIBRATION_STEP.IDLE;
    private TextView textStatus;
    private TextView textDeviceLevel;
    private EditText userInput;
    private CheckBox testGainCheckBox;
    private Spinner spinner;
    private Handler timeHandler;
    private ProgressHandler progressHandler = new ProgressHandler(this);
    private int defaultWarmupTime;
    private int defaultCalibrationTime;
    private LeqStats leqStats;
    private static final double MINIMAL_VALID_MEASURED_VALUE = 72;
    private static final double MAXIMAL_VALID_MEASURED_VALUE = 102;
    private static final String DEFAULT_CALIBRATOR_LEVEL = "94";
    private AudioProcess audioProcess;
    private AtomicBoolean recording = new AtomicBoolean(true);
    private AtomicBoolean canceled = new AtomicBoolean(false);
    private static final Logger LOGGER = LoggerFactory.getLogger(CalibrationActivity.class);
    public static final int COUNTDOWN_STEP_MILLISECOND = 125;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if(intent != null && intent.hasExtra(INTENT_CALIBRATION_MODE)) {
            if(CALIBRATION_MODE_SONOMETER.equals(intent.getStringExtra(INTENT_CALIBRATION_MODE))) {
                calibration_mode = CALIBRATION_MODE_SONOMETER;
                setContentView(R.layout.activity_calibration);
            } else {
                setContentView(R.layout.activity_calibration_calibrator);
                calibration_mode = CALIBRATION_MODE_CALIBRATOR;
            }
        }
        initDrawer();
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.registerOnSharedPreferenceChangeListener(this);
        defaultCalibrationTime = getInteger(sharedPref,CalibrationService
                .SETTINGS_CALIBRATION_TIME, 10);
        defaultWarmupTime = getInteger(sharedPref,CalibrationService
                .SETTINGS_CALIBRATION_WARMUP_TIME, 5);

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
        userInput = (EditText) findViewById(R.id.edit_text_vehicle_speed);
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

        layout_progress.setLongClickable(true);
        layout_progress.setOnLongClickListener(new hiddenCalibrationTouchEvent(this));

        // Load spinner values
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.calibrate_type_list_array, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinner.setAdapter(adapter);
        // Set default value to standard calibration mode (Global)
        if(CALIBRATION_MODE_CALIBRATOR.equals(calibration_mode)) {
            // Calibrator set to 1000 Hz
            spinner.setSelection(4, false);
        } else {
            // Sonometer set to Global
            spinner.setSelection(0, false);
        }
        TextView message = findViewById(R.id.calibration_info_message);
        if(message != null) {
            // Activate links
            message.setMovementMethod(LinkMovementMethod.getInstance());
        }
        initCalibration();
    }

    private static final class hiddenCalibrationTouchEvent implements View.OnLongClickListener {
        private CalibrationActivity calibrationActivity;

        public hiddenCalibrationTouchEvent(CalibrationActivity calibrationActivity) {
            this.calibrationActivity = calibrationActivity;
        }


        @Override
        public boolean onLongClick(View v) {
            Intent im = new Intent(calibrationActivity, CalibrationLinearityActivity.class);
            im.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            calibrationActivity.startActivity(im);
            calibrationActivity.finish();
            return true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        canceled.set(true);
        recording.set(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        canceled.set(true);
        recording.set(false);
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
            editor.putString("settings_calibration_method", String.valueOf(CALIBRATION_MODE_CALIBRATOR.equals(calibration_mode) ? Storage.Record.CALIBRATION_METHODS.Calibrator.ordinal() : Storage.Record.CALIBRATION_METHODS.Reference.ordinal()));
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
        if(CalibrationService.SETTINGS_CALIBRATION_TIME.equals(key)) {
            defaultCalibrationTime = getInteger(sharedPreferences, CalibrationService
                    .SETTINGS_CALIBRATION_TIME, 10);
        } else if(CalibrationService.SETTINGS_CALIBRATION_WARMUP_TIME.equals(key)) {
            defaultWarmupTime = getInteger(sharedPreferences, CalibrationService
                    .SETTINGS_CALIBRATION_WARMUP_TIME, 5);
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
                initAudioProcess();
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
        canceled.set(false);
        recording.set(true);
        audioProcess = new AudioProcess(recording, canceled);
        audioProcess.setDoFastLeq(false);
        audioProcess.setDoOneSecondLeq(true);
        audioProcess.setWeightingA(true);
        audioProcess.setHannWindowOneSecond(true);
        if(testGainCheckBox.isChecked()) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(CalibrationActivity.this);
            audioProcess.setGain((float)Math.pow(10, getDouble(sharedPref,"settings_recording_gain", 0) / 20));
        } else {
            audioProcess.setGain(1);
        }
        audioProcess.getListeners().addPropertyChangeListener(this);

        // Start measurement
        new Thread(audioProcess).start();
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if((calibration_step == CALIBRATION_STEP.CALIBRATION || calibration_step == CALIBRATION_STEP.WARMUP) &&
                AudioProcess.PROP_SLOW_LEQ.equals(event.getPropertyName())) {
            // New leq
            AudioProcess.AudioMeasureResult measure =
                    (AudioProcess.AudioMeasureResult) event.getNewValue();
            final double leq;
            // Use global dB value or only the selected frequency band
            if(spinner.getSelectedItemPosition() == 0) {
                leq = measure.getGlobaldBaValue();
            } else {
                int selectFreq = freq_choice[spinner.getSelectedItemPosition()];
                int index = Arrays.binarySearch(audioProcess.getDelayedCenterFrequency(), selectFreq);
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
                        leqToShow = leqStats.computeLeqOccurrences(null).getLa50();
                    } else {
                        leqToShow = leq;
                    }
                    textDeviceLevel.setText(
                            String.format(Locale.getDefault(), "%.1f", leqToShow));
                }
            });
        }
    }


    public void onReset() {
        initCalibration();
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
            leqStats = new LeqStats(CALIBRATION_PRECISION_CLASS_STEP);
            progressHandler.start(defaultCalibrationTime * 1000);
        } else if(calibration_step == CALIBRATION_STEP.CALIBRATION) {
            calibration_step = CALIBRATION_STEP.END;
            textStatus.setText(R.string.calibration_status_end);
            recording.set(false);
            audioProcess = null;
            // Activate user input
            if(!testGainCheckBox.isChecked()) {
                applyButton.setEnabled(true);
                // Change to default locale when fixed https://code.google.com/p/android/issues/detail?id=2626
                if(CALIBRATION_MODE_CALIBRATOR.equals(calibration_mode)) {
                    userInput.setText(DEFAULT_CALIBRATOR_LEVEL);
                } else {
                    userInput.setText(String.format(Locale.US, "%.1f", leqStats.getLeqMean()));
                }
                userInput.setEnabled(true);
            }
            resetButton.setEnabled(true);
        }
    }

    /**
     * Manage progress timer
     */
    public static final class ProgressHandler implements Handler.Callback {
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
