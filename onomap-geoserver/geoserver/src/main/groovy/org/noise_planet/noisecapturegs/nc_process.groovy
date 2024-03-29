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
import groovy.sql.BatchingStatementWrapper
import groovy.sql.GroovyResultSet
import groovy.sql.Sql
import groovy.transform.CompileStatic
import org.geotools.jdbc.JDBCDataStore
import org.locationtech.jts.geom.Coordinate
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.security.InvalidParameterException
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.time.DayOfWeek
import java.time.ZonedDateTime

title = 'nc_process'
description = 'Recompute cells that contains new measures'

inputs = [
        locationPrecisionFilter: [name: 'locationPrecisionFilter', title: 'Ignore measurements with location precision greater than specified distance',
                           type: Float.class] ,
        processTracksLimit: [name: 'processTrackLimit', title: 'Maximum number of tracks to process, 0 for unlimited',
                           type: Integer.class]]

outputs = [
        result: [name: 'result', title: 'Processed tracks', type: Integer.class]
]

@CompileStatic
class Record{
    List<Double> levels = new ArrayList<Double>()
    Double L50 = null

    Record() {
    }

    void addLeq(double leq) {
        levels.add(leq)
        this.L50 = null
    }

    static double getEnergeticAverage(double l1, double l2) {
        return 10 * Math.log10((Math.pow(10.0d, l1 / 10.0d) + Math.pow(10.0d, l2 / 10.0d)) / 2.0d)
    }

    double getLAeq() {
        return 10 * Math.log10(((levels.sum({Double it -> Math.pow(10.0d, it / 10.0d)}) as Double) / levels.size()) as Double )
    }

    /**
     * @return Average or L50% value of the noise.
     */
    double getLA50() {
        if(L50 == null) {
            levels = levels.sort()
            if(levels.size() % 2 == 1) {
                this.L50 = levels.get((((levels.size()+1)/2)-1).intValue())
            } else {
                this.L50 = getEnergeticAverage(levels.get((((levels.size()+1)/2)-1).intValue()),
                        levels.get((((levels.size()+1)/2)).intValue()))
            }
        }
        return L50
    }
}
/**
 * Fetch all measurements within a range and compute local stats over this area
 * @param hex
 * @param range
 * @param precisionFiler GPS location greater than this value are ignored
 * @param sql
 */
