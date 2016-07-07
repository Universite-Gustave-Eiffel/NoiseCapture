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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.ScatterChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.ScatterData;
import com.github.mikephil.charting.data.ScatterDataSet;
import com.github.mikephil.charting.interfaces.datasets.IScatterDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.orbisgis.sos.FFTSignalProcessing;
import org.orbisgis.sos.LeqStats;
import org.orbisgis.sos.ThirdOctaveBandsFiltering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;


public class CalibrationLinearityActivity extends MainActivity implements PropertyChangeListener, SharedPreferences.OnSharedPreferenceChangeListener {
    private enum CALIBRATION_STEP {IDLE, WARMUP, CALIBRATION, END}
    private int splLoop = 0;
    private double splBackroundNoise = 0;
    private static final int DB_STEP = 6;
    private double whiteNoisedB = 0;
    private ProgressBar progressBar_wait_calibration_recording;
    private Button startButton;
    private Button applyButton;
    private ScatterChart scatterChart;
    private Button resetButton;
    private CALIBRATION_STEP calibration_step = CALIBRATION_STEP.IDLE;
    private TextView textStatus;
    private TextView textDeviceLevel;
    private CheckBox testGainCheckBox;
    private Handler timeHandler;
    private int defaultWarmupTime;
    private int defaultCalibrationTime;
    private LeqStats leqStats;
    private List<LinearCalibrationResult> freqLeqStats = new ArrayList<>();
    private boolean mIsBound = false;
    private MeasurementService measurementService;
    private static final Logger LOGGER = LoggerFactory.getLogger(CalibrationLinearityActivity.class);
    private static final int COUNTDOWN_STEP_MILLISECOND = 125;
    private ProgressHandler progressHandler = new ProgressHandler(this);

    private static final String SETTINGS_CALIBRATION_WARMUP_TIME = "settings_calibration_warmup_time";
    private static final String SETTINGS_CALIBRATION_TIME = "settings_calibration_time";

