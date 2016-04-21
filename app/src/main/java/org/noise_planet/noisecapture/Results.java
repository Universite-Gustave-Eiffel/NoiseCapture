package org.noise_planet.noisecapture;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.XAxis.XAxisPosition;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.components.Legend.LegendPosition;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.utils.Highlight;
import com.github.mikephil.charting.utils.PercentFormatter;

import org.noise_planet.noisecapture.util.CustomPercentFormatter;
import org.orbisgis.sos.LeqStats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Results extends MainActivity {
    public static final String RESULTS_RECORD_ID = "RESULTS_RECORD_ID";
    private MeasurementManager measurementManager;
    private Storage.Record record;
    private static final double[][] CLASS_RANGES = new double[][]{{Double.MIN_VALUE, 45}, {45, 55}, {55, 65}, {65, 75},{75, Double.MAX_VALUE}};

    // For the Charts
    public PieChart rneChart;
    public PieChart neiChart;
    protected BarChart sChart; // Spectrum representation

    // Other ressources
    private String[] ltob;  // List of third-octave bands
    private String[] catNE; // List of noise level category (defined as ressources)
    private List<Float> splHistogram;
    private LeqStats leqStats = new LeqStats();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.measurementManager = new MeasurementManager(getApplicationContext());
        Intent intent = getIntent();
        if(intent != null && intent.hasExtra(RESULTS_RECORD_ID)) {
            record = measurementManager.getRecord(intent.getIntExtra(RESULTS_RECORD_ID, -1));
        } else {
            // Read the last stored record
            List<Storage.Record> recordList = measurementManager.getRecords();
            if(!recordList.isEmpty()) {
                record = recordList.get(recordList.size() - 1);
            } else {
                // Message for starting a record
                Toast.makeText(getApplicationContext(),
                        getString(R.string.no_results), Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        }
        loadMeasurement();
        LeqStats.LeqOccurrences leqOccurrences = leqStats.computeLeqOccurrences(CLASS_RANGES);
        setContentView(R.layout.activity_results);
        initDrawer();

        // RNE PieChart
        rneChart = (PieChart) findViewById(R.id.RNEChart);
        initRNEChart();
        setRNEData(leqOccurrences.getUserDefinedOccurrences());
        Legend lrne = rneChart.getLegend();
        lrne.setTextColor(Color.WHITE);
        lrne.setTextSize(8f);
        lrne.setPosition(LegendPosition.RIGHT_OF_CHART_CENTER);
        lrne.setEnabled(true);

        // NEI PieChart
        neiChart = (PieChart) findViewById(R.id.NEIChart);
        initNEIChart();
        setNEIData();
        Legend lnei = neiChart.getLegend();
        lnei.setEnabled(false);

        // Cumulated spectrum
        sChart = (BarChart) findViewById(R.id.spectrumChart);
        initSpectrumChart();
        setDataS();
        Legend ls = sChart.getLegend();
        ls.setEnabled(false); // Hide legend

        TextView minText = (TextView) findViewById(R.id.textView_value_Min_SL);
        minText.setText(String.format("%.01f", leqStats.getLeqMin()));

        TextView maxText = (TextView) findViewById(R.id.textView_value_Max_SL);
        maxText.setText(String.format("%.01f", leqStats.getLeqMax()));

        TextView la10Text = (TextView) findViewById(R.id.textView_value_LA10);
        la10Text.setText(String.format("%.01f", leqOccurrences.getLa10()));

        TextView la50Text = (TextView) findViewById(R.id.textView_value_LA50);
        la50Text.setText(String.format("%.01f", leqOccurrences.getLa50()));

        TextView la90Text = (TextView) findViewById(R.id.textView_value_LA90);
        la90Text.setText(String.format("%.01f", leqOccurrences.getLa90()));


        // Enabled/disabled history button if necessary
        ImageButton buttonhistory= (ImageButton) findViewById(R.id.historyBtn);
        checkHistoryButton();
        // Action on History button
        buttonhistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Go to history page
                gotoHistory();
            }
        });

        // Action on Map button
        ImageButton buttonmap=(ImageButton)findViewById(R.id.mapBtn);
        buttonmap.setImageResource(R.drawable.button_map);
        buttonmap.setEnabled(true);
        buttonmap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Go to map page
                Intent a = new Intent(getApplicationContext(), MapActivity.class);
                finish();
                startActivity(a);
            }
        });

        // Action on record button
        ImageButton buttonrecord= (ImageButton) findViewById(R.id.recordBtn);
        buttonrecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Go to Measurement activity
                Intent im = new Intent(getApplicationContext(),Measurement.class);
                mDrawerLayout.closeDrawer(mDrawerList);
                startActivity(im);
                finish();
            }
        });


    }

    private void loadMeasurement() {
        Resources resources = getResources();

        // Query database
        List<Integer> frequencies = new ArrayList<Integer>();
        List<Float[]> leqValues = new ArrayList<Float[]>();
        measurementManager.getRecordValues(record.getId(), frequencies, leqValues);

        // Create leq statistics by frequency
        LeqStats[] leqStatsByFreq = new LeqStats[frequencies.size()];
        for(int idFreq = 0; idFreq < leqStatsByFreq.length; idFreq++) {
            leqStatsByFreq[idFreq] = new LeqStats();
        }
        // parse each leq window time
        for(Float[] leqFreqs : leqValues) {
            double rms = 0;
            int idFreq = 0;
            for(float leqValue : leqFreqs) {
                leqStatsByFreq[idFreq].addLeq(leqValue);
                rms += Math.pow(10, leqValue / 10);
                idFreq++;
            }
            leqStats.addLeq(10 * Math.log10(rms));
        }
        splHistogram = new ArrayList<>(leqStatsByFreq.length);
        ltob = new String[leqStatsByFreq.length];
        int idFreq = 0;
        for (LeqStats aLeqStatsByFreq : leqStatsByFreq) {
            ltob[idFreq] = resources.getString(R.string.results_histo_freq, frequencies.get(idFreq));
            splHistogram.add((float) aLeqStatsByFreq.getLeqMean());
            idFreq++;
        }
    }

    public void initSpectrumChart(){
        sChart.setTouchEnabled(false);
        sChart.setDrawBarShadow(false);
        sChart.setDescription("");
        sChart.setPinchZoom(false);
        sChart.setDrawGridBackground(false);
        sChart.setMaxVisibleValueCount(0);
        sChart.setHighlightEnabled(false);
        // XAxis parameters: hide all
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
        // YAxis parameters (right): no axis, hide all
        YAxis yrs = sChart.getAxisRight();
        yrs.setEnabled(false);
        //return true;
    }

    // Read spl data for spectrum representation
    private void setDataS() {

        ArrayList<String> xVals = new ArrayList<String>();
        Collections.addAll(xVals, ltob);


        ArrayList<BarEntry> yVals1 = new ArrayList<BarEntry>();

        for (int i = 0; i < splHistogram.size(); i++) {
            yVals1.add(new BarEntry(splHistogram.get(i), i));
        }

        BarDataSet set1 = new BarDataSet(yVals1, "DataSet");
        //set1.setBarSpacePercent(35f);
        //set1.setColors(new int[] {Color.rgb(0, 153, 204), Color.rgb(0, 153, 204), Color.rgb(0, 153, 204),
        //        Color.rgb(51, 181, 229), Color.rgb(51, 181, 229), Color.rgb(51, 181, 229)});
        set1.setColors(
                new int[]{Color.rgb(0, 128, 255), Color.rgb(0, 128, 255), Color.rgb(0, 128, 255),
                        Color.rgb(102, 178, 255), Color.rgb(102, 178, 255),
                        Color.rgb(102, 178, 255)});

        ArrayList<BarDataSet> dataSets = new ArrayList<BarDataSet>();
        dataSets.add(set1);

        BarData data = new BarData(xVals, dataSets);
        data.setValueTextSize(10f);

        sChart.setData(data);
        sChart.invalidate();
    }

    // Init RNE Pie Chart
    public void initRNEChart(){
        rneChart.setUsePercentValues(true);
        rneChart.setHoleColorTransparent(true);
        rneChart.setHoleRadius(40f);
        rneChart.setDescription("");
        rneChart.setDrawCenterText(true);
        rneChart.setDrawHoleEnabled(true);
        rneChart.setRotationAngle(0);
        rneChart.setRotationEnabled(true);
        rneChart.setDrawSliceText(false);
        rneChart.setCenterText("RNE");
        rneChart.setCenterTextColor(Color.WHITE);
        //return true;
    }


    // Set computed data in chart
    private void setRNEData(List<Double> classRangeValue) {
        ArrayList<Entry> yVals1 = new ArrayList<Entry>();

        // IMPORTANT: In a PieChart, no values (Entry) should have the same
        // xIndex (even if from different DataSets), since no values can be
        // drawn above each other.
        catNE= getResources().getStringArray(R.array.catNE_list_array);
        ArrayList<String> xVals = new ArrayList<String>();
        double maxValue = 0;
        int maxClassId = 0;
        for (int idEntry = 0; idEntry < classRangeValue.size(); idEntry++) {
            float value = classRangeValue.get(classRangeValue.size() - 1 - idEntry).floatValue();
            yVals1.add(new Entry(value, idEntry));
            xVals.add(catNE[idEntry]);
            if (value > maxValue) {
                maxClassId = idEntry;
                maxValue = value;
            }
        }

        PieDataSet dataSet = new PieDataSet(yVals1, "Sound level");
        dataSet.setSliceSpace(3f);
        dataSet.setColors(NE_COLORS);

        PieData data = new PieData(xVals, dataSet);
        data.setValueFormatter(new CustomPercentFormatter());
        data.setValueTextSize(8f);
        data.setValueTextColor(Color.BLACK);
        rneChart.setData(data);

        // highlight the maximum value of the RNE
        // Find the maximum of the array, in order to be highlighted
        Highlight h = new Highlight(maxClassId, 0);
        rneChart.highlightValues(new Highlight[] { h });
        rneChart.invalidate();
    }

    public void initNEIChart() {
        // NEI PieChart
        neiChart.setUsePercentValues(true);
        neiChart.setHoleColorTransparent(true);
        neiChart.setHoleRadius(75f);
        neiChart.setDescription("");
        neiChart.setDrawCenterText(true);
        neiChart.setDrawHoleEnabled(true);
        neiChart.setRotationAngle(90);
        neiChart.setRotationEnabled(true);
        neiChart.setDrawSliceText(false);
        neiChart.setTouchEnabled(false);
        neiChart.setCenterTextSize(15);
        neiChart.setCenterTextColor(Color.WHITE);
        //return true;
    }

    // Generate artificial data for NEI
    private void setNEIData() {

        ArrayList<Entry> yVals1 = new ArrayList<Entry>();

        // IMPORTANT: In a PieChart, no values (Entry) should have the same
        // xIndex (even if from different DataSets), since no values can be
        // drawn above each other.
        yVals1.add(new Entry( record.getLeqMean(), 0));

        ArrayList<String> xVals = new ArrayList<String>();

        xVals.add(catNE[0 % catNE.length]);

        PieDataSet dataSet = new PieDataSet(yVals1, "NEI");
        dataSet.setSliceSpace(3f);
        int nc=getNEcatColors(record.getLeqMean());    // Choose the color category in function of the sound level
        dataSet.setColor(NE_COLORS[nc]);   // Apply color category for the corresponding sound level

        PieData data = new PieData(xVals, dataSet);
        data.setValueFormatter(new PercentFormatter());
        data.setValueTextSize(11f);
        data.setValueTextColor(Color.BLACK);
        data.setDrawValues(false);

        neiChart.setData(data);
        neiChart.setCenterText(String.format("%.1f", record.getLeqMean()).concat(" dB(A)"));
        rneChart.invalidate();
    }


}
