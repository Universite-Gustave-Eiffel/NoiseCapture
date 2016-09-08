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
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Test parsing of zip file using H2GIS database
 */
class TestNoiseCaptureProcess extends GroovyTestCase {
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
        st.execute(new File(TestNoiseCaptureProcess.class.getResource("inith2.sql").getFile()).text)
    }

    @After
    void tearDown() {
        connection.close();
        folder.delete()
    }

    void testProcess1() {
        Sql sql = new Sql(connection)
        // Load timezone file
        sql.execute("CALL FILE_TABLE('"+TestNoiseCaptureProcess.getResource("tz_world.shp").file+"', 'TZ_WORLD');")
        sql.execute("CREATE SPATIAL INDEX ON TZ_WORLD(THE_GEOM)")
        // Insert measure data
        // insert record
        def t = LocalDateTime.ofInstant(Instant.ofEpochMilli(1473255793226), ZoneId.of("UTC"))
        Map record = [track_uuid         : "1b03b7cb-ca16-4965-881c-f3591cd07342",
                      pk_user            : 1,
                      version_number     : 14,
                      record_utc         : new Timestamp(1469630873762),
                      pleasantness       : 50,
                      device_product     : "NC",
                      device_model       : "NC",
                      device_manufacturer: "NC",
                      noise_level        : 75,
                      time_length        : 3,
                      gain_calibration   : 0]
        sql.execute("INSERT INTO noisecapture_user(user_uuid, date_creation) VALUES ('6cb5a12a-74fb-11e6-8b77-86f30ca893d3', current_date)")
        def recordId = sql.executeInsert("INSERT INTO noisecapture_track(track_uuid, pk_user, version_number, record_utc," +
                " pleasantness, device_product, device_model, device_manufacturer, noise_level, time_length, gain_calibration) VALUES (" +
                ":track_uuid, :pk_user, :version_number, :record_utc, :pleasantness, :device_product, :device_model," +
                " :device_manufacturer, :noise_level, :time_length, :gain_calibration)", record)[0][0] as Integer
        // Add 3 points
        def fields = [the_geom     : "POINT(23.73847 37.97503)",
                      pk_track     : recordId,
                      noise_level  : 75,
                      speed        : 0,
                      accuracy     : 4,
                      orientation  : 120,
                      time_date    : new Timestamp(1469620073762),
                      time_location: new Timestamp(1469620073762)]
        def ptId = sql.executeInsert("INSERT INTO noisecapture_point(the_geom, pk_track, noise_level, speed," +
                " accuracy, orientation, time_date, time_location) VALUES (ST_GEOMFROMTEXT(:the_geom, 4326)," +
                " :pk_track, :noise_level, :speed, :accuracy, :orientation, :time_date, :time_location)", fields)[0][0] as Integer
        fields.the_geom =  "POINT(23.73836 37.97519)"
        fields.time_date = new Timestamp(1469630874762)
        ptId = sql.executeInsert("INSERT INTO noisecapture_point(the_geom, pk_track, noise_level, speed," +
                " accuracy, orientation, time_date, time_location) VALUES (ST_GEOMFROMTEXT(:the_geom, 4326)," +
                " :pk_track, :noise_level, :speed, :accuracy, :orientation, :time_date, :time_location)", fields)[0][0] as Integer
        fields.the_geom =  "POINT(23.73826 37.97509)"
        fields.time_date = new Timestamp(1469630875762)
        ptId = sql.executeInsert("INSERT INTO noisecapture_point(the_geom, pk_track, noise_level, speed," +
                " accuracy, orientation, time_date, time_location) VALUES (ST_GEOMFROMTEXT(:the_geom, 4326)," +
                " :pk_track, :noise_level, :speed, :accuracy, :orientation, :time_date, :time_location)", fields)[0][0] as Integer
        // Push track into process queue
        Map processQueue = [pk_track: recordId]
        sql.executeInsert("INSERT INTO NOISECAPTURE_PROCESS_QUEUE VALUES (:pk_track)", processQueue)
        def processed = new nc_process().process(connection, 10)
        assertEquals(6, processed)
        // Read db; check content
        assertEquals(6, sql.firstRow("SELECT COUNT(*) cpt FROM  noisecapture_area").get("cpt"))

    }

}
