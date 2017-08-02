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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * This canvas drawer receive thin or third octave frequency SPL and draw it.
 */
public class Spectrogram extends View {
    public enum SCALE_MODE {SCALE_LINEAR, SCALE_LOG};
    private SCALE_MODE scaleMode = SCALE_MODE.SCALE_LOG;
    private final List<float[]> spectrumData = new LinkedList<>();
    private Bitmap spectrogramBuffer = null;
    private Bitmap frequencyLegend = null;
    private Bitmap timeLegend = null;
    private static final int FREQUENCY_LEGEND_TIC_WIDTH = 4;
    private static final int TIME_LEGEND_TIC_HEIGHT = 4;
    private int canvasHeight = -1;
    private int canvasWidth = -1;
    private int initCanvasHeight = -1;
    private int initCanvasWidth = -1;
    private static final float min = 0;
    private static final float max = 70;
    private static final int[] frequencyLegendPositionLog =  new int[] {63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};
    private static final int[] frequencyLegendPositionLinear =  new int[] {0, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 10000, 12500, 16000};
    private int[] frequencyLegendPosition = frequencyLegendPositionLog;
    private static final int FREQUENCY_LEGEND_TEXT_SIZE = 18;
    private double timeStep;
    private boolean doRedraw = false;
    private static final int MAXIMUM_SPECTROGRAM_BUFFER = 400 / FREQUENCY_LEGEND_TIC_WIDTH;
    /** Color ramp, using http://www.zonums.com/online/color_ramp/ */

    private static final int[] colorRamp = new int[]{
            p("#303030"),
            p("#2D3C2D"),
            p("#2A482A"),
            p("#275427"),
            p("#246024"),
            p("#216C21"),
            p("#3F8E19"),
            p("#61A514"),
            p("#82BB0F"),
            p("#A4D20A"),
            p("#C5E805"),
            p("#E7FF00"),
            p("#EBD400"),
            p("#EFAA00"),
            p("#F37F00"),
            p("#F75500"),
            p("#FB2A00"),
    };

    public Spectrogram(Context context) {
        super(context);
    }

