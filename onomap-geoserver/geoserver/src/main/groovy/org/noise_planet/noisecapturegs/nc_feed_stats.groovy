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
import groovy.sql.Sql
import org.geotools.jdbc.JDBCDataStore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder

import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException;


title = 'nc_feed_stats'
description = 'Parse external CSV file to feed the statistics database'

inputs = [
        dataType: [name: 'dataType', title: 'one of time_matrix_mu, time_matrix_sigma, stations', type: String.class],
        dataUrl: [name: 'dataUrl', title: 'CSV url', type: String.class],
]

outputs = [
        result: [name: 'result', title: 'entry inserted',  type: Integer.class]
]

def static Connection openPostgreSQLDataStoreConnection() {
    Store store = new GeoServer().catalog.getStore("postgis")
    JDBCDataStore jdbcDataStore = (JDBCDataStore)store.getDataStoreInfo().getDataStore(null)
    return jdbcDataStore.getDataSource().getConnection()
}

/**
 * Check if TimeMatrix table exists, if not create it
 * @param connection
 */
def checkTimeMatrixExists(Connection connection) {
    def sql = new Sql(connection)
    def create = true
    def res = sql.firstRow("select count(*) cpt from INFORMATION_SCHEMA.TABLES WHERE LOWER(table_name) = 'delta_sigma_time_matrix'")
    if(res["cpt"] == 1) {
        res = sql.firstRow("SELECT COUNT(*) cpt FROM delta_sigma_time_matrix")
        if (res["cpt"] != 72 * 72) {
            sql.execute("DROP TABLE delta_sigma_time_matrix")
        } else {
            create = false
        }
    }
    if(create) {
        sql.execute("CREATE TABLE delta_sigma_time_matrix " +
                "( hour_ref integer, hour_target integer, delta_db real, delta_sigma real," +
                " CONSTRAINT delta_sigma_time_matrix_area_pk PRIMARY KEY (hour_ref, hour_target))")
        sql.withBatch("INSERT INTO delta_sigma_time_matrix VALUES (:hour_ref, :hour_target, 0, 0)") { batch ->
            for(int hour_ref = 0; hour_ref < 72; hour_ref++) {
                for(int hour_target = 0; hour_target < 72; hour_target++) {
                    batch.addBatch([hour_ref: hour_ref, hour_target: hour_target])
                }
            }
            batch.executeBatch()
        }
    }
}

def processInput(Connection connection, URI csvPath, String dataType) {
    Logger logger = LoggerFactory.getLogger(nc_feed_stats.class)
    def sql = new Sql(connection)
    def processed = 0
    if(dataType.startsWith("time_matrix_")) {
        checkTimeMatrixExists(connection)
        String csvContent = csvPath.toURL().getText();
        def lines = csvContent.split("\n")
        int refHour = 0
        def update
        if(dataType.endsWith("mu")) {
            update = "delta_db = :var"
        } else if(dataType.endsWith("sigma")){
            update = "delta_sigma = :var"
        } else {
            throw new IllegalArgumentException("Expected time_matrix one of sigma or mu")
        }
        sql.withBatch("UPDATE delta_sigma_time_matrix SET "+update+" WHERE hour_ref=:hour_ref AND hour_target=:hour_target") { batch ->
            for(String line : lines) {
                int targetHour = 0
                for(String row : line.split(",")) {
                    batch.addBatch([var: Double.valueOf(row), hour_ref: refHour as Integer, hour_target: targetHour as Integer])
                    processed++
                    targetHour++
                }
                refHour++
            }
            batch.executeBatch()
        }
    } else if("stations" == dataType) {
        String csvContent = csvPath.toURL().getText();
        def lines = csvContent.split("\n")
        sql.execute("DROP TABLE IF EXISTS stations_ref")
        sql.execute("CREATE TABLE stations_ref(id_station integer, hour integer, std_dev real, mu real, sigma real, " +
                "CONSTRAINT stations_ref_id_station_pk PRIMARY KEY (id_station, hour))");
        def refHour = 0;
        sql.withBatch("INSERT INTO stations_ref(id_station, hour, std_dev, mu,sigma) VALUES (:id_station, :hour, :std_dev, :mu, :sigma)") { batch ->
            try {
                for (String line : lines) {
                    def cols = line.split(",")
                    def stationCount = cols.length / 3
                    for (int stationId = 0; stationId < stationCount; stationId++) {
                        def sigma = cols[stationId * 3]
                        def stdDev = cols[stationId * 3 + 1]
                        def mu = cols[stationId * 3 + 2]
                        batch.addBatch([id_station: stationId as Integer, hour: refHour as Integer, std_dev: stdDev as Double, mu: mu as Double, sigma: sigma as Double])
                        processed++
                    }
                    refHour++
                }
                batch.executeBatch()
            } catch (Exception ex) {
                logger.error(ex.getLocalizedMessage(), ex)
            }
        }
    } else {
        throw new IllegalArgumentException("Expected dataType one of time_matrix or stations")
    }
    return processed;
}

def Map run(input) {
    // Sensible WPS process, add a layer of security by checking for authenticated user
    def user = SecurityContextHolder.getContext().getAuthentication();
    if(!user.isAuthenticated()) {
        throw new IllegalStateException("This WPS process require authentication")
    }
    Connection connection = openPostgreSQLDataStoreConnection()
    try {
        return [result : processInput(connection, URI.create((String)input["dataUrl"]), (String)input["dataType"])]
    } finally {
        connection.close()
    }
}
