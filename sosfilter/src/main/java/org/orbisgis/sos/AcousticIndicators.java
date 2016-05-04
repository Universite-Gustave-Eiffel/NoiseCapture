package org.orbisgis.sos;

import java.util.List;

/**
 * Created by G. Guillaume on 18/06/15.
 * Calculation of some acoustic indicators
 */
public class AcousticIndicators {

    // Time periods (in s) for the calculations of the Slow (1 s) and Fast (0.125 s) equivalent sound pressure levels
    public static final double TIMEPERIOD_SLOW = 1.0;
    public static final double TIMEPERIOD_FAST = 0.125;

    // Reference sound pressure level [Pa]
    public static final double REF_SOUND_PRESSURE = 0.00002;

    /**
     * Calculation of the equivalent sound pressure level
     * @param inputSignal time signal [Pa]
     * @return equivalent sound pressure level [dB] not normalised by reference pressure.
     */
    public static double getLeq(double[] inputSignal, double refSoundPressure) {
        double sqrRms = 0.0;
        final double sqrRefSoundPressure = refSoundPressure * refSoundPressure;
        for (int idT = 1; idT < inputSignal.length; idT++) {
            sqrRms += inputSignal[idT] * inputSignal[idT];
        }
        return 10 * Math.log10(sqrRms / (inputSignal.length * sqrRefSoundPressure));
    }

    /**
     * Calculation of the equivalent sound pressure levels over a time period
     * @param inputSignal time signal [Pa]
     * @param sampleRate sampling frequency [Hz]
     * @param timePeriod time period (s)
     * @return double array of equivalent sound pressure levels [dB]
     */
    public static double[] getLeqT(double[] inputSignal, int sampleRate, double timePeriod, double refSoundPressure) {
        int subSamplesLength = (int)(timePeriod * sampleRate);      // Sub-samples length
        int nbSubSamples = inputSignal.length / subSamplesLength;
        double[] leqT = new double[nbSubSamples];
        int idStartForSub = 0;
        for (int idSub = 0; idSub < nbSubSamples; idSub++) {
            double[] subSample = new double[subSamplesLength];
            System.arraycopy(inputSignal, idStartForSub, subSample, 0, subSamplesLength);
            leqT[idSub] = getLeq(subSample, refSoundPressure);
            idStartForSub += subSamplesLength;
        }
        return leqT;
    }

    /**
     * Apply a Hanning window to a signal
     * @param signal time signal
     * @return the windowed signal
     */
    public static double[] hanningWindow(double[] signal) {

        // Iterate until the last line of the data buffer
        for (int n = 1; n < signal.length; n++) {
            // reduce unnecessarily performed frequency part of each and every frequency
            signal[n] *= 0.5 * (1 - Math.cos((2 * Math.PI * n) / (signal.length - 1)));
        }
        // Return modified buffer
        return signal;
    }

    /**
     * Apply a Hanning window to a signal
     * @param signal time signal
     * @return the windowed signal
     */
    public static float[] hanningWindow(float[] signal) {

        // Iterate until the last line of the data buffer
        for (int n = 1; n < signal.length; n++) {
            // reduce unnecessarily performed frequency part of each and every frequency
            signal[n] *= 0.5 * (1 - Math.cos((2 * Math.PI * n) / (signal.length - 1)));
        }
        // Return modified buffer
        return signal;
    }


    public final static class SplStatistics {
        public final double min;
        public final double max;
        public final double mean;

        public SplStatistics(double min, double max, double mean) {
            this.min = min;
            this.max = max;
            this.mean = mean;
        }
    }
}