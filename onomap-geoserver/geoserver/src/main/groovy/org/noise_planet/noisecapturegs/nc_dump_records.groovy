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

import com.vividsolutions.jts.geom.Coordinate
import geoserver.GeoServer
import geoserver.catalog.Store
import groovy.json.JsonOutput
import groovy.sql.Sql
import org.geotools.jdbc.JDBCDataStore

import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.format.DateTimeFormatter


title = 'nc_dump_records'
description = 'Extract raw data from the provided bouding box'

inputs = [
        minLat: [name: 'minLat', title: 'Minimum latitude',
                 type: Double.class],
        maxLat: [name: 'maxLat', title: 'Maximum latitude',
                 type: Double.class],
        minLong: [name: 'minLong', title: 'Minimum longitude',
                 type: Double.class],
        maxLong: [name: 'maxLong', title: 'Maximum longitude',
                 type: Double.class]]

outputs = [
        result: [name: 'result', title: 'Raw records as multiple geojson in GZIP file encoded in base 64', type: String.class]
]

MAX_AREA = 50e6 // max area in m^2 of bounding box for extraction of data

def getDump(Connection connection, double minLat, double maxLat, double minLong, double maxLong) {
    def targetStream = new ByteArrayOutputStream()
    def zipStream = new GZIPOutputStream(targetStream)
    try {
        // List the area identifier using the new measures coordinates
        def sql = new Sql(connection)

        // Compute aproximate area of the user specified zone
        def row = sql.firstRow("SELECT ST_AREA(ST_EXTENT(ST_UNION(ST_TRANSFORM(ST_SETSRID(ST_MAKEPOINT(:minLong, :minLat),4326), 3857) ,ST_TRANSFORM(ST_SETSRID(ST_MAKEPOINT(:maxLong, :maxLat), 4326), 3857)))) area", [minLat: minLat, maxLat: maxLat,
                                                            minLong:minLong, maxLong:maxLong])
        if(!row) {
            return data;
        }
        if(row.area > MAX_AREA) {
            return JsonOutput.toJson([error : "Extraction maximum area is ${maxarea} m² and you request ${row.area} m²"])
        } else {
            last_pk_track = -1
            last
            sql.eachRow("SELECT np.pk_track, track_uuid, gain_calibration, pleasantness, ST_X(the_geom) long, ST_Y(the_geom) lat,accuracy, time_date FROM noisecapture_track nt, noisecapture_point np " +
                    "WHERE nt.pk_track = np.pk_track AND ST_AREA(ST_EXTENT(ST_UNION(ST_SETSRID(ST_MAKEPOINT(:minLong, :minLat),4326) ,ST_SETSRID(ST_MAKEPOINT(:maxLong, :maxLat), 4326)))) && the_geom AND ST_INTERSECTS(the_geom,ST_AREA(ST_EXTENT(ST_UNION(ST_SETSRID(ST_MAKEPOINT(:minLong, :minLat),4326) ,ST_SETSRID(ST_MAKEPOINT(:maxLong, :maxLat), 4326)))))  ORDER BY np.pk_track ",
                    [minLat : minLat, maxLat: maxLat,
                     minLong: minLong, maxLong: maxLong]) {
                track_row ->
                    def data = []
                    
            }
        }
            if(row) {
                data = [laeq              : row.laeq,
                        la50              : row.la50,
                        lden              : row.lden,
                        mean_pleasantness: row.mean_pleasantness instanceof Number &&
                                !row.mean_pleasantness.isNaN() ? row.mean_pleasantness : null,
                        first_measure    : firstMeasure,
                        last_measure     : lastMeasure,
                        measure_count    : row.measure_count,
                        time_zone        : row.tzid]
                // Query hours profile for this area
                def profile = [:]
                sql.eachRow("SELECT * FROM NOISECAPTURE_AREA_PROFILE WHERE PK_AREA = :pk_area", [pk_area:row.pk_area]) {
                    hour_row ->
                        profile[hour_row.hour as Integer] = [leq : hour_row.leq as Double, uncertainty : hour_row.uncertainty as Integer]
                }
                data["profile"] = profile
            }
        }

    } catch (SQLException ex) {
        throw ex
    }
    zipStream.close()
    def zippedBytes = targetStream.toByteArray()
    targetStream.close()
    return zippedBytes.encodeBase64()
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
        return [result : getDump(connection, input["minLat"],input["maxLat"],input["minLong"],input["maxLong"])]
    } finally {
        connection.close()
    }
}
