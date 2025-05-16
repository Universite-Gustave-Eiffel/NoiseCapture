package org.noise_planet.onomap

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.h2gis.utilities.JDBCUtilities
import org.postgresql.ds.PGSimpleDataSource
import kotlin.use
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.first
import org.jetbrains.kotlinx.dataframe.io.readResultSet
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.log

val ONOMAP_LAST_DATABASE_VERSION = 1

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
    fun checkDataBaseState(vertx: Vertx, ds: HikariDataSource?) {
      ds?.connection?.use { connection ->
        val hasUserTable = JDBCUtilities.tableExists(connection, "noisecapture_user")
        val hasVersionTable = JDBCUtilities.tableExists(connection, "noisecapture_db_version")
        if (!hasVersionTable) {
          connection.autoCommit=false
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
            connection.commit()
            connection.autoCommit = true
          } catch (ex: Exception) {
            log.error("Error while init database", ex)
            connection.rollback()
            return
          }
        }
        var dbVersion = 0
        connection.createStatement().use {
          statement -> statement.executeQuery("select db_version from noisecapture_db_version").use { rs ->
            dbVersion = DataFrame.readResultSet (rs, connection).first()["db_version"] as Int
          }
        }
      }
    }
  }
}
