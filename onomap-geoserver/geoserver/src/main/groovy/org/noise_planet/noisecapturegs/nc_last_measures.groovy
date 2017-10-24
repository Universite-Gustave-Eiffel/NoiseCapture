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
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.sql.Sql
import org.geotools.jdbc.JDBCDataStore

import java.sql.Connection
import java.sql.SQLException
import java.time.format.DateTimeFormatter

title = 'nc_last_measures'
description = 'Fetch last measures'

inputs = [noiseparty: [name: 'noiseparty', title: 'NoiseParty id',
                       type: Integer.class, min : 0, max : 1]]


outputs = [
        result: [name: 'result', title: 'Last measures as JSON', type: String.class]
]

/**
 * @param latlong WKT point
 * @return Extracted lat long
 */
def decodeLatLongFromString(latlong) {
    def wktPattern = ~/^POINT\s?\((-?\d+(\.\d+)?)\s*(-?\d+(\.\d+)?)\)$/
    def wktMatch = latlong =~ wktPattern
    if(wktMatch) {
        return [wktMatch.group(1) as Double, wktMatch.group(3) as Double]
    }
    return null
}

def processRow(sql, record_row) {
    // Fetch the timezone of this point
    def res = sql.firstRow("SELECT TZID FROM tz_world WHERE " +
            "ST_GeomFromText(:geom,4326) && the_geom AND" +
            " ST_Intersects(ST_GeomFromText(:geom,4326), the_geom) LIMIT 1", [geom: record_row.env])
    TimeZone tz = res == null ? TimeZone.default : TimeZone.getTimeZone(res.TZID);
    def formater = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    def record_utc;
    if(res) {
        record_utc = record_row.record_utc.toInstant().atZone(tz.toZoneId()).format(formater);
    } else {
        record_utc = record_row.record_utc
    }
    def center = decodeLatLongFromString(record_row.env)
    def longitude = center != null ? center[0] : null
    def latitude = center != null ? center[1] : null
    def the_geom = new JsonSlurper().parseText(record_row.the_geom)
    def start = new JsonSlurper().parseText(record_row.start_pt)
    def stop = new JsonSlurper().parseText(record_row.stop_pt)
    return [time_length : record_row.time_length as Integer, record_utc : record_utc,
              zoom_level : 18, lat : latitude, long : longitude, bounds : the_geom, start : start,
              stop : stop, country : record_row.name_0, name_1 : record_row.name_1, name_3 : record_row.name_3];
}

def getStats(Connection connection, Integer noise_party_id) {
    def data = []
    try {
        // List the 10 last measurements, with aggregation of points
        def sql = new Sql(connection)
        if(noise_party_id == null) {
            sql.eachRow("select T.* from NOISECAPTURE_STATS_LAST_TRACKS T where pk_party is null order by record_utc desc") {
                record_row -> data.add(processRow(sql, record_row))
            }
        } else {
            sql.eachRow("select T.* from NOISECAPTURE_STATS_LAST_TRACKS T where pk_party = :noise_party_id order by record_utc desc", ["noise_party_id" : noise_party_id]) {
                record_row -> data.add(processRow(sql, record_row))
            }
        }
    } catch (SQLException ex) {
        throw ex
    }
    return data;
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
        return [result : JsonOutput.toJson(getStats(connection, input["noiseparty"] as Integer))]
    } finally {
        connection.close()
    }
}
