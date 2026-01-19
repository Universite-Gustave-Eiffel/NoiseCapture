package org.noise_planet.onomap

import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.OpenOptions
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.junit5.VertxTestContext.ExecutionBlock
import io.vertx.kotlin.core.json.get
import org.h2gis.utilities.JDBCUtilities
import org.h2gis.utilities.TableLocation
import org.h2gis.utilities.dbtypes.DBUtils
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.hasEntry
import org.hamcrest.Matchers.hasItem
import org.hamcrest.core.IsEqual.equalTo
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.noise_planet.onomap.sensitive.nc_parse
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.lang.Thread.sleep
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.zip.ZipInputStream
import javax.sql.DataSource
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.exists


@ExtendWith(VertxExtension::class)
class TestMainVerticle {

  companion object {
    lateinit var app: MainVerticle
    lateinit var webClient: WebClient
    var serverPort: Int = 0
    lateinit var workingDir: File

    @JvmStatic
    @BeforeAll
    fun setup(vertx: Vertx, testContext: VertxTestContext, @TempDir tempFolder: Path) {
      workingDir = tempFolder.toFile()
      System.setProperty("workingDir", workingDir.absolutePath)
      System.setProperty("POSTGRES_MAXPOOL_SIZE", "1") // do not generate 20 connections in each parallel unit test

      // Setup Resources
      val resourceGadm = TestMainVerticle::class.java.getResource("ut_deps.geojson")
      if (resourceGadm != null) System.setProperty("GADM_URI", resourceGadm.toURI().toString())
      val resourceTimeZone = TestMainVerticle::class.java.getResource("tz_world.shp")
      if (resourceTimeZone != null) System.setProperty("TIMEZONE_URI", resourceTimeZone.toURI().toString())

      app = MainVerticle()
      webClient = WebClient.create(vertx)

      // Deploy once
      vertx.deployVerticle(app).onComplete(testContext.succeeding {
        serverPort = app.getPort()
        testContext.completeNow()
      })
    }
  }

  @BeforeEach
  fun resetDatabase() {
    // Clean DB before every test so they remain independent
    prepareDbForUnitTest(app.ds)
  }

