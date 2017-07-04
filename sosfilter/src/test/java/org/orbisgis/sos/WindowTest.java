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
    public static final double REF_SOUND_PRESSURE = 1 / Math.pow(10, FFTSignalProcessing.DB_FS_REFERENCE / 20);

    private double getGlobalLeq(double[] leq) {
        double expectedGlobalLeq = 0;
        for (double anExpectedLeq : leq) {
            expectedGlobalLeq += Math.pow(10, anExpectedLeq / 10.);
        }
        return 10 * Math.log10(expectedGlobalLeq);
    }


    private double getGlobalLeq(float[] leq) {
        double expectedGlobalLeq = 0;
        for (double anExpectedLeq : leq) {
            expectedGlobalLeq += Math.pow(10, anExpectedLeq / 10.);
        }
        return 10 * Math.log10(expectedGlobalLeq);
    }

    private double getDelta(double[] expectedLeq, float[] dbResult) {
        double expectedGlobalLeq = getGlobalLeq(expectedLeq);
        double resultGlobalLeq = getGlobalLeq(dbResult);
        return resultGlobalLeq - expectedGlobalLeq;
    }

    /**
     *
     * @param expectedLeq Expected spectrum
     * @param dbResult Original computed spectrum
     * @param delta Apply this delta to dbResult before comparing
     * @param maximalDeviation Fail if one of frequency band is superior than this error (dB)
     */
    private void checkSplSpectrum(double[] expectedLeq, float[] dbResult,double delta, double maximalDeviation) {
        double[] normalisedDbResult = new double[dbResult.length];
        for(int idFreq = 0; idFreq < dbResult.length; idFreq++) {
            normalisedDbResult[idFreq] = dbResult[idFreq] - delta;
        }
        assertArrayEquals(expectedLeq, normalisedDbResult, maximalDeviation);
    }

    private float[] testFFTWindow(short[] signal, int sampleRate,double windowTime,Window.WINDOW_TYPE windowType) {
        Window window = new Window(windowType, sampleRate,
                STANDARD_FREQUENCIES_UNITTEST, windowTime, false, 0, FFTSignalProcessing.DB_FS_REFERENCE);

        int packetSize = (int) (0.01 * sampleRate);
        int processedFastWindows = 0;
        int idSampleStart = 0;
        List<FFTSignalProcessing.ProcessingResult> res = new ArrayList<>();
        while (idSampleStart < signal.length) {
            // Compute sub-sample size in order to not skip samples
            int sampleLen = Math.min(window.getMaximalBufferSize(), packetSize);
            short[] samples = Arrays.copyOfRange(signal, idSampleStart, Math.min(signal.length, idSampleStart + sampleLen));
            idSampleStart += samples.length;

            window.pushSample(samples);
            if (window.isWindowAvailable()) {
                res.add(window.processSample());
                processedFastWindows++;
            }
        }
        // Check if the expected result size is here
        // Ex:
        // The signal is 10s and the window is 125 ms
        // So there is 80 * 125 ms results
        assertEquals((int)(Math.round((signal.length / sampleRate) / (windowTime * (1 - window.getOverlap())))), processedFastWindows, 0);

        FFTSignalProcessing.ProcessingResult fullSampleResult =
                new FFTSignalProcessing.ProcessingResult((int)((signal.length / sampleRate) / windowTime), res.toArray(new FFTSignalProcessing.ProcessingResult[res.size()]));

        return fullSampleResult.getdBaLevels();
    }

    @Test
    public void testVoice1() throws IOException {
        final int sampleRate = 44100;
        // Test error induced by window overlapping
        // Read input signal
        InputStream inputStream =  WindowTest.class.getResourceAsStream("capture_1000hz_16bits_44100hz_signed.raw");
        short[] signal = SOSSignalProcessing.loadShortStream(inputStream, ByteOrder.LITTLE_ENDIAN);

        // Test FFT without cutting
        FFTSignalProcessing fftSignalProcessing =
                new FFTSignalProcessing(sampleRate, ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED, signal.length, FFTSignalProcessing.DB_FS_REFERENCE);
        fftSignalProcessing.addSample(signal);
        FFTSignalProcessing.ProcessingResult processingResult = fftSignalProcessing.processSample(false, false, false);
        double leq = fftSignalProcessing.computeGlobalLeq();
        assertEquals(leq, processingResult.getGlobaldBaValue(), 0.01);

        //Test Butterworth
        ThirdOctaveBandsFiltering thirdOctaveBandsFiltering = new ThirdOctaveBandsFiltering(sampleRate, ThirdOctaveBandsFiltering.FREQUENCY_BANDS.REDUCED);

        double[] doubleSignal = SOSSignalProcessing.convertShortToDouble(signal);

        double[][] actualFilteredSignal = thirdOctaveBandsFiltering.thirdOctaveFiltering(doubleSignal);

        double[] refSpl = new double[STANDARD_FREQUENCIES_UNITTEST.length];

        for (int idf = 0; idf < STANDARD_FREQUENCIES_UNITTEST.length; idf++) {
            refSpl[idf] = (float)AcousticIndicators.getLeq(actualFilteredSignal[idf], REF_SOUND_PRESSURE);
        }

        double leqSOS = getGlobalLeq(refSpl);

        assertEquals(leq, leqSOS, 0.1);
        // Reference spectrum
        //double[] refSpl = {34.0, 31.7, 32.9, 55.4, 68.0, 62.9, 51.8, 64.6, 57.2, 57.0, 51.4, 50.8, 46.9, 47.8, 47.2, 47.5, 47.7, 46.5, 47.4, 46.1, 46.4, 41.6, 33.0};

        // Test FFT windows

        double dBError = 1.87;
        float[] levels = testFFTWindow(signal, sampleRate, 0.125, Window.WINDOW_TYPE.RECTANGULAR);
//        double delta = getDelta(refSpl, levels);
        checkSplSpectrum(refSpl, levels, 0, dBError);
//        dBError = 9.71;
//        testFFTWindow(signal, sampleRate, 0.125, Window.WINDOW_TYPE.RECTANGULAR, refSpl, dBError);
//        dBError = 1.47;
//        testFFTWindow(signal, sampleRate, 1.0, Window.WINDOW_TYPE.HANN, refSpl, dBError);
//        dBError = 2.59;
//        testFFTWindow(signal, sampleRate, 1.0, Window.WINDOW_TYPE.RECTANGULAR, refSpl, dBError);

    }
