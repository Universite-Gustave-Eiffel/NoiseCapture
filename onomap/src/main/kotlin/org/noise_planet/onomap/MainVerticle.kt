package org.noise_planet.onomap

 import com.zaxxer.hikari.HikariDataSource
 import groovy.json.JsonOutput
 import groovy.lang.Script
 import io.vertx.config.ConfigRetriever
 import io.vertx.core.AbstractVerticle
 import io.vertx.core.Promise
 import io.vertx.core.json.Json
 import io.vertx.ext.web.Router
 import io.vertx.ext.web.RoutingContext
 import io.vertx.ext.web.handler.BodyHandler
 import org.slf4j.Logger
 import org.slf4j.LoggerFactory
 import java.io.ByteArrayInputStream
 import java.io.InputStream
 import java.io.InputStreamReader
 import javax.xml.stream.XMLInputFactory


const val ONOMAP_DEFAULT_PORT = 8888

class MainVerticle : AbstractVerticle() {
  val log: Logger = LoggerFactory.getLogger(MainVerticle::class.java)

  var ds: HikariDataSource? = null

  override fun start(startPromise: Promise<Void>) {

    // Init the configuration manager
    val retriever = ConfigRetriever.create(vertx)

    val router = Router.router(vertx).apply {
      post("/geoserver/wps").handler(BodyHandler.create()).handler(this@MainVerticle::noisecapture1WPS)
      post("/geoserver/ows").handler(BodyHandler.create()).handler(this@MainVerticle::noisecapture1WPS)
    }

    retriever
      .config
      .compose { json ->
        try {
          ds = DataBaseManagement.initDataBaseConfiguration(json)
          DataBaseManagement.checkDataBaseState(vertx, ds)
        } catch (ex: Exception) {
          log.error("Error while creating the database data source", ex)
          startPromise.fail(ex)
          return@compose null
        }
        vertx.createHttpServer()
          .requestHandler(router)
          .listen(json.getInteger("ONOMAP_PORT", ONOMAP_DEFAULT_PORT)).onComplete { http ->
            if (http.succeeded()) {
              startPromise.complete()
              log.info("HTTP server started on port ${json.getInteger("ONOMAP_PORT", ONOMAP_DEFAULT_PORT)}")
            } else {
              startPromise.fail(http.cause());
            }
          }
      }
  }

  /**
   * Process WPS queries from NoiseCapture V1 Application
   */
  private fun noisecapture1WPS(context: RoutingContext) {
    val body = context.body()
    // Parse the POST XML content
    val wpsQuery = parseWpsXmlQuery(ByteArrayInputStream(body.buffer().bytes))
    // Run the WPS process
    runWPSScript(context, wpsQuery)
  }

  /**
   * Parse WPS XML query document
   */
  fun parseWpsXmlQuery(stream: InputStream): WPSQuery {
    val reader =
      XMLInputFactory.newFactory().createXMLStreamReader(
        InputStreamReader(stream, "UTF-8")
      )
    val dataInputs = HashMap<String, String>()
    var keyStack = emptyArray<String>()
    var lastLiteralData = ""
    var lastDataIdentifier = ""
    var wpsProcessName = ""
    // Parse WPS xml
    while (reader.hasNext()) {
      reader.next()
      if (reader.hasName()) {
        // Look for WPS
        with(reader.name) {
          if (prefix.equals("wps", true) ||
            prefix.equals("ows", true)
          ) {
            if (reader.isStartElement) {
              keyStack += localPart
            } else if (keyStack.last() == localPart) {
              keyStack = keyStack.sliceArray(0..keyStack.size - 2)
              if (localPart.equals("Input", true)) {
                dataInputs.put(lastDataIdentifier, lastLiteralData)
              }
            }
          }
        }
        if (reader.isStartElement) {
          // clear for next multi-part text
          lastLiteralData = ""
        }
      } else if (reader.hasText()) {
        when (keyStack.last()) {
          "LiteralData" -> {
            lastLiteralData += reader.text
          }

          "Identifier" -> {
            when (keyStack.takeLast(2).first()) {
              "Input" -> lastDataIdentifier = reader.text
              "Execute" -> wpsProcessName = reader.text
            }
          }
        }
      }
    }
    return WPSQuery(wpsProcessName, dataInputs)
  }

  fun runWPSScript(context: RoutingContext, wpsQuery: WPSQuery) {
    if (wpsQuery.wpsProcessName.startsWith("groovy:")) {
      val scriptName =
        wpsQuery.wpsProcessName.substring(wpsQuery.wpsProcessName.indexOf(":") + 1, wpsQuery.wpsProcessName.length)
      try {
        val groovyClass = javaClass.classLoader.loadClass("org.noise_planet.onomap.$scriptName")
        val instance = groovyClass.getConstructor().newInstance()
        if (instance is Script) {
          // invoke the script
          instance.invokeMethod("run", null)
          val inputs = instance.evaluate("inputs") as Map<*, *>
          val title = instance.evaluate("title") as String
          val description = instance.evaluate("description") as String
          ds?.connection.use { connection ->
            context.response().putHeader("Content-Type", "application/json")
            try {
              val result = instance.invokeMethod("exec", listOf(connection, wpsQuery.wpsInput))
              context.response().end(Json.encode(result))
//              if (result is Map<*, *> && result.containsKey("result")) {
//                  // Produce the xml result
//                  if(result["result"] is JsonOutput) {
//                    context.response().end(result["result"].toString())
//                  } else {
//                    context.response().end(Json.encode(result["result"])) // could be json encoded ? Json.encode(result)
//                  }
//              } else {
//                log.warn("No return map in $scriptName")
//              }
            } catch (ex : Exception) {
              log.error("Error while running $scriptName", ex)
              context.response().end(Json.encode(mapOf("result" to ex.toString())))
            }
          }
        } else {
          throw IllegalArgumentException("Not a script")
        }
      } catch (e: Exception) {
        // Produce the xml result
        context.response().putHeader("Content-Type", "text/xml")
        context.response().statusCode = 500
        context.response().end(
          "<ows:ExceptionReport version=\"1.1.0\" " +
            "xsi:schemaLocation=\"http://www.opengis.net/ows/1.1 " +
            "https://onomap-gs.noise-planet.org/geoserver/schemas/ows/1.1.0/owsAll.xsd\">\n" +
            "<ows:Exception exceptionCode=\"MissingParameterValue\" locator=\"request\">\n" +
            "<ows:ExceptionText>\n" +
            "$e\n" +
            "</ows:ExceptionText>\n" +
            "</ows:Exception>\n" +
            "</ows:ExceptionReport>"
        )
      }
    }
  }
}

data class WPSQuery(val wpsProcessName: String, val wpsInput: Map<String, Any>)
