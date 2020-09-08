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

import geoserver.GeoServer
import geoserver.catalog.Store
import groovy.json.JsonSlurper
import groovy.sql.GroovyRowResult
import groovy.sql.Sql
import org.apache.commons.lang.StringEscapeUtils
import org.codehaus.groovy.runtime.StackTraceUtils
import org.geotools.jdbc.JDBCDataStore
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.security.InvalidParameterException
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

title = 'nc_parse'
description = 'Parse uploaded zip files'

inputs = [
        processFileLimit: [name: 'processFileLimit', title: 'Maximum number of file to process, 0 for unlimited',
                           type: Integer.class]
]

outputs = [
        result: [name: 'result', title: 'Processed files', type: Integer.class]
]

class ZipFileFilter implements FilenameFilter {
    @Override
    boolean accept(File dir, String name) {
        return name.toLowerCase().endsWith(".zip")
    }
}

/**
 * Convert EPOCH time to ISO 8601
 * @param epochMillisec
 * @return
 */
static def epochToRFCTime(epochMillisec) {
    return Instant.ofEpochMilli(epochMillisec).atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))
}

static Integer processFile(Connection connection, File zipFile,Map trackData = [:], boolean storeFrequencyLevels = true) throws Exception {
    def zipFileName = zipFile.getName()
    def recordUUID = zipFileName.substring("track_".length(), zipFileName.length() - ".zip".length())
    connection.setAutoCommit(false)
    def sql = new Sql(connection)
    // Fetch metadata
    Properties meta = null;
    zipFile.withInputStream { is ->
        ZipInputStream zipInputStream = new ZipInputStream(is)
        ZipEntry zipEntry
        while ((zipEntry = zipInputStream.getNextEntry()) != null) {
            if ("meta.properties".equals(zipEntry.getName())) {
                meta = new Properties();
                meta.load(zipInputStream)
            }
        }
    }
    if (meta == null) {
        throw new InvalidParameterException("No meta file")
    }
    // Check content
    // Check uuid
    if (!(meta.getProperty("uuid") ==~ '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}')) {
        // Wrong UUID
        throw new InvalidParameterException("Wrong UUID \"" + meta.getProperty("uuid") + "\"")
    }
    trackData.uuid = meta.getProperty("uuid")
    if (!(Integer.valueOf(meta.getProperty("version_number")) > 0)) {
        throw new InvalidParameterException("Wrong version \"" + meta.getProperty("version_number") + "\"")
    }
    if (!(Double.valueOf(meta.getProperty("leq_mean").replace(",", ".")) > 0)) {
        throw new InvalidParameterException("Wrong leq_mean \"" + meta.getProperty("leq_mean") + "\"")
    }
    if (!(meta.getProperty("pleasantness") == null || Integer.valueOf(meta.getProperty("pleasantness")) >= 0 &&
            Integer.valueOf(meta.getProperty("pleasantness")) <= 100)) {
        throw new InvalidParameterException("Wrong pleasantness \"" + meta.getProperty("pleasantness") + "\"")
    }
    Double gain = Double.valueOf(meta.getProperty("gain_calibration", "0").replace(",", "."))
    if (!(gain > -150 && gain < 150)) {
        throw new InvalidParameterException("Wrong gain \"" + gain + "\"")
    }
    // Maximum 15 minutes of time ahead of server time
    if(Long.valueOf(meta.getProperty("record_utc")) > System.currentTimeMillis() + (15*60*1000)) {
        throw new InvalidParameterException("Wrong time, superior than server time \"" + epochToRFCTime(Long.valueOf(meta.getProperty("record_utc"))) + "\"")
    }

    def noisecaptureVersion = Integer.valueOf(meta.getProperty("version_number"));

    // Fetch or insert user
    GroovyRowResult res = sql.firstRow("SELECT * FROM noisecapture_user WHERE user_uuid=:uuid",
            [uuid: meta.getProperty("uuid")])
    def idUser
    if (res == null) {
        // Create user
        idUser = sql.executeInsert("INSERT INTO noisecapture_user(user_uuid, date_creation, profile) VALUES (:uuid, current_date, :profile)",
                [uuid: meta.getProperty("uuid"), profile: meta.getProperty("user_profile", "")])[0][0]
    } else {
        idUser = res.get("pk_user")
        if(meta.hasProperty("user_profile") && res.profile as String != meta.getProperty("user_profile")) {
            // Update account information
            sql.executeUpdate("UPDATE noisecapture_user set profile = :profile where user_uuid = :uuid", [profile: meta.getProperty("user_profile"), uuid : meta.getProperty("uuid")])
        }
    }
    def noiseLevel = Double.valueOf(meta.getProperty("leq_mean").replace(",", "."))
    if (!(noiseLevel > -150 && noiseLevel < 150)) {
        throw new InvalidParameterException("Wrong noise level \"" + noiseLevel + "\"")
    }
    // Check if this measurement has not been already uploaded
    def oldTrackCount = sql.firstRow("SELECT count(*) cpt FROM  noisecapture_track where record_utc=:recordutc::timestamptz and pk_user=:userid",
            [recordutc: epochToRFCTime(Long.valueOf(meta.getProperty("record_utc"))), userid: idUser]).cpt as Integer
    if (oldTrackCount > 0) {
        def previousTrack = sql.firstRow("SELECT track_uuid FROM  noisecapture_track where record_utc=:recordutc::timestamptz and pk_user=:userid",
                [recordutc: epochToRFCTime(Long.valueOf(meta.getProperty("record_utc"))), userid: idUser])
        throw new InvalidParameterException("User tried to reupload " + previousTrack.get("track_uuid"))
    }
    Integer idParty = null;
    String party_tag = meta.getProperty("noiseparty_tag")
    if(party_tag != null && !party_tag.isEmpty()) {
        // Fetch noise party id
        def result = sql.firstRow("SELECT pk_party FROM  noisecapture_party where tag=:tag and (NOT filter_time OR (start_time <= :record_utc::timestamptz AND  end_time >= :record_utc::timestamptz))",
                [tag: party_tag, record_utc : epochToRFCTime(Long.valueOf(meta.getProperty("record_utc")))])
        if(result != null) {
            idParty = result.pk_party as Integer
        }
    }
    String calibrationMethod = meta.getProperty("method_calibration")
    if(calibrationMethod == null) {
        calibrationMethod = "None"
    }
    // insert record
    Map record = [track_uuid         : recordUUID,
                  pk_user            : idUser,
                  version_number     : meta.getProperty("version_number") as int,
                  record_utc         : epochToRFCTime(Long.valueOf(meta.getProperty("record_utc"))),
                  pleasantness       : meta.getOrDefault("pleasantness", null) as Integer,
                  device_product     : meta.get("device_product"),
                  device_model       : meta.get("device_model"),
                  device_manufacturer: meta.get("device_manufacturer"),
                  noise_level        : noiseLevel,
                  time_length        : meta.get("time_length") as int,
                  gain_calibration   : gain,
                  noiseparty_id      : idParty,
                  method_calibration : calibrationMethod]
    def recordId = sql.executeInsert("INSERT INTO noisecapture_track(track_uuid, pk_user, version_number, record_utc," +
            " pleasantness, device_product, device_model, device_manufacturer, noise_level, time_length, gain_calibration, pk_party, calibration_method) VALUES (" +
            ":track_uuid, :pk_user, :version_number,:record_utc::timestamptz, :pleasantness, :device_product, :device_model," +
            " :device_manufacturer, :noise_level, :time_length, :gain_calibration, :noiseparty_id, :method_calibration)", record)[0][0] as Integer
    // insert tags
    String tags = meta.getProperty("tags", "")
    if (!tags.isEmpty()) {
        // Cache tags
        Map<String, Integer> tagToIdTag = new HashMap<>()
        sql.eachRow("SELECT pk_tag, tag_name FROM noisecapture_tag") { row ->
            tagToIdTag.put(row.tag_name, row.pk_tag)
        }
        // Insert tags
        tags.tokenize(",").each() { String tag ->
            def tagId = tagToIdTag.get(tag.toLowerCase()) as Integer
            if (tagId == null) {
                // Insert new tag
                tagId = sql.executeInsert("INSERT INTO noisecapture_tag (tag_name) VALUES (:tag_name)",
                        [tag_name: tag.toLowerCase()])[0][0]
                tagToIdTag.put(tag.toLowerCase(), (int) tagId)
            }
            sql.executeInsert("INSERT INTO noisecapture_track_tag VALUES (:pktrack, :pktag)",
                    [pktrack: recordId, pktag: tagId])
        }
    }

    // Fetch GeoJSON
    def jsonSlurper = new JsonSlurper()
    def jsonRoot
    zipFile.withInputStream { is ->
        ZipInputStream zipInputStream = new ZipInputStream(is)
        ZipEntry zipEntry
        while ((zipEntry = zipInputStream.getNextEntry()) != null) {
            if ("track.geojson".equals(zipEntry.getName())) {
                jsonRoot = jsonSlurper.parse(zipInputStream)
                break
            }
        }
    }
    if (jsonRoot == null) {
        throw new InvalidParameterException("No track.geojson file")
    }

    def startLocation = null
    jsonRoot.features.each() { feature ->
        def theGeom = "GEOMETRYCOLLECTION EMPTY"
        if (feature.geometry != null && feature.geometry.coordinates[0] >= -180 && feature.geometry.coordinates[0] <= 180
                && feature.geometry.coordinates[1] >= -90 && feature.geometry.coordinates[1] <= 90) {
            if (feature.geometry.coordinates.size() == 2) {
                def (x, y) = feature.geometry.coordinates
                feature.geometry.coordinates = [x, y, null]
            }
            def (x, y, z) = feature.geometry.coordinates
            if (z != null) {
                theGeom = "POINT($x $y $z)" as String
            } else {
                // The_geom column are 3d forced, so, must set a Z value
                theGeom = "POINT($x $y)" as String
            }
            if(startLocation == null) {
                startLocation = theGeom
            }
        }
        def p = feature.properties
        if(noisecaptureVersion <= 27) {
            // Issue #197
            // Bearing and speed are swapped in the NoiseCapture app.
            def speed = p.bearing;
            def bearing = p.speed;
            p.speed = speed;
            p.bearing = bearing;
        }
        def fields = [the_geom     : theGeom,
                      pk_track     : recordId,
                      noise_level  : p.leq_mean as Double,
                      speed        : p.speed as Double,
                      accuracy     : p.accuracy as Double,
                      orientation  : p.bearing as Double,
                      time_date    : epochToRFCTime(p.leq_utc as Long),
                      time_location: epochToRFCTime(p.location_utc as Long)]
        def ptId = sql.executeInsert("INSERT INTO noisecapture_point(the_geom, pk_track, noise_level, speed," +
                " accuracy, orientation, time_date, time_location) VALUES (ST_Force3D(ST_GEOMFROMTEXT(:the_geom, 4326))," +
                " :pk_track, :noise_level, :speed, :accuracy, :orientation, :time_date::timestamptz, :time_location::timestamptz)", fields)[0][0] as Integer
        if(storeFrequencyLevels) {
            // Insert frequency
            sql.withBatch("INSERT INTO noisecapture_freq VALUES (:pkpoint, :freq, :noiselvl)") { batch ->
                p.findAll { it.key ==~ 'leq_[0-9]{3,5}' }.each { key, spl ->
                    def freq = key.substring("leq_".length()) as Integer
                    batch.addBatch([pkpoint: ptId, freq: freq, noiselvl: spl as Double])
                }
                batch.executeBatch()
            }
        }
    }

    // Remove pk_party if the track is out of bounds
    if(idParty != null && startLocation != null) {
        sql.eachRow("SELECT ST_CONTAINS(ST_SETSRID(THE_GEOM, 4326), ST_GEOMFROMTEXT(:geom, 4326)) ISCONTAINS, filter_area FROM" +
                " noisecapture_party WHERE pk_party = :pkparty", [pkparty: idParty, geom : startLocation]) { queryParty ->
            if(queryParty.filter_area && !queryParty.iscontains) {
                sql.execute("UPDATE NOISECAPTURE_TRACK SET PK_PARTY = NULL WHERE PK_TRACK = :pktrack", [pktrack:recordId])
                idParty = null
            }
        }
    }

    // Push track into process queue
    Map processQueue = [pk_track: recordId]
    sql.executeInsert("INSERT INTO NOISECAPTURE_PROCESS_QUEUE VALUES (:pk_track)", processQueue)

    // Accept changes
    connection.commit();

    return idParty
}

