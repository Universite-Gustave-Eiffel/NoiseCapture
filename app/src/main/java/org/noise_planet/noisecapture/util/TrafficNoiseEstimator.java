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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;

import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * This class receive the uncalibrated noise level LAeq (125ms) and estimates the number of passing vehicles,
 * With the help of distance to vehicle and average vehicle speed it can estimates
 * the expected noise level with an estimated uncertainty.
 */
public class TrafficNoiseEstimator {
    private JsonNode cnossosData = null;

    public void loadConstants(InputStream inputStream) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            cnossosData = mapper.readTree(inputStream);
        } catch (IOException ex) {
            cnossosData = NullNode.getInstance();
        }
    }

    /**
     * Minimum delay between peaks in seconds
     */
    private double delay = 3;
    private double increaseDelay = 2;
    private double temperature = 20.0;
    private double decreaseDelay = 2;
    private double distance = 3.5;
    double speed = 45.0;
    private double measurementHeight = 1.5 - 0.05; // Measurement height minus the source equivalent height
    private int[] frequencies = new int[] {63, 125, 250, 500, 1000, 2000, 4000, 8000};
    private String roadSurface = "NL01";

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
     * @param laeq 1s max dB(A) values
     * @return Median pass-by peak and number of peaks
     */
    public Estimation getMedianPeak(double[] laeq) {
        // Find received noise levels of passing vehicles
        List<PeakFinder.Element> peaks = getNoisePeaks(laeq);

        // Take median value of noise levels of passing vehicles
        double[] peaksSpl = new double[peaks.size()];
        for (int i = 0; i < peaksSpl.length; i++) {
            peaksSpl[i] = peaks.get(i).value;
        }

        return new Estimation(peaks.size(), new Median().evaluate(peaksSpl));
    }

    /**
     * @param medianPeak Measured level at device location
     */
    public double computeGain(double medianPeak) {

        speed = Math.max(20, Math.min(130, speed));

        double freeFieldDistance = Math.sqrt(distance*distance+measurementHeight*measurementHeight);

        // Evaluate level at source location
        double correction = 20 * Math.log10(freeFieldDistance) + 10 * Math.log10(2*Math.PI);

        double expectedGlobalLvl = 0;

        for (int freqParam = 0; freqParam < frequencies.length; freqParam++) {
            double lvRoadLvl = getNoiseLvl(getCoeff("ar", freqParam, "1"),
                    getCoeff("br", freqParam, "1"), speed, 70.);

            // Correction by temperature p. 36
            lvRoadLvl = lvRoadLvl+ 0.08*(20-temperature); // K = 0.08  p. 36

            //Road surface correction on rolling noise
            lvRoadLvl = lvRoadLvl+ getNoiseLvl(getA_Roadcoeff(freqParam,"1", roadSurface),
                    getB_Roadcoeff("1",roadSurface), speed, 70.);

            double lvMotorLvl = getCoeff("ap", freqParam, "1") +
                    getCoeff("bp", freqParam, "1") * (speed - 70) / 70;

            double lvCompound = sumDba(lvRoadLvl, lvMotorLvl);

            expectedGlobalLvl += dbaToW(lvCompound);
        }
        expectedGlobalLvl = wToDba(expectedGlobalLvl);

        return expectedGlobalLvl - (medianPeak + correction);
    }

    /**
     * @param numberOfPassingVehicles Average number of passing vehicles for each location.
     * @param numberOfLocations Number of differend measurement locations (different streets)
     * @return Estimated calibration uncertainty in dB(A)
     */
    public static double getCalibrationUncertainty(int numberOfPassingVehicles, int numberOfLocations) {
        final double inputAndProtocolUncertainty = 2.5;
        final double measurementUncertainty = 3.31204-1.13217 * Math.log10(numberOfLocations)
                - 1.29958 * Math.log10(numberOfPassingVehicles);

        return Math.sqrt( inputAndProtocolUncertainty*inputAndProtocolUncertainty+
                measurementUncertainty*measurementUncertainty);
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    /**
     * Compute attenuation of sound energy by distance. Minimum distance is one
     * meter.
     * @param distance Distance in meter
     * @return Attenuated sound level. Take only account of geometric dispersion
     * of sound wave.
     */
    public static double getADiv(double distance) {
        return  wToDba(4 * Math.PI * Math.max(1, distance * distance));
    }

    /** get noise level from speed **/
    private static Double getNoiseLvl(double base, double adj, double speed,
                                      double speedBase) {
        return base + adj * Math.log10(speed / speedBase);
    }

    public String getRoadSurface() {
        return roadSurface;
    }

    public void setRoadSurface(String roadSurface) {
        this.roadSurface = roadSurface;
    }

    /**
     * Vehicle emission values coefficients
     * @param coeff ar,br,ap,bp,a,b
     * @param freq frequence index 0-n
     * @param vehicleCategory 1,2,3,4a,4b..
     * @return
     */
    public Double getCoeff(String coeff, int freq, String vehicleCategory) {
        return cnossosData.get("vehicles").get(vehicleCategory).get(coeff).get(freq).doubleValue();
    }

    /** Get a Road Coeff by Freq **/
    public double getA_Roadcoeff(int freq, String vehCat, String RoadSurface) {
        return cnossosData.get("roads").get(RoadSurface).get("ref").get(vehCat).get("spectrum").get(freq).doubleValue();
    }

    /** Get b Road Coeff by Freq **/
    public double getB_Roadcoeff(String vehCat, String roadSurface) {
        return cnossosData.get("roads").get(roadSurface).get("ref").get(vehCat).get("ßm").doubleValue();
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
        public final int numberOfPassby;
        public final double medianPeak;

        public Estimation(int numberOfPassby, double medianPeak) {
            this.numberOfPassby = numberOfPassby;
            this.medianPeak = medianPeak;
        }
    }
}
