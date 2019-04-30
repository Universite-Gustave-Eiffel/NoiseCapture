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
import org.junit.Ignore
import org.junit.Rule
import org.junit.rules.TemporaryFolder

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Test parsing of zip file using H2GIS database
 */
class TestNoiseCaptureProcess extends JdbcTestCase {

    @Before
    void setUp() {
        super.setUp()
        Statement st = connection.createStatement()
        // Init schema
        st.execute(new File(TestNoiseCaptureProcess.class.getResource("inith2.sql").getFile()).text)
    }

    @Ignore
    static
    def addTestRecord(sql, time, location, levels) {
        Map record = [track_uuid         : UUID.randomUUID().toString(),
                      pk_user            : 1,
                      version_number     : 14,
                      record_utc         : time,
                      pleasantness       : 50,
                      device_product     : "NC",
                      device_model       : "NC",
                      device_manufacturer: "NC",
                      noise_level        : 10 * Math.log10(levels.sum({Math.pow(10.0, it / 10.0)})),
                      time_length        : levels.size(),
                      gain_calibration   : 0]
        sql.execute("INSERT INTO noisecapture_user(user_uuid, date_creation) VALUES ('"+UUID.randomUUID()+"', current_date)")
        def recordId = sql.executeInsert("INSERT INTO noisecapture_track(track_uuid, pk_user, version_number, record_utc," +
                " pleasantness, device_product, device_model, device_manufacturer, noise_level, time_length, gain_calibration) VALUES (" +
                ":track_uuid, :pk_user, :version_number, :record_utc, :pleasantness, :device_product, :device_model," +
                " :device_manufacturer, :noise_level, :time_length, :gain_calibration)", record)[0][0] as Integer
        // Add 3 points
        for(float level : levels) {
            def fields = [the_geom     : location,
                          pk_track     : recordId,
                          noise_level  : level,
                          speed        : 0,
                          accuracy     : 4,
                          orientation  : 120,
                          time_date    : time,
                          time_location: time]
            def ptId = sql.executeInsert("INSERT INTO noisecapture_point(the_geom, pk_track, noise_level, speed," +
                    " accuracy, orientation, time_date, time_location) VALUES (ST_GEOMFROMTEXT(:the_geom, 4326)," +
                    " :pk_track, :noise_level, :speed, :accuracy, :orientation, :time_date, :time_location)", fields)[0][0] as Integer
        }
        // Push track into process queue
        Map processQueue = [pk_track: recordId]
        sql.executeInsert("INSERT INTO NOISECAPTURE_PROCESS_QUEUE VALUES (:pk_track)", processQueue)
    }

    void testProcess1() {
        Sql.LOG.level = java.util.logging.Level.SEVERE
        Sql sql = new Sql(connection)
        // Load timezone file
        sql.execute("CALL FILE_TABLE('"+TestNoiseCaptureProcess.getResource("tz_world.shp").file+"', 'TZ_WORLD');")
        sql.execute("CREATE SPATIAL INDEX ON TZ_WORLD(THE_GEOM)")
        new nc_feed_stats().processInput(connection,
                TestNoiseCaptureProcess.getResource("gevfit_of_stations.txt").toURI(), "stations")
        new nc_feed_stats().processInput(connection,
                TestNoiseCaptureProcess.getResource("delta_matrix_mu.txt").toURI(), "time_matrix_mu")
        new nc_feed_stats().processInput(connection,
                TestNoiseCaptureProcess.getResource("delta_matrix_sigma.txt").toURI(), "time_matrix_sigma")
        // Insert measure data
        // insert records
        addTestRecord(sql, "2016-09-07T13:43:13Z", "POINT(23.73847 37.97503)", [70, 75, 72])
        addTestRecord(sql, "2016-09-04T18:43:13Z", "POINT(23.73847 37.97503)", [60, 61, 58])
        addTestRecord(sql, "2016-09-03T16:43:13Z", "POINT(23.73847 37.97503)", [65, 68, 64])
        def processed = new nc_process().process(connection, 10)
        assertEquals(1, processed)
        // Read db; check content
        assertEquals(1, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_area").get("cpt"))
        assertEquals(72.82d, (Double)sql.firstRow("SELECT LAEQ FROM NOISECAPTURE_AREA_PROFILE WHERE HOUR = 16").get("LAEQ"),
                0.01d)
        assertEquals(72.0d, (Double)sql.firstRow("SELECT LA50 FROM NOISECAPTURE_AREA_PROFILE WHERE HOUR = 16").get("LA50"),
                0.01d)

        Set<Integer> levels = new TreeSet<>();
        // Check scaled hexagons
        int[] hexExponent = [3, 4, 5, 6, 7, 8, 9, 10, 11];
        sql.eachRow("SELECT * FROM NOISECAPTURE_AREA_CLUSTER") { row ->
            levels.add(row.CELL_LEVEL)
        }
        assertEquals(hexExponent.length, levels.size())

    }

    void testLA50() {
        Record noiseRecord = new Record()
        noiseRecord.addLeq(65)
        noiseRecord.addLeq(44)
        noiseRecord.addLeq(75)
        noiseRecord.addLeq(66)
        assertEquals(65.52, noiseRecord.getLA50(),0.1)
        noiseRecord.addLeq(70)
        assertEquals(66, noiseRecord.getLA50())
    }

    void testProcessParty() {
        Sql.LOG.level = java.util.logging.Level.SEVERE
        Sql sql = new Sql(connection)
        // Load timezone file
        sql.execute("CALL FILE_TABLE('"+TestNoiseCaptureProcess.getResource("tz_world.shp").file+"', 'TZ_WORLD');")
        sql.execute("CREATE SPATIAL INDEX ON TZ_WORLD(THE_GEOM)")
        new nc_feed_stats().processInput(connection,
                TestNoiseCaptureProcess.getResource("gevfit_of_stations.txt").toURI(), "stations")
        new nc_feed_stats().processInput(connection,
                TestNoiseCaptureProcess.getResource("delta_matrix_mu.txt").toURI(), "time_matrix_mu")
        new nc_feed_stats().processInput(connection,
                TestNoiseCaptureProcess.getResource("delta_matrix_sigma.txt").toURI(), "time_matrix_sigma")
        // Insert measure data
        // insert records
        // Create party before parsing party measurement
        sql.execute("INSERT INTO NOISECAPTURE_PARTY(the_geom, title, tag, description, layer_name) VALUES ('POLYGON((-1.64616016766905 47.1531855961037,-1.64616016766905 47.1553688595939,-1.64392677851205 47.1553688595939,-1.64392677851205 47.1531855961037,-1.64616016766905 47.1531855961037))','OGRS 2018 event','OGRS_2018'," +
                "'Open Geospatial consortium 2018','noisecapture_area_ogrs2018');")
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureParse.getResource("track_fec26b2a-3345-4e58-9055-1a6567b055ad.zip").file))

