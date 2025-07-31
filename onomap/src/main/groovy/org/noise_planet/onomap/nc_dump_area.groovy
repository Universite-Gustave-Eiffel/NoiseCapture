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

import groovy.json.JsonException
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.sql.GroovyResultSet
import groovy.sql.Sql
import groovy.transform.CompileStatic
import groovy.transform.Field
import org.locationtech.jts.geom.Geometry
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.sql.DataSource
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.*
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

title = 'nc_dump_records'
description = 'Dump database data to a folder'

inputs = [
  envelope       : [name: 'envelope', title: '',
                         type: Geometry.class],
  exportTracks        : [name: 'exportTracks', title: 'Export raw track boolean',
                         type: Boolean.class],
  exportMeasures      : [name: 'exportMeasures', title: 'Export raw measures boolean',
                         type: Boolean.class],
  exportAreas         : [name: 'exportAreas', title: 'Export post-processed values',
                         type: Boolean.class],
  exportRaw         : [name: 'exportRaw', title: 'Export raw measurements files',
                         type: Boolean.class],
  fromEpoch   : [name: 'fromEpoch', title: 'Filter from this utc epoch time',
                         type: Long.class, min:0, max:1],
  toEpoch   : [name: 'fromEpoch', title: 'Filter to this utc epoch time',
                 type: Long.class, min:0, max:1]
]

outputs = [
  result: [name: 'result', title: 'Html file', type: String.class]
]

@Field
static Logger LOGGER  = LoggerFactory.getLogger("logger_nc_dump_area")

/**
 * Convert EPOCH time to ISO 8601
 * @param epochMillisecond
 * @return
 */
@CompileStatic
static def epochToRFCTime(long epochMillisecond) {
  return Instant.ofEpochMilli(epochMillisecond).atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))
}

/**
 * Convert EPOCH time to ISO 8601
 * @param epochMillisec
 * @return
 */