def Connection openPostgreSQLConnection() {
    // Init driver
    Class.forName("org.postgresql.Driver");
    // Read login/pass/dbname
    Properties properties = new Properties();
    File configFile = new File("onomapdb.properties")
    if (configFile.exists()) {
        properties.load(new FileReader(configFile))
        Connection connection = null;
        return DriverManager.getConnection(
                "jdbc:postgresql://localhost:" + properties.getProperty("port") + "/" + properties.getProperty("dbname"),
                properties.getProperty("username"), properties.getProperty("password"));
    } else {
        return null
    }
}


def static Connection openPostgreSQLDataStoreConnection() {
    Store store = new GeoServer().catalog.getStore("postgis")
    JDBCDataStore jdbcDataStore = (JDBCDataStore)store.getDataStoreInfo().getDataStore(null)
    return jdbcDataStore.getDataSource().getConnection()
}

def static void buildStatistics(Connection connection, Integer pkParty) {
    def sql = new Sql(connection)
    connection.setAutoCommit(false)
    sql.execute("DELETE FROM NOISECAPTURE_STATS_LAST_TRACKS WHERE (pk_party = :pk_party::int OR (:pk_party::int is null and pk_party is null))", [pk_party : pkParty])
    sql.execute("INSERT INTO NOISECAPTURE_STATS_LAST_TRACKS select t.pk_track, time_length, record_utc,ST_AsGeoJson(t.env) the_geom, st_astext(ST_Centroid(t.env)) env,ST_AsGeoJson((SELECT THE_GEOM FROM noisecapture_point np_start WHERE np_start.pk_track = t.pk_track AND NOT ST_ISEMPTY(np_start.THE_GEOM) AND accuracy < 15 ORDER BY time_date ASC LIMIT 1)) start_pt,  ST_AsGeoJson((SELECT THE_GEOM FROM noisecapture_point np_stop WHERE np_stop.pk_track = t.pk_track AND NOT ST_ISEMPTY(np_stop.THE_GEOM) AND accuracy < 15 ORDER BY time_date DESC LIMIT 1)) stop_pt, name_0, name_1,(CASE WHEN (name_3 IS NULL OR name_3 = '') THEN name_2 ELSE name_3 END) name_3, :pk_party::int from (select t.pk_track,time_length, record_utc, ST_EXTENT(p.the_geom) env, pk_party from noisecapture_track t, noisecapture_point  p where t.pk_track=p.pk_track and p.accuracy > 0 and p.accuracy < 15 and (pk_party = :pk_party::int OR :pk_party::int is null) GROUP BY t.pk_track order by t.record_utc DESC LIMIT 30) t, gadm28 where gadm28.the_geom && t.env AND ST_CONTAINS(gadm28.the_geom, ST_SetSRID(ST_Centroid(t.env),4326))", [pk_party : pkParty])
    sql.commit()
    connection.setAutoCommit(true)
}

