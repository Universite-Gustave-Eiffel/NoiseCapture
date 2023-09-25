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

import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Created by G. Guillaume on 26/06/15.
 *  * Unit tests concerning audio signal processing
 */
public class CoreSignalProcessingTest {

    // Reference sound pressure level [Pa]
    private static final double REF_SOUND_PRESSURE = 2e-5;

//    @Test
//    public void testGetSamplingRate() throws Exception {
//
//    }
//
//    @Test
//    public void testAddSample() throws Exception {
//
//    }

//    @Test
//    public void testFilterSignal() throws Exception {
//
//    }


    @Test
    public void testLeqStats() {
        LeqStats leqStats = new LeqStats();
        leqStats.addLeq(45.15);
        leqStats.addLeq(45.21);
        leqStats.addLeq(45.35);
        leqStats.addLeq(46.33);
        leqStats.addLeq(46.87);
        leqStats.addLeq(45.86);
        leqStats.addLeq(48.15);
        leqStats.addLeq(49.5);
        leqStats.addLeq(50.5);
        leqStats.addLeq(50.52);
        leqStats.addLeq(50.62);
        leqStats.addLeq(49.89);
        leqStats.addLeq(49.88);
        leqStats.addLeq(48.45);
        leqStats.addLeq(65.15);
        leqStats.addLeq(65.14);
        leqStats.addLeq(66.12);
        leqStats.addLeq(66.2);
        leqStats.addLeq(67.21);
        leqStats.addLeq(66.25);
        double[][] classRanges = new double[][]{{Double.MIN_VALUE, 45}, {45, 55}, {55, 65}, {65, 75},{75, Double.MAX_VALUE}};
        LeqStats.LeqOccurrences leqOccurrences = leqStats.computeLeqOccurrences(classRanges);
        assertEquals(66.2, leqOccurrences.getLa10(), 0.01);
        assertEquals(49.8, leqOccurrences.getLa50(), 0.01);
        assertEquals(45.21, leqOccurrences.getLa90(), 0.01);
        List<Double> classRangesValues = leqOccurrences.getUserDefinedOccurrences();
        assertEquals(classRanges.length, classRangesValues.size());
        assertEquals(0, classRangesValues.get(0), 0.01);    // < 45
        assertEquals(0.7, classRangesValues.get(1), 0.01);  // [45-55)
        assertEquals(0, classRangesValues.get(2), 0.01);    // [55-65)
        assertEquals(0.3, classRangesValues.get(3), 0.01);  // [65-75)
        assertEquals(0, classRangesValues.get(4), 0.01);    // > 75
    }



    @Test
    public void testLeqStats2() {
        LeqStats leqStats = new LeqStats();
        leqStats.addLeq(45.15);
        double[][] classRanges = new double[][]{{Double.MIN_VALUE, 45}, {45, 55}, {55, 65}, {65, 75},{75, Double.MAX_VALUE}};
        LeqStats.LeqOccurrences leqOccurrences = leqStats.computeLeqOccurrences(classRanges);
        assertEquals(45.1, leqOccurrences.getLa10(), 0.01);
        assertEquals(45.1, leqOccurrences.getLa50(), 0.01);
        assertEquals(45.1, leqOccurrences.getLa90(), 0.01);
        List<Double> classRangesValues = leqOccurrences.getUserDefinedOccurrences();
        assertEquals(classRanges.length, classRangesValues.size());
        assertEquals(0, classRangesValues.get(0), 0.01);    // < 45
        assertEquals(1., classRangesValues.get(1), 0.01);  // [45-55)
        assertEquals(0, classRangesValues.get(2), 0.01);    // [55-65)
        assertEquals(0, classRangesValues.get(3), 0.01);  // [65-75)
        assertEquals(0, classRangesValues.get(4), 0.01);    // > 75
    }
}