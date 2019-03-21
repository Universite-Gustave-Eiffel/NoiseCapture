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

package org.orbisgis.sos;

import java.util.Arrays;

/**
 * Overlaps the window of signal processing.
 */

public class Window {
    public final FFTSignalProcessing.WINDOW_TYPE window;
    private FFTSignalProcessing signalProcessing;
    // processed sample index
    private int lastProcessedSpectrum = 0;
    // added samples
    private int pushedSamples;
    private int windowSize;
    private boolean aWeighting;
    private boolean outputThinFrequency;
    private double overlap = 0;
    private FFTSignalProcessing.ProcessingResult[] windowResults;

    public Window(FFTSignalProcessing.WINDOW_TYPE window, int samplingRate, double[] standardFrequencies,
                  double windowTime, boolean aWeighting,
                  double dbFsReference,boolean outputThinFrequency) {

        this(window, samplingRate, standardFrequencies, windowTime, aWeighting, dbFsReference,
                outputThinFrequency, 0);
    }

    /**
     *
     * @param window
     * @param samplingRate
     * @param standardFrequencies
     * @param windowTime
     * @param aWeighting
     * @param dbFsReference
     * @param outputThinFrequency
     * @param overlap TODO Fix window overlapping
     */
    public Window(FFTSignalProcessing.WINDOW_TYPE window, int samplingRate, double[] standardFrequencies,
                  double windowTime, boolean aWeighting,
                  double dbFsReference,boolean outputThinFrequency, double overlap) {
        this.overlap = overlap;
        this.signalProcessing = new FFTSignalProcessing(samplingRate, standardFrequencies,
                (int)(samplingRate * windowTime), dbFsReference);
        this.window = window;
        this.aWeighting = aWeighting;
        this.windowSize = (int)(samplingRate * windowTime);
        this.windowResults = new FFTSignalProcessing.ProcessingResult[(int)(Math.round(1 / (1 - overlap)))];
        this.outputThinFrequency = outputThinFrequency;
    }

    public boolean isOutputThinFrequency() {
        return outputThinFrequency;
    }

    public void setaWeighting(boolean aWeighting) {
        this.aWeighting = aWeighting;
    }

    public FFTSignalProcessing.WINDOW_TYPE getWindowType() {
        return window;
    }

    public boolean isAWeighting() {
        return aWeighting;
    }

    public double getWindowTime() {
        return signalProcessing.getSampleDuration();
    }

    public double computeWindowLeq() {
        return signalProcessing.computeSpl(aWeighting);
    }
    /**
     * Process the current window
     */
    private void processSample() {
        lastProcessedSpectrum = pushedSamples;
        FFTSignalProcessing.ProcessingResult result = signalProcessing.processSample(window,
                aWeighting, true);
        // Move result by 1 backward
        if(windowResults.length > 1) {
            System.arraycopy(windowResults, 1, windowResults, 0, windowResults.length - 1);
        }
        windowResults[windowResults.length - 1] = result;
    }

    /**
     * Remove stored windows
     */
    public void cleanWindows() {
        Arrays.fill(windowResults, null);
    }

    /**
     * @return The sum of overlaps windows, null if not available
     */
    public FFTSignalProcessing.ProcessingResult getLastWindowMean() {
        if(windowResults.length > 1) {
            return new FFTSignalProcessing.ProcessingResult(1, windowResults);
        } else {
            return windowResults[0];
        }
    }

    /**
     * @return False if a window mean is available
     */
    public boolean isCacheEmpty() {
        for(FFTSignalProcessing.ProcessingResult windowCache : windowResults) {
            if(windowCache != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return The non-overlaped window index
     */
    public int getWindowIndex() {
        return lastProcessedSpectrum / windowSize;
    }

    public double getOverlap() {
        return overlap;
    }

    /**
     * @return The maximal buffer size provided in pushSample in order to not skip a result
     */
    public int getMaximalBufferSize() {
        return Math.max(0, (int)(windowSize * (1 - overlap)) - (pushedSamples - lastProcessedSpectrum));
    }

    /**
     * Push samples. If the
     * @param buffer Audio signal. If the length is superior than the
     * @return The last result, null if the pushed samples was not enough to get a leq.
     */
    public void pushSample(short[] buffer) {
        signalProcessing.addSample(buffer);
        pushedSamples += buffer.length;
        if(pushedSamples - lastProcessedSpectrum >= (int)(windowSize * (1 - overlap))) {
            processSample();
        }
    }
}
