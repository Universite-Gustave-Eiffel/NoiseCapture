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
 * Copyright (C) IFSTTAR - LAE and Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
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

import geoserver.GeoServer
import geoserver.catalog.Store
import groovy.sql.Sql
import org.apache.commons.io.output.WriterOutputStream
import org.geotools.jdbc.JDBCDataStore
import org.springframework.security.core.context.SecurityContextHolder
import groovy.json.*

import java.sql.Connection
import java.sql.SQLException
import java.util.zip.GZIPOutputStream

title = 'nc_dump_records'
description = 'Dump database data to a folder'

inputs = [
        exportTracks: [name: 'exportTracks', title: 'Export raw track boolean',
                         type: Boolean.class],
        exportMeasures: [name: 'exportMeasures', title: 'Export raw measures boolean',
                 type: Boolean.class],
        exportAreas: [name: 'exportAreas', title: 'Export post-processed values',
                 type: Boolean.class]
         ]

outputs = [
        result: [name: 'result', title: 'Number of files creates', type: Integer.class]
]

def getDump(Connection connection, File outPath, boolean exportMeasures, boolean exportAreas) {
    def createdFiles = 0

    // Process export of raw measures
    try {
        def sql = new Sql(connection)
            // Create a table that contains track envelopes
            sql.execute("DROP TABLE IF EXISTS NOISECAPTURE_DUMP_TRACK_ENVELOPE")
            sql.execute("CREATE TABLE NOISECAPTURE_DUMP_TRACK_ENVELOPE AS SELECT pk_track, " +
                    "ST_ENVELOPE(ST_COLLECT(ST_POINT(ST_X(the_geom),ST_Y(the_geom)))) the_geom" +
                    " from noisecapture_point where not ST_ISEMPTY(the_geom)  group by pk_track")
            // Create a table that link

            // Loop through region level
            // Region table has been downloaded from http://www.gadm.org/version2
            // Export track file
            def lastFileParams = []
            Writer jsonWriter = null
            sql.eachRow("select name_0, name_1, name_2, track_uuid, pleasantness,gain_calibration," +
                    "ST_YMIN(te.the_geom) MINLATITUDE,ST_XMIN(te.THE_GEOM) MINLONGITUDE," +
                    "ST_YMAX(te.the_geom) MAXLATITUDE,ST_XMAX(te.THE_GEOM) MAXLONGITUDE," +
                    " record_utc, noise_level, time_length " +
                    "from noisecapture_dump_track_envelope te, gadm28 ga, noisecapture_track nt  " +
                    "where te.the_geom && ga.the_geom and st_intersects(te.the_geom, ga.the_geom)" +
                    " and te.pk_track = nt.pk_track order by name_0, name_1, name_2;") {
                track_row ->
                    def thisFileParams = [track_row.name_2, track_row.name_1, track_row.name_0]
                    if(thisFileParams != lastFileParams) {
                        if(jsonWriter != null) {
                            jsonWriter.close()
                        }
                        lastFileParams = thisFileParams
                        String fileName = track_row.name_0 + "_" + track_row.name_1 + "_" + track_row.name_2 + ".tracks.geojson.gz"
                        jsonWriter = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(new File(outPath, fileName))), "UTF-8")
                        jsonWriter << "{\n  \"type\": \"FeatureCollection\",\n  \"features\": [\n    {"
                    }

                    def bbox = [[track_row.MINLONGITUDE,track_row.MINLATITUDE],
                                [track_row.MAXLONGITUDE,track_row.MINLATITUDE],
                                [track_row.MAXLONGITUDE,track_row.MAXLATITUDE],
                                [track_row.MINLONGITUDE,track_row.MAXLATITUDE],
                                [track_row.MINLONGITUDE,track_row.MINLATITUDE]]
                    def track = [type:"Feature", geometry : [type:"Polygon", coordinates:bbox], properties : [pleasantness: track_row.pleasantness,
                                                                                                              gain_calibration: track_row.gain_calibration,
                                                                                                              record_utc:track_row.record_utc,
                                                                                                              noise_level:track_row.noise_level,
                                                                                                              time_length:track_row.time_length]]
                    jsonWriter << JsonOutput.toJson(track)
            }
            if(jsonWriter != null) {
            }

            // Export measures file

            // Export hexagons file

    } catch (SQLException ex) {
        throw ex
    }
    return createdFiles
}

def Connection openPostgreSQLDataStoreConnection() {
    Store store = new GeoServer().catalog.getStore("postgis")
    JDBCDataStore jdbcDataStore = (JDBCDataStore)store.getDataStoreInfo().getDataStore(null)
    return jdbcDataStore.getDataSource().getConnection()
}

def run(input) {
    // Sensible WPS process, add a layer of security by checking for authenticated user
    def user = SecurityContextHolder.getContext().getAuthentication();
    if(!user.isAuthenticated()) {
        throw new IllegalStateException("This WPS process require authentication")
    }
    // Open PostgreSQL connection
    Connection connection = openPostgreSQLDataStoreConnection()
    try {
        return [result : doDump(connection, input["exportMeasures"],input["exportAreas"])]
    } finally {
        connection.close()
    }
}