    private AudioTrack audioTrack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration_linearity);
        initDrawer();
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.registerOnSharedPreferenceChangeListener(this);
        defaultCalibrationTime = getInteger(sharedPref,SETTINGS_CALIBRATION_TIME, 10);
        defaultWarmupTime = getInteger(sharedPref,SETTINGS_CALIBRATION_WARMUP_TIME, 5);

        progressBar_wait_calibration_recording = (ProgressBar) findViewById(R.id.progressBar_wait_calibration_recording);
        applyButton = (Button) findViewById(R.id.btn_apply);
        applyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onApply();
            }
        });
        textStatus = (TextView) findViewById(R.id.textView_recording_state);
        textDeviceLevel = (TextView) findViewById(R.id.textView_value_SL_i);
        testGainCheckBox = (CheckBox) findViewById(R.id.checkbox_test_gain);
        scatterChart = (ScatterChart) findViewById(R.id.autoCalibrationChart);
        startButton = (Button) findViewById(R.id.btn_start);
        resetButton = (Button) findViewById(R.id.btn_reset);
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
        initCalibration();
        initGraph();
    }

    private void initGraph() {

        scatterChart.setDescription("");

        scatterChart.setDrawGridBackground(false);

        scatterChart.setTouchEnabled(true);

        // enable scaling and dragging
        scatterChart.setDragEnabled(false);
        scatterChart.setScaleEnabled(false);

        scatterChart.setMaxVisibleValueCount(200);
        scatterChart.setPinchZoom(false);

        Legend l = scatterChart.getLegend();
        l.setPosition(Legend.LegendPosition.RIGHT_OF_CHART);
        l.setTextColor(Color.WHITE);

        YAxis yl = scatterChart.getAxisLeft();
        yl.setAxisMinValue(45f);
        yl.setAxisMaxValue(110f);
        yl.setTextColor(Color.WHITE);
        yl.setGridColor(Color.WHITE);
        scatterChart.getAxisRight().setEnabled(false);

        XAxis xl = scatterChart.getXAxis();
        xl.setDrawGridLines(false);
        xl.setTextColor(Color.WHITE);
        xl.setGridColor(Color.WHITE);
    }

    private void onApply() {
        try {
            // TODO
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
        } else if("settings_recording_gain".equals(key) && measurementService != null) {
            measurementService.setdBGain(getDouble(sharedPreferences, key, 0));
        }
    }

    private void initCalibration() {
        textStatus.setText(R.string.calibration_status_waiting_for_user_start);
        progressBar_wait_calibration_recording.setProgress(progressBar_wait_calibration_recording.getMax());
        textDeviceLevel.setText(R.string.no_valid_dba_value);
        startButton.setEnabled(true);
        applyButton.setEnabled(false);
        resetButton.setEnabled(false);
        testGainCheckBox.setEnabled(true);
        splBackroundNoise = 0;
        splLoop = 0;
        calibration_step = CALIBRATION_STEP.IDLE;
        freqLeqStats = new ArrayList<>();
    }

    private void onCalibrationStart() {
        textStatus.setText(R.string.calibration_status_waiting_for_start_timer);
        calibration_step = CALIBRATION_STEP.WARMUP;
        // Link measurement service with gui
        if(checkAndAskPermissions()) {
            // Application have right now all permissions
            doBindService();
        }
        startButton.setEnabled(false);
        testGainCheckBox.setEnabled(false);
        timeHandler = new Handler(Looper.getMainLooper(), progressHandler);
        progressHandler.start(defaultWarmupTime * 1000);
    }

    private void playNewTrack() {
        double rms = dbToRms(99 - (splLoop++) * DB_STEP);
        short[] data = makeWhiteNoiseSignal(44100, rms);
        double[] fftCenterFreq = FFTSignalProcessing.computeFFTCenterFrequency(AudioProcess.REALTIME_SAMPLE_RATE_LIMITATION);
        FFTSignalProcessing fftSignalProcessing = new FFTSignalProcessing(44100, fftCenterFreq, 44100);
        fftSignalProcessing.addSample(data);
        whiteNoisedB = fftSignalProcessing.computeGlobalLeq();
        freqLeqStats.add(new LinearCalibrationResult(fftSignalProcessing.processSample(true, false, false)));
        LOGGER.info("Emit white noise of "+whiteNoisedB+" dB");
        audioTrack = new AudioTrack(AudioManager.STREAM_RING, 44100, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,data.length * (Short.SIZE / 8), AudioTrack.MODE_STATIC);
        audioTrack.setLoopPoints(0, audioTrack.write(data, 0, data.length), -1);
        audioTrack.play();
    }

    private double dbToRms(double db) {
        return (Math.pow(10, db / 20.)/(Math.pow(10, 90./20.))) * 2500;
    }

    private short[] makeWhiteNoiseSignal(int sampleRate, double powerRMS) {
        // Make signal
        double powerPeak = powerRMS * Math.sqrt(2);
        short[] signal = new short[sampleRate * 2];
        for (int s = 0; s < sampleRate * 2; s++) {
            signal[s] = (short)(powerPeak * ((Math.random() - 0.5) * 2));
        }
        return signal;
    }
    private short[] makeSignal(int sampleRate, int signalFrequency, double powerRMS) {
        // Make signal
        double powerPeak = powerRMS * Math.sqrt(2);
        short[] signal = new short[sampleRate * 2];
        for (int s = 0; s < sampleRate * 2; s++) {
            double t = s * (1 / (double) sampleRate);
            signal[s] = (short)(Math.sin(2 * Math.PI * signalFrequency * t) * (powerPeak));
        }
        return signal;
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

    private void updateScatterChart() {
        if(freqLeqStats.isEmpty()) {
            return;
        }
        float YMin = Float.MAX_VALUE;
        float YMax = Float.MIN_VALUE;
        float XMin = Float.MAX_VALUE;
        float XMax = Float.MIN_VALUE;

        ArrayList<IScatterDataSet> dataSets = new ArrayList<IScatterDataSet>();

        // Frequency count, one dataset by frequency
        int dataSetCount = freqLeqStats.get(freqLeqStats.size() - 1).whiteNoiseLevel.getdBaLevels().length;

        Set<Integer> whiteNoiseValuesSet = new TreeSet<Integer>();
        // Read all white noise values for indexing before usage
        for(LinearCalibrationResult result : freqLeqStats) {
            for(float dbLevel : result.whiteNoiseLevel.getdBaLevels()) {
                whiteNoiseValuesSet.add((int)dbLevel);
            }
        }
        // Convert into ordered list
        int[] whiteNoiseValues = new int[whiteNoiseValuesSet.size()];

        ArrayList<String> xVals = new ArrayList<String>();
        int i = 0;
        for (int dbValue : whiteNoiseValuesSet) {
            whiteNoiseValues[i++] = dbValue;
            xVals.add(String.valueOf(dbValue));
            XMax = Math.max(XMax, dbValue);
            XMin = Math.min(XMin, dbValue);
        }

        for(int freqId = 0; freqId < dataSetCount; freqId += dataSetCount / 5.) {
            ArrayList<Entry> yMeasure = new ArrayList<Entry>();
            for(LinearCalibrationResult result : freqLeqStats) {
                float dbLevel = (float)result.measure[freqId].getLeqMean();
                int whiteNoise = (int)result.whiteNoiseLevel.getdBaLevels()[freqId];
                YMax = Math.max(YMax, dbLevel);
                YMin = Math.min(YMin, dbLevel);
                yMeasure.add(new Entry(dbLevel, Arrays.binarySearch(whiteNoiseValues, whiteNoise)));
            }
            ScatterDataSet freqSet = new ScatterDataSet(yMeasure,
                    Spectrogram.formatFrequency((int)ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[freqId]));
            freqSet.setScatterShape(ScatterChart.ScatterShape.CIRCLE);
            freqSet.setColor(ColorTemplate.COLORFUL_COLORS[freqId % ColorTemplate.COLORFUL_COLORS.length]);
            freqSet.setScatterShapeSize(8f);
            freqSet.setValueTextColor(Color.WHITE);
            freqSet.setDrawValues(false);
            dataSets.add(freqSet);
        }

        // create a data object with the datasets
        ScatterData data = new ScatterData(xVals, dataSets);

        scatterChart.setData(data);
        YAxis yl = scatterChart.getAxisLeft();
        yl.setAxisMinValue(YMin - 3);
        yl.setAxisMaxValue(YMax + 3);
        scatterChart.invalidate();
    }

    /**
     * @return Pearson's product-moment correlation coefficients for the measured data
     */
    private double[] computePearson() {
        if(freqLeqStats.size() < 3) {
            return null;
        }
        // Frequency count, one dataset by frequency
        int dataSetCount = freqLeqStats.get(freqLeqStats.size() - 1).whiteNoiseLevel.getdBaLevels().length;
        double[] pearsonCoefficient = new double[dataSetCount];

        PearsonsCorrelation pearsonsCorrelation = new PearsonsCorrelation();


        for(int freqId = 0; freqId < dataSetCount; freqId++) {
            double[] xValues = new double[freqLeqStats.size()];
            double[] yValues = new double[freqLeqStats.size()];
            int idStep = 0;
            for(LinearCalibrationResult result : freqLeqStats) {
                double dbLevel = result.measure[freqId].getLeqMean();
                double whiteNoise = result.whiteNoiseLevel.getdBaLevels()[freqId];
                xValues[idStep] = whiteNoise;
                yValues[idStep] = dbLevel;
            }
            pearsonCoefficient[freqId] = pearsonsCorrelation.correlation(xValues, yValues);
        }
        return pearsonCoefficient;
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if((calibration_step == CALIBRATION_STEP.CALIBRATION || calibration_step == CALIBRATION_STEP.WARMUP)  &&
                AudioProcess.PROP_DELAYED_STANDART_PROCESSING.equals(event.getPropertyName())) {
            // New leq
            AudioProcess.AudioMeasureResult measure =
                    (AudioProcess.AudioMeasureResult) event.getNewValue();
            final double leq;
            // Use global dB value or only the selected frequency band
            leq = measure.getSignalLeq();
            if(calibration_step == CALIBRATION_STEP.CALIBRATION) {
                leqStats.addLeq(leq);
                if(!freqLeqStats.isEmpty() && splBackroundNoise != 0) {
                    freqLeqStats.get(freqLeqStats.size() - 1).pushMeasure(measure.getResult());
                }
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
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(CalibrationLinearityActivity.this);
            measurementService.setdBGain(
                    getDouble(sharedPref,"settings_recording_gain", 0));
            measurementService.addPropertyChangeListener(CalibrationLinearityActivity.this);
            if(!measurementService.isRecording()) {
                measurementService.startRecording();
            }
            measurementService.getAudioProcess().setDoFastLeq(false);
            measurementService.getAudioProcess().setDoOneSecondLeq(true);
            measurementService.getAudioProcess().setWeightingA(false);
            measurementService.getAudioProcess().setHanningWindowOneSecond(true);
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            measurementService.removePropertyChangeListener(CalibrationLinearityActivity.this);
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
            Toast.makeText(CalibrationLinearityActivity.this, R.string.measurement_service_disconnected,
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
    protected void onPause() {
        super.onPause();
        if(audioTrack != null) {
            audioTrack.stop();
            initCalibration();
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
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(splBackroundNoise == 0) {
                        textStatus.setText(R.string.calibration_status_background_noise);
                    } else {
                        textStatus.setText(getString(R.string.calibration_linear_status_on, whiteNoisedB));
                    }
                }
            });
            // Start calibration
            leqStats = new LeqStats();
            progressHandler.start(defaultCalibrationTime * 1000);
            calibration_step = CALIBRATION_STEP.CALIBRATION;
        } else if(calibration_step == CALIBRATION_STEP.CALIBRATION) {
            if(splBackroundNoise != 0) {
                if (leqStats.getLeqMean() < splBackroundNoise + 3 ) {
                    // Almost reach the background noise, stop calibration
                    calibration_step = CALIBRATION_STEP.END;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textDeviceLevel.setText(R.string.no_valid_dba_value);
                            textStatus.setText(R.string.calibration_status_end);
                            updateScatterChart();
                        }
                    });
                    measurementService.stopRecording();
                    // Remove measurement service
                    doUnbindService();
                    // Stop playing sound
                    audioTrack.stop();
                    // Activate user input
                    if(!testGainCheckBox.isChecked()) {
                        applyButton.setEnabled(true);
                    }
                    resetButton.setEnabled(true);
                } else {
                    calibration_step = CALIBRATION_STEP.WARMUP;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textDeviceLevel.setText(R.string.no_valid_dba_value);
                            textStatus.setText(getString(R.string.calibration_status_waiting_for_start_timer));
                            updateScatterChart();
                        }
                    });
                    audioTrack.stop();
                    playNewTrack();
                    progressHandler.start(defaultWarmupTime * 1000);
                }
            } else {
                // End of calibration of background noise
                calibration_step = CALIBRATION_STEP.WARMUP;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textDeviceLevel.setText(R.string.no_valid_dba_value);
                        textStatus.setText(getString(R.string.calibration_status_waiting_for_start_timer));
                        updateScatterChart();
                    }
                });
                playNewTrack();
                splBackroundNoise = leqStats.getLeqMean();
                progressHandler.start(defaultWarmupTime * 1000);
            }
        }
    }

    /**
     * Manage progress timer
     */
    private static final class ProgressHandler implements Handler.Callback {
        private CalibrationLinearityActivity activity;
        private int delay;
        private long beginTime;

        public ProgressHandler(CalibrationLinearityActivity activity) {
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
            if(currentTime < beginTime + delay) {
                activity.timeHandler.sendEmptyMessageDelayed(0, COUNTDOWN_STEP_MILLISECOND);
            } else {
                activity.onTimerEnd();
            }
            return true;
        }
    }


    private static final class LinearCalibrationResult {
        private LeqStats[] measure;
        private FFTSignalProcessing.ProcessingResult whiteNoiseLevel;

        public LinearCalibrationResult(FFTSignalProcessing.ProcessingResult whiteNoiseLevel) {
            this.whiteNoiseLevel = whiteNoiseLevel;

        }

        public void pushMeasure(FFTSignalProcessing.ProcessingResult measure) {
            float[] measureLevels = measure.getdBaLevels();
            if(this.measure == null) {
                this.measure = new LeqStats[measure.getdBaLevels().length];
                for(int idFreq = 0; idFreq < this.measure.length; idFreq++) {
                    this.measure[idFreq] = new LeqStats();
                }
            }
            for(int idFreq = 0; idFreq < this.measure.length; idFreq++) {
                this.measure[idFreq].addLeq(measureLevels[idFreq]);
            }
        }
    }
}
