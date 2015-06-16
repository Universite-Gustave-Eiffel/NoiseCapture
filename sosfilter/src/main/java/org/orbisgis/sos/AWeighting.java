package org.orbisgis.sos;

import java.util.List;
import java.util.*;

/**
 * Created by molene on 03/06/2015.
 */
public class AWeighting {

    public class AWeightingCoefficients
    {
        private double[] denominator;
        private double[] numerator;
        public AWeightingCoefficients(double[] denominator, double[] numerator)
        {
            this.denominator = denominator;
            this.numerator = numerator;

        }
        public double[] getDenominator() { return denominator ; }
        public double[] getNumerator() { return numerator; }
    }


    private List<AWeightingCoefts> denominatorCoefs = new ArrayList<AWeightingCoefts>(7);
    private List<AWeightingCoefts> numeratorCoefs = new ArrayList<AWeightingCoefts>(7);


    /**
     * @return Denominator coefficients used to weight the signal.
     */
    public List<AWeightingCoefts> getDenominator() { return denominatorCoefs; }

    /**
     * @return Numerator coefficients used to weight the signal.
     */
    public List<AWeightingCoefts> getNumerator() { return numeratorCoefs; }

    /**
     * Denominator coefficients of the A-weighting filter.
     */
    public double[] getDenominatorCoefts() {
        double[] a = new double[7];
        a[0] = 1.0;
        a[1] = -4.0195761811158306;
        a[2] = 6.1894064429206894;
        a[3] = -4.4531989035441137;
        a[4] = 1.420842949621872;
        a[5] = -0.14182547383030505;
        a[6] = 0.004351177233494978;
        return a;
    }

    /**
     * Numerator coefficients of the A-weighting filter.
     */
    public double[] getNumeratorCoefts() {
        double[] b = new double[7];
        b[0] = 0.25574112520425768;
        b[1] = -0.51148225040851569;
        b[2] = -0.25574112520425829;
        b[3] = 1.0229645008170301;
        b[4] = -0.25574112520425829;
        b[5] = -0.51148225040851569;
        b[6] = 0.25574112520425768;
        return b;
    }


    /**
     * A-weighting of the raw time signal
     * @param signal Raw time signal
     * @param aWeightingCoefts A-weighting coefficients
     * @return A-weighted time signal
     */
    public final double[] AWeighting(double[] signal, AWeightingCoefts aWeightingCoefts) {

        int nsamp = signal.length;
        int order = 7;
        double[] weightedSignal = new double[nsamp];
        double[][] z = new double[order-1][nsamp];    // Filter delays
        double[] a = aWeightingCoefts.a;
        double[] b = aWeightingCoefts.b;
        for (int it = 0; it < nsamp; it++){
            weightedSignal[it] = b[0]*signal[it] + z[0][it-1];
            z[0][it] = b[1]*signal[it] + z[1][it-1] - a[1]*signal[it];
            for (int k = 0; k<order-2; k++){
                z[k][it] = b[k+1]*signal[it] + z[k+1][it-1] - a[k+1]*weightedSignal[it];
            }
            z[order-2][it] = b[order-1]*signal[it] - a[order-1]*weightedSignal[it];
        }
        return weightedSignal;
    }

    public static class AWeightingCoefts {

        public double[] a = getDenominatorCoefts();     // denominator coefficient vector
        public double[] b = getNumeratorCoefts();       // numerator coefficient vector


        public AWeightingCoefts(double[] a, double[] b) {
            this.a = a;
            this.b = b;
        }

        public double[] getDenominatorCoefts() { return a; }
        public double[] getNumeratorCoefts() { return b; }

    }

}
