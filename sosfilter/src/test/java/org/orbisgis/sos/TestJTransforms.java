package org.orbisgis.sos;

import org.jtransforms.fft.FloatFFT_1D;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;


public class TestJTransforms {

    @Test
    public void testProcessing() {
        // Make 1000 Hz signal
        final int sampleRate = 44100;
        final int signalFrequency = 1000;
        double powerRMS = 2500; // 90 dBspl
        double powerPeak = powerRMS * Math.sqrt(2);
        short[] signal = new short[sampleRate];
        for (int s = 0; s < sampleRate; s++) {
            double t = s * (1 / (double) sampleRate);
            signal[s] = (short)(Math.sin(2 * Math.PI * signalFrequency * t) * (powerPeak));
        }

        FFTSignalProcessing fftSignalProcessing =
                new FFTSignalProcessing(sampleRate, ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED, signal.length);
        fftSignalProcessing.addSample(signal);
        FFTSignalProcessing.ProcessingResult processingResult = fftSignalProcessing.processSample(false, false);

        assertEquals(90, fftSignalProcessing.computeGlobalLeq(), 0.01);
        assertEquals(90,
                processingResult.dBaLevels[Arrays.binarySearch(ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED,
                        signalFrequency)], 0.01);

    }

    @Test
    public void testRecorder() throws IOException {
        int rate = 44100;
        InputStream inputStream = CoreSignalProcessingTest.class.getResourceAsStream("capture_1000hz_16bits_44100hz_signed.raw");
        FFTSignalProcessing fftSignalProcessing =
                new FFTSignalProcessing(rate, ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED, rate);
        // Read input signal up to buffer.length
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        short[] signal = SOSSignalProcessing.loadShortStream(inputStream);
        for(short tic : signal) {
            min = Math.min(min, tic);
            max = Math.max(max, tic);
        }
        fftSignalProcessing.addSample(signal);
        FFTSignalProcessing.ProcessingResult processingResult = fftSignalProcessing.processSample(true, true);

        assertEquals(72.24, fftSignalProcessing.computeGlobalLeq(), 0.01);
        assertEquals(72.24, processingResult.getGlobaldBaValue(), 1);
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

        assertEquals(power, localMax, 1e-12);
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
