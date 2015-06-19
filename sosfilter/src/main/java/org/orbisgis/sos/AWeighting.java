package org.orbisgis.sos;

/**
 * Created by G. Guillaume on 03/06/2015.
 */
public class AWeighting {

    /**
     * Denominator coefficients of the A-weighting filter.
     */
    public final static double[] denominator = new double[]{1.0,
                                        -4.0195761811158306,
                                        6.1894064429206894,
                                        -4.4531989035441137,
                                        1.420842949621872,
                                        -0.14182547383030505,
                                        0.004351177233494978};

    /**
     * Numerator coefficients of the A-weighting filter.
     */
    public final static double[] numerator = new double[]{0.25574112520425768,
                                      -0.51148225040851569,
                                      -0.25574112520425829,
                                      1.0229645008170301,
                                      -0.25574112520425829,
                                      -0.51148225040851569,
                                      0.25574112520425768};


    /**
     * A-weighting of the raw time signal
     * @param inputSignal Raw time signal
     * @return A-weighted time signal
     */
    public static double[] aWeightingSignal(double[] inputSignal) {

        int nsamp = inputSignal.length;
        int order = Math.max(denominator.length, numerator.length);
        double[] weightedSignal = new double[nsamp];
        double[][] z = new double[order-1][nsamp];    // Filter delays
        for (int it = 0; it < nsamp; it++){
            // Avoid iteration it=0 exception (z[0][it-1]=0)
            weightedSignal[it] = numerator[0]*inputSignal[it] + (it == 0 ? 0 : z[0][it-1]);
            // Avoid iteration it=0 exception (z[1][it-1]=0)
            z[0][it] = numerator[1]*inputSignal[it] + (it == 0 ? 0 : z[1][it-1]) - denominator[1]*inputSignal[it];
            for (int k = 0; k<order-2; k++){
                // Avoid iteration it=0 exception (z[k+1][it-1]=0)
                z[k][it] = numerator[k+1]*inputSignal[it] + (it ==0 ? 0 : z[k+1][it-1]) - denominator[k+1]*weightedSignal[it];
            }
            z[order-2][it] = numerator[order-1]*inputSignal[it] - denominator[order-1]*weightedSignal[it];
        }
        return weightedSignal;
    }

}