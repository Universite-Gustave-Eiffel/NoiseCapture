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

import groovy.json.JsonSlurper
import groovy.sql.Sql
import org.h2.Driver
import org.h2gis.functions.factory.H2GISDBFactory
import org.h2gis.utilities.SFSUtilities
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Rule
import org.junit.rules.TemporaryFolder

import java.sql.Connection
import java.sql.Statement
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream

/**
 * Test parsing of zip file using H2GIS database
 */
class TestNoiseCaptureGetStats extends JdbcTestCase {

    @Before
    void setUp() {
        super.setUp()
        Statement st = connection.createStatement()
        // Init schema
        st.execute(new File(TestNoiseCaptureGetStats.class.getResource("inith2.sql").getFile()).text)
        // Load timezone file
        st.execute("CALL FILE_TABLE('"+TestNoiseCaptureProcess.getResource("tz_world.shp").file+"', 'TZ_WORLD');")
        st.execute("CREATE SPATIAL INDEX ON TZ_WORLD(THE_GEOM)")
        // ut_deps has been derived from https://www.data.gouv.fr/fr/datasets/contours-des-departements-francais-issus-d-openstreetmap/ (c) osm
        // See ut_deps.txt for more details
        st.execute("CALL GEOJSONREAD('"+TestNoiseCaptureProcess.getResource("ut_deps.geojson").file+"', 'GADM28');")
    }

    @Ignore
    static
    def addTestRecord(sql, time, location, levels) {
        def idUser = sql.executeInsert("INSERT INTO noisecapture_user(user_uuid, date_creation) VALUES (:uuid, cast(cast(:userdate as timestamptz) as date))", [uuid: UUID.randomUUID(), userdate : time])[0][0]

        Map record = [track_uuid         : UUID.randomUUID().toString(),
                      pk_user            : idUser,
                      version_number     : 14,
                      record_utc         : time,
                      pleasantness       : 50,
                      device_product     : "NC",
                      device_model       : "NC",
                      device_manufacturer: "NC",
                      noise_level        : 10 * Math.log10(levels.sum({Math.pow(10.0, it / 10.0)})),
                      time_length        : levels.size(),
                      gain_calibration   : 0]

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

    void testTracksExport() {
        Sql.LOG.level = java.util.logging.Level.SEVERE
        Sql sql = new Sql(connection)
        addTestRecord(sql, new nc_parse().epochToRFCTime(System.currentTimeMillis()), "POINT(2.4710 44.2772)", [70, 75, 72])
        addTestRecord(sql, new nc_parse().epochToRFCTime(System.currentTimeMillis()- (1000 * 3600 * 24 * 8)) , "POINT(13.1853 43.0961)", [60, 61, 58])
        addTestRecord(sql, new nc_parse().epochToRFCTime(System.currentTimeMillis()- (1000 * 3600 * 24 * 16)) , "POINT(9.0038 42.2513)", [65, 68, 64])
        // Compute stats
        def stats = new nc_get_stats().getStatistics(connection)
        assertEquals(1, stats["week_new_contributors"])
        assertEquals(1, stats["week_new_tracks_count"])
        assertEquals("France", stats["countries"].names[0])
        assertEquals(2, stats["countries"].total_tracks[0])
        assertEquals("Italy", stats["countries"].names[1])
        assertEquals(1, stats["countries"].total_tracks[1])

        assertEquals("France", stats["week_tracks"]["datasets"][0].label)
        assertEquals(6, stats["week_tracks"]["datasets"][0].data.sum())

        assertEquals("Italy",stats["week_tracks"]["datasets"][1].label)
        assertEquals(3, stats["week_tracks"]["datasets"][1].data.sum())

        // Check contributors

        assertTrue("contributors_7days" in stats.keySet())
        // Only one contributor this week
        assertEquals(1, stats["contributors_7days"].size())
        assertEquals(3, stats["contributors_7days"][0]["total_length"])
    }

}