@CompileStatic
def processArea(Hex hex,float precisionFiler, Sql sql, Integer partyPk) {
    // A ratio < 1 add blank area between hexagons
    double hexSizeRatio = 1.0;
    def Pos center = hex.toMeter()
    def Coordinate centerCoord = center.toCoordinate();
    def geom = "POINT( " + center.x + " " + center.y + ")"
    // Fetch hex timezone
    def res = sql.firstRow("SELECT TZID FROM tz_world WHERE " +
            "ST_TRANSFORM(ST_GeomFromText(:geom,3857),4326) && the_geom AND" +
            " ST_Intersects(ST_TRANSFORM(ST_GeomFromText(:geom,3857),4326), the_geom) LIMIT 1", [geom: geom.toString()])
    TimeZone tz = res == null ? TimeZone.default : TimeZone.getTimeZone(res.TZID as String);
    int pointCount = 0;
    double sumPleasantness = 0
    int pleasantnessCount = 0
    Timestamp firstUtc = null
    Timestamp lastUtc = null
    Map<Integer, Record> records = new HashMap<>()
    def hexaRecord = new Record()
    sql.eachRow("SELECT p.pk_track, ST_X(ST_Transform(ST_SetSRID(p.the_geom, 4326), 3857)) PTX,ST_Y(ST_Transform(ST_SetSRID(p.the_geom, 4326), 3857)) PTY, p.noise_level," +
            " t.pleasantness,time_date FROM noisecapture_point p, noisecapture_track t WHERE p.pk_track = t.pk_track AND p.accuracy < :precision and NOT ST_ISEMPTY(p.the_geom) AND " +
            "ST_TRANSFORM(ST_ENVELOPE(ST_BUFFER(ST_GeomFromText(:geom,3857),:range)),4326) && the_geom AND (pk_party = :pk_party::int OR :pk_party::int is NULL) ORDER BY p.pk_track, time_date", [geom: geom.toString(), range: hex.size, precision : precisionFiler, pk_party : partyPk])
            { row ->
                Pos pos = new Pos(x: row.getDouble('PTX'),
                        y: row.getDouble('PTY'))
                def hexOfMeasure = pos.toHex(hex.size)
                if (hexOfMeasure == hex) {
                    ZonedDateTime zonedDateTime = row.getTimestamp('time_date').toInstant().atZone(tz.toZoneId())
                    int hour = 0
                    if (zonedDateTime.dayOfWeek == DayOfWeek.SUNDAY) {
                        hour = 48 + zonedDateTime.hour
                    } else {
                        if (zonedDateTime.dayOfWeek == DayOfWeek.SATURDAY) {
                            hour = 24 + zonedDateTime.hour
                        } else {
                            hour = zonedDateTime.hour
                        }
                    }
                    Record recordHour
                    if(records.containsKey(hour)) {
                        recordHour = records[hour]
                    } else {
                        recordHour = new Record()
                        records[hour] = recordHour
                    }
                    recordHour.addLeq(row.getDouble('noise_level'))
                    hexaRecord.addLeq(row.getDouble('noise_level'))
                    if(!firstUtc) {
                        firstUtc = row.getTimestamp('time_date')
                    }
                    lastUtc = row.getTimestamp('time_date')
                    double pleasantness = row.getDouble('pleasantness')
                    if (!row.wasNull()) {
                        pleasantnessCount++;
                        sumPleasantness += pleasantness;
                    }
                    pointCount++
                }
            }
    // Delete old cell
    if(partyPk != null) {
        sql.execute("DELETE FROM noisecapture_area a WHERE a.cell_q = :cellq and a.cell_r = :cellr and a.pk_party = :pk_party", [cellq: hex.q, cellr: hex.r, pk_party: partyPk])
    } else {
        sql.execute("DELETE FROM noisecapture_area a WHERE a.cell_q = :cellq and a.cell_r = :cellr and a.pk_party is null", [cellq: hex.q, cellr: hex.r])
    }

    if(pointCount == 0) {
        return false;
    }

    def areaLaeq = hexaRecord.getLAeq()
    def areaL50 = hexaRecord.getLA50()
    double areaLden = 0 // TODO
    // Create area geometry
    def hexaGeom = hex.toWKT(hexSizeRatio)

    // Prepare insert
    def fields = [cell_q           : hex.q,
                  cell_r           : hex.r,
                  tzid : tz.getID() as String,
                  the_geom         : hexaGeom,
                  laeq         : areaLaeq,
                  la50         : areaL50,
                  lden         : areaLden,
                  mean_pleasantness: sumPleasantness / pleasantnessCount,
                  measure_count    : pointCount,
                  first_measure    : firstUtc,
                  last_measure     : lastUtc,
                  pk_party: partyPk]
    def pkArea = sql.executeInsert("INSERT INTO noisecapture_area(cell_q, cell_r, tzid, the_geom, laeq,la50,lden, mean_pleasantness," +
            " measure_count, first_measure, last_measure, pk_party) VALUES (:cell_q, :cell_r, :tzid, " +
            "ST_Transform(ST_GeomFromText(:the_geom,3857),4326) , :laeq, :la50,:lden ," +
            " :mean_pleasantness, :measure_count, :first_measure, :last_measure, :pk_party)", fields)[0][0] as Integer
    // Add profile
    PreparedStatement ps = sql.getConnection().prepareStatement("INSERT INTO NOISECAPTURE_AREA_PROFILE(PK_AREA, HOUR, LAEQ, LA50) VALUES (?, ?, ?, ?)");
    records.each{ k, v ->
        ps.setInt(1, pkArea)
        ps.setInt(2, k)
        ps.setDouble(3, v.getLAeq())
        ps.setDouble(4, v.getLA50())
        ps.addBatch()
    }
    ps.executeBatch()
    return true
}

