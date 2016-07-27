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
 * Test parsing of zip file using H2GIS database
 */
class TestNoiseCaptureParse  extends GroovyTestCase {
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
        // Init schema
        st.execute(new File(TestNoiseCaptureParse.class.getResource("inith2.sql").getFile()).text)
    }

    @After
    void tearDown() {
        connection.close();
        folder.delete()
    }

    void testParse1() {
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureParse.getResource("track_f7ff7498-ddfd-46a3-ab17-36a96c01ba1b.zip").file))
        // Read db; check content
        Sql sql = new Sql(connection)
        assertEquals(1, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_track").get("cpt"))
        sql.eachRow("SELECT * FROM noisecapture_track") { ResultSet row ->
            Integer idTrack = row.pk_track
            assertNotNull(idTrack)
            assertEquals("Logicom", row.getString("device_manufacturer"))
            assertEquals("L-ITE502", row.getString("device_product"))
            assertEquals("L-ITE 502", row.getString("device_model"))
            assertEquals(69, row.getInt("pleasantness"))
            assertEquals(84, row.getDouble("time_length"), 0.01)
            assertEquals(72.94, row.getDouble("noise_level"), 0.01)
            assertEquals("f7ff7498-ddfd-46a3-ab17-36a96c01ba1b", row.getString("track_uuid"))
            assertEquals(new Timestamp(1465474618170), row.getTimestamp("record_utc"))
            // Check tags
            assertEquals(3, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_tag").get("cpt"))
            Set<String> tagStored = new HashSet()
            def expected = ["test","indoor","silent"] as Set
            sql.eachRow("SELECT tag_name FROM noisecapture_track_tag TT, noisecapture_tag T WHERE T.PK_TAG = TT.PK_TAG" +
                    " AND TT.PK_TRACK = :pktrack", [pktrack:idTrack]) { ResultSet rowTag ->
                tagStored.add(rowTag.getString("tag_name"))
            }
            assertEquals(expected, tagStored)
            // Check records
            assertEquals(87, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_point where pk_track=:idtrack",
                    [idtrack:idTrack]).get("cpt"))
            assertEquals(23 * 87, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_freq").get("cpt"))
            
        }
    }

    void testWrongUUID() {
        shouldFail(IllegalArgumentException.class) {
            new nc_parse().processFile(connection, new File(
                    TestNoiseCaptureParse.getResource("track_f7ff7498-zzfd-46a3-ab17-36a96c01ba1b.zip").file))
        }
    }

    // Test parse without user feedback
    void testParseNull() {
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureParse.getResource("track_426f00da-dd68-408f-bd7b-f166ba022f4d.zip").file))
        // Read db; check content
        Sql sql = new Sql(connection)
        assertEquals(1, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_track").get("cpt"))
        sql.eachRow("SELECT * FROM noisecapture_track") { ResultSet row ->
            Integer idTrack = row.pk_track
            assertNotNull(idTrack)
            assertEquals("LGE", row.getString("device_manufacturer"))
            assertEquals("iproj_vdf_de", row.getString("device_product"))
            assertEquals("LG-P936", row.getString("device_model"))
            assertNull(row.getObject("pleasantness"))
            assertEquals(11, row.getDouble("time_length"), 0.01)
            assertEquals(73.7, row.getDouble("noise_level"), 0.01)
            assertEquals("426f00da-dd68-408f-bd7b-f166ba022f4d", row.getString("track_uuid"))
            assertEquals(new Timestamp(1465826253538), row.getTimestamp("record_utc"))
            // Check tags
            assertEquals(0, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_tag").get("cpt"))
            // Check records
            assertEquals(11, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_point where pk_track=:idtrack",
                    [idtrack: idTrack]).get("cpt"))
            assertEquals(23 * 11, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_freq").get("cpt"))
        }
    }



    void testParseWidthExistingTags() {
        Sql sql = new Sql(connection)
        sql.executeInsert("INSERT INTO noisecapture_tag (tag_name) VALUES (:tag_name)",
                [tag_name:"nature"])
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureParse.getResource("track_f7ff7498-ddfd-46a3-ab17-36a96c01ba1b.zip").file))
        // Read db; check content
        assertEquals(1, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_track").get("cpt"))
        sql.eachRow("SELECT * FROM noisecapture_track") { ResultSet row ->
            Integer idTrack = row.pk_track
            assertNotNull(idTrack)
            assertEquals("Logicom", row.getString("device_manufacturer"))
            assertEquals("L-ITE502", row.getString("device_product"))
            assertEquals("L-ITE 502", row.getString("device_model"))
            assertEquals(69, row.getInt("pleasantness"))
            assertEquals(84, row.getDouble("time_length"), 0.01)
            assertEquals(72.94, row.getDouble("noise_level"), 0.01)
            assertEquals("f7ff7498-ddfd-46a3-ab17-36a96c01ba1b", row.getString("track_uuid"))
            assertEquals(new Timestamp(1465474618170), row.getTimestamp("record_utc"))
            // Check tags
            assertEquals(4, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_tag").get("cpt"))
            Set<String> tagStored = new HashSet()
            def expected = ["test","indoor","silent"] as Set
            sql.eachRow("SELECT tag_name FROM noisecapture_track_tag TT, noisecapture_tag T WHERE T.PK_TAG = TT.PK_TAG" +
                    " AND TT.PK_TRACK = :pktrack", [pktrack:idTrack]) { ResultSet rowTag ->
                tagStored.add(rowTag.getString("tag_name"))
            }
            assertEquals(expected, tagStored)
            // Check records
            assertEquals(87, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_point where pk_track=:idtrack",
                    [idtrack:idTrack]).get("cpt"))
            assertEquals(23 * 87, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_freq").get("cpt"))

        }
    }

    void testCellProcessing() {
        double[][] ptArray = [[9.1959717, 45.4656459],
                              [9.190983333333332, 45.464695],
                              [9.190904999999999, 45.46467833333333],
                              [9.190801666666665, 45.46465],
                              [9.190801666666665, 45.46465],
                              [9.190703333333333, 45.464635],
                              [9.190574999999999, 45.46461666666667],
                              [9.190493333333334, 45.46461166666666],
                              [9.190380000000001, 45.464596666666665],
                              [9.190305, 45.46459166666667],
                              [9.190203333333333, 45.46456833333333],
                              [9.19012, 45.46456666666666],
                              [9.190103333333333, 45.46463666666667],
                              [9.190028333333334, 45.464631666666676],
                              [9.189838333333334, 45.46459166666667],
                              [9.189758333333334, 45.464576666666666],
                              [9.189676666666665, 45.464555000000004],
                              [9.189603333333334, 45.46453666666666],
                              [9.189516666666666, 45.464531666666666],
                              [9.189348333333333, 45.464515],
                              [9.189266666666667, 45.46450333333334],
                              [9.1892, 45.46448999999999],
                              [9.189116666666667, 45.464461666666665],
                              [9.189056666666666, 45.464418333333334]];

    }
}
