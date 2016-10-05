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
import geoserver.GeoServer
import geoserver.catalog.Store
import groovy.json.JsonOutput
import groovy.sql.Sql
import org.geotools.jdbc.JDBCDataStore

import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.format.DateTimeFormatter


title = 'nc_get_area_info'
description = 'Fetch informations about an area given its index'

inputs = [
        qIndex: [name: 'qIndex', title: 'Area Q index',
                 type: Long.class],
        rIndex: [name: 'rIndex', title: 'Area R index',
                 type: Long.class]]

outputs = [
        result: [name: 'result', title: 'Area info as JSON', type: String.class]
]

def getAreaInfo(Connection connection, long qIndex, long rIndex) {
    def data = []
    try {
        // List the area identifier using the new measures coordinates
        def sql = new Sql(connection)
        def row = sql.firstRow("SELECT * FROM noisecapture_area a " +
                "WHERE CELL_Q = :qIndex and CELL_R = :rIndex",
                [qIndex: qIndex, rIndex: rIndex])
        if(row) {
            def time_zone = TimeZone.getTimeZone(row.tzid as String).toZoneId();
            def formater = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            def firstMeasure = row.first_measure.toInstant().atZone(time_zone).format(formater);
            def lastMeasure = row.last_measure.toInstant().atZone(time_zone).format(formater);
            data = [leq              : row.mean_leq,
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
        return [result : JsonOutput.toJson(getAreaInfo(connection, input["qIndex"],input["rIndex"]))]
    } finally {
        connection.close()
    }
}