/**
 * Compute and process the list of area that contains new measurements
 * @param connection
 * @param precisionFilter
 * @return
 */
@CompileStatic
def process(Connection connection, float precisionFilter, int trackLimit) {
    Logger logger = LoggerFactory.getLogger("nc_process")
    double hexSize = 15.0
    connection.setAutoCommit(false)
    int processed = 0
    try {
        Set<Hex> areaIndex = new HashSet<>()
        Map<Integer, Set<Hex>> areaNoisePartyIndex = new HashMap<>()
        // Count what to add for each hexagons q,r,level
        int[] hexExponent = [3, 4, 5, 6, 7, 8, 9, 10, 11];
        List<Map<Hex, Integer>> hexagonalClustersDiff = new ArrayList<>()
        for(int i=0; i<hexExponent.length;i++) {
            hexagonalClustersDiff.add(new HashMap<Hex, Integer>());
        }
        // List the area identifier using the new measures coordinates
        def sql = new Sql(connection)
        String expand = ""
        if(trackLimit > 0) {
            expand = " LIMIT " + trackLimit
        }
        Set<Integer> processedPkTrack = new HashSet<>()
        logger.info("Fetch pk_track to process..")
        sql.eachRow("select pk_track from noisecapture_process_queue order by pk_track "+expand) { row ->
            processedPkTrack.add(row.getInt('pk_track'))
        }
        // generate hexagons altered by new tracks points
        logger.info("generate hexagons altered by new tracks points")
        for(int pk_track : processedPkTrack) {
            sql.eachRow("SELECT ST_X(ST_Transform(ST_SetSRID(p.the_geom, 4326), 3857)) PTX," +
                    "ST_Y(ST_Transform(ST_SetSRID(p.the_geom, 4326), 3857)) PTY, pk_party FROM" +
                    " noisecapture_point p, noisecapture_track t  WHERE :pktrack = p.pk_track and " +
                    "t.pk_track = p.pk_track and p.accuracy < :precision and NOT ST_ISEMPTY(p.the_geom)",
                    [precision: precisionFilter, pktrack: pk_track]) { row ->
                Hex hex = new Pos(x: row.getDouble('PTX'),
                        y: row.getDouble('PTY')).toHex(hexSize)
                areaIndex.add(hex)
                int pkParty = row.getInt('pk_party')
                if (!row.wasNull()) {
                    if (!areaNoisePartyIndex.containsKey(pkParty)) {
                        areaNoisePartyIndex.put(pkParty, new HashSet<>())
                    }
                    areaNoisePartyIndex[pkParty].add(hex)
                }
                // Populate scaled hexagons for clustering
                for (int i = 0; i < hexExponent.length; i++) {
                    def scaledHex = new Pos(x: row.getDouble('PTX'),
                            y: row.getDouble('PTY')).toHex(hexSize * Math.pow(3, hexExponent[i]))
                    Integer oldValue = hexagonalClustersDiff[i].get(scaledHex)
                    if (oldValue == null) {
                        hexagonalClustersDiff[i].put(scaledHex, 1)
                    } else {
                        hexagonalClustersDiff[i].put(scaledHex, oldValue + 1)
                    }
                }
            }
        }

        // Process areas
        logger.info("Process areas")
        for (Hex hex : areaIndex) {
            if(processArea(hex, precisionFilter, sql, null)) {
                processed++
                // Accept changes
                connection.commit();
            }
        }

        // Process party areas
        logger.info("Process party areas")
        areaNoisePartyIndex.each { partyPk, hexSet ->
            hexSet.each { hex ->
                if (processArea(hex, precisionFilter, sql, partyPk)) {
                    processed++
                    // Accept changes
                    connection.commit();
                }
            }
        }

        // Feed hexagonal clusters (scaled hexagons)
        logger.info("Feed hexagonal clusters (scaled hexagons)")

        for(int i=0; i<hexExponent.length;i++) {
            hexagonalClustersDiff[i].entrySet().each { Map.Entry<Hex, Integer> entry ->
                def res = sql.firstRow("SELECT MEASURE_COUNT FROM NOISECAPTURE_AREA_CLUSTER WHERE CELL_LEVEL = :cell_level AND CELL_Q = :cell_q AND CELL_R = :cell_r",
                        [cell_level : hexExponent[i], cell_q : entry.key.q, cell_r : entry.key.r])
                if(res != null) {
                    // Already an hexagon in the database
                    sql.executeUpdate("UPDATE NOISECAPTURE_AREA_CLUSTER SET MEASURE_COUNT = :measure_count WHERE CELL_LEVEL = :cell_level AND CELL_Q = :cell_q AND CELL_R = :cell_r", [cell_level : hexExponent[i], cell_q : entry.key.q, cell_r : entry.key.r, measure_count : (res.measure_count as Integer) + entry.getValue()])
                } else {
                    // New hexagon
                    def scaledHex = new Hex(q:entry.key.q, r:entry.key.r, size:hexSize * Math.pow(3, hexExponent[i]))
                    sql.executeInsert("INSERT INTO NOISECAPTURE_AREA_CLUSTER(CELL_LEVEL, CELL_Q, CELL_R,THE_GEOM, MEASURE_COUNT) VALUES (:cell_level, :cell_q, :cell_r,ST_Transform(ST_GeomFromText(:the_geom,3857),4326), :measure_count) ", [cell_level : hexExponent[i], cell_q : entry.key.q, cell_r : entry.key.r,the_geom : scaledHex.toWKT(1.0f), measure_count : entry.getValue()])
                }
            }
        }

        PreparedStatement ps = sql.getConnection().prepareStatement("DELETE FROM noisecapture_process_queue where pk_track = ?");

        logger.info("Clear processed tracks queue")
        // Clear queue
        for( Integer pkTrack : processedPkTrack) {
            ps.setInt(1, pkTrack)
            ps.addBatch()
        }
        ps.executeBatch()

        // Accept changes
        connection.commit();
    } catch (SQLException ex) {
        // Log error
        logger.error("nc_process Message: " + ex.getMessage(), ex);

        if(ex instanceof SQLException) {
            logger.error("SQLState: " +
                    ex.getSQLState());

            logger.error("Error Code: " +
                    ex.getErrorCode());

            Throwable t = ex.getCause();
            while (t != null) {
                logger.error("Cause: " + t);
                t = t.getCause();
            }
        }
        connection.rollback()
    } catch (IllegalArgumentException ex) {
        // Log error
        logger.error("nc_process Message: " + ex.getMessage(), ex);
    }
    return processed
}

