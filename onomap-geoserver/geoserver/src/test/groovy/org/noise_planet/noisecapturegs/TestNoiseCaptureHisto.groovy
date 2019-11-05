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
class TestNoiseCaptureHisto extends JdbcTestCase {

    @Before
    void setUp() {
        super.setUp()
        Statement st = connection.createStatement()
        // Init schema
        st.execute(new File(TestNoiseCaptureHisto.class.getResource("inith2.sql").getFile()).text)
        // Load timezone file
        st.execute("CALL FILE_TABLE('"+TestNoiseCaptureProcess.getResource("tz_world.shp").file+"', 'TZ_WORLD');")
        st.execute("CREATE SPATIAL INDEX ON TZ_WORLD(THE_GEOM)")
        // ut_deps has been derived from https://www.data.gouv.fr/fr/datasets/contours-des-departements-francais-issus-d-openstreetmap/ (c) osm
        // See ut_deps.txt for more details
        st.execute("CALL GEOJSONREAD('"+TestNoiseCaptureProcess.getResource("ut_deps.geojson").file+"', 'GADM28');")
    }

    void testGetLastMeasures() {
        Sql.LOG.level = java.util.logging.Level.SEVERE
        // Parse file to database
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureDumpRecords.getResource("track_f7ff7498-ddfd-46a3-ab17-36a96c01ba1b.zip").file))
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureDumpRecords.getResource("track_a23261b3-b569-4363-95be-e5578d694238.zip").file))
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureDumpRecords.getResource("track_f720018a-a5db-4859-bd7d-377d29356c6f.zip").file))
        new nc_parse().buildStatistics(connection, null)
        // Fetch data
        def arrayData = new nc_last_measures().getStats(connection, null)
        assertEquals(3, arrayData.size())
        assertEquals("France", arrayData[0].country)
        assertEquals("Poitou-Charentes", arrayData[0].name_1)
        assertEquals("Charente-Maritime", arrayData[0].name_3)
        assertEquals("Italy", arrayData[1].country)
        assertEquals("Umbria", arrayData[1].name_1)
        assertEquals("Perugia", arrayData[1].name_3)
        assertEquals("France", arrayData[2].country)
        assertEquals("Pays de la Loire", arrayData[2].name_1)
        assertEquals("Loire-Atlantique", arrayData[2].name_3)
        // Check conversion to json
        def jsonData = JsonOutput.toJson(arrayData);
    }


    void testGetLastMeasuresParty() {
        Sql.LOG.level = java.util.logging.Level.SEVERE
        // Parse file to database
        Sql sql = new Sql(connection)
        sql.execute("INSERT INTO NOISECAPTURE_PARTY(the_geom, title, tag, description, layer_name, filter_area) VALUES ('POLYGON((-1.64616016766905 47.1531855961037,-1.64616016766905 47.1553688595939,-1.64392677851205 47.1553688595939,-1.64392677851205 47.1531855961037,-1.64616016766905 47.1531855961037))','OGRS 2018 event','OGRS_2018'," +
                "'Open Geospatial consortium 2018','OGRS', true);")
        assertEquals(1, new nc_parse().processFiles(connection,
                [new File(TestNoiseCaptureParse.getResource("track_fec26b2a-3345-4e58-9055-1a6567b055ad.zip").file)] as File[], 0, false))
        // Fetch data
        def arrayData = new nc_last_measures().getStats(connection, 1)
        assertEquals(1, arrayData.size())
    }


    void testGetLastMeasuresRawParty() {
        Sql.LOG.level = java.util.logging.Level.SEVERE
        // Parse file to database
        Sql sql = new Sql(connection)
        sql.execute("INSERT INTO NOISECAPTURE_PARTY(the_geom, title, tag, description, layer_name, filter_area) VALUES ('POLYGON((-1.64616016766905 47.1531855961037,-1.64616016766905 47.1553688595939,-1.64392677851205 47.1553688595939,-1.64392677851205 47.1531855961037,-1.64616016766905 47.1531855961037))','OGRS 2018 event','OGRS_2018'," +
                "'Open Geospatial consortium 2018','OGRS', true);")
        assertEquals(1, new nc_parse().processFiles(connection,
                [new File(TestNoiseCaptureParse.getResource("track_fec26b2a-3345-4e58-9055-1a6567b055ad.zip").file)] as File[], 0, false))
        // Fetch data
        def arrayData = new nc_raw_measurements().getStats(connection, 1, null)
        assertEquals(1, arrayData.size())
        assertEquals("http://data.noise-planet.org/raw/ea/8e/cf/ea8ecf6e-3357-4680-bbd9-62389b029ac4/track_fec26b2a-3345-4e58-9055-1a6567b055ad.zip", arrayData.get(0)["data"]);

        arrayData = new nc_raw_measurements().getStats(connection, 1, arrayData[0].record_utc as String)
        assertEquals(0, arrayData.size())
    }
}
