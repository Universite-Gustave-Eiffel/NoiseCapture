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
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Timestamp

/**
 * Test parsing of zip file using H2GIS database
 */
class TestNoiseCaptureParse  extends JdbcTestCase {

    @Before
    void setUp() {
        super.setUp()
        Statement st = connection.createStatement()
        // Init schema
        st.execute(new File(TestNoiseCaptureParse.class.getResource("inith2.sql").getFile()).text)
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
            assertEquals(new Timestamp(1465474618000), row.getTimestamp("record_utc"))
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
            assertEquals(new Timestamp(1465826253000), row.getTimestamp("record_utc"))
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
            assertEquals(new Timestamp(1465474618000), row.getTimestamp("record_utc"))
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
            assertEquals(334.59,sql.firstRow("SELECT orientation FROM noisecapture_point where time_date = :lequtc::timestamptz", [lequtc : new nc_parse().epochToRFCTime(1465474658594)]).get("orientation"), 0.01)
            assertEquals(0.077723056,sql.firstRow("SELECT speed FROM noisecapture_point where time_date = :lequtc::timestamptz", [lequtc : new nc_parse().epochToRFCTime(1465474658594)]).get("speed"), 1e-6)
        }
    }

    void testParseProfile() {
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureParse.getResource("track_e0f5dd71-75f9-44b2-a106-98712a826719.zip").file))
        // Read db; check content
        Sql sql = new Sql(connection)
        assertEquals(1, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_track").get("cpt"))
        assertEquals("NONE", sql.firstRow("SELECT * FROM  noisecapture_user").profile)
        // Check records
        assertEquals(107, sql.firstRow("SELECT ORIENTATION FROM  noisecapture_point where time_date=:time_date",
                [time_date: new nc_parse().epochToRFCTime(1504245786780)]).orientation)
        assertEquals(1.83, sql.firstRow("SELECT SPEED FROM  noisecapture_point where time_date=:time_date",
                [time_date: new nc_parse().epochToRFCTime(1504245786780)]).speed)


    }


    // Test parse with a party tag that does not exists in database
    void testParseInvalidNoiseParty() {
        Sql sql = new Sql(connection)
        sql.execute("INSERT INTO NOISECAPTURE_PARTY(the_geom, title, tag, description, layer_name) VALUES ('POLYGON((2.38717 48.8944,2.38717 48.8964,2.38917 48.8964,2.38917 48.8944,2.38717 48.8944))','ANQES 2017 event','ANQES2017'," +
                "'8es Assises nationales de la qualité de l''environnement sonore','noisecapture_area_anqes2017');")
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureParse.getResource("track_fec26b2a-3345-4e58-9055-1a6567b055ad.zip").file))
        // Read db; check content
        assertEquals(1, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_track").get("cpt"))
        assertNull(sql.firstRow("SELECT pk_party FROM  noisecapture_track").pk_party)
    }

    // Test parse with coordinates without Z values (wifi)
    void testParse2d() {
        Sql sql = new Sql(connection)
        sql.execute("INSERT INTO NOISECAPTURE_PARTY(the_geom, title, tag, description, layer_name) VALUES ('POLYGON((2.38717 48.8944,2.38717 48.8964,2.38917 48.8964,2.38917 48.8944,2.38717 48.8944))','ANQES 2017 event','ANQES2017'," +
                "'8es Assises nationales de la qualité de l''environnement sonore','noisecapture_area_anqes2017');")
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureParse.getResource("track_aaa26b2a-3345-4e58-9055-1a6567b055ad.zip").file))
        // Read db; check content
        assertEquals(1, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_track").get("cpt"))
        assertNull(sql.firstRow("SELECT pk_party FROM  noisecapture_track").pk_party)
    }




    // Test parse with a party tag that exist in database
    void testParseNoiseParty() {
        Sql sql = new Sql(connection)
        sql.execute("INSERT INTO NOISECAPTURE_PARTY(the_geom, title, tag, description, layer_name) VALUES ('POLYGON((-1.64616016766905 47.1531855961037,-1.64616016766905 47.1553688595939,-1.64392677851205 47.1553688595939,-1.64392677851205 47.1531855961037,-1.64616016766905 47.1531855961037))','OGRS 2018 event','OGRS_2018'," +
                "'Open Geospatial consortium 2018','noisecapture_area_ogrs2018');")
        assertEquals(1, new nc_parse().processFile(connection,
                new File(TestNoiseCaptureParse.getResource("track_fec26b2a-3345-4e58-9055-1a6567b055ad.zip").file)))
        // Read db; check content
        assertEquals(1, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_track").get("cpt"))
        assertEquals(1, sql.firstRow("SELECT pk_party FROM  noisecapture_track").pk_party)
    }

    void testOutdatedNoiseParty() {
        Sql sql = new Sql(connection)
        sql.execute("INSERT INTO NOISECAPTURE_PARTY(the_geom, title, tag, description, layer_name, start_time, end_time, filter_time) VALUES ('POLYGON((-1.64616016766905 47.1531855961037,-1.64616016766905 47.1553688595939,-1.64392677851205 47.1553688595939,-1.64392677851205 47.1531855961037,-1.64616016766905 47.1531855961037))','OGRS 2018 event','OGRS_2018'," +
                "'Open Geospatial consortium 2018','noisecapture_area_ogrs2018',:begintime::timestamptz,:endtime::timestamptz,true);", [begintime: "2017-09-13T01:00:00Z", endtime:"2017-09-13T23:59:59Z"])
        assertEquals(null, new nc_parse().processFile(connection,
                new File(TestNoiseCaptureParse.getResource("track_fec26b2a-3345-4e58-9055-1a6567b055ad.zip").file)))
        // Read db; check content
        assertEquals(1, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_track").get("cpt"))
        assertEquals(null, sql.firstRow("SELECT pk_party FROM  noisecapture_track").pk_party)
    }

    void testTimeFilteredNoiseParty() {
        Sql sql = new Sql(connection)
        sql.execute("INSERT INTO NOISECAPTURE_PARTY(the_geom, title, tag, description, layer_name, start_time, end_time, filter_time) VALUES ('POLYGON((-1.64616016766905 47.1531855961037,-1.64616016766905 47.1553688595939,-1.64392677851205 47.1553688595939,-1.64392677851205 47.1531855961037,-1.64616016766905 47.1531855961037))','OGRS 2018 event','OGRS_2018'," +
                "'Open Geospatial consortium 2018','noisecapture_area_ogrs2018',:begintime::timestamptz,:endtime::timestamptz,true);", [begintime: "2017-09-14T01:00:00Z", endtime:"2017-09-14T23:59:59Z"])
        assertEquals(1, new nc_parse().processFile(connection,
                new File(TestNoiseCaptureParse.getResource("track_fec26b2a-3345-4e58-9055-1a6567b055ad.zip").file)))
        // Read db; check content
        assertEquals(1, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_track").get("cpt"))
        assertEquals(1, sql.firstRow("SELECT pk_party FROM  noisecapture_track").pk_party)
    }

    void testOutOfBoundsNoiseParty() {
        Sql sql = new Sql(connection)
        sql.execute("INSERT INTO NOISECAPTURE_PARTY(the_geom, title, tag, description, layer_name, filter_area) VALUES ('POLYGON((2.38717 48.8944,2.38717 48.8964,2.38917 48.8964,2.38917 48.8944,2.38717 48.8944))','OGRS 2018 event','OGRS_2018'," +
                "'Open Geospatial consortium 2018','noisecapture_area_ogrs2018', true);")
        assertEquals(null, new nc_parse().processFile(connection,
                new File(TestNoiseCaptureParse.getResource("track_fec26b2a-3345-4e58-9055-1a6567b055ad.zip").file)))
        // Read db; check content
        assertEquals(1, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_track").get("cpt"))
        assertEquals(null, sql.firstRow("SELECT pk_party FROM  noisecapture_track").pk_party)
    }

    void testInBoundsNoiseParty() {
        Sql sql = new Sql(connection)
        sql.execute("INSERT INTO NOISECAPTURE_PARTY(the_geom, title, tag, description, layer_name, filter_area) VALUES ('POLYGON((-1.64616016766905 47.1531855961037,-1.64616016766905 47.1553688595939,-1.64392677851205 47.1553688595939,-1.64392677851205 47.1531855961037,-1.64616016766905 47.1531855961037))','OGRS 2018 event','OGRS_2018'," +
                "'Open Geospatial consortium 2018','noisecapture_area_ogrs2018', true);")
        assertEquals(1, new nc_parse().processFile(connection,
                new File(TestNoiseCaptureParse.getResource("track_fec26b2a-3345-4e58-9055-1a6567b055ad.zip").file)))
        // Read db; check content
        assertEquals(1, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_track").get("cpt"))
        assertEquals(1, sql.firstRow("SELECT pk_party FROM  noisecapture_track").pk_party)
    }

    void testNoisePartyWithoutDateFilter() {
        Sql sql = new Sql(connection)
        sql.execute("INSERT INTO NOISECAPTURE_PARTY(the_geom, title, tag, description, layer_name,start_time,end_time,filter_area,filter_time) VALUES" +
                " ('POLYGON((-9.30180644989014 42.4626388549805,-9.30180644989014 43.7915267944336," +
                "-7.66208219528198 43.7915267944336,-7.66208219528198 42.4626388549805," +
                "-9.30180644989014 42.4626388549805))','Universidade da Coruña','UDC','This map belongs to the EDUC','UDC','2018-04-17 03:00:00+02','2020-04-18 01:59:59+02',1,1);")
        def idtrack = new nc_parse().processFile(connection,
                new File(TestNoiseCaptureParse.getResource("track_f64ffe12-096a-4000-8282-9c7429795997.zip").file))
        // Read db; check content
        def result = sql.firstRow("SELECT tag FROM  noisecapture_track nt, noisecapture_party np where nt.pk_party = np.pk_party and nt.pk_track = :pktrack",[pktrack:idtrack])
        assertEquals("UDC", result.tag)
    }

    void testFileCorrupt() {
        Statement st = connection.createStatement()
        // Load timezone file
        st.execute("CALL FILE_TABLE('"+TestNoiseCaptureProcess.getResource("tz_world.shp").file+"', 'TZ_WORLD');")
        st.execute("CREATE SPATIAL INDEX ON TZ_WORLD(THE_GEOM)")
        // ut_deps has been derived from https://www.data.gouv.fr/fr/datasets/contours-des-departements-francais-issus-d-openstreetmap/ (c) osm
        // See ut_deps.txt for more details
        st.execute("CALL GEOJSONREAD('"+TestNoiseCaptureProcess.getResource("ut_deps.geojson").file+"', 'GADM28');")

        assertEquals(1, new nc_parse().processFiles(connection, [new File(TestNoiseCaptureParse.getResource("track_00a20ba7-35f7-4ac4-923b-9d43dd5348b8.zip").file)] as File[],
                0, false))
    }

    void testWrongLatLong() {
        Statement st = connection.createStatement()
        Sql sql = new Sql(connection)
        // Load timezone file
        st.execute("CALL FILE_TABLE('"+TestNoiseCaptureProcess.getResource("tz_world.shp").file+"', 'TZ_WORLD');")
        st.execute("CREATE SPATIAL INDEX ON TZ_WORLD(THE_GEOM)")
        // ut_deps has been derived from https://www.data.gouv.fr/fr/datasets/contours-des-departements-francais-issus-d-openstreetmap/ (c) osm
        // See ut_deps.txt for more details
        st.execute("CALL GEOJSONREAD('"+TestNoiseCaptureProcess.getResource("ut_deps.geojson").file+"', 'GADM28');")

        assertEquals(1, new nc_parse().processFiles(connection, [new File(TestNoiseCaptureParse.getResource("track_1c9d12ee-5a98-4176-bdc2-38afd1075aad.zip").file)] as File[],
                0, false))

        def pkTrack =  sql.firstRow("SELECT pk_track FROM  noisecapture_track where track_uuid = '1c9d12ee-5a98-4176-bdc2-38afd1075aad'").get("pk_track")
        assertEquals("GEOMETRYCOLLECTION EMPTY", sql.firstRow("SELECT the_geom FROM  noisecapture_point where pk_track = " + pkTrack).get("the_geom").toString())
    }

    void testParseCalibrationMethods() {
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureParse.getResource("track_88a20ba7-22f7-4ac4-923b-9d43dd5348b8.zip").file))
        // Read db; check content
        Sql sql = new Sql(connection)
        assertEquals(1, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_track").get("cpt"))
        assertEquals("Traffic", sql.firstRow("SELECT calibration_method::varchar calibration_method FROM  noisecapture_track").calibration_method)
    }
}
