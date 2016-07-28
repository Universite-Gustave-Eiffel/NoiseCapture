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

import com.vividsolutions.jts.geom.Coordinate
import groovy.sql.Sql
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp


title = 'nc_process'
description = 'Recompute cells that contains new measures'

inputs = [
        locationPrecisionFilter: [name: 'locationPrecisionFilter', title: 'Ignore measurements with location precision greater than specified distance',
                           type: Float.class] ]

outputs = [
        result: [name: 'result', title: 'Processed cells', type: Integer.class]
]

/**
 * Fetch all measurements within a range and compute local stats over this area
 * @param hex
 * @param range
 * @param sql
 */
def processArea(Hex hex, float range, Sql sql) {
    def Pos center = hex.toMeter()
    def Coordinate centerCoord = center.toCoordinate();
    def geom = "POINT( " + center.x + " " + center.y + ")"
    float sumLeq = 0;
    int pointCount = 0;
    float sumPleasantness = 0
    int pleasantnessCount = 0
    long firstUtc = 0;
    long lastUtc = 0;
    sql.eachRow("SELECT ST_X(ST_Transform(p.the_geom, 3857)) PTX,ST_Y(ST_Transform(p.the_geom, 3857)) PTY, p.noise_level," +
            " t.pleasantness,EXTRACT(MILLISECOND FROM time_date) measureTime FROM noisecapture_point p, NOISECAPTURE_TRACK t WHERE " +
            "ST_TRANSFORM(ST_ENVELOPE(ST_BUFFER(ST_GeomFromText(:geom,3857),:range)),4326) && the_geom ORDER BY time_date", [geom: geom.toString(), range: range])
            { row ->
                Pos pos = new Pos(x: row.PTX, y: row.PTY)
                if (pos.toCoordinate().distance(centerCoord) < range) {
                    if(firstUtc == 0) {
                        firstUtc = row.measureTime
                    }
                    lastUtc = row.measureTime
                    if (row.pleasantness) {
                        pleasantnessCount++;
                        sumPleasantness += row.pleasantness;
                    }
                    pointCount++
                    sumLeq += Math.pow(10.0, row.noise_level / 10.0)
                }
            }
    // Delete old cell
    sql.execute("DELETE FROM noisecapture_area a WHERE a.cell_q = :cellq and a.cell_r = :cellr", [cellq:hex.q, cellr:hex.r])

    if(pointCount == 0) {
        return false;
    }
    // Insert updated cell

    // Create area geometry
    def hexaGeom = new StringBuilder()
    for(int i=0; i<6 ; i++) {
        if(hexaGeom.length() != 0) {
            hexaGeom.append(", ")
        } else {
            hexaGeom.append("POLYGON((")
        }
        Pos vertex = hex.hex_corner(center, i)
        hexaGeom.append(vertex.x)
        hexaGeom.append(" ")
        hexaGeom.append(vertex.y)
    }
    hexaGeom.append(", ")
    Pos vertex = hex.hex_corner(center, 0)
    hexaGeom.append(vertex.x)
    hexaGeom.append(" ")
    hexaGeom.append(vertex.y)
    hexaGeom.append("))")

    // Prepare insert
    def fields = [cell_q           : hex.q,
                  cell_r           : hex.r,
                  the_geom         : hexaGeom.toString(),
                  mean_leq         : 10 * Math.log10(sumLeq / pointCount),
                  mean_pleasantness: sumPleasantness / pleasantnessCount,
                  measure_count    : pointCount,
                  first_measure    : new Timestamp(firstUtc),
                  last_measure     : new Timestamp(lastUtc)]
    sql.executeInsert("INSERT INTO noisecapture_area(cell_q, cell_r, the_geom, mean_leq, mean_pleasantness," +
            " measure_count, first_measure, last_measure) VALUES (:cell_q, :cell_r, " +
            "ST_Transform(ST_GeomFromText(:the_geom,3857),4326) , :mean_leq," +
            " :mean_pleasantness, :measure_count, :first_measure, :last_measure)", fields)
    return true
}

/**
 * Compute and process the list of area that contains new measurements
 * @param connection
 * @param precisionFilter
 * @return
 */