static def epochToRFCTime(long epochMillisec, String zone) {
  ZoneId zoneId = ZoneId.systemDefault();
  try {
    zoneId = ZoneId.of(zone)
  } catch (DateTimeException ex) {
    // skip
  }
  return Instant.ofEpochMilli(epochMillisec).atZone(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}

@CompileStatic
static def getHtmlPageTemplate(String message, String filename, boolean autorefresh) {
  String downloadArea = ""
  String refreshCode = ""
  if(autorefresh) {
    refreshCode = "<meta http-equiv=\"refresh\" content=\"10\">"
  }
  if(!filename.empty) {
    downloadArea = """
    <table class="button_block block-4" width="100%" border="0" cellpadding="10"
           cellspacing="0" role="presentation"
           style="mso-table-lspace:0;mso-table-rspace:0">
        <tr>
            <td class="pad">
                <div class="alignment" align="center">
                    <span class="button"
                          style="background-color: #f14b11; border-bottom: 0px solid transparent; border-left: 0px solid transparent; border-radius: 50px; border-right: 0px solid transparent; border-top: 0px solid transparent; color: #ffffff; display: inline-block; font-family: Tahoma, Verdana, Segoe, sans-serif; font-size: 16px; font-weight: undefined; mso-border-alt: none; padding-bottom: 10px; padding-top: 10px; padding-left: 20px; padding-right: 20px; text-align: center; width: 50%; word-break: keep-all; letter-spacing: normal;"><span
                            style="word-break: break-word; line-height: 32px;"><a href="$filename">Download</a></span></span>
                </div>
            </td>
        </tr>
    </table>"""
  }
  return  """<!DOCTYPE html>
<html lang="en">
<head><title>NoiseCapture area data extraction</title>
    $refreshCode
    <meta http-equiv="Content-Type" content="text/html; charset=utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <style>
        * {
            box-sizing: border-box
        }
        body {
            margin: 0;
            padding: 0
        }
        a[x-apple-data-detectors] {
            color: inherit !important;
            text-decoration: inherit !important
        }
        #MessageViewBody a {
            color: inherit;
            text-decoration: none
        }
        p {
            line-height: inherit
        }
        .image_block img + div {
            display: none
        }
        sub, sup {
            font-size: 75%;
            line-height: 0
        }
        @media (max-width: 530px) {
            .stack .column {
                width: 100%;
                display: block
            }
        }
    </style>
</head>
<body class="body"
      style="background-color:#f14b11;margin:0;padding:0;-webkit-text-size-adjust:none;text-size-adjust:none">
<table class="nl-container" width="100%" border="0" cellpadding="0" cellspacing="0" role="presentation"
       style="mso-table-lspace:0;mso-table-rspace:0;background-color:#f14b11">
    <tbody>
    <tr>
        <td>
            <table align="center"
                   border="0" cellpadding="0" cellspacing="0" class="row row-1" role="presentation"
                   style="mso-table-lspace:0;mso-table-rspace:0"
                   width="100%">
                <tbody>
                <tr>
                    <td>
                        <table class="row-content stack" align="center" border="0" cellpadding="0" cellspacing="0"
                               role="presentation"
                               style="mso-table-lspace:0;mso-table-rspace:0;color:#000;width:510px;margin:0 auto"
                               width="510">
                            <tbody>
                            <tr>
                                <td class="column column-1" width="100%"
                                    style="mso-table-lspace:0;mso-table-rspace:0;font-weight:400;text-align:left;padding-bottom:5px;padding-top:5px;vertical-align:top">
                                    <div class="spacer_block block-1"
                                         style="height:20px;line-height:20px;font-size:1px">&#8202;
                                    </div>
                                </td>
                            </tr>
                            </tbody>
                        </table>
                    </td>
                </tr>
                </tbody>
            </table>
            <table class="row row-2" align="center" width="100%" border="0" cellpadding="0" cellspacing="0"
                   role="presentation" style="mso-table-lspace:0;mso-table-rspace:0">
                <tbody>
                <tr>
                    <td>
                        <table class="row-content stack"
                               align="center" border="0" cellpadding="0" cellspacing="0" role="presentation"
                               style="mso-table-lspace:0;mso-table-rspace:0;background-color:#fff;color:#000;width:510px;margin:0 auto"
                               width="510">
                            <tbody>
                            <tr>
                                <td class="column column-1" width="100%"
                                    style="mso-table-lspace:0;mso-table-rspace:0;font-weight:400;text-align:left;padding-bottom:30px;padding-left:30px;padding-right:30px;padding-top:30px;vertical-align:top">
                                    <table class="text_block block-2" width="100%" border="0" cellpadding="10"
                                           cellspacing="0" role="presentation"
                                           style="mso-table-lspace:0;mso-table-rspace:0;word-break:break-word">
                                        <tr>
                                            <td class="pad">
                                                <div style="font-family:sans-serif">
                                                    <div class
                                                         style="font-size:12px;font-family:Tahoma,Verdana,Segoe,sans-serif;mso-line-height-alt:14.399999999999999px;color:#181c27;line-height:1.2">
                                                        <p style="margin:0;font-size:14px;text-align:center;mso-line-height-alt:16.8px">
                                                            <span style="word-break: break-word; font-size: 30px;">NoiseCapture data extraction</span>
                                                            ★ Add to favorite to not loose this web page
                                                        </p></div>
                                                </div>
                                            </td>
                                        </tr>
                                    </table>
                                    <table class="text_block block-3" width="100%" border="0" cellpadding="0"
                                           cellspacing="0" role="presentation"
                                           style="mso-table-lspace:0;mso-table-rspace:0;word-break:break-word">
                                        <tr>
                                            <td class="pad"
                                                style="padding-bottom:30px;padding-left:10px;padding-right:10px;padding-top:10px">
                                                <div style="font-family:sans-serif">
                                                    <div class
                                                         style="font-size:12px;font-family:Tahoma,Verdana,Segoe,sans-serif;mso-line-height-alt:14.399999999999999px;color:#8d94a3;line-height:1.2">
                                                        <p style="margin:0;font-size:14px;text-align:center;mso-line-height-alt:16.8px">
                                                            <span style="word-break: break-word; font-size: 16px;">$message</span>
                                                        </p></div>
                                                </div>
                                            </td>
                                        </tr>
                                    </table>
                                    $downloadArea
                                </td>
                            </tr>
                            </tbody>
                        </table>
                    </td>
                </tr>
                </tbody>
            </table>
        </td>
    </tr>
    </tbody>
</table>
</body>
</html>"""
}
/**
 * Create dump from database
 * @param dataSource DataBase connection
 * @param zipFileName Path of output data
 * @param input Input fields
 * @return
 */
@CompileStatic
public String getDump(DataSource dataSource, File zipFileName, File onomapArchivePath ,Map input) {
  // Maximum time to generate the dump
  final int MAX_GENERATE_DUMP_TIME = 15 * 60 * 1000

  // gzip then base64 the content (http://www.txtwizard.net/compression)
  final String README_CONTENT = "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"
  def geom = input["envelope"] as Geometry
  boolean exportTracks = input["exportTracks"]
  boolean exportMeasures = input["exportMeasures"]
  boolean exportAreas = input["exportAreas"]
  boolean exportRaw = input["exportRaw"]

  long fromEpoch = 0
  if("fromEpoch" in input) {
    fromEpoch = Math.max(0L, input["fromEpoch"] as Long)
  }
  long toEpoch = Long.MAX_VALUE
  if("toEpoch" in input) {
    toEpoch = Math.max(0L, input["toEpoch"] as Long)
  }

  long startDump = System.currentTimeMillis()


  def envelope = "SRID=2154; $geom"
  long totalDumpTracksCount = 0
  long totalDumpPointsCount = 0
  long totalDumpAreasCount = 0
  long totalDumpRawCount = 0
  long totalDumpTracks = 0
  long totalDumpPoints = 0
  long totalDumpAreas = 0
  long totalDumpRaw = 0

  ZipOutputStream fileZipOutputStream = new ZipOutputStream(new FileOutputStream(zipFileName))
  Writer fileJsonWriter = new OutputStreamWriter(fileZipOutputStream, "UTF-8")

  // Process export of raw measures
  try(Connection connection = dataSource.getConnection()) {
    connection.setAutoCommit(false)
    def sql = new Sql(connection)
    // Change result set type, this way the PostGIS driver use the db cursor with minimal memory usage
    sql.setResultSetType(ResultSet.TYPE_FORWARD_ONLY)
    sql.setResultSetConcurrency(ResultSet.CONCUR_READ_ONLY)
    sql.withStatement {
      Statement stmt -> {
        stmt.setFetchSize(50)
        stmt.setQueryTimeout(15 * 60)
      }
    }
    // Create a table that contains track envelopes
    def lastpktrack = sql.firstRow("SELECT MAX(PK_TRACK) FROM NOISECAPTURE_DUMP_TRACK_ENVELOPE")[0] as Integer
    if(lastpktrack != null) {
      // resume analyze of receivers extents
      sql.execute("INSERT INTO NOISECAPTURE_DUMP_TRACK_ENVELOPE SELECT pk_track, " +
        "ST_SETSRID(ST_EXTENT(ST_MAKEPOINT(ST_X(the_geom),ST_Y(the_geom))), 4326) the_geom,  COUNT(np.pk_point) measure_count" +
        " from noisecapture_point np where pk_track > :maxpktrack and not ST_ISEMPTY(the_geom)  group by pk_track having st_area(ST_Transform(ST_SETSRID(ST_EXTENT(ST_MAKEPOINT(ST_X(the_geom),ST_Y(the_geom))), 4326), 3857)) < 1e8", [maxpktrack: lastpktrack])
    } else {
      sql.execute("INSERT INTO NOISECAPTURE_DUMP_TRACK_ENVELOPE SELECT pk_track, " +
        "ST_SETSRID(ST_EXTENT(ST_MAKEPOINT(ST_X(the_geom),ST_Y(the_geom))), 4326) the_geom,  COUNT(np.pk_point) measure_count" +
        " from noisecapture_point np where not ST_ISEMPTY(the_geom)  group by pk_track having st_area(ST_Transform(ST_SETSRID(ST_EXTENT(ST_MAKEPOINT(ST_X(the_geom),ST_Y(the_geom))), 4326), 3857)) < 1e8")
    }
    if (exportTracks) {
      long beginTracks = System.currentTimeMillis()
      String fileName = "tracks.geojson"
      fileZipOutputStream.putNextEntry(new ZipEntry(fileName))
      fileJsonWriter << "{\n  \"type\": \"FeatureCollection\",\n  \"features\": [\n"
      sql.eachRow("select (select tzid from tz_world tz where ST_Contains(tz.the_geom, ST_Centroid(te.the_geom))" +
        " LIMIT 1) tzid, nt.pk_track, track_uuid, pleasantness,gain_calibration,ST_AsGeoJson(te.the_geom) the_geom," +
        " nt.record_utc, noise_level, time_length, (select string_agg(tag_name, ',') from noisecapture_tag ntag," +
        " noisecapture_track_tag nttag where ntag.pk_tag = nttag.pk_tag and nttag.pk_track = nt.pk_track) tags," +
        " (select noisecapture_party.tag from noisecapture_party where noisecapture_party.pk_party = nt.pk_party)" +
        " partycode from noisecapture_track nt, noisecapture_dump_track_envelope te  " +
        "where record_utc >= :fromEpoch::timestamptz and record_utc < :toEpoch::timestamptz and te.the_geom && :envelope::geometry and nt.pk_track = te.pk_track order by nt.record_utc;",
        [envelope: envelope.toString(), fromEpoch: epochToRFCTime(fromEpoch), toEpoch: epochToRFCTime(toEpoch)]) { GroovyResultSet track_row ->
        def the_geom = new JsonSlurper().parseText((String) track_row['the_geom'])
        def time_ISO_8601 = epochToRFCTime(((Timestamp) track_row['record_utc']).time, (String) track_row['tzid'])
        def track = [type: "Feature", geometry: the_geom, properties: [pleasantness    : track_row['pleasantness'] == null ? null : (Double.isNaN(track_row.getDouble('pleasantness')) ? null : track_row['pleasantness']),
                                                                       pk_track        : track_row['pk_track'],
                                                                       track_uuid      : track_row['track_uuid'],
                                                                       gain_calibration: track_row['gain_calibration'],
                                                                       time_ISO8601    : time_ISO_8601,
                                                                       time_epoch      : ((Timestamp) track_row['record_utc']).time,
                                                                       noise_level     : track_row['noise_level'],
                                                                       time_length     : track_row['time_length'],
                                                                       tags            : track_row['tags'] == null ? null : ((String) track_row['tags']).tokenize(','),
                                                                       party_tag       : track_row['partycode']]]
        try {
          if (totalDumpTracksCount > 0) {
            fileJsonWriter << ",\n"
          }
          fileJsonWriter << JsonOutput.toJson(track)
          totalDumpTracksCount += 1
          if(totalDumpTracksCount % 1000 == 0) {
            if(System.currentTimeMillis() - startDump > MAX_GENERATE_DUMP_TIME) {
              throw new TimeoutException("Dump timeout $totalDumpTracksCount tracks")
            }
          }
        } catch (JsonException ex) {
          LOGGER.error(String.format("Track %d illegal content", track_row.getInt('pk_track')), ex);
        }
      }
      fileJsonWriter << "]\n}\n"
      fileJsonWriter.flush()
      totalDumpTracks += System.currentTimeMillis() - beginTracks
    }
    if (exportMeasures) {
      long beginPoints = System.currentTimeMillis()
      String fileName = "points.geojson"
      fileZipOutputStream.putNextEntry(new ZipEntry(fileName))
      fileJsonWriter << "{\n  \"type\": \"FeatureCollection\",\n  \"features\": [\n"
      sql.eachRow("select (select tzid from tz_world tz where tz.the_geom && np.the_geom and" +
        " ST_Contains(tz.the_geom, np.the_geom) LIMIT 1) tzid, np.pk_track, ST_AsGeoJson(np.the_geom) the_geom," +
        " np.noise_level, np.speed, np.accuracy, np.orientation, np.time_date, np.time_location " +
        " from noisecapture_point np where time_date >= :fromEpoch::timestamptz and time_date < :toEpoch::timestamptz and the_geom && :envelope::geometry order by np.time_date",
        [envelope: envelope.toString(), fromEpoch: epochToRFCTime(fromEpoch), toEpoch: epochToRFCTime(toEpoch)]) {
        GroovyResultSet track_row ->
        def the_geom = new JsonSlurper().parseText(track_row.getString('the_geom'))
        def time_ISO_8601 = epochToRFCTime((track_row.getTimestamp('time_date')).time, track_row.getString('tzid'))
        def time_gps_ISO_8601 = epochToRFCTime(((Timestamp) track_row.getTimestamp('time_location')).time, track_row.getString('tzid'))
        def track = [type: "Feature", geometry: the_geom, properties: [pk_track        : track_row['pk_track'],
                                                                       time_ISO8601    : time_ISO_8601,
                                                                       time_epoch      : (track_row.getTimestamp('time_date')).time,
                                                                       time_gps_ISO8601: time_gps_ISO_8601,
                                                                       time_gps_epoch  : (track_row.getTimestamp('time_location')).time,
                                                                       noise_level     : track_row['noise_level'],
                                                                       speed           : track_row['speed'],
                                                                       orientation     : track_row['orientation'],
                                                                       accuracy        : track_row['accuracy']
        ]]
        if (totalDumpPointsCount > 0) {
          fileJsonWriter << ",\n"
        }
        fileJsonWriter << JsonOutput.toJson(track)
        totalDumpPointsCount += 1
        if(totalDumpPointsCount % 1000 == 0) {
          if(System.currentTimeMillis() - startDump > MAX_GENERATE_DUMP_TIME) {
            throw new TimeoutException("Dump timeout $totalDumpPointsCount points")
          }
        }
      }
      fileJsonWriter << "]\n}\n"
      fileJsonWriter.flush()
      totalDumpPoints += System.currentTimeMillis() - beginPoints
    }

    if (exportAreas) {
      long beginArea = System.currentTimeMillis()
      String fileName = "areas.geojson"
      fileZipOutputStream.putNextEntry(new ZipEntry(fileName))
      fileJsonWriter << "{\n  \"type\": \"FeatureCollection\",\n  \"features\": [\n"
      sql.eachRow("SELECT ST_AsGeoJson(na.the_geom) the_geom, cell_q, cell_r," +
        " (select tzid from tz_world tz where tz.the_geom && na.the_geom and ST_Contains(tz.the_geom, ST_Centroid(na.the_geom)) LIMIT 1) tzid," +
        " na.la50, na.laeq, na.lden , mean_pleasantness, measure_count, first_measure, last_measure," +
        " string_agg(to_char(nap.laeq, 'FM999.9'), '_') leq_profile," +
        " string_agg(to_char(local_hour, '999'), '_') hour_profile" +
        " FROM noisecapture_area na, noisecapture_area_profile nap" +
        " where na.last_measure >= :fromEpoch::timestamptz and na.last_measure < :toEpoch::timestamptz and na.the_geom && :envelope::geometry and nap.pk_area = na.pk_area and" +
        " na.pk_party is null group by na.the_geom, cell_q, cell_r, tzid, na.la50, na.laeq, na.lden , mean_pleasantness," +
        " measure_count, first_measure, last_measure order by cell_q, cell_r;", [envelope: envelope.toString(), fromEpoch: epochToRFCTime(fromEpoch), toEpoch: epochToRFCTime(toEpoch)]) {
        GroovyResultSet track_row ->
          def first_measure_ISO_8601 = epochToRFCTime((track_row.getTimestamp('first_measure')).time, track_row.getString('tzid'))
          def last_measure_ISO_8601 = epochToRFCTime((track_row.getTimestamp('last_measure')).time, track_row.getString('tzid'))

          def the_geom = new JsonSlurper().parseText(track_row.getString('the_geom'))

          def leq_keys = track_row.getString('hour_profile').tokenize('_').collect() { it.toInteger() }
          def leq_values = track_row.getString('leq_profile').tokenize('_').collect() {it.toFloat() }
          def leq_array = new Object[72]
          [leq_keys, leq_values].transpose().each { leq_array[(Integer) ((List<Object>)it)[0]] = ((List<Object>)it)[1] }
          def track = [type      : "Feature", geometry: [type: "Polygon", coordinates: the_geom['coordinates']],
                       properties: [cell_q                : track_row['cell_q'],
                                    cell_r                : track_row['cell_r'],
                                    la50                  : track_row['la50'],
                                    laeq                  : track_row['laeq'],
                                    lden                  : track_row['lden'],
                                    mean_pleasantness     : track_row['mean_pleasantness'] == null ? null : (Double.isNaN(track_row.getDouble('mean_pleasantness')) ? null : track_row.getDouble('mean_pleasantness')),
                                    measure_count         : track_row['measure_count'],
                                    first_measure_ISO_8601: first_measure_ISO_8601,
                                    first_measure_epoch   : (track_row.getTimestamp('first_measure')).time,
                                    last_measure_ISO_8601 : last_measure_ISO_8601,
                                    last_measure_epoch    : (track_row.getTimestamp('last_measure')).time,
                                    leq_profile           : leq_array]]

          if (totalDumpAreasCount > 0) {
            fileJsonWriter << ",\n"
          }
          fileJsonWriter << JsonOutput.toJson(track)
          totalDumpAreasCount += 1
          if(totalDumpAreasCount % 1000 == 0) {
            if(System.currentTimeMillis() - startDump > MAX_GENERATE_DUMP_TIME) {
              throw new TimeoutException("Dump timeout $totalDumpAreasCount areas")
            }
          }
      }
      fileJsonWriter << "]\n}\n"
      fileJsonWriter.flush()
      totalDumpAreas += System.currentTimeMillis() - beginArea
    }
    if(exportRaw) {
      long beginRaw = System.currentTimeMillis()
      fileJsonWriter.flush()
      fileZipOutputStream.putNextEntry(new ZipEntry("raw/"))
      fileZipOutputStream.closeEntry()
      sql.eachRow("select nt.pk_track, track_uuid, user_uuid from noisecapture_track nt, noisecapture_dump_track_envelope te, noisecapture_user ne where record_utc >= :fromEpoch::timestamptz and record_utc < :toEpoch::timestamptz and te.the_geom && :envelope::geometry and nt.pk_track = te.pk_track and nt.pk_user = ne.pk_user order by nt.record_utc;",
        [envelope: envelope.toString(), fromEpoch: epochToRFCTime(fromEpoch), toEpoch: epochToRFCTime(toEpoch)]) { GroovyResultSet track_row ->
        String userUUID = track_row["user_uuid"]
        String trackUUID = track_row["track_uuid"]
        def part1 = userUUID.substring(0, 2)
        def part2 = userUUID.substring(2, 4)
        def part3 = userUUID.substring(4, 6)
        String fileName = "track_"+trackUUID+".zip"
        File rawTrackFile = Path.of(onomapArchivePath.toString(), part1, part2, part3, userUUID, fileName).toFile()
        if(rawTrackFile.exists()) {
          // include zip file on output zip
          fileZipOutputStream.putNextEntry(new ZipEntry("raw/" + fileName))
          rawTrackFile.withInputStream { is -> fileZipOutputStream << is.bytes }
        }
        totalDumpRawCount += 1
        if(totalDumpRawCount % 1000 == 0) {
          if(System.currentTimeMillis() - startDump > MAX_GENERATE_DUMP_TIME) {
            throw new TimeoutException("Dump timeout $totalDumpRawCount raw files")
          }
        }
      }
      totalDumpRaw += System.currentTimeMillis() - beginRaw
    }
  } catch (SQLException ex) {
    throw ex
  } finally {
    fileZipOutputStream.closeEntry()
    // Write readme file
    fileZipOutputStream.putNextEntry(new ZipEntry("README.html"))
    new ByteArrayInputStream(Base64.decoder.decode(README_CONTENT)).withStream {
      bais ->
        new GZIPInputStream(bais).withStream {
          html ->
            fileZipOutputStream << html;
        }
    }
    fileZipOutputStream.closeEntry()
    // Close zip file stream
    fileZipOutputStream.close()
  }
  // Move created files
  zipFileName.renameTo(new File(zipFileName.path.substring(0, zipFileName.path.length() - 4)))
  LOGGER.info(String.format("Dump complete \nTracks: %.2f seconds\nPoints %.2f seconds\nAreas %.2f seconds\nRaw %.2f seconds", totalDumpTracks / 1000, totalDumpPoints / 1000, totalDumpAreas / 1000, totalDumpRaw / 1000))
  return zipFileName.path.substring(0, zipFileName.path.length() - 4)
}

def exec(Connection connection, Map input) {
  // Open PostgreSQL connection
  // Create dump folder
  def workingDir = System.getProperty("workingDir", "data_dir")
  File onomapArchiveDir = Paths.get(workingDir, "onomap_archive").toFile()
  File dumpDir = Paths.get(workingDir, "onomap_area_dump").toFile()
  if (!dumpDir.exists()) {
    dumpDir.mkdirs()
  }

  // create unique zip file
  def uuid = UUID.randomUUID().toString().replace("-", "")
  File zipFileName = new File(dumpDir, "extract_${uuid}.zip.tmp")
  final File htmlFileName = new File(dumpDir, "${uuid}.html")
  try(def f = new FileWriter(htmlFileName)) {
    f.write(getHtmlPageTemplate("Please wait.. Data extraction is in progress.. This web page refresh every 10 seconds", "", true))
  }

  Callable<String> task = new Callable<String>() {
    @Override
    String call() throws Exception {
      String res = getDump(input["dataSource"] as DataSource, zipFileName, onomapArchiveDir, input)
      try(def f = new FileWriter(htmlFileName)) {
        f.write(getHtmlPageTemplate("Click on the link below the download the NoiseCapture data", new File(res).name, false))
      }
      return res
    }
  }

  if ("worker" in input) {
    // Use special vert.x thread pool
    def future = input["worker"].executeBlocking(task)
    return [result: JsonOutput.toJson(htmlFileName.getName())]
  } else {
    def future = Executors.newSingleThreadExecutor().submit(task)
    return [result: JsonOutput.toJson(htmlFileName.getName())]
  }
}
