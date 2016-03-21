package org.orbisgis.sos;

import org.jtransforms.fft.FloatFFT_1D;
import org.junit.Assert;
import org.junit.Test;


public class TestJTransforms {


    @Test
    public void testAmplitude() {

        // Make 50 Hz signal
        final int sampleRate = 44100;
        final int signalFrequency = 7500;
        float[] signal = new float[sampleRate];
        for(int s = 0; s < sampleRate; s++) {
            double t = s * (1 / (double)sampleRate);
            signal[s] = (float)Math.sin(2 * Math.PI * signalFrequency * t);
        }

        // Compute min/max
        float[] minMax = getMinMax(signal);
        System.out.println("Min "+ minMax[0] + " max "+ minMax[1]);

        // Execute FFT
        FloatFFT_1D floatFFT_1D = new FloatFFT_1D(sampleRate);
        floatFFT_1D.realForward(signal);

        // Extract Real part
        float[] result = new float[signal.length / 2];
        for(int s = 0; s < result.length; s++) {
            //result[s] = Math.abs(signal[2*s]);
            float reali = signal[s * 2];
            float imagi = signal[s * 2 + 1];
            result[s] = (float) Math.sqrt(reali * reali + imagi * imagi) / result.length;
        }

        // Compute and print min max
        minMax = getMinMax(result);
        System.out.println("Min "+ minMax[0] + " max "+ minMax[1] + " position " + ((int)minMax[2]) +"/"+result.length);

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
