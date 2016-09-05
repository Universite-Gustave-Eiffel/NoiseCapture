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
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Timestamp

/**
 * Test parsing of csv files using H2GIS database
 */
class TestNoiseCaptureFeedStats extends GroovyTestCase {
    Connection connection;


    @Rule
    public TemporaryFolder folder= new TemporaryFolder(new File("build"));

    @Before
    void setUp() {
        folder.create()
        connection = Driver.load().connect("jdbc:h2:"+new File(folder.newFolder(),"test;MODE=PostgreSQL").getAbsolutePath(), null)
        Statement st = connection.createStatement()
        // Init spatial
        st.execute("CREATE ALIAS IF NOT EXISTS H2GIS_EXTENSION FOR \"org.h2gis.ext.H2GISExtension.load\";\n" +
                "CALL H2GIS_EXTENSION();")
    }

    @After
    void tearDown() {
        connection.close();
        folder.delete()
    }

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
        // Parse csv files
        new nc_feed_stats().processInput(connection, csvFile.toURI(), "time_matrix_mu")
        // Read db; check content
        Sql sql = new Sql(connection)
        def res = sql.firstRow("SELECT COUNT(*) cpt FROM delta_sigma_time_matrix")
        assertEquals(72*72, res["cpt"]);
        res = sql.firstRow("SELECT MAX(delta_db) maxdelta FROM delta_sigma_time_matrix")
        assertTrue(res["maxdelta"] > 0);
    }
}
