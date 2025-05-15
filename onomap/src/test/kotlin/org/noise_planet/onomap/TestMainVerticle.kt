package org.noise_planet.onomap

import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.OpenOptions
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.codec.BodyCodec
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.junit5.VertxTestContext.ExecutionBlock
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual.equalTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File


@ExtendWith(VertxExtension::class)
class TestMainVerticle {

  @BeforeEach
  fun deploy_verticle(vertx: Vertx, testContext: VertxTestContext) {
    vertx.deployVerticle(MainVerticle()).onComplete(testContext.succeeding<String> { _ -> testContext.completeNow() })
  }

  @Test
  fun verticle_deployed(vertx: Vertx, testContext: VertxTestContext) {
    testContext.completeNow()
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


  @Test
  @DisplayName("NC_UPLOAD WPS Query")
  fun testNcUpload(vertx: Vertx, testContext: VertxTestContext) {
    val webClient: WebClient = WebClient.create(vertx)
    val deploymentCheckpoint = testContext.checkpoint()
    val requestCheckpoint = testContext.checkpoint()
    System.setProperty("user.dir", "build")
    vertx.deployVerticle(MainVerticle()).onComplete(testContext.succeeding<String?>(Handler { id: String? ->
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
                val path = "data_dir/onomap_uploading/track_$uuid.zip"
                val outputb64 = File(path).readBytes().base64Encode()
                assertThat(base64String.result(), equalTo(outputb64))
                requestCheckpoint.flag()
                testContext.completeNow()
              })
            }).onSuccess { response -> println("Got HTTP response with status ${response.statusCode()} ") }
        }
      }
    }))
  }

}