//
//    @Test
//    public void testVoice2() throws IOException {
//        final int sampleRate = 44100;
//        // Test error induced by window overlapping
//        // Read input signal
//        InputStream inputStream =  WindowTest.class.getResourceAsStream("pierre_44100Hz_16bitPCM_10s.raw");
//        short[] signal = SOSSignalProcessing.loadShortStream(inputStream, ByteOrder.LITTLE_ENDIAN);
//
//        // Compute global leq without filtering frequencies
//        FFTSignalProcessing fftSignalProcessing =
//                new FFTSignalProcessing(sampleRate, STANDARD_FREQUENCIES_UNITTEST, signal.length);
//        fftSignalProcessing.addSample(signal);
//        double signalLeq = fftSignalProcessing.computeGlobalLeq();
//        //assertEquals(83.62, signalLeq, 0.01);
//        double windowTime = 0.125;
//        Window window = new Window(Window.WINDOW_TYPE.HANN, sampleRate,
//                STANDARD_FREQUENCIES_UNITTEST, windowTime, false, 0);
//
//        int packetSize = (int)(0.01 * sampleRate);
//        int processedFastWindows = 0;
//        int idSampleStart = 0;
//        List<FFTSignalProcessing.ProcessingResult> resFreq = new ArrayList<>();
//
//        while(idSampleStart < signal.length) {
//            // Compute sub-sample size in order to not skip samples
//            int sampleLen = Math.min(window.getMaximalBufferSize(), packetSize);
//            short[] samples = Arrays.copyOfRange(signal, idSampleStart, Math.min(signal.length,idSampleStart+sampleLen));
//            idSampleStart+=samples.length;
//
//            window.pushSample(samples);
//            if(window.isWindowAvailable()) {
//                resFreq.add(window.processSample());
//                processedFastWindows++;
//            }
//        }
//
//        // Compute the final result
//        FFTSignalProcessing.ProcessingResult finalResult = new FFTSignalProcessing.ProcessingResult(true, resFreq.toArray(new FFTSignalProcessing.ProcessingResult[resFreq.size()]));
//
//        float[] dbaLevels = finalResult.getdBaLevels();
//        for(int idFreq = 0; idFreq < STANDARD_FREQUENCIES_UNITTEST.length; idFreq++) {
//            System.out.println(STANDARD_FREQUENCIES_UNITTEST[idFreq] + " " + dbaLevels[idFreq]);
//        }
//    }
//
//    @Test
//    public void testVoice2ButterWorth() throws IOException {
//        // Reference sound pressure level [Pa]
//        final double REF_SOUND_PRESSURE = Math.sqrt(0.00002);
//        final int sampleRate = 44100;
//        // Test error induced by window overlapping
//        // Read input signal
//        InputStream inputStream =  WindowTest.class.getResourceAsStream("pierre_44100Hz_16bitPCM_10s.raw");
//        //short[] signal = SOSSignalProcessing.loadShortStream(inputStream, ByteOrder.LITTLE_ENDIAN);
//        //double[] doubleSignal = new double[signal.length];
//        //for(int t = 0; t < signal.length; t++) {
//        //    doubleSignal[t] = (double)signal[t];
//        //}
//
//        SOSSignalProcessing sosSignalProcessing = new SOSSignalProcessing(sampleRate, ThirdOctaveBandsFiltering.FREQUENCY_BANDS.REDUCED);
//        sosSignalProcessing.setAweighting(false);
//
//        List<double[]> timeLevels = sosSignalProcessing.processAudio(16, sampleRate, inputStream, 1., REF_SOUND_PRESSURE, ByteOrder.LITTLE_ENDIAN);
//
//
//        double[] freqLeq = new double[ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED.length];
//        for(int timestep = 0; timestep < timeLevels.size(); timestep++) {
//            double[] leqVals = timeLevels.get(timestep);
//            for(int idFreq = 0; idFreq < ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED.length; idFreq++) {
//                freqLeq[idFreq] += Math.pow(10, leqVals[idFreq] / 10.0);
//            }
//        }
//        for(int idFreq = 0; idFreq < ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED.length; idFreq++) {
//            double dbVal = 10 * Math.log10(freqLeq[idFreq] / timeLevels.size());
//            System.out.println(ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[idFreq] + " " + dbVal);
//        }
//    }
//
}