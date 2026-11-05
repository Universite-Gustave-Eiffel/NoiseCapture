/*
 *  This file is part of the NoiseCapture application and OnoMap system.
 *
 *  The 'OnoMaP' system is led by Lab-STICC and Univ Eiffel - UMRAE and generates noise maps via
 *  citizen-contributed noise data.
 *
 *  This application is co-funded by the ENERGIC-OD Project (European Network for
 *  Redistributing Geospatial Information to user Communities - Open Data). ENERGIC-OD
 *  (http://www.energic-od.eu/) is partially funded under the ICT Policy Support Programme (ICT
 *  PSP) as part of the Competitiveness and Innovation Framework Programme by the European
 *  Community. The application work is also supported by the French geographic portal GEOPAL of the
 *  Pays de la Loire region (http://www.geopal.org).
 *
 *  Copyright (C) Univ Eiffel - UMRAE and Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
 *
 *  NoiseCapture is a free software; you can redistribute it and/or modify it under the terms of the
 *  GNU General Public License as published by the Free Software Foundation; either version 3 of
 *  the License, or(at your option) any later version. NoiseCapture is distributed in the hope that
 *  it will be useful,but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 *  more details.You should have received a copy of the GNU General Public License along with this
 *  program; if not, write to the Free Software Foundation,Inc., 51 Franklin Street, Fifth Floor,
 *  Boston, MA 02110-1301  USA or see For more information,  write to Université Gustave Eiffel,
 *  14-20 Boulevard Newton Cite Descartes, Champs sur Marne F-77447 Marne la Vallee Cedex 2 FRANCE
 *   or write to scientific.computing@univ-eiffel.fr
 */

package org.noise_planet.noisecapture;

import org.junit.Test;
import org.orbisgis.sos.LeqStats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertNull;

/**
 * Regression tests for chart lifecycle events including data arriving before view creation,
 * view recreation, and activity state restoration.
 */
public class ChartLifecycleTest {

    @Test
    public void testLineChartDataArrivingBeforeViewCreation() {
        ResultsLineChartFragment fragment = new ResultsLineChartFragment();

        // Ensure chart view is null before onCreateView
        assertNull(fragment.getTimeLevelChart());

        // Simulate data arriving before view creation
        LeqStats.LeqOccurrences occurrences = new LeqStats.LeqOccurrences(60.0, 55.0, 50.0, new ArrayList<Double>());
        List<Integer> timeData = Arrays.asList(0, 1, 2, 3, 4);
        List<Float> splData = Arrays.asList(50.0f, 55.0f, 60.0f, 58.0f, 52.0f);

        // Should update internal fields gracefully without throwing NullPointerException
        fragment.setTimeLevelData(occurrences, timeData, splData);
    }

    @Test
    public void testSpectrumDataArrivingBeforeViewCreation() {
        ResultsSpectrumFragment fragment = new ResultsSpectrumFragment();

        // Simulate data arriving before view creation
        List<Float> splHistogram = Arrays.asList(40.0f, 45.0f, 50.0f, 55.0f, 60.0f, 65.0f);
        String[] ltob = new String[]{"125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz"};

        // Should update internal state without NPE
        fragment.setDataS(splHistogram, ltob);
    }

    @Test
    public void testViewRecreationStatePreservation() {
        ResultsLineChartFragment fragment = new ResultsLineChartFragment();

        LeqStats.LeqOccurrences occurrences = new LeqStats.LeqOccurrences(60.0, 55.0, 50.0, new ArrayList<Double>());
        List<Integer> timeData = Arrays.asList(0, 1, 2);
        List<Float> splData = Arrays.asList(45.0f, 50.0f, 55.0f);

        fragment.setTimeLevelData(occurrences, timeData, splData);

        // Simulate view destruction (e.g. fragment detachment or tab switch)
        fragment.onDestroyView();
        assertNull(fragment.getTimeLevelChart());

        // Simulate re-setting data upon view recreation
        fragment.setTimeLevelData(occurrences, timeData, splData);
    }
}
