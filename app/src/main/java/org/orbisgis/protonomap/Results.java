package org.orbisgis.protonomap;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

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

import java.util.ArrayList;

public class Results extends MainActivity {

    static float Leqi; // for testing

    // For the Charts
    public PieChart rneChart;
    public PieChart neiChart;
    protected BarChart sChart; // Spectrum representation

    // Other ressources
    private String[] ltob;  // List of third-octave bands (defined as ressources)
    private String[] catNE; // List of noise level category (defined as ressources)

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results);
        initDrawer();

        // RNE PieChart
        rneChart = (PieChart) findViewById(R.id.RNEChart);
        initRNEChart();
        setRNEData(5, 100);
        Legend lrne = rneChart.getLegend();
        lrne.setTextColor(Color.WHITE);
        lrne.setTextSize(8f);
        lrne.setPosition(LegendPosition.RIGHT_OF_CHART_CENTER);
        lrne.setEnabled(true);

        // NEI PieChart
        neiChart = (PieChart) findViewById(R.id.NEIChart);
        initNEIChart();
        setNEIData(100);
        Legend lnei = neiChart.getLegend();
        lnei.setEnabled(false);

        // Cumulated spectrum
        sChart = (BarChart) findViewById(R.id.spectrumChart);
        initSpectrumChart();
        setDataS(30, 115);
        Legend ls = sChart.getLegend();
        ls.setEnabled(false); // Hide legend

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
                Intent a = new Intent(getApplicationContext(), Map.class);
                startActivity(a);;
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
                //DoProcessing(getApplicationContext(), this);
            }
        });


    }

    public void initSpectrumChart(){
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

    // Generate artificial data for spectrum representation
    private void setDataS(int count, float range) {

        ArrayList<String> xVals = new ArrayList<String>();
        ltob= getResources().getStringArray(R.array.tob_list_array);
        for (int i = 0; i < count; i++) {
            xVals.add(ltob[i % 30]);
        }

        ArrayList<BarEntry> yVals1 = new ArrayList<BarEntry>();

        for (int i = 0; i < count; i++) {
            float mult = (range + 1);
            float val = (float) (20f+Math.random() * mult);
            yVals1.add(new BarEntry(val, i));
        }

        BarDataSet set1 = new BarDataSet(yVals1, "DataSet");
        //set1.setBarSpacePercent(35f);
        //set1.setColors(new int[] {Color.rgb(0, 153, 204), Color.rgb(0, 153, 204), Color.rgb(0, 153, 204),
        //        Color.rgb(51, 181, 229), Color.rgb(51, 181, 229), Color.rgb(51, 181, 229)});
        set1.setColors(new int[] {Color.rgb(0, 128, 255), Color.rgb(0, 128, 255), Color.rgb(0, 128, 255),
                Color.rgb(102, 178, 255), Color.rgb(102, 178, 255), Color.rgb(102, 178, 255)});

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


    // Generate artificial data for RNE
    private void setRNEData(int count, float range) {

        float mult3 = range;
        double[] tab = new double[count];
        int[] color_rep=NE_COLORS();

        ArrayList<Entry> yVals1 = new ArrayList<Entry>();

        // IMPORTANT: In a PieChart, no values (Entry) should have the same
        // xIndex (even if from different DataSets), since no values can be
        // drawn above each other.
        for (int i = 0; i < count ; i++) {
            double randomvalue=(Math.random() * mult3) + mult3 / 5;
            yVals1.add(new Entry((float) randomvalue, i));
            tab[i]=randomvalue;
        }

        ArrayList<String> xVals = new ArrayList<String>();

        catNE= getResources().getStringArray(R.array.catNE_list_array);
        for (int i = 0; i < count + 1; i++)
            xVals.add(catNE[i % catNE.length]);

        PieDataSet dataSet = new PieDataSet(yVals1, "Sound level");
        dataSet.setSliceSpace(3f);
        dataSet.setColors(color_rep);

        PieData data = new PieData(xVals, dataSet);
        data.setValueFormatter(new PercentFormatter());
        data.setValueTextSize(8f);
        data.setValueTextColor(Color.BLACK);
        rneChart.setData(data);

        // highlight the maximum value of the RNE
        // Find the maximum of the array, in order to be highlighted
        int pos=posmax(tab, count);
        Highlight h = new Highlight(pos, 0);
        rneChart.highlightValues(new Highlight[] { h });
        rneChart.invalidate();
    }

    // Find the position of the maximum of an array
    public int posmax(double[] tab, int size){
        {
            int pos = 0;
            int i = 0;

            while (i < size)
            {
                if (tab[i] > tab[pos])
                    pos = i;

                i++;
            }

            return pos;
        }
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
    private void setNEIData(float range) {

        float mult5 = range;
        int[] color_rep=NE_COLORS();

        ArrayList<Entry> yVals1 = new ArrayList<Entry>();

        // IMPORTANT: In a PieChart, no values (Entry) should have the same
        // xIndex (even if from different DataSets), since no values can be
        // drawn above each other.
        Leqi = (float) Math.random() * mult5;
        yVals1.add(new Entry( Leqi, 0));

        ArrayList<String> xVals = new ArrayList<String>();

        xVals.add(catNE[0 % catNE.length]);

        PieDataSet dataSet = new PieDataSet(yVals1, "NEI");
        dataSet.setSliceSpace(3f);
        int nc=getNEcatColors(Leqi);    // Choose the color category in function of the sound level
        dataSet.setColor(color_rep[nc]);   // Apply color category for the corresponding sound level

        PieData data = new PieData(xVals, dataSet);
        data.setValueFormatter(new PercentFormatter());
        data.setValueTextSize(11f);
        data.setValueTextColor(Color.BLACK);
        data.setDrawValues(false);

        neiChart.setData(data);
        neiChart.setCenterText(String.format("%.1f", Leqi).concat(" dB(A)"));
        rneChart.invalidate();
    }


}