def static int processFiles(Connection connection, File[] files, int processFileLimit, boolean writeFiles) {
    Logger logger = LoggerFactory.getLogger("logger_nc_parse")
    int processed = 0
    Set<Integer> partyIds = new HashSet<>();
    for (File zipFile : files) {
        Map trackData = [uuid: '00000000-0000-0000-0000-000000000000']
        try {
            partyIds.add(processFile(connection, zipFile, trackData, false))
        } catch (SQLException|InvalidParameterException|MissingPropertyException|IOException ex) {
            // Log error
            logger.error(zipFile.getName() + " Message: " + ex.getMessage(), StackTraceUtils.sanitize(new Exception(ex)))
            if(ex instanceof SQLException) {
                logger.error("SQLState: " +
                        ex.getSQLState())

                logger.error("Error Code: " +
                        ex.getErrorCode())
            }
            Throwable t = ex.getCause()
            while (t != null) {
                logger.error("Cause:", StackTraceUtils.sanitize(new Exception(t)))
                t = t.getCause()
            }
            // Log track in error
            if(writeFiles) {
                new File("data_dir/onomap_archive", "track_exception.csv") << zipFile.getName() << "," << StringEscapeUtils.escapeCsv(ex.getMessage()) << "\n"
            }
            // Cancel transaction
            connection.rollback()
        }
        // Move file to processed folder
        if(writeFiles) {
            File processedDir = new File("data_dir/onomap_archive", trackData.uuid.substring(0, 2))
            processedDir = new File(processedDir, trackData.uuid.substring(2, 4))
            processedDir = new File(processedDir, trackData.uuid.substring(4, 6))
            processedDir = new File(processedDir, trackData.uuid)
            if (!processedDir.exists()) {
                processedDir.mkdirs()
            }
            zipFile.renameTo(new File(processedDir, zipFile.getName()))
        }
        processed++
        if(processFileLimit > 0 && processed > processFileLimit) {
            break;
        }
    }
    // Add null party id in order to be sure to rebuild global history
    partyIds.add(null)
    // Build x lasts measurements history for each NoiseParty (id!=null) and for the global histry (id=null)
    partyIds.each { partyId -> buildStatistics(connection, partyId)}

    return processed
}

def run(input) {
    int processFileLimit = input["processFileLimit"] as Integer;
    File dataDir = new File("data_dir/onomap_uploading");
    int processed = 0
    if (dataDir.exists()) {
        File[] files = dataDir.listFiles(new ZipFileFilter())
        if (files.length != 0) {
            // Open PostgreSQL connection
            Connection connection = openPostgreSQLDataStoreConnection()
            try {
                processed = processFiles(connection, files, processFileLimit, true)
            } finally {
                connection.close()
            }
        }
    }
    return [result : processed]
}