def Connection openPostgreSQLDataStoreConnection() {
    Store store = new GeoServer().catalog.getStore("postgis")
    JDBCDataStore jdbcDataStore = (JDBCDataStore)store.getDataStoreInfo().getDataStore(null)
    return jdbcDataStore.getDataSource().getConnection()
}

def run(input) {
    // Open PostgreSQL connection
    Connection connection = openPostgreSQLDataStoreConnection()
    try {
        return [result : process(connection, input["locationPrecisionFilter"], input["processTracksLimit"] as Integer)]
    } finally {
        connection.close()
    }
}

@CompileStatic
class Pos {
    double x
    double y
    Hex toHex(double size) {
        def q = (x * Math.sqrt(3.0d)/3.0 - y / 3.0) / size;
        def r = y * 2.0/3.0 / size;
        return new Hex(q:q, r:r, size:size).round();
    }

    def Coordinate toCoordinate() {
        return new Coordinate(x, y)
    }
}

@CompileStatic
class Hex {
    double q
    double r
    double size

    boolean equals(o) {
        if (this.is(o)) {
            return true
        }
        if (getClass() != o.class) {
            return false
        }

        Hex hex = (Hex) o

        if (Double.compare(hex.q, q) != 0) {
            return false
        }
        if (Double.compare(hex.r, r) != 0) {
            return false
        }
        if (Double.compare(hex.size, size) != 0) {
            return false
        }

        return true
    }

