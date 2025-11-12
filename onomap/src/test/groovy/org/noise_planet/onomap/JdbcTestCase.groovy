
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

package org.noise_planet.onomap


import groovy.transform.CompileStatic
import org.h2.Driver
import org.h2.util.OsgiDataSourceFactory
import org.h2.value.Value
import org.h2.value.ValueVarchar
import org.h2gis.functions.factory.H2GISFunctions
import org.h2gis.functions.io.geojson.GeoJsonRead
import org.h2gis.functions.io.shp.SHPRead
import org.h2gis.postgis_jts.ConnectionWrapper
import org.h2gis.utilities.JDBCUtilities
import org.h2gis.utilities.dbtypes.DBTypes
import org.h2gis.utilities.dbtypes.DBUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.osgi.service.jdbc.DataSourceFactory
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.h2gis.postgis_jts.DataSourceWrapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.postgresql.ds.PGSimpleDataSource

import javax.sql.DataSource
import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement

@CompileStatic
class JdbcTestCase {
  DataSource dataSource
  Connection connection
  boolean isH2GISDatabase = false

  static Logger LOG = LoggerFactory.getLogger(this.class.name)

  static DataSource createDataSource(String user, String password, boolean debug) throws SQLException {
    HikariConfig config = new HikariConfig()
    boolean pgHostConfigurationDefined = System.getenv ("POSTGRES_HOST") ?: false

    if(pgHostConfigurationDefined) {
      config.username = System.getenv("POSTGRES_USER") ?: "onomap"
      config.password = System.getenv("POSTGRES_PASSWORD") ?: "onomap"
      config.dataSourceClassName = PGSimpleDataSource.getCanonicalName()
      config.addDataSourceProperty("portNumbers",
        System.getenv("POSTGRES_PORT") as Integer ?: 5432)
      config.addDataSourceProperty("databaseName",
        System.getenv("POSTGRES_DB") ?: "noisecapture")
      config.addDataSourceProperty("serverNames",
        System.getenv("POSTGRES_HOST") ?: "localhost")
      return new HikariDataSource(config)
    } else {
      // Create H2 memory DataSource
      Driver driver = Driver.load();
      OsgiDataSourceFactory dataSourceFactory = new OsgiDataSourceFactory(driver);
      Properties properties = new Properties();
      String databasePath = "jdbc:h2:mem:junit"+System.currentTimeMillis()
      LOG.warn("POSTGRES_HOST is not configured, fallback to H2GIS database: \n${databasePath}")
      properties.setProperty(DataSourceFactory.JDBC_URL, databasePath)
      properties.setProperty(DataSourceFactory.JDBC_USER, user)
      properties.setProperty(DataSourceFactory.JDBC_PASSWORD, password)
      if (debug) {
        properties.setProperty("TRACE_LEVEL_FILE", "3") // enable debug
      }
      return dataSourceFactory.createDataSource(properties)
    }
  }

  void initDb() {
    Statement st = connection.createStatement()
    // Init schema
    if(isH2GISDatabase) {
      st.execute("CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ AS TIMESTAMP")
    }
    st.execute(new File(TestNoiseCaptureHisto.class.getResource("init_db_common.sql").getFile()).text)
    if(isH2GISDatabase) {
      st.execute(new File(TestNoiseCaptureHisto.class.getResource("initdb_h2.sql").getFile()).text)
    } else {
      st.execute(new File(TestNoiseCaptureHisto.class.getResource("initdb_postgres.sql").getFile()).text)
    }
  }

  void installGadmAndTimeZone() {
    // Load timezone file
    if(!JDBCUtilities.tableExists(connection, "TZ_WORLD")) {
      SHPRead.importTable(connection, TestNoiseCaptureProcess.getResource("tz_world.shp").file, ValueVarchar.get("TZ_WORLD"))
      JDBCUtilities.createSpatialIndex(connection, "TZ_WORLD", "the_geom")
    }
    // ut_deps has been derived from https://www.data.gouv.fr/fr/datasets/contours-des-departements-francais-issus-d-openstreetmap/ (c) osm
    // See ut_deps.txt for more details
    if(!JDBCUtilities.tableExists(connection, "GADM28")) {
      GeoJsonRead.importTable(connection, TestNoiseCaptureProcess.getResource("ut_deps.geojson").file, ValueVarchar.get("GADM28"))
      JDBCUtilities.createSpatialIndex(connection, "GADM28", "the_geom")
    }
  }

  @BeforeEach
  void initConnection() {
    dataSource = createDataSource("sa", "sa", false)
    isH2GISDatabase = !(dataSource instanceof HikariDataSource)
    if(isH2GISDatabase) {
      connection = JDBCUtilities.wrapConnection(dataSource.getConnection())
      H2GISFunctions.load(connection)
    } else {
      connection = new ConnectionWrapper(dataSource.getConnection())
    }
  }

  @AfterEach
  void closeConnection() throws SQLException {
    connection.close()
    try {
      // close connection pool, we are supposed to have a single connection pool
      HikariDataSource hds = dataSource.unwrap(HikariDataSource.class)
      hds.close()
    } catch (SQLException e) {
      // ignore
    }
  }
}
