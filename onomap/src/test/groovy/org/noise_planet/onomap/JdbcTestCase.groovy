package org.noise_planet.onomap

import groovy.test.GroovyTestCase
import org.h2.Driver
import org.h2.util.OsgiDataSourceFactory
import org.h2gis.functions.factory.H2GISFunctions
import org.h2gis.utilities.JDBCUtilities
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.osgi.service.jdbc.DataSourceFactory

import javax.sql.DataSource
import java.sql.Connection
import java.sql.SQLException

@Ignore
class JdbcTestCase extends GroovyTestCase {
  Connection connection
  File dbFile = new File(new File("build/tmp"), UUID.randomUUID().toString().replace("-", "") + ".mv.db")

  static DataSource createDataSource(String user, String password, String dbDirectory, String dbName, boolean debug) throws SQLException {
    // Create H2 memory DataSource
    Driver driver = Driver.load();
    OsgiDataSourceFactory dataSourceFactory = new OsgiDataSourceFactory(driver);
    Properties properties = new Properties();
    String databasePath = "jdbc:h2:" + new File(dbDirectory, dbName).getAbsolutePath();
    properties.setProperty(DataSourceFactory.JDBC_URL, databasePath);
    properties.setProperty(DataSourceFactory.JDBC_USER, user);
    properties.setProperty(DataSourceFactory.JDBC_PASSWORD, password);
    if (debug) {
      properties.setProperty("TRACE_LEVEL_FILE", "3"); // enable debug
    }
    DataSource dataSource = dataSourceFactory.createDataSource(properties);
    // Init spatial ext
    try (Connection connection = dataSource.getConnection()) {
      H2GISFunctions.load(connection);
    }
    return dataSource
  }

  @Before
  void setUp() {
    DataSource dataSource = createDataSource("sa", "sa", dbFile.getParent(), dbFile.getName().replace(".mv.db", ""), false)
    connection = JDBCUtilities.wrapConnection(dataSource.getConnection())
  }

  @After
  void tearDown() throws SQLException {
    connection.close()
    dbFile.delete()
  }
}
