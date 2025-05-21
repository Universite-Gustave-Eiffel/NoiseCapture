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
package org.noise_planet.onomap.database

import com.fasterxml.jackson.core.JsonEncoding
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.h2gis.api.EmptyProgressVisitor
import org.h2gis.api.ProgressVisitor
import org.h2gis.functions.io.geojson.GeoJsonReaderDriver
import org.h2gis.utilities.JDBCUtilities
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.first
import org.jetbrains.kotlinx.dataframe.io.readResultSet
import org.noise_planet.onomap.utilities.DisplayProgressVisitor
import org.noise_planet.onomap.utilities.downloadFile
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.sql.Connection
import java.util.*
import kotlin.io.path.pathString

const val ONOMAP_LAST_DATABASE_VERSION = 1
const val DEFAULT_GADM_URI = "https://github.com/nicolas-f/gadm/releases/download/4.1/gadm410.geojson.gz"

class DataBaseManagement {
  companion object {
    val log: Logger = LoggerFactory.getLogger(DataBaseManagement::class.java)
    fun initDataBaseConfiguration(configuration: JsonObject?): HikariDataSource {
      val config = HikariConfig()
      config.username = configuration?.getString("POSTGRES_USER", "onomap") ?: "onomap"
      config.password = configuration?.getString("POSTGRES_PASSWORD", "onomap") ?: "onomap"
      config.maximumPoolSize = configuration?.getInteger("POSTGRES_MAXPOOL_SIZE", 20) ?: 20
      config.dataSourceClassName = PGSimpleDataSource::class.qualifiedName
      config.addDataSourceProperty(
        "portNumbers",
        configuration?.getInteger("PGPORT", 5432) ?: 5432
      )
      config.addDataSourceProperty(
        "databaseName",
        configuration?.getString("PGDBNAME", "noisecapture") ?: "noisecapture"
      )
      config.addDataSourceProperty(
        "serverNames",
        configuration?.getString("PGHOST", "localhost") ?: "localhost"
      )
      return HikariDataSource(config)
    }


    /**
     * Check the content of the database
     * Upgrade if necessary
     */
    fun checkDataBaseState(vertx: Vertx, ds: HikariDataSource?, configuration: JsonObject?) {
      ds?.connection?.use { connection ->
        val hasUserTable = JDBCUtilities.tableExists(connection, "noisecapture_user")
        val hasVersionTable = JDBCUtilities.tableExists(connection, "noisecapture_db_version")
        if (!hasVersionTable) {
          connection.autoCommit = false
          try {
            connection.createStatement().use { statement ->
              statement.execute(
                "CREATE TABLE NOISECAPTURE_DB_VERSION (\n" +
                  "  DB_VERSION int NOT NULL\n" +
                  ")"
              )
              statement.execute("INSERT INTO NOISECAPTURE_DB_VERSION VALUES ($ONOMAP_LAST_DATABASE_VERSION)")
              if (hasUserTable) {
                // version 0 database (geoserver db)
                statement.execute("ALTER TABLE NOISECAPTURE_AREA_PROFILE ALTER COLUMN HOUR RENAME TO LOCAL_HOUR")
              } else {
                // empty db
                val fs = vertx.fileSystem()
                connection.createStatement().use { statement ->
                  statement.execute(
                    fs.readFileBlocking("org/noise_planet/onomap/init_db_common.sql").toString("UTF-8")
                  )
                  statement.execute(
                    fs.readFileBlocking("org/noise_planet/onomap/initdb_postgres.sql").toString("UTF-8")
                  )
                }
              }
            }
            connection.autoCommit = true
          } catch (ex: Exception) {
            log.error("Error while init database", ex)
            connection.rollback()
            return
          }
        }
        // Upgrade database version
        var dbVersion = 0
        connection.createStatement().use { statement ->
          statement.executeQuery("select db_version from noisecapture_db_version").use { rs ->
            dbVersion = DataFrame.readResultSet(rs, connection).first()["db_version"] as Int
          }
        }
      }

      // Check special data tables
      if (configuration?.getBoolean("download_data_tables", true) ?: true) {
        val hasGadmTable = ds?.connection?.use(fun(connection: Connection): Boolean {
          return JDBCUtilities.tableExists(connection, "gadm28")
        })
        if (hasGadmTable == false) {
          vertx.fileSystem().createTempDirectory("gadm").onComplete { res ->
            val tempDirectory = res.result()
            val gadmFile = Path.of(tempDirectory, "gadm.geojson.gz")
            val url = URI(configuration?.getString("GADM_URI", DEFAULT_GADM_URI) ?: DEFAULT_GADM_URI).toURL()
            log.info("Table GADM28 is not in database download the file from $url to ${gadmFile.pathString}..")
            url.downloadFile(gadmFile.toFile(), DisplayProgressVisitor(1, true, 1.0))
            log.info("File download parse the data and transfer it in the database")
            ds.connection?.use { connection ->
              val readerDriver = GeoJsonReaderDriver(
                connection, gadmFile.toFile(),
                JsonEncoding.UTF8.name, true
              )
              readerDriver.read(DisplayProgressVisitor(1, true, 1.0), "gadm28")
              connection.createStatement().execute("SELECT UPDATEGEOMETRYSRID('gadm28', 'the_geom', 4326)")
            }
          }
        }
      }
    }
  }
}