        def processed = new nc_process().process(connection, 50)
        // Two area processed (same place, but one for party area)
        assertEquals(2, processed)
        // Read db; check content
        assertEquals(1, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_area where pk_party = 1").get("cpt"))
        assertEquals(54.9d, (Double)sql.firstRow("SELECT AP.LAEQ FROM NOISECAPTURE_AREA_PROFILE AP,NOISECAPTURE_AREA A WHERE A.pk_area = ap.pk_area and HOUR = 10 and pk_party = 1").get("LAEQ"),
                0.1d)
        assertEquals(51.8d, (Double)sql.firstRow("SELECT AP.LA50 FROM NOISECAPTURE_AREA_PROFILE AP,NOISECAPTURE_AREA A WHERE A.pk_area = ap.pk_area and HOUR = 10 and pk_party = 1").get("LA50"),
                0.1d)
        assertEquals(54.9d, (Double)sql.firstRow("SELECT AP.LAEQ FROM NOISECAPTURE_AREA_PROFILE AP,NOISECAPTURE_AREA A WHERE A.pk_area = ap.pk_area and HOUR = 10 and pk_party is null").get("LAEQ"),
                0.1d)
        assertEquals(51.8d, (Double)sql.firstRow("SELECT AP.LA50 FROM NOISECAPTURE_AREA_PROFILE AP,NOISECAPTURE_AREA A WHERE A.pk_area = ap.pk_area and HOUR = 10 and pk_party is null").get("LA50"),
                0.1d)
    }


    void testProcessParty2() {
        Sql.LOG.level = java.util.logging.Level.SEVERE
        Sql sql = new Sql(connection)
        // Load timezone file
        sql.execute("CALL FILE_TABLE('"+TestNoiseCaptureProcess.getResource("tz_world.shp").file+"', 'TZ_WORLD');")
        sql.execute("CREATE SPATIAL INDEX ON TZ_WORLD(THE_GEOM)")
        // Insert measure data
        // insert records
        // Create party before parsing party measurement
        sql.execute("INSERT INTO noisecapture_party (the_geom, layer_name, title, tag, description) VALUES ('POLYGON((-2.34041 47.25688,-2.34041 47.26488,-2.33241 47.26488,-2.33241 47.25688,-2.34041 47.25688))'::geometry, 'noisecapture:noisecapture_area_dw2017', 'Digital Week 2017 Pornichet', 'SNDIGITALWEEK', '<p>La Ville de Pornichet s''associe à la Saint-Nazaire Digital Week le mercredi 20 septembre, et propose de nombreuses animations gratuites et ouvertes à tous dédiées au numérique à l''hippodrome.</p><p>Venez contribuer à la création d''une carte du bruit participative, en temps réel sur les territoires de la CARENE / CAP ATLANTIQUE grâce à l''utilisation d''une application smartphone : Noise Capture.</p>');")
        // Parse Gwendall measurement
        assertEquals(1, new nc_parse().processFile(connection,
                new File(TestNoiseCaptureParse.getResource("track_07efe9f7-bda1-4e49-8514-f3a2a1fc576d.zip").file)))

        def processed = new nc_process().process(connection, 50)
        assertEquals(16, processed);
        assertEquals(8, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_area where pk_party = 1").get("cpt"))
        assertEquals(8, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_area where pk_party is null").get("cpt"))
    }
}
