package org.noise_planet.noisecapture;

/**
 * This class receive the uncalibrated noise level LAeq (125ms) and estimates the number of passing vehicles,
 * With the help of distance to vehicle and average vehicle speed it can estimates
 * the expected noise level with an estimated uncertainty.
 */
public class TrafficNoiseEstimator {
    /**
     * Minimum delay between peaks in seconds
     */
    private double delay = 3;

    /**
     *
     * @param laeq 125ms dB(A) values
     */
    public Estimation evaluate(float[] laeq) {
        Estimation estimation = new Estimation();

        return estimation;
    }

    /**
     * Result
     */
    public static class Estimation {

    }
}
