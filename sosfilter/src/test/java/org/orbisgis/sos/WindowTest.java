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
     * @param expectedLeq      Expected spectrum
     * @param dbResult         Original computed spectrum
     * @param delta            Apply this delta to dbResult before comparing
     * @param maximalDeviation Fail if one of frequency band is superior than this error (dB)
     */
    private void checkSplSpectrum(double[] expectedLeq, float[] dbResult, double delta, double maximalDeviation) {
        double[] normalisedDbResult = new double[dbResult.length];
        for (int idFreq = 0; idFreq < dbResult.length; idFreq++) {
            normalisedDbResult[idFreq] = dbResult[idFreq] - delta;
        }

        double err = 0;
        for (int idFreq = 0; idFreq < dbResult.length; idFreq++) {
            err += Math.pow(normalisedDbResult[idFreq] - expectedLeq[idFreq], 2);
        }
        err = Math.sqrt(err / dbResult.length);
        assertEquals("Deviation of " + err + "\nExpected: \n" + Arrays.toString(expectedLeq) + "\nGot:\n" + Arrays.toString(normalisedDbResult), 0, err, maximalDeviation);
    }

    private float[] testFFTWindow(short[] signal, int sampleRate, double windowTime, FFTSignalProcessing.WINDOW_TYPE windowType, double dbFsReference) {
        Window window = new Window(windowType, sampleRate,
                STANDARD_FREQUENCIES_UNITTEST, windowTime, false, dbFsReference, false);

        int packetSize = (int) (0.1 * sampleRate);
        int idSampleStart = 0;
        List<FFTSignalProcessing.ProcessingResult> res = new ArrayList<>();
        int lastPushIndex = 0;
        while (idSampleStart < signal.length) {
            // Compute sub-sample size in order to not skip samples
            int sampleLen = Math.min(window.getMaximalBufferSize(), packetSize);
            short[] samples = Arrays.copyOfRange(signal, idSampleStart, Math.min(signal.length, idSampleStart + sampleLen));
            idSampleStart += samples.length;

            window.pushSample(samples);
            if (window.getWindowIndex() != lastPushIndex) {
                lastPushIndex = window.getWindowIndex();
                res.add(window.getLastWindowMean());
                window.cleanWindows();
            }
        }
        // Zero padding for window finishing scan
        if (!window.isCacheEmpty()) {
            res.add(window.getLastWindowMean());
            window.cleanWindows();
        }
        // Check if the expected result size is here
        // Ex:
        // The signal is 10s and the window is 125 ms
        // So there is 80 * 125 ms results
        assertEquals((int) ((signal.length / sampleRate) / windowTime), res.size(), 0);

        FFTSignalProcessing.ProcessingResult fullSampleResult =
                new FFTSignalProcessing.ProcessingResult((signal.length / sampleRate) / windowTime, res.toArray(new FFTSignalProcessing.ProcessingResult[res.size()]));

        return fullSampleResult.getdBaLevels();
    }

    @Test
    public void testVoice1() throws IOException {
        final int sampleRate = 44100;
        double p0 = 32767.;
        double dbFsReference = -20 * Math.log10(p0);
        // Reference spectrum
        double[] refSpl = {-66, -68.04, -67.52, -45.97, -31.96, -37.13, -49.21, -35.29, -43.01,
                -42.95, -48.65, -49.04, -53.27, -52.15, -52.65, -52.8, -52.31, -53.56, -52.54,
                -53.96, -53.63, -58.53, -67.22};
        double refGlobalSpl = 0;
        for (double lvl : refSpl) {
            refGlobalSpl += Math.pow(10, lvl / 10.);
        }
        refGlobalSpl = 10 * Math.log10(refGlobalSpl);
        // Test error induced by window overlapping
        // Read input signal
        InputStream inputStream = WindowTest.class.getResourceAsStream("speak_44100Hz_16bitsPCM_10s.raw");
        short[] signal = SOSSignalProcessing.loadShortStream(inputStream, ByteOrder.LITTLE_ENDIAN);
        // Reference value from ITA Toolbox

        // Test FFT without cutting
        FFTSignalProcessing fftSignalProcessing =
                new FFTSignalProcessing(sampleRate, ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED, signal.length, dbFsReference);
        fftSignalProcessing.addSample(signal);
        FFTSignalProcessing.ProcessingResult processingResult = fftSignalProcessing.processSample(FFTSignalProcessing.WINDOW_TYPE.RECTANGULAR, false, false);

        assertEquals(refGlobalSpl, fftSignalProcessing.computeGlobalLeq(), 0.01);
        assertEquals(refGlobalSpl, processingResult.getGlobaldBaValue(), 0.01);

        // Test FFT windows

        checkSplSpectrum(refSpl, testFFTWindow(signal, sampleRate, 0.125, FFTSignalProcessing.WINDOW_TYPE.TUKEY, dbFsReference), 0, 1.11);

        checkSplSpectrum(refSpl, testFFTWindow(signal, sampleRate, 0.125, FFTSignalProcessing.WINDOW_TYPE.RECTANGULAR, dbFsReference), 0, 2.64);

        checkSplSpectrum(refSpl, testFFTWindow(signal, sampleRate, 1., FFTSignalProcessing.WINDOW_TYPE.TUKEY, dbFsReference), 0, 0.36);

        checkSplSpectrum(refSpl, testFFTWindow(signal, sampleRate, 1., FFTSignalProcessing.WINDOW_TYPE.RECTANGULAR, dbFsReference), 0, 0.774);

        //Test SOS

        //        SOSSignalProcessing sosSignalProcessing = new SOSSignalProcessing(sampleRate, ThirdOctaveBandsFiltering.FREQUENCY_BANDS.REDUCED);
        //        sosSignalProcessing.setAweighting(false);
        //
        //        int idSampleStart = 0;
        //        float[] sosBands = new float[STANDARD_FREQUENCIES_UNITTEST.length];
        //        while (idSampleStart < signal.length) {
        //            // Compute sub-sample size in order to not skip samples
        //            short[] samples = Arrays.copyOfRange(signal, idSampleStart, Math.min(signal.length, idSampleStart + sampleRate));
        //            idSampleStart += samples.length;
        //
        //            sosSignalProcessing.addSample(SOSSignalProcessing.convertShortToDouble(samples));
        //            double[] secondResults = sosSignalProcessing.processSample(p0);
        //            for (int idf = 0; idf < STANDARD_FREQUENCIES_UNITTEST.length; idf++) {
        //                sosBands[idf] += Math.pow(10, secondResults[idf] / 10);
        //            }
        //        }
        //        for (int idf = 0; idf < STANDARD_FREQUENCIES_UNITTEST.length; idf++) {
        //            sosBands[idf] = (float)(10 * Math.log10(sosBands[idf]));
        //        }
        //        dBError = 0.;
        //        checkSplSpectrum(refSpl, sosBands, 0, dBError);
    }
}