    public Spectrogram(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public Spectrogram(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private static int p(String color) {
        return Color.parseColor(color);
    }

    public void setScaleMode(SCALE_MODE scaleMode) {
        this.scaleMode = scaleMode;
        doRedraw = true;
    }

    /**
     * @param timeStep Call delay of addTimeStep for time legend.
     */
    public void setTimeStep(double timeStep) {
        this.timeStep = timeStep;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvasWidth = canvas.getWidth();
        canvasHeight = canvas.getHeight();
        if(spectrogramBuffer != null) {
            canvas.drawBitmap(spectrogramBuffer, 0, 0, null);
            canvas.drawBitmap(frequencyLegend, spectrogramBuffer.getWidth(), 0, null);
            canvas.drawBitmap(timeLegend, 0, spectrogramBuffer.getHeight(), null);
        } else {
            canvas.drawColor(colorRamp[0]);
        }
    }

    public static String formatFrequency(int frequency) {
        if(frequency > 1000) {
            return new DecimalFormat("#.## kHz").format(frequency / 1000.);
        } else {
            return String.format(Locale.US, "%d Hz", frequency);
        }
    }

    /**
     * Add the spectrum as a new spectrogramImage column
     * @param spectrum FFT response
     * @param hertzBySpectrumCell Used to build the legend. How many hertz are covered by one FFT response cell
     */
    public void addTimeStep(float[] spectrum, double hertzBySpectrumCell) {
        final int ticWidth = 4; // Timestep width in pixels
        spectrumData.add(0, spectrum);
        if(spectrumData.size() > MAXIMUM_SPECTROGRAM_BUFFER) {
            spectrumData.remove(spectrumData.size() - 1);
        }
        if(canvasWidth > 0 && canvasHeight > 0) {
            if (spectrogramBuffer == null || initCanvasWidth != canvasWidth ||
                    initCanvasHeight != canvasHeight || doRedraw ) {
                doRedraw = false;
                frequencyLegendPosition = scaleMode == SCALE_MODE.SCALE_LOG ?
                        frequencyLegendPositionLog : frequencyLegendPositionLinear;
                initCanvasWidth = canvasWidth;
                initCanvasHeight = canvasHeight;
                // Build legend
                // Determine legend bitmap width
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.WHITE);
                paint.setTextAlign(Paint.Align.LEFT);
                paint.setTextSize(FREQUENCY_LEGEND_TEXT_SIZE);
                Rect bounds = new Rect();

                //
                int legendWidth = 0;
                String[] frequencyLegendLabels = new String[frequencyLegendPosition.length];
                for(int freqIndex = 0; freqIndex < frequencyLegendLabels.length; freqIndex++) {
                    String frequencyLegendLabel = formatFrequency(frequencyLegendPosition[freqIndex]);
                    frequencyLegendLabels[freqIndex] = frequencyLegendLabel;
                    paint.getTextBounds(frequencyLegendLabel, 0, frequencyLegendLabel.length(), bounds);
                    legendWidth = Math.max(legendWidth, bounds.width());
                }
                legendWidth += FREQUENCY_LEGEND_TIC_WIDTH;
                // Construct time legend
                paint.getTextBounds("+SS.d", 0, "+SS.d".length(), bounds);
                timeLegend = Bitmap.createBitmap(canvasWidth, bounds.height() +
                        TIME_LEGEND_TIC_HEIGHT,
                        Bitmap.Config.ARGB_8888);
                Canvas timeLegendCanvas = new Canvas(timeLegend);
                double timeCursor = 0;
                // Add a small tic each 1 s and a big one with label each second
                final double ticStep = 1.;
                float timePos = canvasWidth - legendWidth;
                int maximumLabels = (int)Math.floor((canvasWidth - legendWidth) / bounds.width());
                double maximumShownTime = ((canvasWidth - legendWidth) / ticWidth) * timeStep;
                int stepByPrintLabels = (int)Math.ceil(maximumShownTime / maximumLabels);
                int ticPrinted = 0;
                while(timePos > 0) {
                    timePos = (float)((canvasWidth - legendWidth) - ticWidth * (timeCursor / timeStep));
                    timeLegendCanvas.drawLine(timePos, 0, timePos, TIME_LEGEND_TIC_HEIGHT, paint);
                    if(stepByPrintLabels > 0 && ticPrinted % stepByPrintLabels == 0) {
                        String text = String.format("+%.1f", timeCursor);
                        paint.getTextBounds(text, 0, text.length(), bounds);
                        float textX = timePos - (bounds.width() / 2);
                        timeLegendCanvas.drawText(text, textX, TIME_LEGEND_TIC_HEIGHT + Math.abs(bounds.top), paint);
                    }
                    timeCursor += ticStep;
                    ticPrinted++;
                }
                // Make empty legend bitmap
                frequencyLegend = Bitmap.createBitmap(legendWidth, canvasHeight - timeLegend.getHeight(),
                        Bitmap.Config.ARGB_8888);
                // Draw text on legend
                Canvas legendCanvas = new Canvas(frequencyLegend);
                final int spectrogramHeight = canvasHeight - timeLegend.getHeight();
                double fmax = spectrum.length * hertzBySpectrumCell;
                double fmin = frequencyLegendPosition[0];
                double cellByPixel = spectrum.length / (double)spectrogramHeight;
                double r = fmax / fmin;
                for(int freqIndex = 0; freqIndex < frequencyLegendPosition.length; freqIndex++) {
                    int frequency = frequencyLegendPosition[freqIndex];
                    float tickHeightPos;
                    if(scaleMode == SCALE_MODE.SCALE_LOG) {
                        tickHeightPos = (float) (frequencyLegend.getHeight() - (spectrogramHeight
                                * Math.log(frequency / fmin) / Math.log(r)));
                    } else {
                        tickHeightPos = (float) (frequencyLegend.getHeight() -
                        frequency / (cellByPixel * hertzBySpectrumCell) );
                    }
                    float heightPos = Math.max(bounds.height(), tickHeightPos + (bounds.height() / 2));
                    final String labelFreq = frequencyLegendLabels[freqIndex];
                    paint.getTextBounds(labelFreq, 0, labelFreq.length(), bounds);
                    if(bounds.height() + heightPos > frequencyLegend.getHeight()) {
                        heightPos = frequencyLegend.getHeight() - bounds.height() / 2;
                    }
                    legendCanvas.drawText(labelFreq, FREQUENCY_LEGEND_TIC_WIDTH + bounds.left, heightPos, paint);
                    legendCanvas.drawLine(0, tickHeightPos, FREQUENCY_LEGEND_TIC_WIDTH, tickHeightPos, paint);
                }
                spectrogramBuffer = Bitmap.createBitmap(canvasWidth - legendWidth, spectrogramHeight,
                        Bitmap.Config.ARGB_8888);
                spectrogramBuffer.eraseColor(colorRamp[0]);
            } else {
                // Move spectrum on the left
                Canvas canvas = new Canvas(spectrogramBuffer);
                canvas.drawBitmap(spectrogramBuffer, -(spectrumData.size() * ticWidth), 0, null);
            }
            Canvas canvas = new Canvas(spectrogramBuffer);
            int drawnTics = 0;
            final int spectrogramHeight = spectrogramBuffer.getHeight();
            final int spectrogramWidth = spectrogramBuffer.getWidth();
            while ( !spectrumData.isEmpty()) {
                float[] ticSpectrum = spectrumData.remove(0);
                // Rescale to the range of color ramp
                Bitmap ticBuffer = Bitmap.createBitmap(ticWidth, spectrogramHeight,
                        Bitmap.Config.ARGB_8888);
                int[] ticColors = new int[spectrogramHeight * ticWidth];

                // merge frequencies following the destination resolution
                double freqByPixel = ticSpectrum.length / (double)spectrogramHeight;
                double fmax = spectrum.length * hertzBySpectrumCell;
                double fmin = frequencyLegendPosition[0];
                double r = fmax / fmin;
                int lastProcessFrequencyIndex = 0;
                for(int pixel = 0; pixel < spectrogramHeight; pixel++) {
                    // Compute frequency range covered by this pixel
                    double f = fmin * Math.pow(10, pixel * Math.log10(r) / frequencyLegend.getHeight());
                    float sumVal = 0;
                    int nextFrequencyIndex = Math.min(spectrum.length, (int)(f / hertzBySpectrumCell));
                    int freqStart;
                    int freqEnd;
                    if(scaleMode == SCALE_MODE.SCALE_LOG) {
                        freqStart = lastProcessFrequencyIndex;
                        freqEnd = Math.min(spectrum.length, (int)(f / hertzBySpectrumCell) + 1);
                    } else {
                        freqStart = (int)Math.floor(pixel * freqByPixel);
                        freqEnd =(int) Math.min(pixel * freqByPixel + freqByPixel, ticSpectrum.length);
                    }
                    for (int idfreq = freqStart; idfreq < freqEnd; idfreq++) {
                        // Rescale value and pick the color in the color ramp
                        sumVal += Math.pow(10, ticSpectrum[idfreq] / 10);
                    }
                    lastProcessFrequencyIndex = Math.min(spectrum.length, nextFrequencyIndex);
                    sumVal = (float)Math.max(0,
                            (10 * Math.log10(sumVal)));
                    int pixColor = getColor(sumVal, min, max);
                    for(int y = 0; y < ticWidth; y++) {
                        ticColors[((spectrogramHeight - 1) - pixel) * ticWidth + y] = pixColor;
                    }
                }
                ticBuffer.setPixels(ticColors, 0, ticWidth, 0, 0, ticWidth, spectrogramHeight);
                int leftPos = spectrogramWidth - ((drawnTics + 1) * ticWidth);
                canvas.drawBitmap(ticBuffer, leftPos, 0, null);
                drawnTics++;
            }
            postInvalidate(); // redraws the view calling onDraw()
        }
    }

    public static int getColor(float sel, float min, float max) {
        return colorRamp[Math.min(colorRamp.length - 1, Math.max(0,
                (int) (((sel - min) / (max - min)) * colorRamp.length)))];
    }
}
