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

import org.jtransforms.fft.FloatFFT_1D;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;


public class TestJTransforms {
    public static final double[] STANDARD_FREQUENCIES_UNITTEST = new double[]{100, 125, 160, 200, 250, 315, 400, 500, 630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000, 12500, 16000};

    @Test
    public void testProcessingWhiteNoise() throws IOException {
        final int sampleRate = 44100;
        final double length = 0.5;
        InputStream inputStream = TestJTransforms.class.getResourceAsStream("whitenoise_44100Hz_16bitPCM_10s.raw");
        FFTSignalProcessing fftSignalProcessing =
                new FFTSignalProcessing(sampleRate, STANDARD_FREQUENCIES_UNITTEST, (int) (sampleRate * length));
        // Read input signal up to buffer.length
        short[] signal = SOSSignalProcessing.loadShortStream(inputStream, ByteOrder.LITTLE_ENDIAN);
        inputStream.close();
        fftSignalProcessing.addSample(signal);
        FFTSignalProcessing.ProcessingResult processingResult = fftSignalProcessing.processSample(FFTSignalProcessing.WINDOW_TYPE.RECTANGULAR, false, false);
        assertEquals(1251.7, fftSignalProcessing.computeRms() ,0.1);
        // Check third-octave recomposition. 1 dB because the global dba value filter frequencies outside bounds 100-16000
        assertEquals(83, fftSignalProcessing.computeGlobalLeq(),1.0);
        assertEquals(83, processingResult.getGlobaldBaValue(),1.0);
    }

    /**
     * Check combination to third octave of white noise.
     * Disabled as the combination is rectangular
     */
    //@Test
    public void testThirdOctaveSum() {
        float[] fftResult = new float[20000];
        Arrays.fill(fftResult, 1.f);
        FFTSignalProcessing fftSignalProcessing = new FFTSignalProcessing(44100,
                ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED, fftResult.length);
        float[] thirdOctaveSum = fftSignalProcessing.thirdOctaveProcessing(fftResult, false, fftResult.length);
        double ref = fftSignalProcessing.todBspl(thirdOctaveSum[0]);
        for(int idThirdOctave  = 1; idThirdOctave < thirdOctaveSum.length; idThirdOctave++) {
            assertEquals(ref + idThirdOctave, fftSignalProcessing.todBspl(thirdOctaveSum[idThirdOctave]), 0.01);
        }
    }

    @Test
    public void testProcessing() {
        // Make 2s 1000 Hz signal
        final double length = 2;
        final int sampleRate = 44100;
        final int samples = (int)(sampleRate * length);
        final int signalFrequency = 1000;
        double powerRMS = 2500; // 90 dBspl
        double powerPeak = powerRMS * Math.sqrt(2);
        short[] signal = new short[samples];
        for (int s = 0; s < samples; s++) {
            double t = s * (1 / (double) sampleRate);
            signal[s] = (short)(Math.sin(2 * Math.PI * signalFrequency * t) * (powerPeak));
        }

        FFTSignalProcessing fftSignalProcessing =
                new FFTSignalProcessing(sampleRate, ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED, signal.length);
        fftSignalProcessing.addSample(signal);
        FFTSignalProcessing.ProcessingResult processingResult = fftSignalProcessing.processSample(FFTSignalProcessing.WINDOW_TYPE.RECTANGULAR, false, false);

        assertEquals(90, fftSignalProcessing.computeGlobalLeq(), 0.01);
        assertEquals(90,
                processingResult.dBaLevels[Arrays.binarySearch(ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED,
                        signalFrequency)], 0.01);
        assertEquals(90,
                processingResult.getGlobaldBaValue(), 0.01);


    }

