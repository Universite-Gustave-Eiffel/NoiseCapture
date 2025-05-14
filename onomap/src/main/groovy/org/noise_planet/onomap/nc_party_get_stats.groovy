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
import groovy.time.TimeCategory
import org.geotools.jdbc.JDBCDataStore

import java.sql.Connection

title = 'nc_party_gt_stats'
description = 'Build statistics from database for a specific NoiseCapture party'

inputs = [noiseparty: [name: 'noiseparty', title: 'NoiseParty id',
                       type: Integer.class]]

outputs = [
        result: [name: 'result', title: 'Statistics as JSON', type: String.class]
]


static
def getStatistics(Connection connection, Integer pk_party) {

    def sql = new Sql(connection)

    def statistics = [:];

    // Approximate number of contributors
    statistics["total_contributors"] = sql.firstRow("select count(*) user_count from (select pk_user from noisecapture_track where pk_party = :pk_party group by pk_user) track_users", [pk_party : pk_party]).user_count as Integer;
    // Total number of tracks
    statistics["total_tracks"] = sql.firstRow("select count(*) cpt from noisecapture_track where pk_party = :pk_party", [pk_party : pk_party]).cpt as Integer;
    // Duration (JJ:HH:MM:SS)
    statistics["total_tracks_duration"] = sql.firstRow("select sum(time_length) timelen from noisecapture_track where pk_party = :pk_party", [pk_party : pk_party]).timelen as Long;

    // Top contributors
    def topContributors = []
    sql.eachRow("select USER_UUID, SUM(TIME_LENGTH) total_length,COUNT(*) total_records from noisecapture_user u, noisecapture_track t where u.pk_user = t.pk_user and pk_party = :pk_party group by USER_UUID order by total_length desc", [pk_party : pk_party]) {
        record_row ->
            topContributors.add([userid:record_row.USER_UUID as String,
                                 total_records : record_row.total_records as Integer,
                                 total_length : record_row.total_length as Long])
    }
    statistics["contributors"] = topContributors

    return statistics
}

static def Connection openPostgreSQLDataStoreConnection() {
    Store store = new GeoServer().catalog.getStore("postgis")
    JDBCDataStore jdbcDataStore = (JDBCDataStore) store.getDataStoreInfo().getDataStore(null)
    return jdbcDataStore.getDataSource().getConnection()
}

def run(input) {
    // Open PostgreSQL connection
    // Create dump folder
    Connection connection = openPostgreSQLDataStoreConnection()
    try {
        return [result: JsonOutput.toJson(getStatistics(connection, input["noiseparty"] as Integer))]
    } finally {
        connection.close()
    }
}
