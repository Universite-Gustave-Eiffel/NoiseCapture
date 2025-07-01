
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
import org.h2gis.functions.factory.H2GISFunctions
import org.h2gis.utilities.JDBCUtilities
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.osgi.service.jdbc.DataSourceFactory

import javax.sql.DataSource
import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement

@CompileStatic
class JdbcTestCase {
  Connection connection

  static DataSource createDataSource(String user, String password, boolean debug) throws SQLException {
    // Create H2 memory DataSource
    Driver driver = Driver.load();
    OsgiDataSourceFactory dataSourceFactory = new OsgiDataSourceFactory(driver);
    Properties properties = new Properties();
    String databasePath = "jdbc:h2:mem:junit"
    properties.setProperty(DataSourceFactory.JDBC_URL, databasePath)
    properties.setProperty(DataSourceFactory.JDBC_USER, user)
    properties.setProperty(DataSourceFactory.JDBC_PASSWORD, password)
    if (debug) {
      properties.setProperty("TRACE_LEVEL_FILE", "3") // enable debug
    }
    return dataSourceFactory.createDataSource(properties)
  }

  void initDb() {
    Statement st = connection.createStatement()
    // Init schema
    st.execute("CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ AS TIMESTAMP")
    st.execute(new File(TestNoiseCaptureHisto.class.getResource("init_db_common.sql").getFile()).text)
    st.execute(new File(TestNoiseCaptureHisto.class.getResource("initdb_h2.sql").getFile()).text)
  }

  void installGadmAndTimeZone() {
    Statement st = connection.createStatement()
    // Load timezone file
    st.execute("CALL SHPREAD('"+TestNoiseCaptureProcess.getResource("tz_world.shp").file+"', 'TZ_WORLD');")
    st.execute("CREATE SPATIAL INDEX ON TZ_WORLD(THE_GEOM)")
    // ut_deps has been derived from https://www.data.gouv.fr/fr/datasets/contours-des-departements-francais-issus-d-openstreetmap/ (c) osm
    // See ut_deps.txt for more details
    st.execute("CALL GEOJSONREAD('"+TestNoiseCaptureProcess.getResource("ut_deps.geojson").file+"', 'GADM28');")
    st.execute("CALL UPDATEGEOMETRYSRID('GADM28', 'THE_GEOM', 4326)")
    st.execute("CREATE SPATIAL INDEX ON GADM28(THE_GEOM)")
  }

  @BeforeEach
  void initConnection() {
    DataSource dataSource = createDataSource("sa", "sa", false)
    connection = JDBCUtilities.wrapConnection(dataSource.getConnection())
    H2GISFunctions.load(connection)
  }

  @AfterEach
  void closeConnection() throws SQLException {
    connection.close()
  }
}
