package org.orbisgis.sos;

/**
 * Created by G. Guillaume on 18/06/15.
 */
public class AcousticIndicators {

    // Time periods (in s) for the calculations of the Slow and Fast equivalent sound pressure levels
    public static final double TIMEPERIOD_SLOW = 1.0;
    public static final double TIMEPERIOD_FAST = 0.125;

    // Reference sound pressure level (Pa)
    public static final double pRef = 0.00002;

    /**
     * Calculation of the equivalent sound pressure level
     * @param inputSignal Time signal (Pa)
     * @return Equivalent sound pressure level (dB)
     */
    public static double getLeq(double[] inputSignal) {
        double rms = 0.0;
        for (int i = 1; i < inputSignal.length; i++) {
            rms += inputSignal[i] * inputSignal[i];    // Math.pow(inputSignal[i], 2.);
        }
        double spl = 10 * Math.log10(rms / (inputSignal.length*pRef));
        return spl;
    }


    /**
     * Calculation of the equivalent sound pressure levels over time periods
     * @param inputSignal Time signal (Pa)
     * @param sampleRate Sampling frequency (Hz)
     * @param timePeriod Time period (s)
     * @return
     */
    public static double[] getLeqT(double[] inputSignal, int sampleRate, double timePeriod) {
        int subSamplesLength = (int)(timePeriod * sampleRate);      // Sub-samples length
        int nbSubSamples = (int)(inputSignal.length / subSamplesLength);
        double[] leqT = new double[nbSubSamples];
        int idStartForSub = 0;
        for (int idSub = 0; idSub < nbSubSamples; idSub++) {
            double[] subSample = new double[subSamplesLength];
            for (int iter = 0; iter < subSamplesLength; iter++) {
                subSample[iter] = inputSignal[idStartForSub+iter];
            }
            leqT[idSub] = getLeq(subSample);
            idStartForSub += subSamplesLength;
        }
        return leqT;
    }

    /**
     * Calculation of the averaged equivalent sound pressure level
     * @param spl
     * @return
     */
    public static double getAverageSpl(double[] spl) {
        int nbVals = spl.length;
        double sumSpl = 0.0;
        for (int idSpl = 0; idSpl < nbVals; idSpl++) {
            sumSpl += Math.pow(10.0, spl[idSpl] / 10.0);
        }
        return Math.log10((1.0/nbVals) * sumSpl);
    }


}
