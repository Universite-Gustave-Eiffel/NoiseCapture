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

package org.noise_planet.noisecapture.util;

import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private double increaseDelay = 2;
    private double decreaseDelay = 2;
    private double distance = 3.5;

    public List<PeakFinder.Element> getNoisePeaks(double[] laeq) {
        if(laeq.length == 0) {
            return new ArrayList<>();
        }

        //double[] maxLaeq = new la
        // Evaluate L95 (Quantile 0.05)
        Percentile percentile = new Percentile(5);
        percentile.setData(laeq);
        double l95 = percentile.evaluate();
        // Find peaks
        PeakFinder peakFinder = new PeakFinder();
        peakFinder.setMinDecreaseCount((int)(increaseDelay));
        peakFinder.setMinIncreaseCount((int)(decreaseDelay));
        long i = 0;

        for(double l : laeq) {
            peakFinder.add(i, l);
            i+=1;
        }
        List<PeakFinder.Element> filtered = PeakFinder.filter(peakFinder.getPeaks(), l95 + 15);
        return PeakFinder.filter(filtered, (int)(delay));
    }

    public double[] fastToSlowLeqMax(double[] laeq) {
        // Compute LaMax on 8 moving values
        double[] maxLaeq = new double[laeq.length / 8];
        for(int i = 0; i < maxLaeq.length; i++) {
            maxLaeq[i] = Double.MIN_VALUE;
        }
        for(int i = 0; i < maxLaeq.length * 8; i++) {
            maxLaeq[i / 8] = Math.max( maxLaeq[i / 8], laeq[i]);
        }
        return maxLaeq;
    }

    /**
     *
     * @param laeq 125ms dB(A) values
     */
    public Estimation evaluate(double[] laeq) {
        Estimation estimation = new Estimation();

        // Find received noise levels of passing vehicles
        List<PeakFinder.Element> peaks = getNoisePeaks(fastToSlowLeqMax(laeq));

        // Take median value of noise levels of passing vehicles
        double[] peaksSpl = new double[peaks.size()];
        for (int i = 0; i < peaksSpl.length; i++) {
            peaksSpl[i] = peaks.get(i).value;
        }

        double medianPeak = new Median().evaluate(peaksSpl);

        /*
        double lvRoadLvl = getNoiseLvl(getCoeff("ar", freqParam , "1"  ,coeffVer),
                getCoeff("br", freqParam , "1"  ,coeffVer), parameters.getSpeedLv(), 70.);

        double lvMotorLvl = getCoeff("ap", freqParam , "1" ,coeffVer ) +
                getCoeff("bp", freqParam , "1" ,coeffVer ) * (parameters.getSpeedLv()-70)/70 ;

        double lvCompound = sumDba(lvRoadLvl, lvMotorLvl);

        double lvLvl = lvCompound + 10 * Math.log10(vperhour / (1000 * speed));
        */


        return estimation;
    }

    private static Double sumDba(Double dBA1, Double dBA2) {
        return wToDba(dbaToW(dBA1) + dbaToW(dBA2));
    }

    public static double wToDba(double w) {
        return 10 * Math.log10(w);
    }

    public static double dbaToW(double dBA) {
        return Math.pow(10., dBA / 10.);
    }

    /**
     * @return Distance to closest line of vehicles
     */
    public double getDistance() {
        return distance;
    }

    /**
     * @param distance Distance to closest line of vehicles
     */
    public void setDistance(double distance) {
        this.distance = distance;
    }

    /**
     * @return Minimum delay in seconds for considering a passing vehicle
     */
    public double getDelay() {
        return delay;
    }

    /**
     * @param delay Minimum delay in seconds for considering a passing vehicle
     */
    public void setDelay(double delay) {
        this.delay = delay;
    }

    public double getIncreaseDelay() {
        return increaseDelay;
    }

    public void setIncreaseDelay(double increaseDelay) {
        this.increaseDelay = increaseDelay;
    }

    public double getDecreaseDelay() {
        return decreaseDelay;
    }

    public void setDecreaseDelay(double decreaseDelay) {
        this.decreaseDelay = decreaseDelay;
    }

    /**
     * Result
     */
    public static class Estimation {

    }
}
