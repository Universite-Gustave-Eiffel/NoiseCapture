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
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.mail.Message
import javax.mail.Authenticator
import javax.mail.MessagingException
import javax.mail.PasswordAuthentication
import javax.mail.Multipart
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.AddressException
import javax.mail.internet.InternetAddress
import javax.mail.internet.InternetHeaders
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.internet.MimeUtility
import javax.sql.DataSource
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
  startLatitude       : [name: 'startLatitude', title: '',
                         type: Double.class],
  startLongitude      : [name: 'startLongitude', title: '',
                         type: Double.class],
  stopLatitude        : [name: 'stopLatitude', title: '',
                         type: Double.class],
  stopLongitude       : [name: 'stopLongitude', title: '',
                         type: Double.class],
  exportTracks        : [name: 'exportTracks', title: 'Export raw track boolean',
                         type: Boolean.class],
  exportMeasures      : [name: 'exportMeasures', title: 'Export raw measures boolean',
                         type: Boolean.class],
  exportAreas         : [name: 'exportAreas', title: 'Export post-processed values',
                         type: Boolean.class],
  emailNotification   : [name: 'emailNotification', title: 'Send links to download content to this email',
                         type: String.class],
  fromEpoch   : [name: 'fromEpoch', title: 'Filter from this utc epoch time',
                         type: Long.class, min:0, max:1]
]

outputs = [
  result: [name: 'result', title: 'Created file', type: String.class]
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
 *  Create mail session.
 *  @return mail session, may not be null.
 */
@CompileStatic
static def createSession(Map input) {
  String smtpProtocol = input.getOrDefault("smtpProtocol", null)
  def smtpHost = input.getOrDefault("smtpHost", null)
  def smtpPort = input.getOrDefault("smtpPort", 0)
  String smtpPassword = input.getOrDefault("smtpPassword", null)
  String smtpUsername = input.getOrDefault("smtpUsername", null)
  boolean smtpDebug = input.getOrDefault("smtpDebug", false)

  Properties props
  try {
    props = new Properties (System.getProperties());
  } catch(SecurityException ex) {
    props = new Properties()
  }

  String prefix = "mail.smtp"
  if (smtpProtocol != null) {
    props.put("mail.transport.protocol", smtpProtocol)
    prefix = "mail." + smtpProtocol
  }
  if (smtpHost != null) {
    props.put(prefix + ".host", smtpHost)
  }
  if (smtpPort > 0) {
    props.put(prefix + ".port", String.valueOf(smtpPort))
  }

  Authenticator auth = null;
  if(smtpPassword != null && smtpUsername != null) {
    props.put(prefix + ".auth", "true")
    auth = new Authenticator() {
      protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(smtpUsername, smtpPassword)
      }
    };
  }
  Session session = Session.getInstance(props, auth);
  if (smtpProtocol != null) {
    session.setProtocolForAddress("rfc822", smtpProtocol)
  }
  if (smtpDebug) {
    session.setDebug(smtpDebug)
  }
  return session
}


@CompileStatic
static InternetAddress[] parseAddress(String addressStr) {
  try {
    return InternetAddress.parse(addressStr, true)
  } catch(AddressException e) {
    LOGGER.error("Could not parse address ["+addressStr+"].", e)
    return null
  }
}

