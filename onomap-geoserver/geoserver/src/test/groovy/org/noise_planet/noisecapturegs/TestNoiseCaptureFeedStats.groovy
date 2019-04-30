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
 * Copyright (C) 2007-2016 - IFSTTAR - LAE
 * Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
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

package org.noise_planet.noisecapturegs

import groovy.sql.Sql
import org.h2.Driver
import org.h2gis.functions.factory.H2GISDBFactory
import org.h2gis.utilities.SFSUtilities
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder

import java.sql.Connection
import java.sql.Statement

/**
 * Test parsing of csv files using H2GIS database
 */
class TestNoiseCaptureFeedStats extends JdbcTestCase {

    void testFeed1() {
        Sql.LOG.level = java.util.logging.Level.SEVERE
        // Generate CSV file
        File csvFile = folder.newFile("time_matrix_mu.csv")
        csvFile.withWriter { out ->
            for(int hour_ref = 1; hour_ref <= 72; hour_ref++) {
                for(int hour_target = 1; hour_target <= 72; hour_target++) {
                    def value = Math.random() * 12 - 6
                    if(hour_target > 1) {
                        out.write(",")
                    }
                    out.write(String.format(Locale.ROOT, "%.4f", value))
                }
                out.write("\n")
            }
        }
        File csvSigmaFile = folder.newFile("time_matrix_sigma.csv")
        csvSigmaFile.withWriter { out ->
            for(int hour_ref = 1; hour_ref <= 72; hour_ref++) {
                for(int hour_target = 1; hour_target <= 72; hour_target++) {
                    def value = Math.random() * 12 - 6
                    if(hour_target > 1) {
                        out.write(",")
                    }
                    out.write(String.format(Locale.ROOT, "%.4f", value))
                }
                out.write("\n")
            }
        }
        // Parse csv files
        assertEquals(72*72, new nc_feed_stats().processInput(connection, csvFile.toURI(), "time_matrix_mu"))
        assertEquals(72*72, new nc_feed_stats().processInput(connection, csvSigmaFile.toURI(), "time_matrix_sigma"))
        // Read db; check content
        Sql sql = new Sql(connection)
        def res = sql.firstRow("SELECT COUNT(*) cpt FROM delta_sigma_time_matrix")
        assertEquals(72*72, res["cpt"]);
        res = sql.firstRow("SELECT MAX(delta_db) maxdelta FROM delta_sigma_time_matrix")
        assertTrue(res["maxdelta"] > 0);
        res = sql.firstRow("SELECT MAX(delta_sigma) maxsigma FROM delta_sigma_time_matrix")
        assertTrue(res["maxsigma"] > 0);
    }



    void testStation() {
        Sql.LOG.level = java.util.logging.Level.SEVERE
        // Generate CSV file
        File csvFile = folder.newFile("stations.csv")
        csvFile.withWriter { out ->
            for(int hour_ref = 1; hour_ref <= 72; hour_ref++) {
                for(int stationid = 0; stationid < 23; stationid++) {
                    if(stationid > 0) {
                        out.write(",")
                    }
                    // sigma
                    out.write(String.format(Locale.ROOT, "%.4f", 0.5))
                    out.write(",")
                    // std_dev
                    out.write(String.format(Locale.ROOT, "%.4f", -0.3))
                    out.write(",")
                    // mu
                    out.write(String.format(Locale.ROOT, "%.4f", 0.2))
                }
                out.write("\n")
            }
        }
        // Parse csv files
        assertEquals(72*23, new nc_feed_stats().processInput(connection, csvFile.toURI(), "stations"))
        // Read db; check content
        Sql sql = new Sql(connection)
        def res = sql.firstRow("SELECT COUNT(*) cpt FROM stations_ref")
        assertEquals(72*23, res["cpt"]);
        res = sql.firstRow("SELECT MAX(std_dev) maxstd_dev FROM stations_ref")
        assertEquals(-0.3, res["maxstd_dev"], 0.01);
        res = sql.firstRow("SELECT MAX(mu) max_mu FROM stations_ref")
        assertEquals(0.2, res["max_mu"], 0.01);
        res = sql.firstRow("SELECT MAX(sigma) max_sigma FROM stations_ref")
        assertEquals(0.5, res["max_sigma"], 0.01);
    }
}
