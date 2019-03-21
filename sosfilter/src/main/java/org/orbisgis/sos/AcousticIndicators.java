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
    public static final double REF_SOUND_PRESSURE = 2e-5;

    public static double computeRms(double[] inputSignal) {
        double sampleSum = 0;
        for (double sample : inputSignal) {
            sampleSum += sample * sample;
        }
        return Math.sqrt(sampleSum / inputSignal.length);
    }

    public static double computeRms(float[] inputSignal) {
        double sampleSum = 0;
        for (float sample : inputSignal) {
            sampleSum += sample * sample;
        }
        return Math.sqrt(sampleSum / inputSignal.length);
    }

    public static double computeRms(short[] inputSignal) {
        double sampleSum = 0;
        for (short sample : inputSignal) {
            sampleSum += sample * sample;
        }
        return Math.sqrt(sampleSum / inputSignal.length);
    }

    public static double todBspl(double rms, double refSoundPressure ) {
        return 20 * Math.log10(rms / refSoundPressure);
    }

    /**
     * Calculation of the equivalent sound pressure level
     * @param inputSignal time signal [Pa]
     * @return equivalent sound pressure level [dB] not normalised by reference pressure.
     */
    public static double getLeq(double[] inputSignal, double refSoundPressure) {
        return todBspl(computeRms(inputSignal), refSoundPressure);
    }

    /**
     * Calculation of the equivalent sound pressure level
     * @param inputSignal time signal [Pa]
     * @return equivalent sound pressure level [dB] not normalised by reference pressure.
     */
    public static double getLeq(short[] inputSignal, double refSoundPressure) {
        return todBspl(computeRms(inputSignal), refSoundPressure);
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

    public static double tukeyWindow(float[] signal, double tukey_alpha) {
        double energy_correction = 0;
        int index_begin_flat = (int)((tukey_alpha / 2) * signal.length);
        int index_end_flat = signal.length - index_begin_flat;
        double window_value;
        // Begin Hann part
        for(int i=0; i < index_begin_flat; i++) {
            window_value = (0.5 * (1 + Math.cos(2 * Math.PI / tukey_alpha * ((i / (float)signal.length) - tukey_alpha / 2))));
            energy_correction += window_value * window_value;
            signal[i] *= window_value;
        }
        // Flat part
        energy_correction += index_end_flat - index_begin_flat;
        // No changes
        // End Hann part
        for(int i=index_end_flat; i < signal.length; i++) {
            window_value = (0.5 * (1 + Math.cos(2 * Math.PI / tukey_alpha * ((i / (float)signal.length) - 1 + tukey_alpha / 2))));
            energy_correction += window_value * window_value;
            signal[i] *= window_value;
        }
        return energy_correction;
    }
    /**
     * Apply a Hanning window to a signal
     * @param signal time signal
     * @return the windowed signal
     */
    public static double hannWindow(float[] signal) {
        double energyCorrection = 0;
        // Iterate until the last line of the data buffer
        for (int n = 1; n < signal.length; n++) {
            // reduce unnecessarily performed frequency part of each and every frequency
            double coeff = 0.5 * (1 - Math.cos((2 * Math.PI * n) / (signal.length - 1)));
            signal[n] *= coeff;
            energyCorrection += coeff * coeff;
        }
        // Return modified buffer
        return energyCorrection;
    }

    /**
     * Fast computation of median (L50)
     * Derived from C code from http://www.stat.cmu.edu/~ryantibs/median/
     * @param levels noise levels
     * @return median noise level or Float.NaN if median not found
     */
    public static float medianApprox(float[] levels) {
        if(levels.length == 0) {
            return Float.NaN;
        } else if(levels.length == 1){
            return levels[0];
        }
        // Compute the mean and standard deviation
        float sum = 0;
        int i;
        for (i = 0; i < levels.length; i++) {
            sum += levels[i];
        }
        float mu = sum/levels.length;

        sum = 0;
        for (i = 0; i < levels.length; i++) {
            sum += (levels[i]-mu)*(levels[i]-mu);
        }
        float sigma = (float)Math.sqrt(sum/levels.length);

        // Bin x across the interval [mu-sigma, mu+sigma]
        int bottomCount = 0;
        int binCounts[] = new int[1001];
        for (i = 0; i < binCounts.length; i++) {
            binCounts[i] = 0;
        }
        float scaleFactor = 1000/(2*sigma);
        float leftEnd =  mu-sigma;
        float rightEnd = mu+sigma;
        int bin;

        for (i = 0; i < levels.length; i++) {
            if (levels[i] < leftEnd) {
                bottomCount++;
            }
            else if (levels[i] < rightEnd) {
                bin = (int)((levels[i]-leftEnd) * scaleFactor);
                binCounts[bin]++;
            }
        }

        // If n is odd
        if ((levels.length & 1) != 0){
            // Find the bin that contains the median
            int k = (levels.length+1)/2;
            int count = bottomCount;

            for (i = 0; i < 1001; i++) {
                count += binCounts[i];
                if (count >= k) {
                    return (i+0.5f)/scaleFactor + leftEnd;
                }
            }
        } else {
            // If n is even
            // Find the bins that contains the medians
            int k = levels.length/2;
            int count = bottomCount;

            for (i = 0; i < 1001; i++) {
                count += binCounts[i];

                if (count >= k) {
                    int j = i;
                    while (count == k) {
                        j++;
                        if(j >= binCounts.length) {
                            break;
                        }
                        count += binCounts[j];
                    }
                    return (i+j+1)/(2*scaleFactor) + leftEnd;
                }
            }
        }
        return Float.NaN;
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