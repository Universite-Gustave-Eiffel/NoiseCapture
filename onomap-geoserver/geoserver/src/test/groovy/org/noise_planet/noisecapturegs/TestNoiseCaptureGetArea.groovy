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

import groovy.json.JsonOutput
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
import java.sql.Timestamp

/**
 * Test parsing of zip file using H2GIS database
 */
class TestNoiseCaptureGetArea extends JdbcTestCase {

    @Before
    void setUp() {
        super.setUp()
        Statement st = connection.createStatement()
        // Init schema
        st.execute(new File(TestNoiseCaptureGetArea.class.getResource("inith2.sql").getFile()).text)
        // Load timezone file
        st.execute("CALL FILE_TABLE('"+TestNoiseCaptureProcess.getResource("tz_world.shp").file+"', 'TZ_WORLD');")
        st.execute("CREATE SPATIAL INDEX ON TZ_WORLD(THE_GEOM)")
        new nc_feed_stats().processInput(connection,
                TestNoiseCaptureProcess.getResource("gevfit_of_stations.txt").toURI(), "stations")
        new nc_feed_stats().processInput(connection,
                TestNoiseCaptureProcess.getResource("delta_matrix_mu.txt").toURI(), "time_matrix_mu")
        new nc_feed_stats().processInput(connection,
                TestNoiseCaptureProcess.getResource("delta_matrix_sigma.txt").toURI(), "time_matrix_sigma")
    }

    void testProcess1() {
        // Parse file to database
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureGetArea.getResource("track_f7ff7498-ddfd-46a3-ab17-36a96c01ba1b.zip").file))
        // convert to hexagons
        new nc_process().process(connection, 10)
        // Fetch data
        def arrayData = new nc_get_area_info().getAreaInfo(connection, -139656, 265210, null)
        assertFalse(arrayData.isEmpty())
        assertEquals(62.37, (double)(arrayData.la50), 0.1)
        assertEquals(69, (double)(arrayData.mean_pleasantness), 0.01)
        assertEquals(30, (int)(arrayData.measure_count))
        assertEquals("2016-06-09T14:17:25+02:00", arrayData.first_measure)
        assertEquals("2016-06-09T14:18:02+02:00", arrayData.last_measure)

        // Check with NaN in pleasantness
        def sql = new Sql(connection)
        def fields = [cell_q           : 5,
                      cell_r           : 10,
                      the_geom         : "POLYGON EMPTY",
                      laeq         : 80,
                      la50         : 75,
                      lden         : 77,
                      mean_pleasantness: Double.NaN,
                      measure_count    : 1,
                      first_measure    : new Timestamp(1465474645151),
                      last_measure     : new Timestamp(1465474645151),
                      tzid             : TimeZone.default.ID]
        sql.executeInsert("INSERT INTO noisecapture_area(cell_q, cell_r, the_geom, laeq,la50,lden, mean_pleasantness," +
                " measure_count, first_measure, last_measure, tzid) VALUES (:cell_q, :cell_r, " +
                "ST_Transform(ST_GeomFromText(:the_geom,3857),4326) , :laeq, :la50,:lden," +
                " :mean_pleasantness, :measure_count, :first_measure, :last_measure, :tzid)", fields)
        // Fetch data
        arrayData = new nc_get_area_info().getAreaInfo(connection, 5, 10, null)
        assertNull(arrayData.mean_pleasantness)
        JsonOutput.toJson(arrayData);

    }


    void testGetAreaProfile() {
        Sql.LOG.level = java.util.logging.Level.SEVERE
        Sql sql = new Sql(connection)
        // Insert measure data
        // insert records
        TestNoiseCaptureProcess.addTestRecord(sql, "2016-09-07T13:43:13Z", "POINT(23.73847 37.97503)", [70, 75, 72])
        TestNoiseCaptureProcess.addTestRecord(sql, "2016-09-04T18:43:13Z", "POINT(23.73847 37.97503)", [60, 61, 58])
        TestNoiseCaptureProcess.addTestRecord(sql, "2016-09-03T16:43:13Z", "POINT(23.73847 37.97503)", [65, 68, 64])
        def processed = new nc_process().process(connection, 10)
        assertEquals(1, processed)
        // Read db; check content
        def row = sql.firstRow("SELECT cell_q, cell_r FROM  noisecapture_area")
        def arrayData = new nc_get_area_info().getAreaInfo(connection, row.cell_q, row.cell_r, null)
        assertNotNull(arrayData)
        assertEquals(72, arrayData["profile"].size())
        assertNull(arrayData["profile"][0])
        assertEquals(72.82d, (Double)arrayData["profile"][16]["laeq"], 0.01d)
        assertEquals(66.01d, (Double)arrayData["profile"][43]["laeq"], 0.01d)
        assertEquals(59.83d, (Double)arrayData["profile"][69]["laeq"], 0.01d)
        assertEquals(72.0d, (Double)arrayData["profile"][16]["la50"], 0.01d)
        assertEquals(65.0d, (Double)arrayData["profile"][43]["la50"], 0.01d)
        assertEquals(60.0d, (Double)arrayData["profile"][69]["la50"], 0.01d)
        JsonOutput.toJson(arrayData); // Check if conversion goes well
    }

    void testTagExtract() {
        Sql.LOG.level = java.util.logging.Level.SEVERE
        Sql sql = new Sql(connection)
        // Insert measure data
        // insert records
        // Create party before parsing party measurement
        sql.execute("INSERT INTO noisecapture_party (the_geom, layer_name, title, tag, description) VALUES ('POLYGON((-2.34041 47.25688,-2.34041 47.26488,-2.33241 47.26488,-2.33241 47.25688,-2.34041 47.25688))'::geometry, 'noisecapture:noisecapture_area_dw2017', 'Digital Week 2017 Pornichet', 'SNDIGITALWEEK', '<p>La Ville de Pornichet s''associe à la Saint-Nazaire Digital Week le mercredi 20 septembre, et propose de nombreuses animations gratuites et ouvertes à tous dédiées au numérique à l''hippodrome.</p><p>Venez contribuer à la création d''une carte du bruit participative, en temps réel sur les territoires de la CARENE / CAP ATLANTIQUE grâce à l''utilisation d''une application smartphone : Noise Capture.</p>');")
        // Parse Gwendall measurement
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureParse.getResource("track_07efe9f7-bda1-4e49-8514-f3a2a1fc576d.zip").file))

        def processed = new nc_process().process(connection, 50)

        def row = sql.firstRow("SELECT cell_q, cell_r FROM  noisecapture_area")

        assertNotNull(row);

        def arrayData = new nc_get_area_info().getAreaInfo(connection, row.cell_q, row.cell_r, null)
        assertNotNull(arrayData)

        assertTrue("tags" in arrayData)
        assertEquals(1, arrayData["tags"].size())
        assertEquals("road", arrayData["tags"][0].text)
        assertEquals(26, arrayData["tags"][0].weight)


    }
}
