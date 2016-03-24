package org.orbisgis.protonomap;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Chronometer;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.XAxis.XAxisPosition;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.utils.LargeValueFormatter;
import com.github.mikephil.charting.utils.ValueFormatter;

import org.orbisgis.sos.AcousticIndicators;
import org.orbisgis.sos.ThirdOctaveBandsFiltering;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class Measurement extends MainActivity {

    //public ImageButton buttonrecord;
    //public ImageButton buttoncancel;

    // For the Charts
    protected HorizontalBarChart mChart; // VUMETER representation
    protected BarChart sChart; // Spectrum representation
    protected Spectrogram spectrogram;

    // Other ressources
    private AtomicBoolean isRecording = new AtomicBoolean(false);
    private AtomicBoolean isComputingMovingLeq = new AtomicBoolean(false);
    private AudioProcess audioProcess = new AudioProcess(isRecording);

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measurement);
        initDrawer();

        // Message for starting a record
        Toast.makeText(getApplicationContext(),
                getString(R.string.record_message), Toast.LENGTH_LONG).show();

        // Check if the dialog box (for caution) must be displayed
        // Depending of the settings
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        Boolean CheckNbRunSettings = sharedPref.getBoolean("settings_caution", true);
        if (CheckNbRun() & CheckNbRunSettings) {

            // show dialog
            // TODO : verify calibration mode and inform user
            new AlertDialog.Builder(this).setTitle(R.string.title_caution)
                    .setMessage(R.string.text_caution)
                    .setNeutralButton(R.string.text_OK, null)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }

        // Enabled/disabled buttons
        // history disabled; cancel enabled; record button enabled; map enabled
        ImageButton buttonhistory = (ImageButton) findViewById(R.id.historyBtn);
        checkHistoryButton();
        ImageButton buttoncancel = (ImageButton) findViewById(R.id.cancelBtn);
        buttoncancel.setImageResource(R.drawable.button_cancel_disabled);
        buttoncancel.setEnabled(false);
        ImageButton buttonmap = (ImageButton) findViewById(R.id.mapBtn);
        buttonmap.setImageResource(R.drawable.button_map_normal);
        buttoncancel.setEnabled(true);

        // Display element in expert mode or not
        Boolean CheckViewModeSettings = sharedPref.getBoolean("settings_view_mode", true);
        //FrameLayout Frame_Stat_SEL = (FrameLayout) findViewById(R.id.Frame_Stat_SEL);
        TextView textView_value_Min_i = (TextView) findViewById(R.id.textView_value_Min_i);
        TextView textView_value_Mean_i = (TextView) findViewById(R.id.textView_value_Mean_i);
        TextView textView_value_Max_i = (TextView) findViewById(R.id.textView_value_Max_i);
        TextView textView_title_Min_i = (TextView) findViewById(R.id.textView_title_Min_i);
        TextView textView_title_Mean_i = (TextView) findViewById(R.id.textView_title_Mean_i);
        TextView textView_title_Max_i = (TextView) findViewById(R.id.textView_title_Max_i);
        if (!CheckViewModeSettings){
            textView_value_Min_i.setVisibility(View.GONE);
            textView_value_Mean_i.setVisibility(View.GONE);
            textView_value_Max_i.setVisibility(View.GONE);
            textView_title_Min_i.setVisibility(View.GONE);
            textView_title_Mean_i.setVisibility(View.GONE);
            textView_title_Max_i.setVisibility(View.GONE);
        }


        // To start a record (test mode)
        ImageButton buttonrecord = (ImageButton) findViewById(R.id.recordBtn);
        buttonrecord.setImageResource(R.drawable.button_record_normal);
        buttonrecord.setEnabled(true);

        // Actions on record button
        buttonrecord.setOnClickListener(new DoProcessing(getApplicationContext(), this));

        // Action on cancel button (during recording)
        buttoncancel.setOnClickListener(onButtonCancel);

        // Action on History button
        buttonhistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Go to history page
                gotoHistory();
            }
        });

        // Action on Map button
        buttonmap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Go to map page
                Intent a = new Intent(getApplicationContext(), Map.class);
                startActivity(a);
                ;
            }
        });

        // Instantaneous sound level VUMETER
        // Stacked bars are used for represented Min, Current and Max values
        // Horizontal barchart
        sChart = (BarChart) findViewById(R.id.spectrumChart);
        mChart = (HorizontalBarChart) findViewById(R.id.vumeter);
        spectrogram = (Spectrogram) findViewById(R.id.spectrogram_view);
        // When user click on spectrum control, view are switched
        SwitchVisibilityListener switchVisibilityListener = new SwitchVisibilityListener(sChart, spectrogram);
        sChart.setOnClickListener(switchVisibilityListener);
        spectrogram.setOnClickListener(switchVisibilityListener);
        initSpectrum();
        initVueMeter();
        setData(0);
        // Legend: hide all
        Legend lv = mChart.getLegend();
        lv.setEnabled(false); // Hide legend

        // Instantaneous spectrum
        // Stacked bars are used for represented Min, Current and Max values
        sChart = (BarChart) findViewById(R.id.spectrumChart);
        if (!CheckViewModeSettings){
            sChart.setVisibility(View.GONE);
        }
        initSpectrum();
        // Data (before legend)
        updateSpectrumGUI();
        // (ltob.length-1) values for each third-octave band// Legend: hide all
        Legend ls = sChart.getLegend();
        ls.setEnabled(false); // Hide legend
    }

    private View.OnClickListener onButtonCancel = new View.OnClickListener() {
        @Override
        public void onClick(View view) {

            // Stop measurement
            isRecording.set(false);

            // Stop and reset chronometer
            Chronometer chronometer = (Chronometer) findViewById(R.id.chronometer_recording_time);
            chronometer.stop();
            chronometer.setText("00:00");

            // Enabled/disabled buttons after measurement
            // history enabled or disabled (if isHistory); cancel disable; record button enabled
            checkHistoryButton();
            ImageButton buttoncancel = (ImageButton) findViewById(R.id.cancelBtn);
            buttoncancel.setImageResource(R.drawable.button_cancel_disabled);
            buttoncancel.setEnabled(false);
            ImageButton buttonrecord = (ImageButton) findViewById(R.id.recordBtn);
            buttonrecord.setImageResource(R.drawable.button_record);
            buttoncancel.setEnabled(true);
            ImageButton buttonmap = (ImageButton) findViewById(R.id.mapBtn);
            buttonmap.setImageResource(R.drawable.button_map);
            buttonmap.setEnabled(true);

            // Goto the Results activity
            isResults = false;

        }
    };


    private void initSpectrum() {
        sChart.setDrawBarShadow(false);
        sChart.setDescription("");
        sChart.setPinchZoom(false);
        sChart.setDrawGridBackground(false);
        sChart.setMaxVisibleValueCount(0);
        sChart.setDrawValuesForWholeStack(true); // Stacked
        sChart.setHighlightEnabled(false);
        sChart.setNoDataTextDescription("Start by pressing the record button");
        // XAxis parameters:
        XAxis xls = sChart.getXAxis();
        xls.setPosition(XAxisPosition.BOTTOM);
        xls.setDrawAxisLine(true);
        xls.setDrawGridLines(false);
        xls.setDrawLabels(true);
        xls.setTextColor(Color.WHITE);
        // YAxis parameters (left): main axis for dB values representation
        YAxis yls = sChart.getAxisLeft();
        yls.setDrawAxisLine(true);
        yls.setDrawGridLines(true);
        yls.setAxisMaxValue(141.f);
        yls.setStartAtZero(true);
        yls.setTextColor(Color.WHITE);
        yls.setGridColor(Color.WHITE);
        yls.setValueFormatter(new SPLValueFormatter());
        // YAxis parameters (right): no axis, hide all
        YAxis yrs = sChart.getAxisRight();
        yrs.setEnabled(false);
    }

    // Init RNE Pie Chart
    public void initVueMeter(){
        mChart.setDrawBarShadow(false);
        mChart.setDescription("");
        mChart.setPinchZoom(false);
        mChart.setDrawGridBackground(false);
        mChart.setMaxVisibleValueCount(0);
        mChart.setScaleXEnabled(false); // Disable scaling on the X-axis
        mChart.setHighlightEnabled(false);
        // XAxis parameters: hide all
        XAxis xlv = mChart.getXAxis();
        xlv.setPosition(XAxisPosition.BOTTOM);
        xlv.setDrawAxisLine(false);
        xlv.setDrawGridLines(false);
        xlv.setDrawLabels(false);
        // YAxis parameters (left): main axis for dB values representation
        YAxis ylv = mChart.getAxisLeft();
        ylv.setDrawAxisLine(false);
        ylv.setDrawGridLines(true);
        ylv.setAxisMaxValue(141f);
        ylv.setStartAtZero(true);
        ylv.setTextColor(Color.WHITE);
        ylv.setGridColor(Color.WHITE);
        ylv.setValueFormatter(new dBValueFormatter());
        // YAxis parameters (right): no axis, hide all
        YAxis yrv = mChart.getAxisRight();
        yrv.setEnabled(false);
        //return true;
    }

    /***
     * Checks that application runs first time and write flags at SharedPreferences
     * Need further codes for enhancing conditions
     * @return true if 1st time
     * see : http://stackoverflow.com/questions/9806791/showing-a-message-dialog-only-once-when-application-is-launched-for-the-first
     * see also for checking version (later) : http://stackoverflow.com/questions/7562786/android-first-run-popup-dialog
     * Can be used for checking new version
     */
    private boolean CheckNbRun() {
        Resources res = getResources();
        Integer NbRunMaxCaution = res.getInteger(R.integer.NbRunMaxCaution);
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
                Integer NbRun = preferences.getInt("NbRun", 1);
        if (NbRun > NbRunMaxCaution) {
            NbRun=1;
            editor.putInt("NbRun", NbRun+1);
            editor.apply();
        }
        else
        {
            editor.putInt("NbRun", NbRun+1);
            editor.apply();
            //AlreadyRanBefore = preferences.getBoolean("AlreadyRanBefore", false);
        }
        return (NbRun==1);
    }

    // Fix the format of the dB Axis of the vumeter
    public class dBValueFormatter implements ValueFormatter {

        private DecimalFormat mFormat;

        public dBValueFormatter() {
            mFormat = new DecimalFormat("###,###,##0"); // use one decimal
        }

        @Override
        public String getFormattedValue(float value) {
            return mFormat.format(value);
        }
    }

    // Generate artificial 1 data (sound level) for vumeter representation
    private void setData(float val) {

        ArrayList<String> xVals = new ArrayList<String>();
        xVals.add("");

        ArrayList<BarEntry> yVals1 = new ArrayList<BarEntry>();
        yVals1.add(new BarEntry(val, 0));

        BarDataSet set1 = new BarDataSet(yVals1, "DataSet");
        //set1.setBarSpacePercent(35f);
        //set1.setColor(Color.rgb(0, 153, 204));
        int nc=getNEcatColors(val);    // Choose the color category in function of the sound level
        int[] color_rep=NE_COLORS();
        set1.setColor(color_rep[nc]);

        ArrayList<BarDataSet> dataSets = new ArrayList<BarDataSet>();
        dataSets.add(set1);

        BarData data = new BarData(xVals, dataSets);
        data.setValueTextSize(10f);

        mChart.setData(data);
        mChart.invalidate(); // refresh
    }

    private void updateSpectrumGUI() {


        ArrayList<String> xVals = new ArrayList<String>();
        ArrayList<BarEntry> yVals1 = new ArrayList<BarEntry>();
        double[] freqLabels = audioProcess.getRealtimeCenterFrequency();
        float[] freqValues = audioProcess.getThirdOctaveFrequencySPL();
        LargeValueFormatter largeValueFormatter = new LargeValueFormatter();

        for(int idfreq =0; idfreq < freqLabels.length; idfreq++) {
            xVals.add(largeValueFormatter.getFormattedValue((float)freqLabels[idfreq]));
            // Sum values
            // Compute frequency range covered by frequency
            yVals1.add(new BarEntry(new float[] {freqValues[idfreq]}, idfreq));
        }

        BarDataSet set1 = new BarDataSet(yVals1, "DataSet");
        set1.setColor(Color.rgb(102, 178, 255));
        set1.setStackLabels(new String[]{
                "SL"
        });

        ArrayList<BarDataSet> dataSets = new ArrayList<BarDataSet>();
        dataSets.add(set1);

        BarData data = new BarData(xVals, dataSets);
        data.setValueTextSize(10f);

        sChart.setData(data);
        sChart.invalidate(); // refresh
    }

    // Color for spectrum representation (min, iSL, max)
    public static final int[] SPECTRUM_COLORS = {
            Color.rgb(0, 128, 255), Color.rgb(102, 178, 255), Color.rgb(204, 229, 255),
    };

    private static class DoProcessing implements CompoundButton.OnClickListener, PropertyChangeListener {
        private Context context;
        private Measurement activity;


        private DoProcessing(Context context, Measurement activity) {
            this.context = context;
            this.activity = activity;
        }

        @Override
        public void propertyChange(PropertyChangeEvent event) {
            if(AudioProcess.PROP_MOVING_SPECTRUM.equals(event.getPropertyName())) {
                activity.spectrogram.addTimeStep((float[])event.getNewValue(), activity.audioProcess.getFFTFreqArrayStep());
                if(activity.isComputingMovingLeq.compareAndSet(false, true)) {
                    activity.runOnUiThread(new UpdateText(activity));
                }
            }
        }

        @Override
        public void onClick(View v) {

            // Update buttons: history disabled; cancel enabled; record button to stop; map disabled
            ImageButton buttonhistory= (ImageButton) activity.findViewById(R.id.historyBtn);
            buttonhistory.setImageResource(R.drawable.button_history_disabled);
            buttonhistory.setEnabled(false);
            ImageButton buttoncancel= (ImageButton) activity.findViewById(R.id.cancelBtn);
            buttoncancel.setImageResource(R.drawable.button_cancel_normal);
            buttoncancel.setEnabled(true);
            ImageButton buttonrecord= (ImageButton) activity.findViewById(R.id.recordBtn);
            buttonrecord.setImageResource(R.drawable.button_record_pressed);
            ImageButton buttonmap= (ImageButton) activity.findViewById(R.id.mapBtn);
            buttonmap.setImageResource(R.drawable.button_map_disabled);

            if (activity.isRecording.compareAndSet(false, true)) {

                // Start measurement

                // Start chronometer
                Chronometer chronometer = (Chronometer) activity.findViewById(R.id.chronometer_recording_time);
                chronometer.setBase(SystemClock.elapsedRealtime());
                chronometer.start();

                // Start recording
                new Thread(activity.audioProcess).start();
                activity.audioProcess.getListeners().addPropertyChangeListener(this);
            }
            else
            {
                // Stop measurement
                activity.isRecording.set(false);

                // Enabled/disabled buttons after measurement
                // history enabled or disabled (if isHistory); cancel disable; record button enabled
                if (activity.isHistory){
                    buttonhistory.setImageResource(R.drawable.button_history_normal);
                    buttonhistory.setEnabled(true);
                }
                else {
                    buttonhistory.setImageResource(R.drawable.button_history_disabled);
                    buttonhistory.setEnabled(false);
                }
                buttoncancel.setImageResource(R.drawable.button_cancel_disabled);
                buttoncancel.setEnabled(false);
                buttonrecord.setImageResource(R.drawable.button_record);
                buttonrecord.setEnabled(true);
                buttonmap.setImageResource(R.drawable.button_map);
                buttonmap.setEnabled(true);

                // Goto the Results activity
                activity.isResults = true;
                Intent ir = new Intent(context, Results.class);
                activity.startActivity(ir);

                // Stop and reset chronometer
                Chronometer chronometer = (Chronometer) activity.findViewById(R.id.chronometer_recording_time);
                chronometer.stop();
                chronometer.setText("00:00");

                // TODO save the results to the webphone storage and send data to the server (check if data transfer); add results to history change isHistory to true
            }
        }
    }

    private static class UpdateText implements Runnable {
        Measurement activity;

        private UpdateText(Measurement activity) {
            this.activity = activity;
        }

        @Override
        public void run() {
            try {
                final float leq = (float)activity.audioProcess.getLeq();
                activity.setData(leq);
                // Change the text and the textcolor in the corresponding textview
                // for the Leqi value
                final TextView mTextView = (TextView) activity.findViewById(R.id.textView_value_SL_i);
                mTextView.setText(String.format("%.1f", leq));
                int nc = activity.getNEcatColors(leq);    // Choose the color category in function of the sound level
                int[] color_rep = activity.NE_COLORS();
                mTextView.setTextColor(color_rep[nc]);

                // Spectrum data
                activity.updateSpectrumGUI();

                // Debug processing time
                //final TextView valueMin = (TextView) activity.findViewById(R.id.textView_value_Min_i);
                //mTextProcessingView.setText(String.format("%d", minValue));
            } finally {
                activity.isComputingMovingLeq.set(false);
            }
        }

    }

    private static class SwitchVisibilityListener implements View.OnClickListener {
        private BarChart sChart; // Spectrum representation
        private Spectrogram spectrogram;

        public SwitchVisibilityListener(BarChart sChart, Spectrogram spectrogram) {
            this.sChart = sChart;
            this.spectrogram = spectrogram;
        }

        @Override
        public void onClick(View view) {
            if(sChart.getVisibility() == View.GONE) {
                sChart.setVisibility(View.VISIBLE);
                spectrogram.setVisibility(View.GONE);
            } else {
                sChart.setVisibility(View.GONE);
                spectrogram.setVisibility(View.VISIBLE);
            }
        }
    }

}