@CompileStatic
static def sendEmail(Map input, String recipient, String from, String subject, String body) {
  try {
    MimeBodyPart part
    try {
      ByteArrayOutputStream os = new ByteArrayOutputStream()
      Writer writer = new OutputStreamWriter(
        MimeUtility.encode(os, "quoted-printable"), "UTF-8")
      writer.write(body)
      writer.close()
      InternetHeaders headers = new InternetHeaders()
      headers.setHeader("Content-Type", "text/html; charset=UTF-8")
      headers.setHeader("Content-Transfer-Encoding", "quoted-printable")
      part = new MimeBodyPart(headers, os.toByteArray())
    } catch(Exception ex) {
      StringBuffer sbuf = new StringBuffer(body)
      for (int i = 0; i < sbuf.length(); i++) {
        if (sbuf.charAt(i) >= 0x80) {
          sbuf.setCharAt(i, '?' as char)
        }
      }
      part = new MimeBodyPart()
      part.setContent(sbuf.toString(), "text/html")
    }

    Session session = createSession(input)
    Message msg = new MimeMessage(session)
    Multipart mp = new MimeMultipart()
    mp.addBodyPart(part)
    msg.setContent(mp)
    msg.setSentDate(new Date(System.currentTimeMillis()))
    msg.setFrom(from)
    msg.setSubject(subject, "UTF-8")
    msg.setRecipients(Message.RecipientType.TO, parseAddress(recipient))
    Transport.send(msg)
  } catch(MessagingException e) {
    LOGGER.error("Error occurred while sending e-mail notification.", e);
  } catch(RuntimeException e) {
    LOGGER.error("Error occurred while sending e-mail notification.", e);
  }
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

/**
 * Create dump from database
 * @param connection SQL Connection
 * @param outPath Path of dump folder
 * @param exportTracks True to export tracks
 * @param exportMeasures True to export measurement points
 * @param exportAreas True to export hexagons
 * @param lastModificationDaysFilter Maximum number of days since last modification to update a country (0 no filter)
 * @return
 */
@CompileStatic
public String getDump(DataSource dataSource, File zipFileName, Map input) {
  // Maximum time to generate the dump
  final int MAX_GENERATE_DUMP_TIME = 15 * 60 * 1000

  // gzip then base64 the content (http://www.txtwizard.net/compression)
  final String README_CONTENT = "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"
  def startLatitude = input["startLatitude"] as Double
  def startLongitude = input["startLongitude"] as Double
  def stopLatitude = input["stopLatitude"] as Double
  def stopLongitude = input["stopLongitude"] as Double
  boolean exportTracks= input["exportTracks"]
  boolean exportMeasures = input["exportMeasures"]
  boolean exportAreas = input["exportAreas"]
  long fromEpoch = 0
  if("fromEpoch" in input) {
    fromEpoch = Math.max(0L, input["fromEpoch"] as Long)
  }

  long startDump = System.currentTimeMillis()


  def envelope = "SRID=2154;Polygon (($startLongitude $startLatitude, $stopLongitude $startLatitude, $stopLongitude $stopLatitude, $startLongitude $stopLatitude, $startLongitude $startLatitude))"
  long totalDumpTracks = 0
  long totalDumpPoints = 0
  long totalDumpAreas = 0

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
        "where record_utc >= :fromEpoch and te.the_geom && :envelope::geometry and nt.pk_track = te.pk_track order by nt.record_utc;",
        [envelope: envelope.toString(), fromEpoch: epochToRFCTime(fromEpoch)]) { GroovyResultSet track_row ->
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
          if (totalDumpTracks > 0) {
            fileJsonWriter << ",\n"
          }
          fileJsonWriter << JsonOutput.toJson(track)
          totalDumpTracks += 1
          if(totalDumpTracks % 1000 == 0) {
            if(System.currentTimeMillis() - startDump > MAX_GENERATE_DUMP_TIME) {
              throw new TimeoutException("Dump timeout $totalDumpTracks tracks")
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
        " from noisecapture_point np where time_date >= :fromEpoch and the_geom && :envelope::geometry order by np.time_date",
        [envelope: envelope.toString(), fromEpoch: epochToRFCTime(fromEpoch)]) {
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
        if (totalDumpPoints > 0) {
          fileJsonWriter << ",\n"
        }
        fileJsonWriter << JsonOutput.toJson(track)
        totalDumpPoints+=1
        if(totalDumpPoints % 1000 == 0) {
          if(System.currentTimeMillis() - startDump > MAX_GENERATE_DUMP_TIME) {
            throw new TimeoutException("Dump timeout $totalDumpPoints points")
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
        " where na.last_measure >= :fromEpoch and na.the_geom && :envelope::geometry and nap.pk_area = na.pk_area and" +
        " na.pk_party is null group by na.the_geom, cell_q, cell_r, tzid, na.la50, na.laeq, na.lden , mean_pleasantness," +
        " measure_count, first_measure, last_measure order by cell_q, cell_r;", [envelope: envelope.toString(), fromEpoch: epochToRFCTime(fromEpoch)]) {
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

          if (totalDumpAreas > 0) {
            fileJsonWriter << ",\n"
          }
          fileJsonWriter << JsonOutput.toJson(track)
          totalDumpAreas += 1
          if(totalDumpAreas % 1000 == 0) {
            if(System.currentTimeMillis() - startDump > MAX_GENERATE_DUMP_TIME) {
              throw new TimeoutException("Dump timeout $totalDumpAreas areas")
            }
          }
      }

      fileJsonWriter << "]\n}\n"
      fileJsonWriter.flush()
      totalDumpAreas += System.currentTimeMillis() - beginArea
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
  LOGGER.info(String.format("Dump complete \nTracks: %.2f seconds\nPoints %.2f seconds\nAreas %.2f seconds", totalDumpTracks / 1000, totalDumpPoints / 1000, totalDumpAreas / 1000))
  return zipFileName.path.substring(0, zipFileName.path.length() - 4)
}

def exec(Connection connection, Map input) {
  // Open PostgreSQL connection
  // Create dump folder
  def workingDir = System.getProperty("workingDir", "data_dir")
  File dumpDir = Paths.get(workingDir, "onomap_area_dump").toFile()
  if (!dumpDir.exists()) {
    dumpDir.mkdirs()
  }

  // create unique zip file
  def uuid = UUID.randomUUID().toString().replace("-", "")
  File zipFileName = new File(dumpDir, "extract_${uuid}.zip.tmp")

  Callable<String> task = new Callable<String>() {
    @Override
    String call() throws Exception {
      String res = getDump(input["dataSource"] as DataSource, zipFileName, input)
      def body = "<!DOCTYPE html>\n" +
        "<html lang=\"en\">\n" +
        "<head><title></title>\n" +
        "    <meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n" +
        "    <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n" +
        "    <style>\n" +
        "        * {\n" +
        "            box-sizing: border-box\n" +
        "        }\n" +
        "        body {\n" +
        "            margin: 0;\n" +
        "            padding: 0\n" +
        "        }\n" +
        "        a[x-apple-data-detectors] {\n" +
        "            color: inherit !important;\n" +
        "            text-decoration: inherit !important\n" +
        "        }\n" +
        "        #MessageViewBody a {\n" +
        "            color: inherit;\n" +
        "            text-decoration: none\n" +
        "        }\n" +
        "        p {\n" +
        "            line-height: inherit\n" +
        "        }\n" +
        "        .image_block img + div {\n" +
        "            display: none\n" +
        "        }\n" +
        "        sub, sup {\n" +
        "            font-size: 75%;\n" +
        "            line-height: 0\n" +
        "        }\n" +
        "        @media (max-width: 530px) {\n" +
        "            .stack .column {\n" +
        "                width: 100%;\n" +
        "                display: block\n" +
        "            }\n" +
        "        }\n" +
        "    </style>\n" +
        "</head>\n" +
        "<body class=\"body\"\n" +
        "      style=\"background-color:#f14b11;margin:0;padding:0;-webkit-text-size-adjust:none;text-size-adjust:none\">\n" +
        "<table class=\"nl-container\" width=\"100%\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\"\n" +
        "       style=\"mso-table-lspace:0;mso-table-rspace:0;background-color:#f14b11\">\n" +
        "    <tbody>\n" +
        "    <tr>\n" +
        "        <td>\n" +
        "            <table align=\"center\"\n" +
        "                   border=\"0\" cellpadding=\"0\" cellspacing=\"0\" class=\"row row-1\" role=\"presentation\"\n" +
        "                   style=\"mso-table-lspace:0;mso-table-rspace:0\"\n" +
        "                   width=\"100%\">\n" +
        "                <tbody>\n" +
        "                <tr>\n" +
        "                    <td>\n" +
        "                        <table class=\"row-content stack\" align=\"center\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\"\n" +
        "                               role=\"presentation\"\n" +
        "                               style=\"mso-table-lspace:0;mso-table-rspace:0;color:#000;width:510px;margin:0 auto\"\n" +
        "                               width=\"510\">\n" +
        "                            <tbody>\n" +
        "                            <tr>\n" +
        "                                <td class=\"column column-1\" width=\"100%\"\n" +
        "                                    style=\"mso-table-lspace:0;mso-table-rspace:0;font-weight:400;text-align:left;padding-bottom:5px;padding-top:5px;vertical-align:top\">\n" +
        "                                    <div class=\"spacer_block block-1\"\n" +
        "                                         style=\"height:20px;line-height:20px;font-size:1px\">&#8202;\n" +
        "                                    </div>\n" +
        "                                </td>\n" +
        "                            </tr>\n" +
        "                            </tbody>\n" +
        "                        </table>\n" +
        "                    </td>\n" +
        "                </tr>\n" +
        "                </tbody>\n" +
        "            </table>\n" +
        "            <table class=\"row row-2\" align=\"center\" width=\"100%\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\"\n" +
        "                   role=\"presentation\" style=\"mso-table-lspace:0;mso-table-rspace:0\">\n" +
        "                <tbody>\n" +
        "                <tr>\n" +
        "                    <td>\n" +
        "                        <table class=\"row-content stack\"\n" +
        "                               align=\"center\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\"\n" +
        "                               style=\"mso-table-lspace:0;mso-table-rspace:0;background-color:#fff;color:#000;width:510px;margin:0 auto\"\n" +
        "                               width=\"510\">\n" +
        "                            <tbody>\n" +
        "                            <tr>\n" +
        "                                <td class=\"column column-1\" width=\"100%\"\n" +
        "                                    style=\"mso-table-lspace:0;mso-table-rspace:0;font-weight:400;text-align:left;padding-bottom:30px;padding-left:30px;padding-right:30px;padding-top:30px;vertical-align:top\">\n" +
        "                                    <table class=\"text_block block-2\" width=\"100%\" border=\"0\" cellpadding=\"10\"\n" +
        "                                           cellspacing=\"0\" role=\"presentation\"\n" +
        "                                           style=\"mso-table-lspace:0;mso-table-rspace:0;word-break:break-word\">\n" +
        "                                        <tr>\n" +
        "                                            <td class=\"pad\">\n" +
        "                                                <div style=\"font-family:sans-serif\">\n" +
        "                                                    <div class\n" +
        "                                                         style=\"font-size:12px;font-family:Tahoma,Verdana,Segoe,sans-serif;mso-line-height-alt:14.399999999999999px;color:#181c27;line-height:1.2\">\n" +
        "                                                        <p style=\"margin:0;font-size:14px;text-align:center;mso-line-height-alt:16.8px\">\n" +
        "                                                            <span style=\"word-break: break-word; font-size: 30px;\">Your Download is Ready</span>\n" +
        "                                                        </p></div>\n" +
        "                                                </div>\n" +
        "                                            </td>\n" +
        "                                        </tr>\n" +
        "                                    </table>\n" +
        "                                    <table class=\"text_block block-3\" width=\"100%\" border=\"0\" cellpadding=\"0\"\n" +
        "                                           cellspacing=\"0\" role=\"presentation\"\n" +
        "                                           style=\"mso-table-lspace:0;mso-table-rspace:0;word-break:break-word\">\n" +
        "                                        <tr>\n" +
        "                                            <td class=\"pad\"\n" +
        "                                                style=\"padding-bottom:30px;padding-left:10px;padding-right:10px;padding-top:10px\">\n" +
        "                                                <div style=\"font-family:sans-serif\">\n" +
        "                                                    <div class\n" +
        "                                                         style=\"font-size:12px;font-family:Tahoma,Verdana,Segoe,sans-serif;mso-line-height-alt:14.399999999999999px;color:#8d94a3;line-height:1.2\">\n" +
        "                                                        <p style=\"margin:0;font-size:14px;text-align:center;mso-line-height-alt:16.8px\">\n" +
        "                                                            <span style=\"word-break: break-word; font-size: 16px;\">Use the link below to download <strong>the NoiseCapture database dump</strong></span>\n" +
        "                                                        </p></div>\n" +
        "                                                </div>\n" +
        "                                            </td>\n" +
        "                                        </tr>\n" +
        "                                    </table>\n" +
        "                                    <table class=\"button_block block-4\" width=\"100%\" border=\"0\" cellpadding=\"10\"\n" +
        "                                           cellspacing=\"0\" role=\"presentation\"\n" +
        "                                           style=\"mso-table-lspace:0;mso-table-rspace:0\">\n" +
        "                                        <tr>\n" +
        "                                            <td class=\"pad\">\n" +
        "                                                <div class=\"alignment\" align=\"center\">\n" +
        "                                                    <span class=\"button\"\n" +
        "                                                          style=\"background-color: #f14b11; border-bottom: 0px solid transparent; border-left: 0px solid transparent; border-radius: 50px; border-right: 0px solid transparent; border-top: 0px solid transparent; color: #ffffff; display: inline-block; font-family: Tahoma, Verdana, Segoe, sans-serif; font-size: 16px; font-weight: undefined; mso-border-alt: none; padding-bottom: 10px; padding-top: 10px; padding-left: 20px; padding-right: 20px; text-align: center; width: 50%; word-break: keep-all; letter-spacing: normal;\"><span\n" +
        "                                                            style=\"word-break: break-word; line-height: 32px;\"><a href=\"https://data.noise-planet.org/extract/$res\">Download</a></span></span>\n" +
        "                                                </div>\n" +
        "                                            </td>\n" +
        "                                        </tr>\n" +
        "                                    </table>\n" +
        "                                </td>\n" +
        "                            </tr>\n" +
        "                            </tbody>\n" +
        "                        </table>\n" +
        "                    </td>\n" +
        "                </tr>\n" +
        "                </tbody>\n" +
        "            </table>\n" +
        "        </td>\n" +
        "    </tr>\n" +
        "    </tbody>\n" +
        "</table><!-- End -->\n" +
        "</body>\n" +
        "</html>"
      sendEmail(input, input["emailNotification"] as String, input["emailFrom"] as String,
        "Your data extraction is ready", body)
      return res
    }
  }

  if ("worker" in input) {
    // Use special vert.x thread pool
    def future = input["worker"].executeBlocking(task)
    return [result: future]
  } else {
    def future = Executors.newSingleThreadExecutor().submit(task)
    return [result: future]
  }
}
