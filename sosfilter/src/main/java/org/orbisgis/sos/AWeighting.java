package org.orbisgis.sos;

/**
 * Created by G. Guillaume on 03/06/2015.
 * A-weighting of a time signal
 * This module applies an A-weighting filter according to the standard IEC 61672 "Electroacoustics - sound level meters" (2013)
 * @see <a href="http://siggigue.github.io/pyfilterbank/splweighting.html">http://siggigue.github.io/pyfilterbank/splweighting.html</a>
 * @see <a href="http://www.mathworks.com/matlabcentral/fileexchange/69-octave/content//octave/adsgn.m">http://www.mathworks.com/matlabcentral/fileexchange/69-octave/content//octave/adsgn.m</a>
 */
public class AWeighting {

    /**
     * Denominator coefficients of the A-weighting filter determined by means of a bilinear transform that converts
     * second-order section analog weights to second-order section digital weights.
     */
    public final static double[] denominator = new double[]{ 1.0,
                                                            -4.0195761811158306,
                                                             6.1894064429206894,
                                                            -4.4531989035441137,
                                                             1.420842949621872,
                                                            -0.14182547383030505,
                                                             0.004351177233494978};

    /**
     * Numerator coefficients of the A-weighting filter determined by means of a bilinear transform that converts
     * second-order section analog weights to second-order section digital weights.
     */
    public final static double[] numerator = new double[]{ 0.25574112520425768,
                                                          -0.51148225040851569,
                                                          -0.25574112520425829,
                                                           1.0229645008170301,
                                                          -0.25574112520425829,
                                                          -0.51148225040851569,
                                                           0.25574112520425768};

    /**
     * A-weighting of the raw time signal
     * Second order section filtering
     * @param inputSignal Raw time signal
     * @return A-weighted time signal
     */
    public static double[] aWeightingSignal(double[] inputSignal) {

        int signalLength = inputSignal.length;
        int order = Math.max(denominator.length, numerator.length);
        double[] weightedSignal = new double[signalLength];
        // Filter delays
        double[][] z = new double[order-1][signalLength];

        for (int idT = 0; idT < signalLength; idT++){
            // Avoid iteration idT=0 exception (z[0][idT-1]=0)
            weightedSignal[idT] = numerator[0]*inputSignal[idT] + (idT == 0 ? 0 : z[0][idT-1]);
            // Avoid iteration idT=0 exception (z[1][idT-1]=0)
            z[0][idT] = numerator[1]*inputSignal[idT] + (idT == 0 ? 0 : z[1][idT-1]) - denominator[1]*inputSignal[idT];
            for (int k = 0; k<order-2; k++){
                // Avoid iteration idT=0 exception (z[k+1][idT-1]=0)
                z[k][idT] = numerator[k+1]*inputSignal[idT] + (idT ==0 ? 0 : z[k+1][idT-1]) - denominator[k+1]*weightedSignal[idT];
            }
            z[order-2][idT] = numerator[order-1]*inputSignal[idT] - denominator[order-1]*weightedSignal[idT];
        }
        return weightedSignal;
    }





    /**
     * A-weighting of the raw time signal
     * Second order section filtering
     * @param inputSignal Raw time signal
     * @return A-weighted time signal
     */
    public static float[] aWeightingSignal(float[] inputSignal) {

        int signalLength = inputSignal.length;
        int order = Math.max(denominator.length, numerator.length);
        float[] weightedSignal = new float[signalLength];
        // Filter delays
        float[][] z = new float[order-1][signalLength];

        for (int idT = 0; idT < signalLength; idT++){
            // Avoid iteration idT=0 exception (z[0][idT-1]=0)
            weightedSignal[idT] = (float)(numerator[0]*inputSignal[idT] + (idT == 0 ? 0 : z[0][idT-1]));
            // Avoid iteration idT=0 exception (z[1][idT-1]=0)
            z[0][idT] = (float)(numerator[1]*inputSignal[idT] + (idT == 0 ? 0 : z[1][idT-1]) - denominator[1]*inputSignal[idT]);
            for (int k = 0; k<order-2; k++){
                // Avoid iteration idT=0 exception (z[k+1][idT-1]=0)
                z[k][idT] = (float)(numerator[k+1]*inputSignal[idT] + (idT ==0 ? 0 : z[k+1][idT-1]) - denominator[k+1]*weightedSignal[idT]);
            }
            z[order-2][idT] = (float)(numerator[order-1]*inputSignal[idT] - denominator[order-1]*weightedSignal[idT]);
        }
        return weightedSignal;
    }
}