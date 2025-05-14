package org.noise_planet.onomap

import groovy.lang.Script
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import io.vertx.core.json.Json
import javax.xml.stream.XMLInputFactory

const val ONOMAP_DEFAULT_PORT = 8888

class MainVerticle : AbstractVerticle() {

  override fun start(startPromise: Promise<Void>) {

    val router = Router.router(vertx).apply {
      post("/geoserver/wps").handler(BodyHandler.create()).handler (this@MainVerticle::noisecapture1WPS)
      post("/geoserver/ows").handler(BodyHandler.create()).handler (this@MainVerticle::noisecapture1WPS)
    }

    vertx
      .createHttpServer()
      .requestHandler(router)
      .listen(ONOMAP_DEFAULT_PORT).onComplete { http ->
        if (http.succeeded()) {
          startPromise.complete()
          println("HTTP server started on port $ONOMAP_DEFAULT_PORT")
        } else {
          startPromise.fail(http.cause());
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
    val result = runWPSScript(wpsQuery)
    // Produce the xml result
    context.response().putHeader("Content-Type", "text/xml");
    context.response().end(Json.encode(result))
  }

  /**
   * Parse WPS XML query document
   */
  fun parseWpsXmlQuery(stream : InputStream) : WPSQuery {
    val reader =
      XMLInputFactory.newFactory().createXMLStreamReader(
        InputStreamReader(stream, "UTF-8"))
    val dataInputs = HashMap<String, String>()
    var keyStack = emptyArray<String>()
    var lastLiteralData = ""
    var lastDataIdentifier = ""
    var wpsProcessName = ""
    // Parse WPS xml
    while(reader.hasNext()) {
      reader.next()
      if(reader.hasName()) {
        // Look for WPS
        with(reader.name) {
          if(prefix.equals("wps", true) ||
            prefix.equals("ows", true)) {
            if(reader.isStartElement) {
              keyStack += localPart
            } else if(keyStack.last() == localPart) {
              keyStack = keyStack.sliceArray(0.. keyStack.size - 2)
              if(localPart.equals("Input", true)) {
                dataInputs.put(lastDataIdentifier, lastLiteralData)
              }
            }
          }
        }
        if(reader.isStartElement) {
          // clear for next multi-part text
          lastLiteralData = ""
        }
      } else if(reader.hasText()) {
        when(keyStack.last()) {
          "LiteralData" -> {
            lastLiteralData += reader.text
          }
          "Identifier" -> {
            when(keyStack.takeLast(2).first()) {
              "Input" -> lastDataIdentifier = reader.text
              "Execute" -> wpsProcessName = reader.text
            }
          }
        }
      }
    }
    return WPSQuery(wpsProcessName, dataInputs)
  }

  fun runWPSScript(wpsQuery: WPSQuery) : Map<*, *> {
    if(wpsQuery.wpsProcessName.startsWith("groovy:")) {
      val scriptName = wpsQuery.wpsProcessName.substring(wpsQuery.wpsProcessName.indexOf(":") + 1, wpsQuery.wpsProcessName.length)
      try {
        val groovyClass = javaClass.classLoader.loadClass("org.noise_planet.onomap.$scriptName")
        val instance = groovyClass.getConstructor().newInstance()
        if(instance is Script) {
          val result = instance.invokeMethod("run", wpsQuery.wpsInput)
          if(result is Map<*, *>) {
            return result
          } else {
            return mapOf("result" to result)
          }
        }
      } catch (e: ClassNotFoundException) {
        return mapOf("result" to "<ows:ExceptionReport version=\"1.1.0\" " +
          "xsi:schemaLocation=\"http://www.opengis.net/ows/1.1 " +
          "https://onomap-gs.noise-planet.org/geoserver/schemas/ows/1.1.0/owsAll.xsd\">\n" +
          "<ows:Exception exceptionCode=\"MissingParameterValue\" locator=\"request\">\n" +
          "<ows:ExceptionText>\n" +
          "This WPS process does not exists ${wpsQuery.wpsProcessName}\n" +
          "</ows:ExceptionText>\n" +
          "</ows:Exception>\n" +
          "</ows:ExceptionReport>")
      }
    }
    return mapOf("result" to "")
  }

}

data class WPSQuery(val wpsProcessName: String, val wpsInput: Map<String, Any>)
