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

title = 'nc_raw_measurements'
description = 'List measurements raw files filtered by noisecapture party'

inputs = [noiseparty: [name: 'noiseparty', title: 'NoiseParty id',
                       type: Integer.class, min : 0, max : 1],
          datefilter: [name: 'datefilter', title: 'Display records since',
                       type: String.class, min : 0, max : 1]]


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
    def url = "http://data.noise-planet.org/raw/"+record_row.user_uuid.substring(0,2)+"/"+
            record_row.user_uuid.substring(2,4)+"/"+record_row.user_uuid.substring(4,6)+"/"+record_row.user_uuid+"/track_"+record_row.track_uuid+".zip"
    return [time_length : record_row.time_length as Integer, record_utc : record_row.record_utc, data : url, pk_party: record_row.pk_party]
}

def getStats(Connection connection, Integer noise_party_id, String dateFilter) {
    def data = []
    try {
        // List the 10 last measurements, with aggregation of points
        def sql = new Sql(connection)
        String dateFilterQuery = ""
        def params = [:]
        if(dateFilter != null) {
            dateFilterQuery = " AND record_utc > :datefilter::timestamp"
            params.put("datefilter", dateFilter)
        }
        if(noise_party_id == null) {
            sql.eachRow("select nt.*, nu.user_uuid from noisecapture_track nt, noisecapture_user nu where pk_party is null and nt.pk_user = nu.pk_user "+dateFilterQuery+" order by record_utc desc LIMIT 10000", params) {
                record_row -> data.add(processRow(sql, record_row))
            }
        } else {
            params.put("noise_party_id", noise_party_id)
            sql.eachRow("select nt.*, nu.user_uuid from noisecapture_track nt, noisecapture_user nu where nt.pk_user = nu.pk_user and pk_party = :noise_party_id "+dateFilterQuery+" order by record_utc desc LIMIT 10000", params) {
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
        return [result : JsonOutput.toJson(getStats(connection, input["noiseparty"] as Integer, input["datefilter"] as String))]
    } finally {
        connection.close()
    }
}
