/*
 *  This file is part of the NoiseCapture application and OnoMap system.
 *
 *  The 'OnoMaP' system is led by Lab-STICC and Univ Eiffel - UMRAE and generates noise maps via
 *  citizen-contributed noise data.
 *
 *  This application is co-funded by the ENERGIC-OD Project (European Network for
 *  Redistributing Geospatial Information to user Communities - Open Data). ENERGIC-OD
 *  (http://www.energic-od.eu/) is partially funded under the ICT Policy Support Programme (ICT
 *  PSP) as part of the Competitiveness and Innovation Framework Programme by the European
 *  Community. The application work is also supported by the French geographic portal GEOPAL of the
 *  Pays de la Loire region (http://www.geopal.org).
 *
 *  Copyright (C) Univ Eiffel - UMRAE and Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
 *
 *  NoiseCapture is a free software; you can redistribute it and/or modify it under the terms of the
 *  GNU General Public License as published by the Free Software Foundation; either version 3 of
 *  the License, or(at your option) any later version. NoiseCapture is distributed in the hope that
 *  it will be useful,but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 *  more details.You should have received a copy of the GNU General Public License along with this
 *  program; if not, write to the Free Software Foundation,Inc., 51 Franklin Street, Fifth Floor,
 *  Boston, MA 02110-1301  USA or see For more information,  write to Université Gustave Eiffel,
 *  14-20 Boulevard Newton Cite Descartes, Champs sur Marne F-77447 Marne la Vallee Cedex 2 FRANCE
 *   or write to scientific.computing@univ-eiffel.fr
 */
package org.noise_planet.onomap.database

import com.fasterxml.jackson.core.JsonEncoding
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.h2gis.functions.io.geojson.GeoJsonReaderDriver
import org.h2gis.functions.io.shp.SHPDriverFunction
import org.h2gis.utilities.JDBCUtilities
import org.h2gis.utilities.dbtypes.DBUtils
import org.noise_planet.onomap.utilities.DisplayProgressVisitor
import org.noise_planet.onomap.utilities.downloadFile
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.sql.Connection
import javax.sql.DataSource
import kotlin.io.path.pathString

const val ONOMAP_LAST_DATABASE_VERSION = 1
const val DEFAULT_GADM_URI = "https://github.com/nicolas-f/gadm/releases/download/4.1/gadm410.geojson.gz"
const val DEFAULT_TIMEZONE_URI = "https://github.com/nicolas-f/gadm/releases/download/4.1/timezones-with-oceans.geojson.gz"

private const val FILE_DOWNLOADED_MESSAGE = "File downloaded parse the data and transfer it in the database"

/**
 * This class provides methods to initialize, check, and manage the database for the NoiseCapture application.
 * It includes functions to check if a data table exists in the database, download the file from a given URL,
 * and parse it into the database.
 * The class also checks the content of the database and upgrades it if necessary.
 */
