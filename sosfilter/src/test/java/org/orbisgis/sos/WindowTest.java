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

import com.sun.org.apache.regexp.internal.RE;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Test window overlapping
 */
public class WindowTest {
    public static final double[] STANDARD_FREQUENCIES_UNITTEST = new double[]{100, 125, 160, 200, 250, 315, 400, 500, 630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000, 12500, 16000};

    private void testHann125ms(short[] signal, int sampleRate, double signalLeq) {
        double windowTime = 0.125;
        Window window = new Window(Window.WINDOW_TYPE.HANN, sampleRate,
                STANDARD_FREQUENCIES_UNITTEST, windowTime, false, 0);

        int packetSize = (int)(0.01 * sampleRate);
        int processedFastWindows = 0;
        double sum = 0;
        int idSampleStart = 0;
        while(idSampleStart < signal.length) {
            // Compute sub-sample size in order to not skip samples
            int sampleLen = Math.min(window.getMaximalBufferSize(), packetSize);
            short[] samples = Arrays.copyOfRange(signal, idSampleStart, Math.min(signal.length,idSampleStart+sampleLen));
            idSampleStart+=samples.length;

            window.pushSample(samples);
            if(window.isWindowAvailable()) {
                FFTSignalProcessing.ProcessingResult res = window.processSample();
                sum+=Math.pow(10, res.getGlobaldBaValue() / 10.);
                processedFastWindows++;
            }
        }
        // The record length is 10 seconds
        // The window is 125 ms
        // So there is 80 * 125 ms results
        assertEquals((signal.length / sampleRate) / windowTime, processedFastWindows, 0);
        assertEquals(signalLeq, 10 * Math.log10(sum / processedFastWindows), 0.51);
    }

    private void testRectangular125ms(short[] signal, int sampleRate, double signalLeq) {
        double windowTime = 0.125;
        Window window = new Window(Window.WINDOW_TYPE.RECTANGULAR, sampleRate,
                STANDARD_FREQUENCIES_UNITTEST, windowTime, false, 0);

        int packetSize = (int)(0.01 * sampleRate);
        int processedFastWindows = 0;
        double sum = 0;
        int idSampleStart = 0;
        while(idSampleStart < signal.length) {
            // Compute sub-sample size in order to not skip samples
            int sampleLen = Math.min(window.getMaximalBufferSize(), packetSize);
            short[] samples = Arrays.copyOfRange(signal, idSampleStart, Math.min(signal.length,idSampleStart+sampleLen));
            idSampleStart+=samples.length;

            window.pushSample(samples);
            if(window.isWindowAvailable()) {
                FFTSignalProcessing.ProcessingResult res = window.processSample();
                sum+=Math.pow(10, res.getGlobaldBaValue() / 10.);
                processedFastWindows++;
            }
        }
        // The record length is 10 seconds
        // The window is 125 ms
        // So there is 80 * 125 ms results
        assertEquals((signal.length / sampleRate) / windowTime, processedFastWindows, 0);
        assertEquals(signalLeq, 10 * Math.log10(sum / processedFastWindows), 4.33);
    }
    private void testHann1s(short[] signal, int sampleRate, double signalLeq) {
        double windowTime = 1.0;
        Window window = new Window(Window.WINDOW_TYPE.HANN, sampleRate,
                STANDARD_FREQUENCIES_UNITTEST, windowTime, false, 0);

        int packetSize = (int)(0.01 * sampleRate);
        int processedFastWindows = 0;
        double sum = 0;
        int idSampleStart = 0;
        while(idSampleStart < signal.length) {
            // Compute sub-sample size in order to not skip samples
            int sampleLen = Math.min(window.getMaximalBufferSize(), packetSize);
            short[] samples = Arrays.copyOfRange(signal, idSampleStart, Math.min(signal.length,idSampleStart+sampleLen));
            idSampleStart+=samples.length;

            window.pushSample(samples);
            if(window.isWindowAvailable()) {
                FFTSignalProcessing.ProcessingResult res = window.processSample();
                sum+=Math.pow(10, res.getGlobaldBaValue() / 10.);
                processedFastWindows++;
            }
        }
        // The record length is 10 seconds
        assertEquals((signal.length / sampleRate) / windowTime, processedFastWindows, 0);
        assertEquals(signalLeq, 10 * Math.log10(sum / processedFastWindows), 0.43);
    }


    private void testRectangular1s(short[] signal, int sampleRate, double signalLeq) {
        double windowTime = 1.0;
        Window window = new Window(Window.WINDOW_TYPE.RECTANGULAR, sampleRate,
                STANDARD_FREQUENCIES_UNITTEST, windowTime, false, 0);

        int packetSize = (int)(0.01 * sampleRate);
        int processedFastWindows = 0;
        double sum = 0;
        int idSampleStart = 0;
        while(idSampleStart < signal.length) {
            // Compute sub-sample size in order to not skip samples
            int sampleLen = Math.min(window.getMaximalBufferSize(), packetSize);
            short[] samples = Arrays.copyOfRange(signal, idSampleStart, Math.min(signal.length,idSampleStart+sampleLen));
            idSampleStart+=samples.length;

            window.pushSample(samples);
            if(window.isWindowAvailable()) {
                FFTSignalProcessing.ProcessingResult res = window.processSample();
                sum+=Math.pow(10, res.getGlobaldBaValue() / 10.);
                processedFastWindows++;
            }
        }
        // The record length is 10 seconds
        assertEquals((signal.length / sampleRate) / windowTime, processedFastWindows, 0);
        assertEquals(signalLeq, 10 * Math.log10(sum / processedFastWindows), 4.28);
    }