def process(Connection connection, float precisionFilter) {
    float hexSize = 25.0
    float hexRange = 25.0
    connection.setAutoCommit(false)
    int processed = 0
    try {
        // List the area identifier using the new measures coordinates
        def sql = new Sql(connection)
        Set<Hex> areaIndex = new HashSet()
        sql.eachRow("SELECT ST_X(ST_Transform(p.the_geom, 3857)) PTX,ST_Y(ST_Transform(p.the_geom, 3857)) PTY FROM" +
                " noisecapture_process_queue q, noisecapture_point p " +
                "WHERE q.pk_track = p.pk_track and p.accuracy > :precision and NOT ST_ISEMPTY(p.the_geom)",
                [precision: precisionFilter]) { row ->
            def hex = new Pos(x:row.PTX, y:row.PTY).toHex(hexSize)
            areaIndex.add(hex)
            //TODO use hexRange by navigating from hex to neighbors
        }

        // Process areas
        for (Hex hex : areaIndex) {
            if(processArea(hex, hexRange, sql)) {
                processed++
            }
        }

        // Clear queue (since the beginning of transaction no new items has been added in queue)
        sql.execute("DELETE FROM noisecapture_process_queue")

        // Accept changes
        connection.commit();
    } catch (SQLException ex) {
        connection.rollback();
        throw ex
    }
    return processed
}


def run(input) {
    // Open PostgreSQL connection
    Connection connection = nc_parse.openPostgreSQLDataStoreConnection()
    try {
        return [result : process(connection, (float)input)]
    } finally {
        connection.close()
    }
}

class Pos {
    double x
    double y
    def toHex(float size) {
        def q = (x * Math.sqrt(3.0)/3.0 - y / 3.0) / size;
        def r = y * 2.0/3.0 / size;
        return new Hex(q:q, r:r, size:size).round();
    }

    def Coordinate toCoordinate() {
        return new Coordinate(x, y)
    }
}

class Hex {
    float q
    float r
    float size

    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        Hex hex = (Hex) o

        if (Float.compare(hex.q, q) != 0) return false
        if (Float.compare(hex.r, r) != 0) return false
        if (Float.compare(hex.size, size) != 0) return false

        return true
    }

    int hashCode() {
        int result
        result = (q != +0.0f ? Float.floatToIntBits(q) : 0)
        result = 31 * result + (r != +0.0f ? Float.floatToIntBits(r) : 0)
        result = 31 * result + (size != +0.0f ? Float.floatToIntBits(size) : 0)
        return result
    }
    static final def directions = [
    new Hex(q:+1,  r:0), new Hex(q:+1, r:-1), new Hex(q:0, r:-1),
    new Hex(q:-1,  r:0), new Hex(q:-1, r:+1),new Hex(q:0, r:+1)
    ]

    def neighbor(hex, int direction) {
        def dir = directions[direction]
        return new Hex(q:hex.q + dir.q, r:hex.r + dir.r, size:size)
    }

    /**
     * @return Local coordinate of hexagon index
     */
    def Pos toMeter() {
            def x = size * Math.sqrt(3.0) * (q + r/2.0);
            def y = size * 3.0/2.0 * r;
            return new Pos(x:x, y:y);
    }
    /**
     * @param i Vertex [0-5]
     * @return Vertex coordinate
     */
    def hex_corner(Pos center, int i) {
        def angle_deg = 60.0 * i   + 30.0;
        def angle_rad = Math.PI / 180.0 * angle_deg;
        return new Pos(x:center.x + size * Math.cos(angle_rad), y:center.y + size * Math.sin(angle_rad));
    }

    /**
     * @return Integral hex index
     */
    def round() {
        return toCube().round().toHex();
    }

    /**
     * @return Cube instance
     */
    def toCube() {
        return new Cube(x:q, y:-q-r, z:r, size:size);
    }
}

class Cube {
    float x
    float y
    float z
    float size

    def toHex() {
        return new Hex(q:x, r: z, size:size);
    }

    def round() {
        def rx = Math.round(x);
        def ry = Math.round(y);
        def rz = Math.round(z);

        def x_diff = Math.abs(rx - x);
        def y_diff = Math.abs(ry - y);
        def z_diff = Math.abs(rz - z);

        if (x_diff > y_diff && x_diff > z_diff)
            rx = -ry-rz
        else if (y_diff > z_diff)
            ry = -rx-rz
        else
            rz = -rx-ry

        return new Cube(x:rx, y:ry, z:rz, size:size);
    }
}