class DataBaseManagement {
  companion object {
    val log: Logger = LoggerFactory.getLogger(DataBaseManagement::class.java)
    fun initDataBaseConfiguration(configuration: JsonObject?): HikariDataSource {
      val config = HikariConfig()
      val pgHostConfigurationDefined : Boolean = configuration?.getString("PGHOST", "localhost")?.isEmpty() ?: false
      if(pgHostConfigurationDefined) {
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
      } else {
        log.warn("PGHOST is not configured, fallback to H2 database")
        val workingDirectory : String = configuration?.getString("workingDir") ?: File("").absolutePath
      }
      return HikariDataSource(config)
    }

    /**
     * Checks if a data table exists in the database. If it doesn't exist,
     * downloads the file from the given URL and parses it into the database.
     *
     * @param vertx The Vertx instance used for file operations.
     * @param ds The data source used to connect to the database.
     * @param dataTable The name of the data table to check.
     * @param url The URL from which to download the data table file.
     */
    fun checkDataTable(vertx: Vertx, ds: DataSource?, dataTable: String, url : URL) {
      val hasDataTable = ds?.connection?.use(fun(connection: Connection): Boolean {
        return JDBCUtilities.tableExists(connection, dataTable)
      })
      if (hasDataTable == false) {
        GlobalScope.launch(vertx.dispatcher()) {
          vertx.fileSystem().createTempDirectory("gadm").onComplete { res ->
            val tempDirectory = res.result()
            val fileName = url.path.substringAfterLast('/')
            val fileExtension = fileName.substringAfterLast('.')
            val dataFile = Path.of(tempDirectory, fileName)
            log.info("Table $dataTable is not in database download the file from $url to ${dataFile.pathString}..")
            url.downloadFile(dataFile.toFile(), DisplayProgressVisitor(1, true, 1.0, logger = log))
            // download additional files for specific formats
            if(fileExtension.equals("shp",true)) {
              val otherExt = arrayOf("dbf", "prj", "shx")
              otherExt.forEach { ext->
                val otherFile = Path.of(url.path.substringBeforeLast('/'),
                  fileName.substringBeforeLast('.') + ".$ext")
                val otherDataFile = Path.of(tempDirectory,
                  otherFile.pathString.substringAfterLast('/'))
                otherFile.toUri().toURL().downloadFile(otherDataFile.toFile(),
                  DisplayProgressVisitor(1,
                    true, 1.0, logger = log))
              }
            }
            log.info(FILE_DOWNLOADED_MESSAGE)
            ds.connection?.use { connection ->
              if(fileExtension.equals("shp",true)) {
                SHPDriverFunction().importFile(connection, dataTable, dataFile.toFile(),
                  DisplayProgressVisitor(1, true, 1.0, logger = log))
              } else if(fileExtension.equals("geojson",true) ||
                fileExtension.equals("geojson.gz",true)) {
                val readerDriver = GeoJsonReaderDriver(
                  connection, dataFile.toFile(),
                  JsonEncoding.UTF8.name, true
                )
                readerDriver.read(DisplayProgressVisitor(1, true,
                  1.0, logger = log), dataTable)
                connection.createStatement().execute("SELECT UPDATEGEOMETRYSRID('$dataTable', 'the_geom', 4326)")
              }
              val isDataTableCreated = JDBCUtilities.tableExists(connection, dataTable)
              if(!isDataTableCreated) {
                log.warn("Table $dataTable is not present in the database")
              }
            }
          }
        }
      }
    }

    /**
     * Checks the content of the database and upgrades it if necessary.
     *
     * @param vertx The Vertx instance used for file operations.
     * @param ds The data source used to connect to the database.
     * @param configuration The configuration object containing the database connection details and options.
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun checkDataBaseState(vertx: Vertx, ds: HikariDataSource?, configuration: JsonObject?) {
      ds?.connection?.use { connection ->
        val dbType = DBUtils.getDBType(connection)
        val hasUserTable = JDBCUtilities.tableExists(connection, "noisecapture_user")
        val hasVersionTable = JDBCUtilities.tableExists(connection, "noisecapture_db_version")
        if (!hasVersionTable) {
          connection.autoCommit = false
          try {
            connection.createStatement().use { statement ->
              statement.execute(
                "CREATE TABLE IF NOT EXISTS NOISECAPTURE_DB_VERSION (\n" +
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
            rs.next()
            dbVersion = rs.getInt("db_version")
          }
        }
      }

      // Check special data tables
      if (configuration?.getBoolean("download_data_tables", true) ?: true) {
        checkDataTable(vertx, ds, "gadm28",
          URI(configuration?.getString("GADM_URI",
            DEFAULT_GADM_URI) ?: DEFAULT_GADM_URI).toURL())
        checkDataTable(vertx, ds, "tz_world",
          URI(configuration?.getString("TIMEZONE_URI",
            DEFAULT_TIMEZONE_URI) ?: DEFAULT_TIMEZONE_URI).toURL())
      }
    }
  }
}
