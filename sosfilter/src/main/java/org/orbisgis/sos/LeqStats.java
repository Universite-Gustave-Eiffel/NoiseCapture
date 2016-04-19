package org.orbisgis.sos;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Compute descriptive statistics on leq
 */
public class LeqStats {
    private double rmsSum = 0;
    private int rmsSumCount = 0;
    private double leqMin = Double.MAX_VALUE;
    private double leqMax = Double.MIN_VALUE;
    // Sorted map of leq classes
    private Map<Integer, AtomicInteger> leqClass = new TreeMap<>();
    private static final double classStep = 0.1;

    public LeqStats() {
    }

    public void addLeq(double leq) {
        leqMin = Math.min(leqMin, leq);
        leqMax = Math.max(leqMax, leq);
        rmsSum += Math.pow(10., leq / 10.);
        int key = (int)(leq / classStep);
        AtomicInteger leqCounter = leqClass.get(key);
        if(leqCounter == null) {
            leqCounter = new AtomicInteger(0);
            leqClass.put(key, leqCounter);
        }
        leqCounter.addAndGet(1);
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

    public LeqOccurences computeLeqOccurences() {
        List<Integer> classList = new ArrayList<>(leqClass.size());
        List<Integer> classValue = new ArrayList<>(leqClass.size());
        long sum = 0;
        for(Map.Entry<Integer, AtomicInteger> entry : leqClass.entrySet()) {
            classList.add(entry.getKey());
            classValue.add(0, entry.getValue().get());
            sum += entry.getValue().get();
        }
        List<Double> sumClassValuePerc = new ArrayList<>(classValue.size());
        double invSum = 0.;
        for(int classVal : classValue) {
            invSum += classVal / (double)sum;
            sumClassValuePerc.add(0, invSum);
        }
        return new LeqOccurences(0, 0, 0, sumClassValuePerc);
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

    public static class LeqOccurences {
        private final double la10;
        private final double la50;
        private final double la90;
        private final List<Double> userDefinedOccurences;

        public LeqOccurences(double la10, double la50, double la90, List<Double> userDefinedOccurences) {
            this.la10 = la10;
            this.la50 = la50;
            this.la90 = la90;
            this.userDefinedOccurences = userDefinedOccurences;
        }

        public double getLa10() {
            return la10;
        }

        public double getLa50() {
            return la50;
        }

        public double getLa90() {
            return la90;
        }

        public List<Double> getUserDefinedOccurences() {
            return userDefinedOccurences;
        }
    }
}
