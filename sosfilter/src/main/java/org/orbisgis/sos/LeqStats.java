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

import org.apache.commons.math3.stat.descriptive.rank.Percentile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
    private static final double DEFAULT_CLASS_STEP = 0.1;
    private double classStep = DEFAULT_CLASS_STEP;


    public LeqStats() {
    }

    public LeqStats(double classStep) {
        this.classStep = classStep;
    }

    public LeqStats(LeqStats copyFrom) {
        leqMin = copyFrom.leqMin;
        leqMax = copyFrom.leqMax;
        rmsSum = copyFrom.rmsSum;
        classStep = copyFrom.classStep;
        rmsSumCount = copyFrom.rmsSumCount;
        leqClass = new TreeMap<>(copyFrom.leqClass);
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
     * Compute Leq stats using specified range.double[][] classRanges = ;
     * @param laOccurrencesRanges Min-Max range ex: new double[][]{{Double.MIN_VALUE, 45}, {45, 55}, {55, 65}, {65, 75},{75, Double.MAX_VALUE}}
     * @return LeqOccurrences instance
     */
    public LeqOccurrences computeLeqOccurrences(double[][] laOccurrencesRanges) {
        // Compute invert sum of class occurrences
        List<Double> classList = new ArrayList<>(leqClass.size());
        List<Integer> classValue = new ArrayList<>(leqClass.size());
        long sum = 0;
        double[] values = new double[rmsSumCount];
        int valCounter = 0;
        for(Map.Entry<Integer, AtomicInteger> entry : leqClass.entrySet()) {
            double leq = entry.getKey() * classStep;
            classList.add(leq);
            classValue.add(0, entry.getValue().get());
            sum += entry.getValue().get();
            for(int classValCount = 0; classValCount < entry.getValue().get(); classValCount++) {
                values[valCounter++] = leq;
            }
        }
        List<Double> sumClassValuePerc = new ArrayList<>(classValue.size());
        double invSum = 0.;
        for(int classVal : classValue) {
            invSum += classVal / (double)sum;
            sumClassValuePerc.add(0, invSum);
        }
        Percentile percentile = new Percentile();
        percentile.setData(values);
        // Fetch level at each lae
        double la10 = percentile.evaluate(100 - 10);
        double la50 = percentile.evaluate(50);
        double la90 = percentile.evaluate(100 - 90);

        // Sum percentage between provided laOccurrancesRanges
        List<Double> laOccurrencesRangesValue = new ArrayList<>();
        if(laOccurrencesRanges != null) {
            for(double[] range : laOccurrencesRanges) {
                double min = range[0];
                double max = range[1];
                double sumClass = 0;
                for(int idClass = 0; idClass < sumClassValuePerc.size(); idClass++) {
                    if(classList.get(idClass) >= min) {
                        if(classList.get(idClass) < max) {
                            sumClass += classValue.get(sumClassValuePerc.size() - 1 - idClass) / (double)sum;
                        } else {
                            break;
                        }
                    }
                }
                laOccurrencesRangesValue.add(sumClass);
            }
        }

        return new LeqOccurrences(la10, la50, la90, laOccurrencesRangesValue);
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

    public static class LeqOccurrences {
        private final double la10;
        private final double la50;
        private final double la90;
        private final List<Double> userDefinedOccurrences;

        public LeqOccurrences(double la10, double la50, double la90, List<Double> userDefinedOccurrences) {
            this.la10 = la10;
            this.la50 = la50;
            this.la90 = la90;
            this.userDefinedOccurrences = userDefinedOccurrences;
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

        public List<Double> getUserDefinedOccurrences() {
            return userDefinedOccurrences;
        }
    }
}
