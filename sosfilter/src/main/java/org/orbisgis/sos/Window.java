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

import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Overlaps the window of signal processing.
 */
public class Window {
    enum WINDOW_TYPE { RECTANGULAR, HANN }
    public final WINDOW_TYPE window;
    private FFTSignalProcessing signalProcessing;
    // processed sample index
    private int lastProcessedSpectrum = 0;
    // added samples
    private int pushedSamples;
    private int samplingRate;
    private double windowTime;
    private int windowSize;
    // The size of this buffer is dependent on window type, sampling rate and window size
    private short[] sampleBuffer;
    private boolean aWeighting;
    private long beginRecordTime = 0;

    public Window(WINDOW_TYPE window, int samplingRate, double[] standardFrequencies,
                  double windowTime, boolean aWeighting, long beginRecordTime) {
        this.signalProcessing = new FFTSignalProcessing(samplingRate, standardFrequencies,
                (int)(samplingRate * windowTime));
        this.samplingRate = samplingRate;
        this.window = window;
        this.windowTime = windowTime;
        this.aWeighting = aWeighting;
        this.beginRecordTime = beginRecordTime;
        this.windowSize = (int)(samplingRate * windowTime);
        this.sampleBuffer = new short[window == WINDOW_TYPE.HANN ?
                (int)(Math.ceil(samplingRate * (5. / 3.) * windowTime ))
                : windowSize];
    }

    public FFTSignalProcessing.ProcessingResult processSample() {
        lastProcessedSpectrum = pushedSamples;
        if(window == WINDOW_TYPE.HANN) {
            // Window, energetic sum of overlapping
            int step = (int)(samplingRate * (windowTime / 3.));
            int start = Math.max(0, sampleBuffer.length - step * 5);
            FFTSignalProcessing.ProcessingResult[] results =
                    new FFTSignalProcessing.ProcessingResult[3];
            signalProcessing.addSample(Arrays.copyOfRange(sampleBuffer, start,
                    Math.min(sampleBuffer.length, start + (3 * step))));
            results[0] = signalProcessing.processSample(true,
                    aWeighting, true);
            start +=  3 * step;
            signalProcessing.addSample(Arrays.copyOfRange(sampleBuffer, start,
                    Math.min(sampleBuffer.length, start + step)));
            results[1] = signalProcessing.processSample(true,
                    aWeighting, true);
            start += step;
            signalProcessing.addSample(Arrays.copyOfRange(sampleBuffer, start,
                    Math.min(sampleBuffer.length, start + step)));
            results[2] = signalProcessing.processSample(true,
                    aWeighting, true);
            return new FFTSignalProcessing.ProcessingResult(false, results);
        } else {
            //window == WINDOW_TYPE.RECTANGULAR
            signalProcessing.addSample(sampleBuffer);
            return signalProcessing.processSample(false,
                    aWeighting, true);
        }
    }

    /**
     * @return True if the defined windowTime is pushed since the last call of processSample
     */
    public boolean isWindowAvailable() {
        return pushedSamples - lastProcessedSpectrum >= windowSize;
    }

    /**
     * @return The maximal buffer size provided in pushSample in order to not skip a result
     */
    public int getMaximalBufferSize() {
        return Math.max(0, windowSize - (pushedSamples - lastProcessedSpectrum));
    }

    /**
     * Push samples. If the
     * @param buffer Audio signal. If the length is superior than the
     * @return The last result, null if the pushed samples was not enough to get a leq.
     */
    public void pushSample(short[] buffer) {
        if(buffer.length < sampleBuffer.length) {
            // Move previous samples backward
            System.arraycopy(sampleBuffer, buffer.length, sampleBuffer, 0, sampleBuffer.length - buffer.length);
            System.arraycopy(buffer, 0, sampleBuffer, sampleBuffer.length - buffer.length, buffer.length);
        } else {
            // Take last samples
            System.arraycopy(buffer, Math.max(0, buffer.length - sampleBuffer.length), sampleBuffer, 0,
                    sampleBuffer.length);
        }
        pushedSamples += buffer.length;
    }
}
