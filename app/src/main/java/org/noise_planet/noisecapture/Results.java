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
 * Copyright (C) 2007-2016 - IFSTTAR - LAE
 * Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
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
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.FileProvider;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.ShareActionProvider;
import android.view.Menu;
import android.view.MenuItem;
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
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.formatter.LargeValueFormatter;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;

import org.noise_planet.noisecapture.util.CustomPercentFormatter;
import org.orbisgis.sos.LeqStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Results extends MainActivity implements ShareActionProvider.OnShareTargetSelectedListener{
    public static final String RESULTS_RECORD_ID = "RESULTS_RECORD_ID";
    private static final Logger LOGGER = LoggerFactory.getLogger(Results.class);
    private MeasurementManager measurementManager;
    private Storage.Record record;
    private static final double[][] CLASS_RANGES = new double[][]{{Double.MIN_VALUE, 45}, {45, 55},
            {55, 65}, {65, 75},{75, Double.MAX_VALUE}};


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
        this.measurementManager = new MeasurementManager(this);
        Intent intent = getIntent();
        if(intent != null && intent.hasExtra(RESULTS_RECORD_ID)) {
            record = measurementManager.getRecord(intent.getIntExtra(RESULTS_RECORD_ID, -1), false);
        } else {
            // Read the last stored record
            List<Storage.Record> recordList = measurementManager.getRecords(false);
            if(!recordList.isEmpty()) {
                record = recordList.get(recordList.size() - 1);
            } else {
                // Message for starting a record
                Toast.makeText(getApplicationContext(),
                        getString(R.string.no_results), Toast.LENGTH_LONG).show();
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
        findViewById(R.id.textView_color_Min_SL)
                .setBackgroundColor(NE_COLORS[getNEcatColors(leqStats.getLeqMin())]);

        TextView maxText = (TextView) findViewById(R.id.textView_value_Max_SL);
        maxText.setText(String.format("%.01f", leqStats.getLeqMax()));
        findViewById(R.id.textView_color_Max_SL)
                .setBackgroundColor(NE_COLORS[getNEcatColors(leqStats.getLeqMax())]);

        TextView la10Text = (TextView) findViewById(R.id.textView_value_LA10);
        la10Text.setText(String.format("%.01f", leqOccurrences.getLa10()));
        findViewById(R.id.textView_color_LA10)
                .setBackgroundColor(NE_COLORS[getNEcatColors(leqOccurrences.getLa10())]);

        TextView la50Text = (TextView) findViewById(R.id.textView_value_LA50);
        la50Text.setText(String.format("%.01f", leqOccurrences.getLa50()));
        findViewById(R.id.textView_color_LA50)
                .setBackgroundColor(NE_COLORS[getNEcatColors(leqOccurrences.getLa50())]);

        TextView la90Text = (TextView) findViewById(R.id.textView_value_LA90);
        la90Text.setText(String.format("%.01f", leqOccurrences.getLa90()));
        findViewById(R.id.textView_color_LA90)
                .setBackgroundColor(NE_COLORS[getNEcatColors(leqOccurrences.getLa90())]);

        // Action on Map button
        ImageButton buttonmap=(ImageButton)findViewById(R.id.mapBtn);
        buttonmap.setEnabled(true);
        buttonmap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Go to map page
                Intent a = new Intent(getApplicationContext(), MapActivity.class);
                a.putExtra(MapActivity.RESULTS_RECORD_ID, record.getId());
                startActivity(a);
            }
        });

        // Action on comment button
        ImageButton buttonComment=(ImageButton)findViewById(R.id.userCommentBtn);
        buttonComment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Go to map page
                Intent a = new Intent(getApplicationContext(), CommentActivity.class);
                a.putExtra(CommentActivity.COMMENT_RECORD_ID, record.getId());
                startActivity(a);
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_result, menu);


        // Locate MenuItem with ShareActionProvider
        MenuItem item = menu.findItem(R.id.menu_item_share);

        // Fetch and store ShareActionProvider
        ShareActionProvider mShareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(item);

        File requestFile = getSharedFile();
        Uri fileUri = FileProvider.getUriForFile(
        this,
        "org.noise_planet.noisecapture.fileprovider",
        requestFile);


        Intent mResultIntent = new Intent(Intent.ACTION_SEND);

        mResultIntent.setDataAndType(fileUri, "application/zip");
        mResultIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        mResultIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if(mShareActionProvider != null) {
            mShareActionProvider.setShareIntent(mResultIntent);
            mShareActionProvider.setOnShareTargetSelectedListener(this);
        }
        // Return true to display menu
        return true;
    }

    private File getSharedFile() {
        return new File(getCacheDir().getAbsolutePath() ,MeasurementExport.ZIP_FILENAME);
    }

    @Override
    public boolean onShareTargetSelected(ShareActionProvider source, Intent intent) {
        // Write file
        try {
            File file = getSharedFile();
            // Create parent dirs if necessary
            file.getParentFile().mkdirs();
            FileOutputStream fop = new FileOutputStream(file);
            try {
                MeasurementExport measurementExport = new MeasurementExport(this);
                measurementExport.exportRecord(this, record.getId(), fop);
            } finally {
                fop.close();
            }
        } catch (IOException ex) {
            Toast.makeText(getApplicationContext(),
                    getString(R.string.fail_share), Toast.LENGTH_LONG).show();
            LOGGER.error(ex.getLocalizedMessage(), ex);
        }
        return false;
    }

    private void loadMeasurement() {
        Resources resources = getResources();

        // Query database
        List<Integer> frequencies = new ArrayList<Integer>();
        List<Float[]> leqValues = new ArrayList<Float[]>();
        measurementManager.getRecordLeqs(record.getId(), frequencies, leqValues);

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
            ltob[idFreq] = Spectrogram.formatFrequency(frequencies.get(idFreq));
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
        // XAxis parameters: hide all
        XAxis xls = sChart.getXAxis();
        xls.setPosition(XAxisPosition.BOTTOM);
        xls.setDrawAxisLine(true);
        xls.setDrawGridLines(false);
        xls.setLabelRotationAngle(-90);
        xls.setDrawLabels(true);
        xls.setTextColor(Color.WHITE);
        // YAxis parameters (left): main axis for dB values representation
        YAxis yls = sChart.getAxisLeft();
        yls.setDrawAxisLine(true);
        yls.setDrawGridLines(true);
        yls.setAxisMaxValue(110.f);
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

        ArrayList<IBarDataSet> dataSets = new ArrayList<IBarDataSet>();
        dataSets.add(set1);

        BarData data = new BarData(xVals, dataSets);
        data.setValueTextSize(10f);

        sChart.setData(data);
        sChart.invalidate();
    }

    // Init RNE Pie Chart
    public void initRNEChart(){
        rneChart.setUsePercentValues(true);
        rneChart.setHoleColor(Color.TRANSPARENT);
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
        neiChart.setHoleColor(Color.TRANSPARENT);
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
        int nc=getNEcatColors(leqStats.getLeqMean());    // Choose the color category in function of the sound level
        dataSet.setColor(NE_COLORS[nc]);   // Apply color category for the corresponding sound level

        PieData data = new PieData(xVals, dataSet);
        data.setValueFormatter(new PercentFormatter());
        data.setValueTextSize(11f);
        data.setValueTextColor(Color.BLACK);
        data.setDrawValues(false);

        neiChart.setData(data);
        neiChart.setCenterText(String.format("%.1f", leqStats.getLeqMean()).concat(" dB(A)"));
        rneChart.invalidate();
    }


}