  fun generateWpsGetAreaInfo(): Buffer {
    return Buffer.buffer(
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <wps:Execute version="1.0.0" service="WPS"
      	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      	xmlns="http://www.opengis.net/wps/1.0.0"
      	xmlns:wfs="http://www.opengis.net/wfs"
      	xmlns:wps="http://www.opengis.net/wps/1.0.0"
      	xmlns:ows="http://www.opengis.net/ows/1.1"
      	xmlns:gml="http://www.opengis.net/gml"
      	xmlns:ogc="http://www.opengis.net/ogc"
      	xmlns:wcs="http://www.opengis.net/wcs/1.1.1"
      	xmlns:xlink="http://www.w3.org/1999/xlink"
         xsi:schemaLocation="http://www.opengis.net/wps/1.0.0
         http://schemas.opengis.net/wps/1.0.0/wpsAll.xsd">
      	<ows:Identifier>groovy:nc_get_area_info</ows:Identifier>
      	<wps:DataInputs>
      		<wps:Input>
      			<ows:Identifier>rIndex</ows:Identifier>
      			<wps:Data>
      				<wps:LiteralData>299791</wps:LiteralData>
      			</wps:Data>
      		</wps:Input>
      		<wps:Input>
      			<ows:Identifier>qIndex</ows:Identifier>
      			<wps:Data>
      				<wps:LiteralData>-167652</wps:LiteralData>
      			</wps:Data>
      		</wps:Input>
      		<wps:Input>
      			<ows:Identifier>noiseparty</ows:Identifier>
      			<wps:Data>
      				<wps:LiteralData>null</wps:LiteralData>
      			</wps:Data>
      		</wps:Input>
      	</wps:DataInputs>
      	<wps:ResponseForm>
      		<wps:RawDataOutput>
      			<ows:Identifier>result</ows:Identifier>
      		</wps:RawDataOutput>
      	</wps:ResponseForm>
      </wps:Execute>
    """.trimIndent()
    )
  }

  fun generateWPSUpload(dataB64: String): Buffer {
    return Buffer.buffer(
      """<?xml version="1.0" encoding="UTF-8"?>
    <wps:Execute version="1.0.0" service="WPS" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
     xmlns="http://www.opengis.net/wps/1.0.0" xmlns:wfs="http://www.opengis.net/wfs"
      xmlns:wps="http://www.opengis.net/wps/1.0.0" xmlns:ows="http://www.opengis.net/ows/1.1"
       xmlns:gml="http://www.opengis.net/gml" xmlns:ogc="http://www.opengis.net/ogc"
        xmlns:wcs="http://www.opengis.net/wcs/1.1.1" xmlns:xlink="http://www.w3.org/1999/xlink"
         xsi:schemaLocation="http://www.opengis.net/wps/1.0.0 http://schemas.opengis.net/wps/1.0.0/wpsAll.xsd">
      <ows:Identifier>groovy:nc_upload</ows:Identifier>
      <wps:DataInputs>
        <wps:Input>
          <ows:Identifier>encode64ZIP</ows:Identifier>
          <wps:Data>
            <wps:LiteralData>$dataB64</wps:LiteralData>
          </wps:Data>
        </wps:Input>
        <wps:Input>
          <ows:Identifier>triggerWpsEvent</ows:Identifier>
          <wps:Data>
            <wps:LiteralData>false</wps:LiteralData>
          </wps:Data>
        </wps:Input>
      </wps:DataInputs>
      <wps:ResponseForm>
        <wps:RawDataOutput>
          <ows:Identifier>result</ows:Identifier>
        </wps:RawDataOutput>
      </wps:ResponseForm>
    </wps:Execute>
    """
    )
  }

  fun generateWPSLastMeasures(): Buffer {
    return Buffer.buffer(
      """<?xml version="1.0" encoding="UTF-8"?>
<wps:Execute version="1.0.0"      service="WPS"
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xmlns="http://www.opengis.net/wps/1.0.0"
	xmlns:wfs="http://www.opengis.net/wfs"
	xmlns:wps="http://www.opengis.net/wps/1.0.0"
	xmlns:ows="http://www.opengis.net/ows/1.1"
	xmlns:gml="http://www.opengis.net/gml"
	xmlns:ogc="http://www.opengis.net/ogc"
	xmlns:wcs="http://www.opengis.net/wcs/1.1.1"
	xmlns:xlink="http://www.w3.org/1999/xlink"
  xsi:schemaLocation="http://www.opengis.net/wps/1.0.0 http://schemas.opengis.net/wps/1.0.0/wpsAll.xsd">
	<ows:Identifier>groovy:nc_last_measures</ows:Identifier>
	<wps:DataInputs>
		<wps:Input>
			<ows:Identifier>noiseparty</ows:Identifier>
			<wps:Data>
				<wps:LiteralData></wps:LiteralData>
			</wps:Data>
		</wps:Input>
	</wps:DataInputs>
	<wps:ResponseForm>
		<wps:RawDataOutput>
			<ows:Identifier>result</ows:Identifier>
		</wps:RawDataOutput>
	</wps:ResponseForm>
</wps:Execute>"""
    )
  }

  fun generateWPSDumpArea(envelope: String, fromEpoch: Long, toEpoch: Long): Buffer {
    return Buffer.buffer(
      """<?xml version="1.0" encoding="UTF-8"?><wps:Execute version="1.0.0"
     service="WPS" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
     xmlns="http://www.opengis.net/wps/1.0.0" xmlns:wfs="http://www.opengis.net/wfs"
     xmlns:wps="http://www.opengis.net/wps/1.0.0" xmlns:ows="http://www.opengis.net/ows/1.1"
     xmlns:gml="http://www.opengis.net/gml" xmlns:ogc="http://www.opengis.net/ogc"
      xmlns:wcs="http://www.opengis.net/wcs/1.1.1" xmlns:xlink="http://www.w3.org/1999/xlink"
      xsi:schemaLocation="http://www.opengis.net/wps/1.0.0 http://schemas.opengis.net/wps/1.0.0/wpsAll.xsd">
      <ows:Identifier>groovy:nc_dump_area</ows:Identifier>
      <wps:DataInputs>
        <wps:Input>
          <ows:Identifier>envelope</ows:Identifier>
          <wps:Data>
            <wps:LiteralData>$envelope</wps:LiteralData>
          </wps:Data>
        </wps:Input>
        <wps:Input>
          <ows:Identifier>exportTracks</ows:Identifier>
          <wps:Data>
            <wps:LiteralData>on</wps:LiteralData>
          </wps:Data>
        </wps:Input>
        <wps:Input>
          <ows:Identifier>exportMeasures</ows:Identifier>
          <wps:Data>
            <wps:LiteralData>on</wps:LiteralData>
          </wps:Data>
        </wps:Input>
        <wps:Input>
          <ows:Identifier>exportAreas</ows:Identifier>
          <wps:Data>
            <wps:LiteralData>on</wps:LiteralData>
          </wps:Data>
        </wps:Input>
        <wps:Input>
          <ows:Identifier>exportRaw</ows:Identifier>
          <wps:Data>
            <wps:LiteralData>on</wps:LiteralData>
          </wps:Data>
        </wps:Input>
        <wps:Input>
          <ows:Identifier>fromEpoch</ows:Identifier>
          <wps:Data>
            <wps:LiteralData>$fromEpoch</wps:LiteralData>
          </wps:Data>
        </wps:Input>
        <wps:Input>
          <ows:Identifier>toEpoch</ows:Identifier>
          <wps:Data>
            <wps:LiteralData>$toEpoch</wps:LiteralData>
          </wps:Data>
        </wps:Input>
        <wps:Input>
          <ows:Identifier>emailNotification</ows:Identifier>
          <wps:Data>
            <wps:LiteralData>ffyy@ff.fr</wps:LiteralData>
          </wps:Data>
        </wps:Input>
      </wps:DataInputs>
  <wps:ResponseForm>
    <wps:RawDataOutput>
      <ows:Identifier>result</ows:Identifier>
    </wps:RawDataOutput>
  </wps:ResponseForm>
</wps:Execute>"""
    )
  }

  @BeforeEach
  fun initEnv(@TempDir folder: Path) {
    workingDirectory = folder.toFile()
    System.setProperty("workingDir", workingDirectory.absolutePath)
    System.setProperty("POSTGRES_MAXPOOL_SIZE", "1") // do not generate 20 connections in each parallel unit test
    val resourceGadm = TestMainVerticle::class.java.getResource("ut_deps.geojson")
    if (resourceGadm != null) {
      System.setProperty("GADM_URI", resourceGadm.toURI().toString())
    }
    val resourceTimeZone = TestMainVerticle::class.java.getResource("tz_world.shp")
    if (resourceTimeZone != null) {
      System.setProperty("TIMEZONE_URI", resourceTimeZone.toURI().toString())
    }
  }

  var workingDirectory = File("")

  @Test
  fun testNcUpload(vertx: Vertx, testContext: VertxTestContext) {
    val requestCheckpoint = testContext.checkpoint()
    val fs = vertx.fileSystem()
    val filename = "org/noise_planet/onomap/track_upload_test.zip"
    // Open local file to create the WPS query with this file embedded into a xml text element
    fs.open(filename, OpenOptions()).compose { asyncFile ->
      asyncFile.toBase64().onComplete { base64String ->
        // Record file has been fully converted into base 64
        // send a POST query to Vert.X http server
        webClient.post(app.getPort(), "localhost", "/geoserver/wps")
          .sendBuffer(generateWPSUpload(base64String.result()))
          .onComplete(testContext.succeeding { resp ->
            // We got a response from Vert.X http server
            testContext.verify(ExecutionBlock {
              // Check HTTP status code
              assertThat(resp.statusCode(), equalTo(200))
              // Check written file content
              val uuid = resp.bodyAsString()
              val path = Paths.get(workingDirectory.absolutePath, "onomap_uploading", "track_$uuid.zip")
              assert(path.exists())
              val outputb64 = path.toFile().readBytes().base64Encode()
              assertThat(base64String.result(), equalTo(outputb64))
              requestCheckpoint.flag()
              testContext.completeNow()
            })
          }).onSuccess { response -> println("Got HTTP response with status ${response.statusCode()} ") }
      }
    }
  }

  fun prepareDbForUnitTest(ds: DataSource?) {
    ds?.connection?.use { connection ->
      val dbType = DBUtils.getDBType(connection)
      // wait for availability of requested table gadm28 and TZ_WORLD
      val start = System.currentTimeMillis()
      while (!(JDBCUtilities.tableExists(
          connection,
          TableLocation.parse("tz_world", dbType)
        ) && JDBCUtilities.tableExists(
          connection,
          TableLocation.parse("gadm28", dbType)
        ))
        && System.currentTimeMillis() - start < 10000
      ) {
        // Still initializing the database
        sleep(100)
      }
      connection.createStatement().use { statement ->
        with(statement) {
          execute("DELETE FROM noisecapture_area")
          execute("DELETE FROM noisecapture_user") // will cascade suppression of tracks
          execute("DELETE FROM noisecapture_tag")
          execute("DELETE FROM noisecapture_stats_last_tracks")
        }
      }
    }
  }

  @Test
  fun parseWPSTest(vertx: Vertx, testContext: VertxTestContext) {
    val requestCheckpoint = testContext.checkpoint()
    val fs = vertx.fileSystem()
    // Copy example file into the temporary directory as it was uploaded
    val filename = "org/noise_planet/onomap/track_f7ff7498-ddfd-46a3-ab17-36a96c01ba1b.zip"
    // create test dirs
    Paths.get(workingDirectory.absolutePath, "onomap_uploading").createDirectory()
    Paths.get(workingDirectory.absolutePath, "onomap_archives").createDirectory()
    // Copy test data file
    val path =
      Paths.get(workingDirectory.absolutePath, "onomap_uploading", "track_f7ff7498-ddfd-46a3-ab17-36a96c01ba1b.zip")
    path.parent.createDirectories()
    fs.copyBlocking(filename, path.absolutePathString())

    // Open local file to create the WPS query with this file embedded into a xml text element
    testContext.verify {
      assert(path.exists())

      // send a POST query to Vert.X http server
      webClient.get(app.getPort(), "localhost", "/api/parse")
        .send()
        .onComplete(testContext.succeeding { resp ->
          // We got a response from Vert.X http server
          testContext.verify(ExecutionBlock {
            // Check HTTP status code
            assertThat(resp.statusCode(), equalTo(200))
            // Check return result
            assertThat(resp.bodyAsString().toInt(), equalTo(1))
            // Check database content
            app.ds?.connection?.use { connection ->
              connection.createStatement().use { st ->
                st.executeQuery("SELECT COUNT(*) cpt FROM  noisecapture_track").use { rs ->
                  assert(rs.next())
                  assertThat(rs.getInt("cpt"), equalTo(1))
                }
                st.executeQuery("SELECT * FROM noisecapture_track").use { rs ->
                  assert(rs.next())
                  assertThat(rs.getString("device_manufacturer"), equalTo("Logicom"))
                  assertThat(rs.getString("device_product"), equalTo("L-ITE502"))
                  assertThat(rs.getString("device_model"), equalTo("L-ITE 502"))
                  assertThat(rs.getInt("pleasantness"), equalTo(69))
                  assertThat(rs.getDouble("time_length"), closeTo(84.0, 0.01))
                  assertThat(rs.getDouble("noise_level"), closeTo(72.94, 0.01))
                  assertThat(rs.getString("track_uuid"), equalTo("f7ff7498-ddfd-46a3-ab17-36a96c01ba1b"))
                  assertThat(rs.getTimestamp("record_utc"), equalTo(Timestamp(1465474618000)))
                }
              }
            }
            requestCheckpoint.flag()
            testContext.completeNow()
          })
        })
    }
  }


  /*
  regression test
Nov 07, 2025 9:13:29 AM groovy.sql.Sql eachRow
WARNING: Failed to execute: SELECT ST_INTERSECTS(ST_SETSRID(THE_GEOM, 4326), ST_GEOMFROMTEXT(:geom, 4326)) intersects,
 filter_area FROM noisecapture_party WHERE pk_party = :pkparty because: Can't infer the SQL type to use for an instance
  of org.locationtech.jts.geom.Polygon. Use setObject() with an explicit Types value to specify the type to use.
[vert.x-eventloop-thread-1] WARN  2025-11-07 09:13:29 - track_ce0076d0-c253-458b-9d57-b55a875b2f1d.zip Message:
 Can't infer the SQL type to use for an instance of org.locationtech.jts.geom.Polygon. Use setObject()
 with an explicit Types value to specify the type to use.
*/

  @Test
  fun parseWPSTestFence(vertx: Vertx, testContext: VertxTestContext) {
    val webClient: WebClient = WebClient.create(vertx)
    val requestCheckpoint = testContext.checkpoint()
    val fs = vertx.fileSystem()
    // Copy example file into the temporary directory as it was uploaded
    val filename = "org/noise_planet/onomap/track_noiseparty.zip"
    // create test dirs
    Paths.get(workingDirectory.absolutePath, "onomap_uploading").createDirectory()
    Paths.get(workingDirectory.absolutePath, "onomap_archives").createDirectory()
    // Copy test data file
    val path =
      Paths.get(workingDirectory.absolutePath, "onomap_uploading", "track_f7gg7498-ddfd-46a3-ab17-44a96c01ba1b.zip")
    path.parent.createDirectories()
    fs.copyBlocking(filename, path.absolutePathString())

    // Open local file to create the WPS query with this file embedded into a xml text element
    testContext.verify {
      assert(path.exists())

      // send a POST query to Vert.X http server
      webClient.get(app.getPort(), "localhost", "/api/parse")
        .send()
        .onComplete(testContext.succeeding { resp ->
          // We got a response from Vert.X http server
          testContext.verify(ExecutionBlock {
            // Check HTTP status code
            assertThat(resp.statusCode(), equalTo(200))
            // Check return result
            assertThat(resp.bodyAsString().toInt(), equalTo(1))
            // Check database content
            app.ds?.connection?.use { connection ->
              connection.createStatement().use { st ->
                st.executeQuery("SELECT COUNT(*) cpt FROM  noisecapture_track").use { rs ->
                  assert(rs.next())
                  assertThat(rs.getInt("cpt"), equalTo(1))
                }
                st.executeQuery("SELECT * FROM noisecapture_track").use { rs ->
                  assert(rs.next())
                  assertThat(rs.getString("device_manufacturer"), equalTo("Xiaomi"))
                  assertThat(rs.getString("device_product"), equalTo("houji_eea"))
                  assertThat(rs.getString("device_model"), equalTo("23127PN0CG"))
                  assertThat(rs.getInt("pleasantness"), equalTo(100))
                  assertThat(rs.getDouble("time_length"), closeTo(40.0, 0.01))
                  assertThat(rs.getDouble("noise_level"), closeTo(65.26, 0.01))
                  assertThat(rs.getString("track_uuid"), equalTo("f7gg7498-ddfd-46a3-ab17-44a96c01ba1b"))
                  assertThat(rs.getTimestamp("record_utc"), equalTo(Timestamp(1762503985000)))
                }
              }
            }
            requestCheckpoint.flag()
            testContext.completeNow()
          })
        })
    }
  }

  @Test
  fun dumpStatsApiTest(vertx: Vertx, testContext: VertxTestContext) {
    val webClient: WebClient = WebClient.create(vertx)
    val requestCheckpoint = testContext.checkpoint()
    val fs = vertx.fileSystem()
    // Copy example file into the temporary directory as it was uploaded
    val filename = "org/noise_planet/onomap/track_f7ff7498-ddfd-46a3-ab17-36a96c01ba1b.zip"
    // create test dirs
    Paths.get(workingDirectory.absolutePath, "onomap_uploading").createDirectory()
    Paths.get(workingDirectory.absolutePath, "onomap_archives").createDirectory()
    // Copy test data file
    val path =
      Paths.get(workingDirectory.absolutePath, "onomap_uploading", "track_f7ff7498-ddfd-46a3-ab17-36a96c01ba1b.zip")
    path.parent.createDirectories()
    fs.copyBlocking(filename, path.absolutePathString())

    app.ds?.connection?.use { connection ->
      assert(path.exists())
      nc_parse().exec(connection, mapOf("processFileLimit" to 20) as Map<String, *>)
    }
    // Open local file to create the WPS query with this file embedded into a xml text element
    testContext.verify {

      // send a POST query to Vert.X http server
      webClient.get(app.getPort(), "localhost", "/api/dumpStats")
        .send()
        .onComplete(testContext.succeeding { resp ->
          // We got a response from Vert.X http server
          testContext.verify(ExecutionBlock {
            // Check HTTP status code
            assertThat(resp.statusCode(), equalTo(200))
            // Check return result
            val json = Json.decodeValue(resp.body())
            assertInstanceOf<JsonObject>(json)
            assertThat(json.containsKey("week_new_contributors"), equalTo(true))
            assertThat(json["week_new_contributors"], equalTo(1))
            // "countries":{"names":["France"],"total_tracks":[1],"track_length":[84]}
            assertThat(json.containsKey("countries"), equalTo(true))
            val countries: JsonObject = json.get("countries")
            assertInstanceOf<JsonObject>(countries)
            assertInstanceOf<JsonArray>(countries["names"])
            assertThat(countries["names"], equalTo(JsonArray(listOf("France"))))
            requestCheckpoint.flag()
            testContext.completeNow()
          })
        })
    }
  }

  @Test
  fun lastMeasuresWPSTest(vertx: Vertx, testContext: VertxTestContext) {
    val webClient: WebClient = WebClient.create(vertx)
    val requestCheckpoint = testContext.checkpoint()

    app.ds?.connection?.use { connection ->
      connection.createStatement().use { st ->
        st.execute(
          """INSERT INTO public.noisecapture_stats_last_tracks
            (pk_track,time_length,record_utc,the_geom,env,start_pt,stop_pt,name_0,name_1,name_3,pk_party) VALUES
	          (35,84.0,'2016-06-09 12:16:58+00','{"type":"Polygon","coordinates":[[[-1.645776667,47.153316667],
            [-1.645776667,47.154056667],[-1.645363333,47.154056667],[-1.645363333,47.153316667],[-1.645776667,47.153316667]]]}',
            'POINT(-1.64557 47.153686666666665)','{"type":"Point","coordinates":[-1.645776667,47.153316667,52.1]}',
            '{"type":"Point","coordinates":[-1.645363333,47.154056667,56.2]}','France','Pays de la Loire','Loire-Atlantique',
            NULL);""".trimMargin()
        )
      }
    }
    // send a POST query to Vert.X http server
    webClient.post(
      app.getPort(),
      "localhost",
      "/geoserver/wps?REQUEST=Execute&SERVICE=wps&VERSION=1.0.0&IDENTIFIER=groovy%3Anc_last_measures"
    )
      .sendBuffer(generateWPSLastMeasures())
      .onComplete(testContext.succeeding { resp ->
        // We got a response from Vert.X http server
        testContext.verify(ExecutionBlock {
          // Check HTTP status code
          assertThat(resp.statusCode(), equalTo(200))
          // Check return result
          val json = Json.decodeValue(resp.body())
          assertThat(json is JsonArray, equalTo(true))
          if (json is JsonArray) {
            assertThat(json.size(), equalTo(1))
            val firstEntry = json.first()
            assertInstanceOf<JsonObject>(firstEntry)
            assertThat(firstEntry.map, hasEntry("time_length", 84))
            assertThat(firstEntry.map, hasEntry("record_utc", "2016-06-09T14:16:58.000+0200"))
            assertThat(firstEntry.map, hasEntry("zoom_level", 18))
            assertThat(firstEntry.map, hasEntry("lat", 47.153686666666665))
            assertThat(firstEntry.map, hasEntry("long", -1.64557))
            assertThat(firstEntry.map, hasEntry("country", "France"))
            assertThat(firstEntry.map, hasEntry("name_1", "Pays de la Loire"))
            assertThat(firstEntry.map, hasEntry("name_3", "Loire-Atlantique"))
          }
          requestCheckpoint.flag()
          testContext.completeNow()
        })
      })
  }


  @Test
  fun getAreaInfoWPSTest(vertx: Vertx, testContext: VertxTestContext) {
    val webClient: WebClient = WebClient.create(vertx)
    val requestCheckpoint = testContext.checkpoint()

    prepareDbForUnitTest(app.ds)
    // insert test data
    app.ds?.connection?.use { connection ->
      connection.createStatement().use { st ->
        st.execute(TestMainVerticle::class.java.getResource("areainfo_dataset_test.sql")?.readText())
      }
    }
    // send a POST query to Vert.X http server
    webClient.post(app.getPort(), "localhost", "/geoserver/wps?REQUEST=Execute&SERVICE=wps&VERSION=1.0.0")
      .sendBuffer(generateWpsGetAreaInfo())
      .onComplete(testContext.succeeding { resp ->
        // We got a response from Vert.X http server
        testContext.verify(ExecutionBlock {
          // Check HTTP status code
          assertThat(resp.statusCode(), equalTo(200))
          // Check return result
          val firstEntry = Json.decodeValue(resp.body())
          assertInstanceOf<JsonObject>(firstEntry)
          assertThat(firstEntry.map, hasEntry("first_measure", "2025-05-28T11:36:23+01:00"))
          assertThat(firstEntry.map, hasEntry("last_measure", "2025-05-28T11:36:24+01:00"))
          assertThat(firstEntry.map, hasEntry("time_zone", "Europe/London"))
          assertThat(firstEntry.map, hasEntry("laeq", 63.43935001855943))
          assertThat(firstEntry.map, hasEntry("la50", 63.43935001855943))
          requestCheckpoint.flag()
          testContext.completeNow()
        })
      })
  }

  @Test
  fun dumpRecordsTest(vertx: Vertx, testContext: VertxTestContext) {
    val webClient: WebClient = WebClient.create(vertx)
    val requestCheckpoint = testContext.checkpoint()
    val fs = vertx.fileSystem()
    // Copy example file into the temporary directory as it was uploaded
    val filename = "org/noise_planet/onomap/track_a23261b3-b569-4363-95be-e5578d694238.zip"
    // create test dirs
    Paths.get(workingDirectory.absolutePath, "onomap_uploading").createDirectory()
    Paths.get(workingDirectory.absolutePath, "onomap_archives").createDirectory()
    // Copy test data file
    val path =
      Paths.get(workingDirectory.absolutePath, "onomap_uploading", "track_a23261b3-b569-4363-95be-e5578d694238.zip")
    path.parent.createDirectories()
    fs.copyBlocking(filename, path.absolutePathString())

    app.ds?.connection?.use { connection ->
      assert(path.exists())
      nc_parse().exec(connection, mapOf("processFileLimit" to 20) as Map<String, *>)
    }

    val startLatitude = 46.145837
    val startLongitude = -1.158028
    val stopLatitude = 46.152378
    val stopLongitude = -1.150161
    val dateStart = "01/01/2017"
    val dateStop = "12/31/2017"
    val sdf = SimpleDateFormat("dd/MM/yyyy")
    val envelope =
      "Polygon ((${startLongitude} ${startLatitude}, $stopLongitude ${startLatitude}, $stopLongitude ${stopLatitude}, $startLongitude ${stopLatitude}, $startLongitude ${startLatitude}))"
    webClient.post(
      app.getPort(),
      "localhost",
      "/geoserver/wps?REQUEST=Execute&SERVICE=wps&VERSION=1.0.0&IDENTIFIER=groovy%3Anc_dump_area"
    )
      .sendBuffer(generateWPSDumpArea(envelope = envelope, sdf.parse(dateStart).time, sdf.parse(dateStop).time))
      .onComplete(testContext.succeeding { resp ->
        // We got a response from Vert.X http server
        testContext.verify(ExecutionBlock {
          // Check HTTP status code
          assertThat(resp.statusCode(), equalTo(200))
          // Check return result
          val json = Json.decodeValue(resp.body())
          val outputDir = File(workingDirectory, "onomap_area_dump")
          val start = System.currentTimeMillis()
          while (outputDir.listFiles()
              .none { f -> f.name.lowercase().endsWith(".zip") } && System.currentTimeMillis() - start < 10000L
          ) {
            sleep(100)
          }
          val zipList = outputDir.listFiles().filter { f -> f.name.lowercase().endsWith(".zip") }
          assertThat(zipList.size, equalTo(1))
          val zipFile = zipList.first()
          val expectedEntries = HashSet<String>(
            listOf(
              "tracks.geojson",
              "points.geojson",
              "areas.geojson",
              "raw/track_a23261b3-b569-4363-95be-e5578d694238.zip"
            )
          )
          val got = HashSet<String>()
          ZipInputStream(ByteArrayInputStream(zipFile.readBytes())).use { zis ->
            var nextEntry = zis.nextEntry
            while (nextEntry != null) {
              got.add(nextEntry.name)
              nextEntry = zis.nextEntry
            }
            for (expectedEntry in expectedEntries) {
              assertThat(got, hasItem(expectedEntry))
            }
          }
          requestCheckpoint.flag()
          testContext.completeNow()
        })
      })
  }
}

