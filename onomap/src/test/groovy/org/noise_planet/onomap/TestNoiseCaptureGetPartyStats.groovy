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

import groovy.sql.Sql
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.noise_planet.onomap.sensitive.nc_parse

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertTrue
/**
 * Test party stats
 */
class TestNoiseCaptureGetPartyStats extends JdbcTestCase {

    @BeforeEach
    void setUp() {
        initDb()
        installGadmAndTimeZone()
    }

  @Test
    void testPartyBestContributors() {
        Sql.LOG.level = java.util.logging.Level.SEVERE
        // Parse file to database
        Sql sql = new Sql(connection)
        def res = sql.executeInsert("INSERT INTO NOISECAPTURE_PARTY(the_geom, title, tag, description, layer_name, filter_area) VALUES ('POLYGON((-1.64616016766905 47.1531855961037,-1.64616016766905 47.1553688595939,-1.64392677851205 47.1553688595939,-1.64392677851205 47.1531855961037,-1.64616016766905 47.1531855961037))','OGRS 2018 event','OGRS_2018'," +
                "'Open Geospatial consortium 2018','OGRS', true);")
        assertEquals(1, new nc_parse().processFiles(connection,
                [new File(TestNoiseCaptureGetPartyStats.getResource("track_fec26b2a-3345-4e58-9055-1a6567b055ad.zip").file)] as File[], 0, false))
        // Fetch data
        assertNotNull(res.first())
        def pk_party = res.first()[0] as Integer
        def arrayData = new nc_party_get_stats().getStatistics (connection, pk_party)
        assertTrue("contributors" in arrayData.keySet())
        assertEquals(1, arrayData["contributors"].size())
        assertEquals("ea8ecf6e-3357-4680-bbd9-62389b029ac4", arrayData["contributors"][0]["userid"])
        assertEquals(1, arrayData["contributors"][0]["total_records"])
        assertEquals(23, arrayData["contributors"][0]["total_length"])
    }
}
