package org.noise_planet.noisecapture;


import org.orbisgis.sos.AcousticIndicators;

/**
 * This class receive the uncalibrated noise level LAeq (125ms) and estimates the number of passing vehicles,
 * With the help of distance to vehicle and average vehicle speed it can estimates
 * the expected noise level with estimated uncertainty.
 */
public class TrafficNoiseEstimator {
    final float[] freqs;
    AcousticIndicators backgroundLevelEstimator = new AcousticIndicators();

    /**
     * @param freqs Nominal frequency bands
     */
    public TrafficNoiseEstimator(float[] freqs) {
        this.freqs = freqs;
    }

    /**
     *
     * @param values LAeq for each spectrum band
     */
    public void addFastLAeq(float[] values) {

    }

    /**
     * Result
     */
    public static class Estimation {

    }
}
