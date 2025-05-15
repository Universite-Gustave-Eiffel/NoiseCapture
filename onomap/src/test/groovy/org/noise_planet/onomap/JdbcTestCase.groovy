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