    @Test
    public void testVoice1() throws IOException {
        final int sampleRate = 44100;
        // Test error induced by window overlapping
        // Read input signal
        InputStream inputStream =  WindowTest.class.getResourceAsStream("speak_44100Hz_16bitsPCM_10s.raw");
        short[] signal = SOSSignalProcessing.loadShortStream(inputStream, ByteOrder.LITTLE_ENDIAN);

        // Compute global leq without filtering frequencies
        FFTSignalProcessing fftSignalProcessing =
                new FFTSignalProcessing(sampleRate, STANDARD_FREQUENCIES_UNITTEST, signal.length);
        fftSignalProcessing.addSample(signal);
        double signalLeq = fftSignalProcessing.computeGlobalLeq();
        assertEquals(83.62, signalLeq, 0.01);

        // Test Hann window 125 ms
        testHann125ms(signal, sampleRate, signalLeq);

        testRectangular125ms(signal, sampleRate, signalLeq);

        testHann1s(signal, sampleRate, signalLeq);

        testRectangular1s(signal, sampleRate, signalLeq);
    }

    @Test
    public void testVoice2() throws IOException {
        final int sampleRate = 44100;
        // Test error induced by window overlapping
        // Read input signal
        InputStream inputStream =  WindowTest.class.getResourceAsStream("pierre_44100Hz_16bitPCM_10s.raw");
        short[] signal = SOSSignalProcessing.loadShortStream(inputStream, ByteOrder.LITTLE_ENDIAN);

        // Compute global leq without filtering frequencies
        FFTSignalProcessing fftSignalProcessing =
                new FFTSignalProcessing(sampleRate, STANDARD_FREQUENCIES_UNITTEST, signal.length);
        fftSignalProcessing.addSample(signal);
        double signalLeq = fftSignalProcessing.computeGlobalLeq();
        //assertEquals(83.62, signalLeq, 0.01);
        double windowTime = 0.125;
        Window window = new Window(Window.WINDOW_TYPE.HANN, sampleRate,
                STANDARD_FREQUENCIES_UNITTEST, windowTime, false, 0);

        int packetSize = (int)(0.01 * sampleRate);
        int processedFastWindows = 0;
        int idSampleStart = 0;
        List<FFTSignalProcessing.ProcessingResult> resFreq = new ArrayList<>();

        while(idSampleStart < signal.length) {
            // Compute sub-sample size in order to not skip samples
            int sampleLen = Math.min(window.getMaximalBufferSize(), packetSize);
            short[] samples = Arrays.copyOfRange(signal, idSampleStart, Math.min(signal.length,idSampleStart+sampleLen));
            idSampleStart+=samples.length;

            window.pushSample(samples);
            if(window.isWindowAvailable()) {
                resFreq.add(window.processSample());
                processedFastWindows++;
            }
        }

        // Compute the final result
        FFTSignalProcessing.ProcessingResult finalResult = new FFTSignalProcessing.ProcessingResult(true, resFreq.toArray(new FFTSignalProcessing.ProcessingResult[resFreq.size()]));

        float[] dbaLevels = finalResult.getdBaLevels();
        for(int idFreq = 0; idFreq < STANDARD_FREQUENCIES_UNITTEST.length; idFreq++) {
            System.out.println(STANDARD_FREQUENCIES_UNITTEST[idFreq] + " " + dbaLevels[idFreq]);
        }
    }

    @Test
    public void testVoice2ButterWorth() throws IOException {
        // Reference sound pressure level [Pa]
        final double REF_SOUND_PRESSURE = Math.sqrt(0.00002);
        final int sampleRate = 44100;
        // Test error induced by window overlapping
        // Read input signal
        InputStream inputStream =  WindowTest.class.getResourceAsStream("pierre_44100Hz_16bitPCM_10s.raw");
        //short[] signal = SOSSignalProcessing.loadShortStream(inputStream, ByteOrder.LITTLE_ENDIAN);
        //double[] doubleSignal = new double[signal.length];
        //for(int t = 0; t < signal.length; t++) {
        //    doubleSignal[t] = (double)signal[t];
        //}

        SOSSignalProcessing sosSignalProcessing = new SOSSignalProcessing(sampleRate, ThirdOctaveBandsFiltering.FREQUENCY_BANDS.REDUCED);
        sosSignalProcessing.setAweighting(false);

        List<double[]> timeLevels = sosSignalProcessing.processAudio(16, sampleRate, inputStream, 1., REF_SOUND_PRESSURE, ByteOrder.LITTLE_ENDIAN);


        double[] freqLeq = new double[ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED.length];
        for(int timestep = 0; timestep < timeLevels.size(); timestep++) {
            double[] leqVals = timeLevels.get(timestep);
            for(int idFreq = 0; idFreq < ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED.length; idFreq++) {
                freqLeq[idFreq] += Math.pow(10, leqVals[idFreq] / 10.0);
            }
        }
        for(int idFreq = 0; idFreq < ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED.length; idFreq++) {
            double dbVal = 10 * Math.log10(freqLeq[idFreq] / timeLevels.size());
            System.out.println(ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[idFreq] + " " + dbVal);
        }
    }
}