package org.orbisgis.sos;

public class DigitalFilter {
    int order;
    double[] delay1;
    double[] delay2;
    double[] numerator;
    double[] denominator;
    int circularIndex = 0;

    public DigitalFilter(double[] numerator, double[] denominator) {
        this.numerator = numerator;
        this.denominator = denominator;
        order = numerator.length;
        delay1 = new double[order];
        delay2 = new double[order];
    }

    public void clearDelay() {
        circularIndex = 0;
        delay1 = new double[order];
        delay2 = new double[order];
    }

    /**
     * Direct form II transposed filter
     * @param samplesIn: Input samples
     * @link https://rosettacode.org/wiki/Apply_a_digital_filter_(direct_form_II_transposed)#Java
     * @param samplesIn
     * @param samplesOut
     */
    void filter(float[] samplesIn, float[] samplesOut) {
        for(int i=0; i < samplesIn.length; i++) {
            double inputAccumulator = 0;
            delay2[circularIndex] = samplesIn[i];
            for(int j=0; j < order; j++) {
                int indexDelay2 = (circularIndex - j) % order;
                if(indexDelay2 < 0)
                    indexDelay2 += delay2.length;
                inputAccumulator += numerator[j] * delay2[indexDelay2];
                if(j==0)
                    continue;
                int indexDelay1 = (order - j + circularIndex) % order;
                if(indexDelay1 < 0) {
                    indexDelay1 += delay1.length;
                }
                inputAccumulator -= denominator[j] * delay1[indexDelay1];
            }
            inputAccumulator /= denominator[0];
            delay1[circularIndex] = inputAccumulator;
            circularIndex++;
            if(circularIndex == order)
                circularIndex = 0;
            samplesOut[i] = (float)inputAccumulator;
        }
    }

    public double filterLeq(float[] samplesIn) {
        double squareSum = 0;
        for(int i=0; i < samplesIn.length; i++) {
            double inputAccumulator = 0;
            delay2[circularIndex] = samplesIn[i];
            for(int j=0; j < order; j++) {
                int indexDelay2 = (circularIndex - j) % order;
                if(indexDelay2 < 0)
                    indexDelay2 += delay2.length;
                inputAccumulator += numerator[j] * delay2[indexDelay2];
                if(j==0)
                    continue;
                int indexDelay1 = (order - j + circularIndex) % order;
                if(indexDelay1 < 0) {
                    indexDelay1 += delay1.length;
                }
                inputAccumulator -= denominator[j] * delay1[indexDelay1];
            }
            inputAccumulator /= denominator[0];
            delay1[circularIndex] = inputAccumulator;
            circularIndex++;
            if(circularIndex == order)
                circularIndex = 0;
            squareSum += inputAccumulator * inputAccumulator;
        }
        return 10 * Math.log10(squareSum / samplesIn.length);
    }
}
