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
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.sql.Sql
import org.geotools.jdbc.JDBCDataStore
import org.springframework.security.core.context.SecurityContextHolder

import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

title = 'nc_dump_records'
description = 'Dump database data to a folder'

inputs = [
        exportTracks  : [name: 'exportTracks', title: 'Export raw track boolean',
                         type: Boolean.class],
        exportMeasures: [name: 'exportMeasures', title: 'Export raw measures boolean',
                         type: Boolean.class],
        exportAreas   : [name: 'exportAreas', title: 'Export post-processed values',
                         type: Boolean.class]
]

outputs = [
        result: [name: 'result', title: 'Created files', type: String.class]
]

/**
 * Convert EPOCH time to ISO 8601
 * @param epochMillisec
 * @return
 */
static def epochToRFCTime(epochMillisec, zone) {
    return Instant.ofEpochMilli(epochMillisec).atZone(ZoneId.of(zone)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}



static
def getDump(Connection connection, File outPath, boolean exportTracks, boolean exportMeasures, boolean exportAreas) {
    // gzip then base64 the content (http://www.txtwizard.net/compression)
    final String README_CONTENT = "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"


    def createdFiles = new ArrayList<String>()

    // Process export of raw measures
    try {
        connection.setAutoCommit(false);
        def sql = new Sql(connection)
        // Change result set type, this way the PostGIS driver use the db cursor with minimal memory usage
        sql.setResultSetType(ResultSet.TYPE_FORWARD_ONLY)
        sql.setResultSetConcurrency(ResultSet.CONCUR_READ_ONLY)
        sql.withStatement{ stmt -> stmt.fetchSize = 50 }
        // This variable host the opened connection to all files
        def countryZipOutputStream = [:]
        // Create a table that contains track envelopes
        if(exportTracks || exportMeasures) {
            sql.execute("TRUNCATE TABLE NOISECAPTURE_DUMP_TRACK_ENVELOPE")
            sql.execute("INSERT INTO NOISECAPTURE_DUMP_TRACK_ENVELOPE SELECT pk_track, " +
                    "ST_SETSRID(ST_EXTENT(ST_MAKEPOINT(ST_X(the_geom),ST_Y(the_geom))), 4326) the_geom,  COUNT(np.pk_point) measure_count" +
                    " from noisecapture_point np where not ST_ISEMPTY(the_geom)  group by pk_track")
        }
        // Loop through region level
        // Region table has been downloaded from http://www.gadm.org/version2
        def lastFileParams = []
        ZipOutputStream lastFileZipOutputStream = null
        Writer lastFileJsonWriter = null

        try {

            if (exportTracks) {
                // Export track file
                sql.eachRow("select name_0, name_1, name_2, tzid, nt.pk_track, track_uuid, pleasantness,gain_calibration,ST_AsGeoJson(te.the_geom) the_geom, record_utc, noise_level, time_length, (select string_agg(tag_name, ',') from noisecapture_tag ntag, noisecapture_track_tag nttag where ntag.pk_tag = nttag.pk_tag and nttag.pk_track = nt.pk_track) tags from noisecapture_dump_track_envelope te, gadm28 ga, noisecapture_track nt,tz_world tz  where te.the_geom && ga.the_geom and st_intersects(te.the_geom, ga.the_geom) and ga.the_geom && tz.the_geom and st_intersects(ST_PointOnSurface(ga.the_geom),tz.the_geom) and te.pk_track = nt.pk_track order by name_0, name_1, name_2, record_utc;") {
                    track_row ->
                        def thisFileParams = [track_row.name_2, track_row.name_1, track_row.name_0]
                        if (thisFileParams != lastFileParams) {
                            if (lastFileJsonWriter != null) {
                                lastFileJsonWriter << "]\n}\n"
                                lastFileJsonWriter.flush()
                                lastFileZipOutputStream.closeEntry()
                            }
                            lastFileParams = thisFileParams
                            String fileName = track_row.name_0 + "_" + track_row.name_1 + "_" + track_row.name_2 + ".tracks.geojson"
                            // Fetch ZipOutputStream for this new country
                            if (countryZipOutputStream.containsKey(track_row.name_0)) {
                                lastFileZipOutputStream = (ZipOutputStream) countryZipOutputStream.get(track_row.name_0)
                            } else {
                                // Create new file or overwrite it
                                def zipFileName = new File(outPath, track_row.name_0 + ".zip")
                                lastFileZipOutputStream = new ZipOutputStream(new FileOutputStream(zipFileName));
                                countryZipOutputStream.put(track_row.name_0, lastFileZipOutputStream)
                                createdFiles.add(zipFileName.getAbsolutePath())
                            }
                            lastFileZipOutputStream.putNextEntry(new ZipEntry(fileName))
                            lastFileJsonWriter = new OutputStreamWriter(lastFileZipOutputStream, "UTF-8")
                            lastFileJsonWriter << "{\n  \"type\": \"FeatureCollection\",\n  \"features\": [\n"
                        } else {
                            lastFileJsonWriter << ",\n"
                        }
                        def the_geom = new JsonSlurper().parseText(track_row.the_geom)
                        def time_ISO_8601 = epochToRFCTime(((Timestamp) track_row.record_utc).time, track_row.tzid)
                        def track = [type: "Feature", geometry: the_geom, properties: [pleasantness    : track_row.pleasantness == null ? null : (Double.isNaN(track_row.pleasantness) ? null : track_row.pleasantness),
                                                                                       pk_track        : track_row.pk_track,
                                                                                       track_uuid      : track_row.track_uuid,
                                                                                       gain_calibration: track_row.gain_calibration,
                                                                                       time_ISO8601    : time_ISO_8601,
                                                                                       time_epoch      : ((Timestamp) track_row.record_utc).time,
                                                                                       noise_level     : track_row.noise_level,
                                                                                       time_length     : track_row.time_length,
                                                                                       tags            : track_row.tags == null ? null : track_row.tags.tokenize(',')]]
                        lastFileJsonWriter << JsonOutput.toJson(track)
                }
                if (lastFileJsonWriter != null) {
                    lastFileJsonWriter << "]\n}\n"
                    lastFileJsonWriter.flush()
                    lastFileZipOutputStream.closeEntry()
                }
            }
            lastFileParams = []
            lastFileZipOutputStream = null
            lastFileJsonWriter = null

            // Export measures file
            if (exportMeasures) {
                sql.eachRow("select name_0, name_1, name_2, tzid, np.pk_track, ST_AsGeoJson(np.the_geom) the_geom, np.noise_level, np.speed, np.accuracy, np.orientation, np.time_date, np.time_location  from noisecapture_dump_track_envelope te, gadm28 ga, noisecapture_point np,tz_world tz  where te.the_geom && ga.the_geom and st_intersects(te.the_geom, ga.the_geom) and ga.the_geom && tz.the_geom and st_intersects(ST_PointOnSurface(ga.the_geom),tz.the_geom) and te.pk_track = np.pk_track and not ST_ISEMPTY(np.the_geom) order by name_0, name_1, name_2, np.time_date") {
                    track_row ->
                        def thisFileParams = [track_row.name_2, track_row.name_1, track_row.name_0]
                        if (thisFileParams != lastFileParams) {
                            if (lastFileJsonWriter != null) {
                                lastFileJsonWriter << "]\n}\n"
                                lastFileJsonWriter.flush()
                                lastFileZipOutputStream.closeEntry()
                            }
                            lastFileParams = thisFileParams
                            String fileName = track_row.name_0 + "_" + track_row.name_1 + "_" + track_row.name_2 + ".points.geojson"
                            // Fetch ZipOutputStream for this new country
                            if (countryZipOutputStream.containsKey(track_row.name_0)) {
                                lastFileZipOutputStream = (ZipOutputStream) countryZipOutputStream.get(track_row.name_0)
                            } else {
                                // Create new file or overwrite it
                                def zipFileName = new File(outPath, track_row.name_0 + ".zip")
                                lastFileZipOutputStream = new ZipOutputStream(new FileOutputStream(zipFileName));
                                countryZipOutputStream.put(track_row.name_0, lastFileZipOutputStream)
                                createdFiles.add(zipFileName.getAbsolutePath())
                            }
                            lastFileZipOutputStream.putNextEntry(new ZipEntry(fileName))
                            lastFileJsonWriter = new OutputStreamWriter(lastFileZipOutputStream, "UTF-8")
                            lastFileJsonWriter << "{\n  \"type\": \"FeatureCollection\",\n  \"features\": [\n"
                        } else {
                            lastFileJsonWriter << ",\n"
                        }
                        def the_geom = new JsonSlurper().parseText(track_row.the_geom)
                        def time_ISO_8601 = epochToRFCTime(((Timestamp) track_row.time_date).time, track_row.tzid)
                        def time_gps_ISO_8601 = epochToRFCTime(((Timestamp) track_row.time_location).time, track_row.tzid)
                        def track = [type: "Feature", geometry: the_geom, properties: [pk_track        : track_row.pk_track,
                                                                                                               time_ISO8601    : time_ISO_8601,
                                                                                                               time_epoch      : ((Timestamp) track_row.time_date).time,
                                                                                                               time_gps_ISO8601: time_gps_ISO_8601,
                                                                                                               time_gps_epoch  : ((Timestamp) track_row.time_location).time,
                                                                                                               noise_level     : track_row.noise_level,
                                                                                                               speed           : track_row.speed,
                                                                                                               orientation     : track_row.orientation,
                                                                                                               accuracy        : track_row.accuracy
                        ]]
                        lastFileJsonWriter << JsonOutput.toJson(track)
                }
                if (lastFileJsonWriter != null) {
                    lastFileJsonWriter << "]\n}\n"
                    lastFileJsonWriter.flush()
                    lastFileZipOutputStream.closeEntry()
                }
            }
            // Export hexagons file
            lastFileParams = []
            lastFileZipOutputStream = null
            lastFileJsonWriter = null
            if (exportAreas) {
                // Export track file
                sql.eachRow("SELECT name_0, name_1, name_2,ST_AsGeoJson(na.the_geom) the_geom, cell_q, cell_r, tzid, la50, na.laeq, lden , mean_pleasantness, measure_count, first_measure, last_measure, string_agg(to_char(nap.laeq, 'FM999'), '_') leq_profile, string_agg(to_char(hour, '999'), '_') hour_profile FROM noisecapture_area na, gadm28 ga, (select pk_area, nap.laeq, hour from noisecapture_area_profile nap  order by hour) nap  where ST_Centroid(na.the_geom) && ga.the_geom and st_contains(ga.the_geom, ST_centroid(na.the_geom)) and nap.pk_area = na.pk_area group by name_0, name_1, name_2,na.the_geom, cell_q, cell_r, tzid, la50, na.laeq, lden , mean_pleasantness, measure_count, first_measure, last_measure order by name_0, name_1, name_2, cell_q, cell_r;") {
                    track_row ->
                        def thisFileParams = [track_row.name_2, track_row.name_1, track_row.name_0]
                        if (thisFileParams != lastFileParams) {
                            if (lastFileJsonWriter != null) {
                                lastFileJsonWriter << "]\n}\n"
                                lastFileJsonWriter.flush()
                                lastFileZipOutputStream.closeEntry()
                            }
                            lastFileParams = thisFileParams
                            String fileName = track_row.name_0 + "_" + track_row.name_1 + "_" + track_row.name_2 + ".areas.geojson"
                            // Fetch ZipOutputStream for this new country
                            if (countryZipOutputStream.containsKey(track_row.name_0)) {
                                lastFileZipOutputStream = (ZipOutputStream) countryZipOutputStream.get(track_row.name_0)
                            } else {
                                // Create new file or overwrite it
                                def zipFileName = new File(outPath, track_row.name_0 + ".zip")
                                lastFileZipOutputStream = new ZipOutputStream(new FileOutputStream(zipFileName));
                                countryZipOutputStream.put(track_row.name_0, lastFileZipOutputStream)
                                createdFiles.add(zipFileName.getAbsolutePath())
                            }
                            lastFileZipOutputStream.putNextEntry(new ZipEntry(fileName))
                            lastFileJsonWriter = new OutputStreamWriter(lastFileZipOutputStream, "UTF-8")
                            lastFileJsonWriter << "{\n  \"type\": \"FeatureCollection\",\n  \"features\": [\n"
                        } else {
                            lastFileJsonWriter << ",\n"
                        }
                        def first_measure_ISO_8601 = epochToRFCTime(((Timestamp) track_row.first_measure).time, track_row.tzid)
                        def last_measure_ISO_8601 = epochToRFCTime(((Timestamp) track_row.last_measure).time, track_row.tzid)

                        def the_geom = new JsonSlurper().parseText(track_row.the_geom)

                        def leq_keys= track_row.hour_profile.tokenize('_')*.toInteger()
                        def leq_values= track_row.leq_profile.tokenize('_')*.toInteger()
                        def leq_array = new Object[72]
                        [leq_keys, leq_values].transpose().each {leq_array[it[0]] = it[1]}
                        def track = [type: "Feature", geometry: [type: "Polygon", coordinates: the_geom.coordinates], properties: [cell_q                : track_row.cell_q,
                                                                                                                                  cell_r                : track_row.cell_r,
                                                                                                                                  la50                  : track_row.la50,
                                                                                                                                  laeq                  : track_row.laeq,
                                                                                                                                  lden                  : track_row.lden,
                                                                                                                                  mean_pleasantness     : track_row.mean_pleasantness == null ? null : (Double.isNaN(track_row.mean_pleasantness) ? null : track_row.mean_pleasantness),
                                                                                                                                  measure_count         : track_row.measure_count,
                                                                                                                                  first_measure_ISO_8601: first_measure_ISO_8601,
                                                                                                                                  first_measure_epoch   : ((Timestamp) track_row.first_measure).time,
                                                                                                                                  last_measure_ISO_8601 : last_measure_ISO_8601,
                                                                                                                                  last_measure_epoch    : ((Timestamp) track_row.last_measure).time,
                                                                                                                                  leq_profile           : leq_array]]
                        lastFileJsonWriter << JsonOutput.toJson(track)
                }
                if (lastFileJsonWriter != null) {
                    lastFileJsonWriter << "]\n}\n"
                    lastFileJsonWriter.flush()
                    lastFileZipOutputStream.closeEntry()
                }
            }
        } finally {
            // Close opened files
            countryZipOutputStream.each {
                k, v ->
                    v.closeEntry()
                    // Write readme file
                    v.putNextEntry(new ZipEntry("README.html"))
                    new ByteArrayInputStream(Base64.decoder.decode(README_CONTENT)).withStream {
                        bais ->
                            new GZIPInputStream(bais).withStream {
                                html ->
                                    v <<  html;
                            }
                    }
                    v.closeEntry()
                    // Close zip file stream
                    v.close()
            }
        }
    } catch (SQLException ex) {
        throw ex
    }
    return createdFiles
}

static def Connection openPostgreSQLDataStoreConnection() {
    Store store = new GeoServer().catalog.getStore("postgis")
    JDBCDataStore jdbcDataStore = (JDBCDataStore) store.getDataStoreInfo().getDataStore(null)
    return jdbcDataStore.getDataSource().getConnection()
}

def run(input) {
    // Sensible WPS process, add a layer of security by checking for authenticated user
    def user = SecurityContextHolder.getContext().getAuthentication();
    if (!user.isAuthenticated()) {
        throw new IllegalStateException("This WPS process require authentication")
    }
    // Open PostgreSQL connection
    // Create dump folder
    File dumpDir = new File("data_dir/onomap_public_dump");
    if (!dumpDir.exists()) {
        dumpDir.mkdirs()
    }
    Connection connection = openPostgreSQLDataStoreConnection()
    try {
        return [result: JsonOutput.toJson(getDump(connection, dumpDir,input["exportTracks"], input["exportMeasures"], input["exportAreas"]))]
    } finally {
        connection.close()
    }
}
