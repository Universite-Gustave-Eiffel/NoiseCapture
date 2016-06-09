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

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

title = 'nc_parse'
description = 'Parse uploaded zip files'

inputs = [
]

outputs = [
        result: [name: 'result', title: 'Processed files',  type: Integer.class]
]

class ZipFileFilter implements FilenameFilter {
    @Override
    boolean accept(File dir, String name) {
        return name.toLowerCase().endsWith(".zip")
    }
}

def processFile(Connection connection, File zipFile) {
    try {
        connection.setAutoCommit(false)
        zipFile.withInputStream { is ->
            ZipInputStream zipInputStream = new ZipInputStream(is)
            ZipEntry zipEntry
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if ("track.geojson".equals(zipEntry.getName())) {

                } else if ("metadata.properties".equals(zipEntry.getName())) {

                } else if ("readme.txt".equals(zipEntry.getName())) {
                    // pass
                } else {
                    // Weird file, ignore whole zip file
                    return;
                }
            }
        }
        // Accept changes
        connection.commit();
    } catch (SQLException ex) {
        connection.rollback();
    } finally {
        // Move file to processed folder
        File processedDir = new File("data_dir/onomap_archive");
        if(!processedDir.exists()) {
            processedDir.mkdirs()
        }
        zipFile.renameTo(new File(processedDir, zipFile.getName()))
    }
}
def Connection openPostgreSQLConnection() {
    // Init driver
    Class.forName("org.postgresql.Driver");
    // Read login/pass/dbname
    Properties properties = new Properties();
    File configFile = new File("onomapdb.properties")
    if(configFile.exists()) {
        properties.load(new FileReader(configFile))
        Connection connection = null;
        return DriverManager.getConnection(
                "jdbc:postgresql://localhost:"+properties.getProperty("port")+"/"+properties.getProperty("dbname"),
                properties.getProperty("username"), properties.getProperty("password"));
    } else {
        return null
    }
}
def run(input) {
    File dataDir = new File("data_dir/onomap_uploading");
    if(dataDir.exists()) {
        File[] files = dataDir.listFiles(new ZipFileFilter())
        if (files.length != 0) {
            // Open PostgreSQL connection
            Connection connection = openPostgreSQLConnection()
            try {
                for (File zipFile : files) {
                    processFile(connection, zipFile)
                }
            } finally {
                connection.close()
            }
        }
    }
    return 0;
}