    String toWKT(double hexSizeRatio = 1.0) {
        def center = toMeter()
        def hexaGeom = new StringBuilder()
        for (int i = 0; i < 6; i++) {
            if (hexaGeom.length() != 0) {
                hexaGeom.append(", ")
            } else {
                hexaGeom.append("POLYGON((")
            }
            Pos vertex = hex_corner(center, i, hexSizeRatio)
            hexaGeom.append(vertex.x)
            hexaGeom.append(" ")
            hexaGeom.append(vertex.y)
        }
        hexaGeom.append(", ")
        Pos vertex = hex_corner(center, 0, hexSizeRatio)
        hexaGeom.append(vertex.x)
        hexaGeom.append(" ")
        hexaGeom.append(vertex.y)
        hexaGeom.append("))")
        return hexaGeom
    }

    int hashCode() {
        int result
        long temp
        temp = q != +0.0d ? Double.doubleToLongBits(q) : 0L
        result = (int) (temp ^ (temp >>> 32))
        temp = r != +0.0d ? Double.doubleToLongBits(r) : 0L
        result = 31 * result + (int) (temp ^ (temp >>> 32))
        temp = size != +0.0d ? Double.doubleToLongBits(size) : 0L
        result = 31 * result + (int) (temp ^ (temp >>> 32))
        return result
    }

    static final List<Hex> directions = [
            new Hex(q:+1,  r:0), new Hex(q:+1, r:-1), new Hex(q:0, r:-1),
            new Hex(q:-1,  r:0), new Hex(q:-1, r:+1),new Hex(q:0, r:+1)
    ]

    def neighbor(Hex hex, int direction) {
        def dir = directions[direction]
        return new Hex(q:hex.q + dir.q, r:hex.r + dir.r, size:size)
    }

    /**
     * @return Local coordinate of hexagon index
     */
    def Pos toMeter() {
        double x = size * Math.sqrt(3.0d) * (q + r/2.0);
        double y = size * 3.0d/2.0d * r;
        return new Pos(x:x, y:y);
    }
    /**
     * @param i Vertex [0-5]
     * @return Vertex coordinate
     */
    Pos hex_corner(Pos center, int i, double ratio = 1.0) {
        double angle_deg = 60.0 * i   + 30.0d;
        double angle_rad = Math.PI / 180.0 * angle_deg;
        return new Pos(x:center.x + (size * ratio) * Math.cos(angle_rad), y:center.y + (size * ratio) * Math.sin(angle_rad));
    }

    /**
     * @return Integral hex index
     */
    Hex round() {
        return toCube().round().toHex();
    }

    /**
     * @return Cube instance
     */
    Cube toCube() {
        return new Cube(x:q, y:-q-r, z:r, size:size);
    }
}

@CompileStatic
class Cube {
    double x
    double y
    double z
    double size

    Hex toHex() {
        return new Hex(q:x, r: z, size:size);
    }

    Cube round() {
        def rx = Math.round(x);
        def ry = Math.round(y);
        def rz = Math.round(z);

        def x_diff = Math.abs(rx - x);
        def y_diff = Math.abs(ry - y);
        def z_diff = Math.abs(rz - z);

        if (x_diff > y_diff && x_diff > z_diff) {
            rx = -ry - rz
        } else {
            if (y_diff > z_diff) {
                ry = -rx - rz
            } else {
                rz = -rx - ry
            }
        }

        return new Cube(x:rx, y:ry, z:rz, size:size);
    }
}
