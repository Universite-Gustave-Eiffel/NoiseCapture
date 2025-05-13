package org.noise_planet.onomap

import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.file.OpenOptions
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.codec.BodyCodec
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.junit5.VertxTestContext.ExecutionBlock
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith


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


  @Test
  @DisplayName("NC_UPLOAD WPS Query")
  fun useSampleVerticle(vertx: Vertx, testContext: VertxTestContext) {
    val webClient: WebClient = WebClient.create(vertx)
    val deploymentCheckpoint = testContext.checkpoint()
    val requestCheckpoint = testContext.checkpoint()

    vertx.deployVerticle(MainVerticle()).onComplete(testContext.succeeding<String?>(Handler { id: String? ->
      deploymentCheckpoint.flag()
      val fs = vertx.fileSystem()
      val filename = "org/noise_planet/noisecapturegs/query_upload_track_00a20ba7.xml";
      fs.open(filename, OpenOptions()).compose { asyncFile ->
        webClient.post(ONOMAP_DEFAULT_PORT, "localhost", "/geoserver/wps")
          .`as`(BodyCodec.string())
          .sendStream(asyncFile)
          .onComplete(testContext.succeeding { resp ->
            testContext.verify(ExecutionBlock {
              assertThat("blah", resp.statusCode() == 200)
              // Check written file content
              requestCheckpoint.flag()
              testContext.completeNow()
            })
          }).onSuccess { response -> println("Got HTTP response with status ${response.statusCode()} ") }
      }
    }))
  }

}
