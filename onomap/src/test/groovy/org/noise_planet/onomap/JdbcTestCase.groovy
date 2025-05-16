package org.noise_planet.onomap

import groovy.test.GroovyTestCase
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
    st.execute(new File(TestNoiseCaptureHisto.class.getResource("inith2.sql").getFile()).text)
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
