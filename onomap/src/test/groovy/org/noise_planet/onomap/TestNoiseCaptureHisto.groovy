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
package org.noise_planet.onomap

import groovy.json.JsonOutput
import groovy.sql.Sql
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import java.util.logging.Level

import static org.junit.jupiter.api.Assertions.assertEquals
/**
 * Test parsing of zip file using H2GIS database
 */
class TestNoiseCaptureHisto extends JdbcTestCase {

    @BeforeEach
    void setUp() {
      initDb()
      installGadmAndTimeZone()
    }

  @Test
    void testGetLastMeasures() {
        Sql.LOG.level = Level.SEVERE
        // Parse file to database
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureDumpRecords.getResource("track_f7ff7498-ddfd-46a3-ab17-36a96c01ba1b.zip").file))
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureDumpRecords.getResource("track_a23261b3-b569-4363-95be-e5578d694238.zip").file))
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureDumpRecords.getResource("track_f720018a-a5db-4859-bd7d-377d29356c6f.zip").file))

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

  @Test
    void testGetLastMeasuresParty() {
        Sql.LOG.level = java.util.logging.Level.SEVERE
        // Parse file to database
        Sql sql = new Sql(connection)
        sql.execute("DELETE FROM NOISECAPTURE_PARTY WHERE tag='OGRS_2018'")
        def partyPk = sql.executeInsert("INSERT INTO NOISECAPTURE_PARTY(the_geom, title, tag, description, layer_name, filter_area) VALUES ('POLYGON((-1.64616016766905 47.1531855961037,-1.64616016766905 47.1553688595939,-1.64392677851205 47.1553688595939,-1.64392677851205 47.1531855961037,-1.64616016766905 47.1531855961037))','OGRS 2018 event','OGRS_2018'," +
                "'Open Geospatial consortium 2018','OGRS', true);").first()[0] as Integer
        assertEquals(1, new nc_parse().processFiles(connection,
                [new File(TestNoiseCaptureParse.getResource("track_fec26b2a-3345-4e58-9055-1a6567b055ad.zip").file)] as File[], 0, false))
        // Fetch data
        def arrayData = new nc_last_measures().getStats(connection, partyPk)
        assertEquals(1, arrayData.size())
    }

  @Test
    void testGetLastMeasuresRawParty() {
        Sql.LOG.level = java.util.logging.Level.SEVERE
        // Parse file to database
        Sql sql = new Sql(connection)
        sql.execute("DELETE FROM NOISECAPTURE_PARTY WHERE tag='OGRS_2018'")
        def partyPk = sql.executeInsert("INSERT INTO NOISECAPTURE_PARTY(the_geom, title, tag, description, layer_name, filter_area) VALUES ('POLYGON((-1.64616016766905 47.1531855961037,-1.64616016766905 47.1553688595939,-1.64392677851205 47.1553688595939,-1.64392677851205 47.1531855961037,-1.64616016766905 47.1531855961037))','OGRS 2018 event','OGRS_2018'," +
          "'Open Geospatial consortium 2018','OGRS', true);").first()[0] as Integer
        assertEquals(1, new nc_parse().processFiles(connection,
                [new File(TestNoiseCaptureParse.getResource("track_fec26b2a-3345-4e58-9055-1a6567b055ad.zip").file)] as File[], 0, false))
        // Fetch data
        def arrayData = new nc_raw_measurements().getStats(connection, partyPk, null)
        assertEquals(1, arrayData.size())
        assertEquals("http://data.noise-planet.org/raw/ea/8e/cf/ea8ecf6e-3357-4680-bbd9-62389b029ac4/track_fec26b2a-3345-4e58-9055-1a6567b055ad.zip", arrayData.get(0)["data"]);

        arrayData = new nc_raw_measurements().getStats(connection, partyPk, arrayData[0].record_utc as String)
        assertEquals(0, arrayData.size())
    }
}
