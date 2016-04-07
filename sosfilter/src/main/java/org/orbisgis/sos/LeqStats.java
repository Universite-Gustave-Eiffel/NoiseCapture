package org.orbisgis.sos;

/**
 * Compute descriptive statistics on leq
 */
public class LeqStats {
    private double rmsSum = 0;
    private int rmsSumCount = 0;
    private double leqMin = Double.MAX_VALUE;
    private double leqMax = Double.MIN_VALUE;

    public LeqStats() {
    }

    public void addLeq(double leq) {
        leqMin = Math.min(leqMin, leq);
        leqMax = Math.max(leqMax, leq);
        rmsSum += Math.pow(10., leq / 10.);
        rmsSumCount++;
    }

    /**
     * @param rms root mean squared
     * @return Leq
     */
    public double addRms(double rms) {
        rmsSum += rms;
        final double leq = 10 * Math.log10(rms);
        leqMin = Math.min(leqMin, leq);
        leqMax = Math.max(leqMax, leq);
        rmsSumCount++;
        return leq;
    }


    public double getLeqMin() {
        return leqMin;
    }

    public double getLeqMax() {
        return leqMax;
    }

    public double getLeqMean() {
        if(rmsSumCount > 0) {
            return 10 * Math.log10(rmsSum / rmsSumCount);
        } else {
            return 0;
        }
    }
}
