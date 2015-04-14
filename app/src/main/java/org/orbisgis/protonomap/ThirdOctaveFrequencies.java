package org.orbisgis.protonomap;

/**
 * Calculation of the central and lateral frequencies of the third octave bands.
 */

public class ThirdOctaveFrequencies {

    private static final double LAT_FREQ_FACTOR = Math.pow(2., 1./6.);

    private ThirdOctaveFrequencies() {}

    /**
     * Standard center frequencies of third octave bands
     */
    public static final double[] STANDARD_FREQUENCY = new double[]{16, 20, 25, 31.5, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6000, 8000, 10000, 12500, 16000, 20000};


    /**
     * Center frequency of the third octave band
     * @param idFreq center frequency index
     */
    private static double getCtrFreq(int idFreq) {
        return Math.pow(10., 3.) * Math.pow(2., (idFreq-18) / 3.);
    }

    public static LowHigh getLatFreqs(int idFreq) {
        double ctrFreq = getCtrFreq(idFreq);
        double lowFreq = getLowLatFreq(ctrFreq);
        double highFreq = getHighLatFreq(ctrFreq);
        return new LowHigh(ctrFreq, lowFreq, highFreq);
    }


    /** Low lateral frequency for the third octave band
     * @param fCtr center frequency (Hz)
     */
    private static double getLowLatFreq(double fCtr) {
        return fCtr / LAT_FREQ_FACTOR;
    }


    /** High lateral frequency for the third octave band
     * @param fCtr center frequency (Hz)
     */
    private static double getHighLatFreq(double fCtr) {
        return fCtr * LAT_FREQ_FACTOR;
    }

    public static class LowHigh {
        public final double ctr;
        public final double low;
        public final double high;

        public LowHigh(double ctr, double low, double high) {
            this.ctr = ctr;
            this.low = low;
            this.high = high;
        }
    }
}