    @Test
    public void testProcessingFast() {
        // Make 1000 Hz signal
        double fastRate = 0.250;
        final int sampleRate = 44100;
        final int signalFrequency = 1000;
        double powerRMS = 2500; // 90 dBspl
        double powerPeak = powerRMS * Math.sqrt(2);
        short[] signal = new short[(int)(sampleRate * fastRate)];
        for (int s = 0; s < signal.length; s++) {
            double t = s * (1 / (double) sampleRate);
            signal[s] = (short)(Math.sin(2 * Math.PI * signalFrequency * t) * (powerPeak));
        }

        FFTSignalProcessing fftSignalProcessing =
                new FFTSignalProcessing(sampleRate, ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED, signal.length);
        fftSignalProcessing.addSample(signal);
        FFTSignalProcessing.ProcessingResult processingResult = fftSignalProcessing.processSample(FFTSignalProcessing.WINDOW_TYPE.RECTANGULAR, false, false);

        assertEquals(90, fftSignalProcessing.computeGlobalLeq(), 0.01);
        assertEquals(90,
                processingResult.dBaLevels[Arrays.binarySearch(ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED,
                        signalFrequency)], 0.01);
        assertEquals(90,
                processingResult.getGlobaldBaValue(), 0.01);

        processingResult = fftSignalProcessing.processSample(FFTSignalProcessing.WINDOW_TYPE.TUKEY, false, false);

        assertEquals(90, fftSignalProcessing.computeGlobalLeq(), 0.01);
        assertEquals(90,
                processingResult.dBaLevels[Arrays.binarySearch(ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED,
                        signalFrequency)], 0.01);
        assertEquals(90,
                processingResult.getGlobaldBaValue(), 0.01);

    }
    @Test
    public void testRecorder() throws IOException {
        int rate = 44100;
        InputStream inputStream = CoreSignalProcessingTest.class.getResourceAsStream("capture_1000hz_16bits_44100hz_signed.raw");
        FFTSignalProcessing fftSignalProcessing =
                new FFTSignalProcessing(rate, STANDARD_FREQUENCIES_UNITTEST, (int)(0.3 * rate));
        // Read input signal up to buffer.length
        short[] signal = SOSSignalProcessing.loadShortStream(inputStream, ByteOrder.LITTLE_ENDIAN);
        // Split signal to simulate recording
        int[] splitParts = new int[] {10, 5, 5, 30, 1, 9 , 20, 19, 1};
        int lastPart = 0;
        for(int part : splitParts) {
            short[] signalPart = new short[(int)((part / 100.) * signal.length)];
            System.arraycopy(signal, lastPart, signalPart, 0, signalPart.length);
            fftSignalProcessing.addSample(signalPart);
            lastPart += signalPart.length;
        }
        FFTSignalProcessing.ProcessingResult processingResult = fftSignalProcessing.processSample(FFTSignalProcessing.WINDOW_TYPE.RECTANGULAR, false, false);

        assertEquals(323.85, fftSignalProcessing.computeRms(), 0.01);
        assertEquals(72.24, AcousticIndicators.getLeq(signal, fftSignalProcessing.getRefSoundPressure()), 0.01) ;
        assertEquals(72.24, fftSignalProcessing.computeGlobalLeq(), 0.01);
        assertEquals(72.24, processingResult.getGlobaldBaValue(), 0.01);
    }

    @Test
    public void testAmplitude() {

        // Make 50 Hz signal
        final int sampleRate = 44100;
        final int signalFrequency = 50;
        float power = 2500;
        float[] signal = new float[sampleRate];
        for(int s = 0; s < sampleRate; s++) {
            double t = s * (1 / (double)sampleRate);
            signal[s] = (float)Math.sin(2 * Math.PI * signalFrequency * t) * power;
        }

        // Execute FFT
        FloatFFT_1D floatFFT_1D = new FloatFFT_1D(sampleRate);
        floatFFT_1D.realForward(signal);

        // Extract Real part
        float localMax = Float.MIN_VALUE;
        int maxValueFreq = -1;
        float[] result = new float[signal.length / 2];
        for(int s = 0; s < result.length; s++) {
            //result[s] = Math.abs(signal[2*s]);
            float re = signal[s * 2];
            float im = signal[s * 2 + 1];
            result[s] = (float) Math.sqrt(re * re + im * im) / result.length;
            if(result[s] > localMax) {
                maxValueFreq = s;
            }
            localMax = Math.max(localMax, result[s]);
        }

        assertEquals(power, localMax, 0.001);
        assertEquals(signalFrequency, maxValueFreq);
    }

    private float[] getMinMax(float[] signal) {
        float localMin = Float.MAX_VALUE;
        float localMax = Float.MIN_VALUE;
        int maxVal = -1;
        int pos = 0;
        for(float val : signal) {
            if(val > localMax) {
                maxVal = pos;
            }
            localMin = Math.min(localMin, val);
            localMax = Math.max(localMax, val);
            pos++;
        }
        return new float[] {localMin, localMax, (float) maxVal};
    }
}
