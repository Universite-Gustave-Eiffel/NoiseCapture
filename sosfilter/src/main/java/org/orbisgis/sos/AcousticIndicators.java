package org.orbisgis.sos;

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
     * @return equivalent sound pressure level [dB]
     */
    public static double getLeq(double[] inputSignal) {
        double rms = 0.0;

        for (int idT = 1; idT < inputSignal.length; idT++) {
            rms += inputSignal[idT] * inputSignal[idT];    // Math.pow(inputSignal[i], 2.);
        }
        return 10 * Math.log10(rms / (inputSignal.length* REF_SOUND_PRESSURE));
    }

    /**
     * Calculation of the equivalent sound pressure levels over a time period
     * @param inputSignal time signal [Pa]
     * @param sampleRate sampling frequency [Hz]
     * @param timePeriod time period (s)
     * @return double array of equivalent sound pressure levels [dB]
     */
    public static double[] getLeqT(double[] inputSignal, int sampleRate, double timePeriod) {
        int subSamplesLength = (int)(timePeriod * sampleRate);      // Sub-samples length
        int nbSubSamples = inputSignal.length / subSamplesLength;
        double[] leqT = new double[nbSubSamples];
        int idStartForSub = 0;
        for (int idSub = 0; idSub < nbSubSamples; idSub++) {
            double[] subSample = new double[subSamplesLength];
            System.arraycopy(inputSignal, idStartForSub, subSample, 0, subSamplesLength);
            leqT[idSub] = getLeq(subSample);
            idStartForSub += subSamplesLength;
        }
        return leqT;
    }

    /**
     * Calculation of the averaged equivalent sound pressure level
     * @param spl sound pressure level [dB]
     * @return averaged equivalent sound pressure level [dB]
     */
    public static double getAverageSpl(double[] spl) {
        int splLength = spl.length;
        double sumSpl = 0.0;
        for (int idSpl = 0; idSpl < splLength; idSpl++) {
            sumSpl += Math.pow(10.0, spl[idSpl] / 10.0);
        }
        return Math.log10((1.0/splLength) * sumSpl);
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
}