package org.noise_planet.onomap

import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.Vertx.vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.OpenOptions
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.codec.BodyCodec
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.junit5.VertxTestContext.ExecutionBlock
import org.h2.value.ValueBoolean
import org.h2gis.functions.io.geojson.GeoJsonRead
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual.equalTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import javax.sql.DataSource
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.exists
import kotlin.use


@ExtendWith(VertxExtension::class)
class TestMainVerticle {

  @BeforeEach
  fun deploy_verticle(vertx: Vertx, testContext: VertxTestContext) {
    vertx.deployVerticle(MainVerticle()).onComplete(testContext.succeeding<String> { _ -> testContext.completeNow() })
  }

  @Test
  fun verticle_deployed(testContext: VertxTestContext) {
    testContext.completeNow()
  }

  fun generateWPSParse() : Buffer {
    return Buffer.buffer("""<?xml version="1.0" encoding="UTF-8"?><wps:Execute version="1.0.0" service="WPS" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://www.opengis.net/wps/1.0.0" xmlns:wfs="http://www.opengis.net/wfs" xmlns:wps="http://www.opengis.net/wps/1.0.0" xmlns:ows="http://www.opengis.net/ows/1.1" xmlns:gml="http://www.opengis.net/gml" xmlns:ogc="http://www.opengis.net/ogc" xmlns:wcs="http://www.opengis.net/wcs/1.1.1" xmlns:xlink="http://www.w3.org/1999/xlink" xsi:schemaLocation="http://www.opengis.net/wps/1.0.0 http://schemas.opengis.net/wps/1.0.0/wpsAll.xsd">
  <ows:Identifier>groovy:nc_parse</ows:Identifier>
  <wps:DataInputs>
    <wps:Input>
      <ows:Identifier>processFileLimit</ows:Identifier>
      <wps:Data>
        <wps:LiteralData>20</wps:LiteralData>
      </wps:Data>
    </wps:Input>
  </wps:DataInputs>
  <wps:ResponseForm>
    <wps:RawDataOutput>
      <ows:Identifier>result</ows:Identifier>
    </wps:RawDataOutput>
  </wps:ResponseForm>
</wps:Execute>""")
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

  @BeforeEach
  fun initEnv(@TempDir folder : Path) {
    workingDirectory = folder.toFile()
    System.setProperty("workingDir", workingDirectory.absolutePath)
  }

  var workingDirectory = File("")

  @Test
  @DisplayName("NC_UPLOAD WPS Query")
  fun testNcUpload(vertx: Vertx, testContext: VertxTestContext) {
    val webClient: WebClient = WebClient.create(vertx)
    val deploymentCheckpoint = testContext.checkpoint()
    val requestCheckpoint = testContext.checkpoint()
    System.setProperty("user.dir", "build")
    val app = MainVerticle()
    vertx.deployVerticle(app).onComplete(testContext.succeeding<String?>(Handler {
      // HTTP server is ready
      deploymentCheckpoint.flag()
      val fs = vertx.fileSystem()
      val filename = "org/noise_planet/onomap/track_upload_test.zip"
      // Open local file to create the WPS query with this file embedded into a xml text element
      fs.open(filename, OpenOptions()).compose { asyncFile ->
        asyncFile.toBase64().onComplete { base64String ->
          // Record file has been fully converted into base 64
          // send a POST query to Vert.X http server
          webClient.post(ONOMAP_DEFAULT_PORT, "localhost", "/geoserver/wps")
            .`as`(BodyCodec.jsonObject())
            .sendBuffer(generateWPSUpload(base64String.result()))
            .onComplete(testContext.succeeding { resp ->
              // We got a response from Vert.X http server
              testContext.verify(ExecutionBlock {
                // Check HTTP status code
                assertThat(resp.statusCode(), equalTo(200))
                // Check written file content
                val response = resp.body().map
                val uuid = response["result"] as String
                val path = Paths.get(workingDirectory.absolutePath,  "onomap_uploading","track_$uuid.zip")
                assert(path.exists())
                val outputb64 = path.toFile().readBytes().base64Encode()
                assertThat(base64String.result(), equalTo(outputb64))
                requestCheckpoint.flag()
                testContext.completeNow()
              })
            }).onSuccess { response -> println("Got HTTP response with status ${response.statusCode()} ") }
        }
      }
    }))
  }

  fun prepareDbForUnitTest(ds: DataSource?) {
    ds?.connection?.use { connection ->
      val resourceFile = TestMainVerticle::class.java.getResource("ut_deps.geojson")
      if (resourceFile != null) {
        GeoJsonRead.importTable(connection, resourceFile.file ,"GADM28", ValueBoolean.get(true))
        connection.createStatement().use { statement ->
          statement.execute("SELECT UPDATEGEOMETRYSRID('gadm28', 'the_geom', 4326)")
          statement.execute("TRUNCATE TABLE NOISECAPTURE_AREA CASCADE")
          statement.execute("TRUNCATE TABLE NOISECAPTURE_USER CASCADE") // will cascade suppression of tracks
          statement.execute("TRUNCATE TABLE NOISECAPTURE_TAG CASCADE")
        }
      }
    }
  }

  @Test
  @DisplayName("NC_PARSE WPS Query")
  fun parseWPSTest(vertx: Vertx, testContext: VertxTestContext) {
    val webClient: WebClient = WebClient.create(vertx)
    val deploymentCheckpoint = testContext.checkpoint()
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

    val app = MainVerticle()
    // launch web server and ask to process the file through WPS query
    vertx.deployVerticle(app).onComplete(testContext.succeeding<String?>(Handler {
      prepareDbForUnitTest(app.ds)
      // HTTP server is ready
      deploymentCheckpoint.flag()
      // Open local file to create the WPS query with this file embedded into a xml text element
      testContext.verify {
        assert(path.exists())

        // send a POST query to Vert.X http server
        webClient.post(ONOMAP_DEFAULT_PORT, "localhost", "/geoserver/wps")
          .`as`(BodyCodec.jsonObject())
          .sendBuffer(generateWPSParse())
          .onComplete(testContext.succeeding { resp ->
            // We got a response from Vert.X http server
            testContext.verify(ExecutionBlock {
              // Check HTTP status code
              assertThat(resp.statusCode(), equalTo(200))
              // Check return result
              println("got ${resp.body()}")
              assertThat(resp.body().map["result"], equalTo(1))
              requestCheckpoint.flag()
              testContext.completeNow()
            })
          })
      }

    }))
  }
}
