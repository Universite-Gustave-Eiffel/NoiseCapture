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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import com.google.android.material.tabs.TabLayout;
import androidx.viewpager.widget.ViewPager;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.ScatterChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.ScatterData;
import com.github.mikephil.charting.data.ScatterDataSet;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;


public class CalibrationLinearityActivity extends MainActivity implements PropertyChangeListener, SharedPreferences.OnSharedPreferenceChangeListener,ViewPager.OnPageChangeListener {
    private enum CALIBRATION_STEP {IDLE, WARMUP, CALIBRATION, END}
    private int splLoop = 0;
    private double splBackroundNoise = 0;
    private static final int DB_STEP = 3;
    private double whiteNoisedB = 0;
    private ProgressBar progressBar_wait_calibration_recording;
    private TextView startButton;
    private TextView applyButton;
    private TextView resetButton;
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
    private TabLayout tabLayout;

    private AudioProcess audioProcess;
    private AtomicBoolean recording = new AtomicBoolean(true);
    private AtomicBoolean canceled = new AtomicBoolean(false);

    private static final Logger LOGGER = LoggerFactory.getLogger(CalibrationLinearityActivity.class);
    private static final int COUNTDOWN_STEP_MILLISECOND = 125;
    private ProgressHandler progressHandler = new ProgressHandler(this);
    private Set<Integer> selectedFrequencies = new HashSet<>(Arrays.asList(1000));
    private ViewPager viewPager;
    private static final int PAGE_SCATTER_CHART = 0;
    private static final int PAGE_LINE_CHART = 1;
    private static final int PAGE_BAR_CHART = 2;

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
        startButton = (TextView) findViewById(R.id.btn_start);
        viewPager = (ViewPager) findViewById(R.id.viewpager);
        setupViewPager(viewPager);
        tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(viewPager);
        resetButton = (TextView) findViewById(R.id.btn_reset);
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
        viewPager.addOnPageChangeListener(this);
        initCalibration();
    }


    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {

    }

    @Override
    public void onPageScrollStateChanged(int state) {
        if(state == ViewPager.SCROLL_STATE_SETTLING) {
            initSelectedGraph();
        } else if(state == ViewPager.SCROLL_STATE_IDLE) {
            updateSelectedGraph();
        }
    }

    private void initSelectedGraph() {
        try {
            switch (viewPager.getCurrentItem()) {
                case PAGE_SCATTER_CHART:
                    initScatter();
                    break;
                case PAGE_LINE_CHART:
                    initLine();
                    break;
                case PAGE_BAR_CHART:
                    initBar();
                    break;
            }
        } catch (NullPointerException ex) {
            // May arise while measuring
            LOGGER.error(ex.getLocalizedMessage(), ex);
        }
    }

    private void updateSelectedGraph() {
        try {
            switch (viewPager.getCurrentItem()) {
                case PAGE_SCATTER_CHART:
                    updateScatterChart();
                    break;
                case PAGE_LINE_CHART:
                    updateLineChart();
                    break;
                case PAGE_BAR_CHART:
                    updateBarChart();
                    break;
            }
        } catch (NullPointerException ex) {
            // May arise while measuring
            LOGGER.error(ex.getLocalizedMessage(), ex);
        }
    }

    private ScatterChart getScatterChart() {
        View view = ((ViewPagerAdapter)viewPager.getAdapter()).getItem(PAGE_SCATTER_CHART).getView();
        if(view != null) {
            return (ScatterChart) view.findViewById(R.id.autoCalibrationScatterChart);
        } else {
            return null;
        }
    }


    private BarChart getBarChart() {
        View view = ((ViewPagerAdapter)viewPager.getAdapter()).getItem(PAGE_BAR_CHART).getView();
        if(view != null) {
            return (BarChart) view.findViewById(R.id.autoCalibrationBarChart);
        } else {
            return null;
        }
    }

    private LineChart getLineChart() {
        View view = ((ViewPagerAdapter)viewPager.getAdapter()).getItem(PAGE_LINE_CHART).getView();
        if(view != null) {
            return (LineChart) view.findViewById(R.id.autoCalibrationLineChart);
        } else {
            return null;
        }
    }

    private void setupViewPager(ViewPager viewPager) {
        ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager());
        adapter.addFragment(new CalibrationScatterFragment(), "Scatter");
        adapter.addFragment(new CalibrationLineFragment(), "Line");
        adapter.addFragment(new CalibrationBarFragment(), "Pearson");
        viewPager.setAdapter(adapter);
    }

    private void initBar() {
        BarChart barChart = getBarChart();
        if(barChart == null) {
            return;
        }
        barChart.setDescription("");

        barChart.setDrawGridBackground(false);

        barChart.setMaxVisibleValueCount(200);

        Legend l = barChart.getLegend();
        l.setPosition(Legend.LegendPosition.ABOVE_CHART_LEFT);
        l.setTextColor(Color.WHITE);

        YAxis yl = barChart.getAxisLeft();
        yl.setTextColor(Color.WHITE);
        yl.setGridColor(Color.WHITE);
        barChart.getAxisRight().setEnabled(false);

        XAxis xl = barChart.getXAxis();
        xl.setDrawGridLines(false);
        xl.setTextColor(Color.WHITE);
        xl.setGridColor(Color.WHITE);
        xl.setPosition(XAxis.XAxisPosition.BOTTOM);
        xl.setDrawAxisLine(true);
        xl.setLabelRotationAngle(-90);
        xl.setDrawLabels(true);
        xl.setLabelsToSkip(0);
    }

    private static final class ItemActionOnClickListener implements DialogInterface.OnClickListener {
        CalibrationLinearityActivity calibrationLinearityActivity;
        ScatterChart scatterChart;

        public ItemActionOnClickListener(CalibrationLinearityActivity calibrationLinearityActivity, ScatterChart scatterChart) {
            this.calibrationLinearityActivity = calibrationLinearityActivity;
            this.scatterChart = scatterChart;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if(which > 0) {
                calibrationLinearityActivity.selectedFrequencies = new HashSet<Integer>(
                        Collections.singletonList((int)
                                ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[which - 1]));
            } else {
                // Select all frequencies
                calibrationLinearityActivity.selectedFrequencies = new HashSet<Integer>();
                for(double freq : ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED) {
                    calibrationLinearityActivity.selectedFrequencies.add((int)freq);
                }
            }
            calibrationLinearityActivity.updateSelectedGraph();
        }
    }

    private void initScatter() {
        final ScatterChart scatterChart = getScatterChart();
        if(scatterChart == null) {
            return;
        }
        scatterChart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Show
                AlertDialog.Builder builder = new AlertDialog.Builder(CalibrationLinearityActivity.this);
                builder.setTitle(CalibrationLinearityActivity.this.getText(R.string.calibration_select_frequency));
                ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(CalibrationLinearityActivity.this,
                        R.array.calibrate_type_list_array, android.R.layout.simple_selectable_list_item);
                builder.setAdapter(adapter,
                        new ItemActionOnClickListener(CalibrationLinearityActivity.this, scatterChart));
                builder.show();
            }
        });

        scatterChart.setDescription("");

        scatterChart.setDrawGridBackground(false);

        scatterChart.setMaxVisibleValueCount(200);

        Legend l = scatterChart.getLegend();
        l.setPosition(Legend.LegendPosition.RIGHT_OF_CHART);
        l.setTextColor(Color.WHITE);

        YAxis yl = scatterChart.getAxisLeft();
        yl.setTextColor(Color.WHITE);
        yl.setGridColor(Color.WHITE);
        scatterChart.getAxisRight().setEnabled(false);

        XAxis xl = scatterChart.getXAxis();
        xl.setDrawGridLines(false);
        xl.setTextColor(Color.WHITE);
        xl.setGridColor(Color.WHITE);
    }

    private void initLine() {
        LineChart lineChart = getLineChart();
        if(lineChart == null) {
            return;
        }
        lineChart.setDescription("");

        lineChart.setDrawGridBackground(false);

        // enable scaling and dragging

        Legend l = lineChart.getLegend();
        l.setPosition(Legend.LegendPosition.RIGHT_OF_CHART);
        l.setTextColor(Color.WHITE);

        YAxis yl = lineChart.getAxisLeft();
        yl.setTextColor(Color.WHITE);
        yl.setGridColor(Color.WHITE);
        lineChart.getAxisRight().setEnabled(false);

        XAxis xl = lineChart.getXAxis();
        xl.setDrawGridLines(false);
        xl.setTextColor(Color.WHITE);
        xl.setGridColor(Color.WHITE);
        xl.setPosition(XAxis.XAxisPosition.BOTTOM);
        xl.setDrawAxisLine(true);
        xl.setLabelRotationAngle(-90);
        xl.setDrawLabels(true);
        xl.setLabelsToSkip(0);
    }

    private void onApply() {
        try {
            // TODO
        } finally {
            onReset();
        }
    }



    private void initAudioProcess() {
        canceled.set(false);
        recording.set(true);
        audioProcess = new AudioProcess(recording, canceled);
        audioProcess.setDoFastLeq(false);
        audioProcess.setDoOneSecondLeq(true);
        audioProcess.setWeightingA(false);
        audioProcess.setHannWindowOneSecond(true);
        if(testGainCheckBox.isChecked()) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(CalibrationLinearityActivity.this);
            audioProcess.setGain((float)Math.pow(10, getDouble(sharedPref,"settings_recording_gain", 0) / 20));
        } else {
            audioProcess.setGain(1);
        }
        audioProcess.getListeners().addPropertyChangeListener(this);

        // Start measurement
        new Thread(audioProcess).start();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(SETTINGS_CALIBRATION_TIME.equals(key)) {
            defaultCalibrationTime = getInteger(sharedPreferences, SETTINGS_CALIBRATION_TIME, 10);
        } else if(SETTINGS_CALIBRATION_WARMUP_TIME.equals(key)) {
            defaultWarmupTime = getInteger(sharedPreferences, SETTINGS_CALIBRATION_WARMUP_TIME, 5);
        } else if("settings_recording_gain".equals(key) && audioProcess != null) {
            if(testGainCheckBox.isChecked()) {
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(CalibrationLinearityActivity.this);
                audioProcess.setGain((float)Math.pow(10, getDouble(sharedPreferences, key, 0) / 20));
            } else {
                audioProcess.setGain(1);
            }
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
            initAudioProcess();
        }
        startButton.setEnabled(false);
        testGainCheckBox.setEnabled(false);
        timeHandler = new Handler(Looper.getMainLooper(), progressHandler);
        progressHandler.start(defaultWarmupTime * 1000);
    }

    private int getAudioOutput() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String value = sharedPref.getString("settings_calibration_audio_output", "STREAM_MUSIC");

        if("STREAM_VOICE_CALL".equals(value)) {
            return AudioManager.STREAM_VOICE_CALL;
        } else if("STREAM_SYSTEM".equals(value)) {
            return AudioManager.STREAM_SYSTEM;
        } else if("STREAM_RING".equals(value)) {
            return AudioManager.STREAM_RING;
        } else if("STREAM_MUSIC".equals(value)) {
            return AudioManager.STREAM_MUSIC;
        } else if("STREAM_ALARM".equals(value)) {
            return AudioManager.STREAM_ALARM;
        } else if("STREAM_NOTIFICATION".equals(value)) {
            return AudioManager.STREAM_NOTIFICATION;
        } else if("STREAM_DTMF".equals(value)) {
            return AudioManager.STREAM_DTMF;
        } else {
            return AudioManager.STREAM_RING;
        }
    }

    private void playNewTrack() {

        double rms = dbToRms(99 - (splLoop++) * DB_STEP);
        short[] data = makeWhiteNoiseSignal(44100, rms);
        double[] fftCenterFreq = FFTSignalProcessing.computeFFTCenterFrequency(AudioProcess.REALTIME_SAMPLE_RATE_LIMITATION);
        FFTSignalProcessing fftSignalProcessing = new FFTSignalProcessing(44100, fftCenterFreq, 44100);
        fftSignalProcessing.addSample(data);
        whiteNoisedB = fftSignalProcessing.computeGlobalLeq();
        freqLeqStats.add(new LinearCalibrationResult(fftSignalProcessing.processSample(FFTSignalProcessing.WINDOW_TYPE.TUKEY, false, false)));
        LOGGER.info("Emit white noise of "+whiteNoisedB+" dB");
        if(audioTrack == null) {
            audioTrack = new AudioTrack(getAudioOutput(), 44100, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, data.length * (Short.SIZE / 8), AudioTrack.MODE_STATIC);
        } else {
            try {
                audioTrack.pause();
                audioTrack.flush();
            } catch (IllegalStateException ex) {
                // Ignore
            }
        }
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
                    initAudioProcess();
                } else {
                    // permission denied
                    // Ask again
                    checkAndAskPermissions();
                }
            }
        }
    }

    private void updateBarChart() {
        BarChart barChart = getBarChart();
        if(barChart == null) {
            return;
        }
        if(freqLeqStats.size() <= 2) {
            return;
        }
        double[] pearsons = computePearson();
        if(pearsons == null) {
            return;
        }

        float YMin = Float.MAX_VALUE;
        float YMax = Float.MIN_VALUE;

        ArrayList<IBarDataSet> dataSets = new ArrayList<IBarDataSet>();

        // Read all white noise values for indexing before usage
        ArrayList<BarEntry> yMeasure = new ArrayList<BarEntry>();
        int idfreq = 0;
        for (double value : pearsons) {
            YMax = Math.max(YMax, (float)value);
            YMin = Math.min(YMin, (float)value);
            yMeasure.add(new BarEntry((float)value, idfreq++));
        }
        BarDataSet freqSet = new BarDataSet(yMeasure, "Pearson's correlation");
        freqSet.setColor(ColorTemplate.COLORFUL_COLORS[0]);
        freqSet.setValueTextColor(Color.WHITE);
        freqSet.setDrawValues(true);
        dataSets.add(freqSet);


        ArrayList<String> xVals = new ArrayList<String>();
        double[] freqs = FFTSignalProcessing.computeFFTCenterFrequency(AudioProcess.REALTIME_SAMPLE_RATE_LIMITATION);
        for (double freqValue : freqs) {
            xVals.add(Spectrogram.formatFrequency((int)freqValue));
        }

        // create a data object with the datasets
        BarData data = new BarData(xVals, dataSets);
        barChart.setData(data);
        YAxis yl = barChart.getAxisLeft();
        yl.setAxisMinValue(YMin - 0.1f);
        yl.setAxisMaxValue(YMax + 0.1f);

        barChart.invalidate();
    }

    private void updateLineChart() {
        LineChart lineChart = getLineChart();
        if(lineChart == null) {
            return;
        }
        if(freqLeqStats.isEmpty()) {
            return;
        }
        float YMin = Float.MAX_VALUE;
        float YMax = Float.MIN_VALUE;

        ArrayList<ILineDataSet> dataSets = new ArrayList<ILineDataSet>();

        // Read all white noise values for indexing before usage
        int idStep = 0;
        double referenceLevel = freqLeqStats.get(0).whiteNoiseLevel.getGlobaldBaValue();
        for(LinearCalibrationResult result : freqLeqStats) {
            ArrayList<Entry> yMeasure = new ArrayList<Entry>();
            int idfreq = 0;
            for (LeqStats leqStat : result.measure) {
                float dbLevel = (float)leqStat.getLeqMean();
                YMax = Math.max(YMax, dbLevel);
                YMin = Math.min(YMin, dbLevel);
                yMeasure.add(new Entry(dbLevel, idfreq++));
            }
            LineDataSet freqSet = new LineDataSet(yMeasure, String.format(Locale.getDefault(),"%d dB",
                    (int)(result.whiteNoiseLevel.getGlobaldBaValue() - referenceLevel)));
            freqSet.setColor(ColorTemplate.COLORFUL_COLORS[idStep % ColorTemplate.COLORFUL_COLORS.length]);
            freqSet.setFillColor(ColorTemplate.COLORFUL_COLORS[idStep % ColorTemplate.COLORFUL_COLORS.length]);
            freqSet.setValueTextColor(Color.WHITE);
            freqSet.setCircleColorHole(ColorTemplate.COLORFUL_COLORS[idStep % ColorTemplate.COLORFUL_COLORS.length]);
            freqSet.setDrawValues(false);
            freqSet.setDrawFilled(true);
            freqSet.setFillAlpha(255);
            freqSet.setDrawCircles(true);
            freqSet.setMode(LineDataSet.Mode.LINEAR);
            dataSets.add(freqSet);
            idStep++;
        }


        ArrayList<String> xVals = new ArrayList<String>();
        double[] freqs = FFTSignalProcessing.computeFFTCenterFrequency(AudioProcess.REALTIME_SAMPLE_RATE_LIMITATION);
        for (double freqValue : freqs) {
            xVals.add(Spectrogram.formatFrequency((int)freqValue));
        }

        // create a data object with the datasets
        LineData data = new LineData(xVals, dataSets);
        lineChart.setData(data);
        YAxis yl = lineChart.getAxisLeft();
        yl.setAxisMinValue(YMin - 3);
        yl.setAxisMaxValue(YMax + 3);
        lineChart.invalidate();
    }

    private void updateScatterChart() {
        ScatterChart scatterChart = getScatterChart();
        if(scatterChart == null) {
            return;
        }
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
            for(int idFreq = 0; idFreq < result.whiteNoiseLevel.getdBaLevels().length; idFreq++) {
                float dbLevel = result.whiteNoiseLevel.getdBaLevels()[idFreq];
                float referenceDbLevel = freqLeqStats.get(0).whiteNoiseLevel.getdBaLevels()[idFreq];
                whiteNoiseValuesSet.add((int)(dbLevel - referenceDbLevel));
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

        double[] freqs = FFTSignalProcessing.computeFFTCenterFrequency(AudioProcess.REALTIME_SAMPLE_RATE_LIMITATION);
        for(int freqId = 0; freqId < dataSetCount; freqId += 1) {
            int freqValue = (int)freqs[freqId];
            if(selectedFrequencies.contains(freqValue)) {
                ArrayList<Entry> yMeasure = new ArrayList<Entry>();
                for (LinearCalibrationResult result : freqLeqStats) {
                    float dbLevel = (float) result.measure[freqId].getLeqMean();
                    float referenceDbLevel = freqLeqStats.get(0).whiteNoiseLevel.getdBaLevels()[freqId];
                    int whiteNoise = (int) (result.whiteNoiseLevel.getdBaLevels()[freqId] - referenceDbLevel);
                    YMax = Math.max(YMax, dbLevel);
                    YMin = Math.min(YMin, dbLevel);
                    yMeasure.add(new Entry(dbLevel, Arrays.binarySearch(whiteNoiseValues, whiteNoise)));
                }
                ScatterDataSet freqSet = new ScatterDataSet(yMeasure,
                        Spectrogram.formatFrequency((int) ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[freqId]));
                freqSet.setScatterShape(ScatterChart.ScatterShape.CIRCLE);
                freqSet.setColor(ColorTemplate.COLORFUL_COLORS[freqId % ColorTemplate.COLORFUL_COLORS.length]);
                freqSet.setScatterShapeSize(8f);
                freqSet.setValueTextColor(Color.WHITE);
                freqSet.setDrawValues(false);
                dataSets.add(freqSet);
            }
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

        StringBuilder log = new StringBuilder();
        for(int freqId = 0; freqId < dataSetCount; freqId++) {
            double[] xValues = new double[freqLeqStats.size()];
            double[] yValues = new double[freqLeqStats.size()];
            int idStep = 0;
            for(LinearCalibrationResult result : freqLeqStats) {
                double dbLevel = result.measure[freqId].getLeqMean();
                double whiteNoise = result.whiteNoiseLevel.getdBaLevels()[freqId];
                xValues[idStep] = whiteNoise;
                yValues[idStep] = dbLevel;
                if(freqId == 0) {
                    LOGGER.info("100 hZ white noise " + whiteNoise + " dB spl: " + dbLevel+ " dB");
                }
                idStep++;
            }
            pearsonCoefficient[freqId] = new PearsonsCorrelation().correlation(xValues, yValues);
            if(log.length() == 0) {
                log.append("[");
            } else {
                log.append(", ");
            }
            log.append(String.format(Locale.getDefault(), "%.2f %%",pearsonCoefficient[freqId] * 100 ));
        }
        log.append("]");
        LOGGER.info("Pearson's values : "+log.toString());
        return pearsonCoefficient;
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if((calibration_step == CALIBRATION_STEP.CALIBRATION || calibration_step == CALIBRATION_STEP.WARMUP)  &&
                AudioProcess.PROP_SLOW_LEQ.equals(event.getPropertyName())) {
            // New leq
            AudioProcess.AudioMeasureResult measure =
                    (AudioProcess.AudioMeasureResult) event.getNewValue();
            final double leq;
            // Use global dB value or only the selected frequency band
            leq = measure.getGlobaldBaValue();
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

    public void onReset() {
        initCalibration();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(audioTrack != null) {
            audioTrack.stop();
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
            if(!freqLeqStats.isEmpty()) {
                freqLeqStats.get(freqLeqStats.size() - 1).setGlobalMeasure(leqStats);
            }
            progressHandler.start(defaultCalibrationTime * 1000);
            calibration_step = CALIBRATION_STEP.CALIBRATION;
        } else if(calibration_step == CALIBRATION_STEP.CALIBRATION) {
            if(splBackroundNoise != 0) {
                double previousLeq = Double.MAX_VALUE;
                if(freqLeqStats.size() > 1) {
                    previousLeq = freqLeqStats.get(freqLeqStats.size() - 2).globalMeasure.getLeqMean();
                }
                // If the powered calibration reach the background noise or
                // if the last leq is superior than the previous leq
                if (leqStats.getLeqMean() < splBackroundNoise + 3 || leqStats.getLeqMean() > previousLeq) {
                    // Almost reach the background noise, stop calibration
                    calibration_step = CALIBRATION_STEP.END;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textDeviceLevel.setText(R.string.no_valid_dba_value);
                            textStatus.setText(R.string.calibration_status_end);
                            updateSelectedGraph();
                        }
                    });
                    recording.set(false);
                    canceled.set(true);
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
                            updateSelectedGraph();
                        }
                    });
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
                        updateSelectedGraph();
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
        private LeqStats globalMeasure;

        public LinearCalibrationResult(FFTSignalProcessing.ProcessingResult whiteNoiseLevel) {
            this.whiteNoiseLevel = whiteNoiseLevel;

        }

        public void setGlobalMeasure(LeqStats globalMeasure) {
            this.globalMeasure = globalMeasure;
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
