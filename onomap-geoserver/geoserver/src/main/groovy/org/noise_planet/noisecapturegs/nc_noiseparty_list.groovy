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

title = 'nc_noiseparty_list'
description = 'Fetch all Noise Party'

inputs = [:]


outputs = [
        result: [name: 'result', title: 'NoiseParty list as JSON', type: String.class]
]

def getNoiseParty(Connection connection) {
    def data = []
    try {
        def sql = new Sql(connection)
        sql.eachRow("select pk_party, title, tag, description, ST_AsGeoJSON(the_geom) the_geom, layer_name from noisecapture_party order by pk_party desc") {
            record_row ->
                def the_geom = new JsonSlurper().parseText(record_row.the_geom as String)
                data.add([pk_party:record_row.pk_party, title : record_row.title as String, tag : record_row.tag as String,
                          description : record_row.description as String, geometry : the_geom, layer_name : record_row.layer_name])
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
        return [result : JsonOutput.toJson(getNoiseParty(connection))]
    } finally {
        connection.close()
    }
}
