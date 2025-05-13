package org.noise_planet.onomap

import groovy.lang.GroovyShell
import groovy.lang.Script
import groovy.namespace.QName
import io.netty.buffer.ByteBufInputStream
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.codehaus.groovy.reflection.ReflectionUtils
import java.io.ByteArrayInputStream
import javax.xml.stream.XMLInputFactory
import java.io.InputStreamReader
import java.lang.reflect.Constructor

public const val ONOMAP_DEFAULT_PORT = 8888

class MainVerticle : AbstractVerticle() {

  override fun start(startPromise: Promise<Void>) {

    val router = Router.router(vertx).apply {
      post("/geoserver/wps").handler (this@MainVerticle::noisecapture1WPS)
      post("/geoserver/ows").handler (this@MainVerticle::noisecapture1WPS)
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
    context.request().bodyHandler{ buffer ->
      val returnValue = wpsBodyHandle(buffer)
      context.response().putHeader("Content-Type", "text/xml");
      context.response().end(returnValue.toString())
    }
  }

  /**
   * Process WPS queries XML from NoiseCapture V1 Application
   */
  private fun wpsBodyHandle(buffer: Buffer) : Map<String, Any> {
    val reader =
      XMLInputFactory.newFactory().createXMLStreamReader(
        InputStreamReader(ByteArrayInputStream(buffer.bytes), "UTF-8"))
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
      } else if(reader.hasText()) {
        when(keyStack.last()) {
           "LiteralData" -> {
            lastLiteralData = reader.text
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
    return runWPSScript(wpsProcessName, dataInputs)
  }

  fun runWPSScript(wpsProcessName : String, dataInputs : Map<String, String>) : Map<String, Any> {
    if(wpsProcessName.startsWith("groovy:")) {
      val scriptName = wpsProcessName.substring(wpsProcessName.indexOf(":") + 1, wpsProcessName.length)
      try {
        val groovyClass = javaClass.classLoader.loadClass("org.noise_planet.onomap.$scriptName")
        val instance = groovyClass.getConstructor().newInstance()
        if(instance is Script) {
          instance.invokeMethod("run", dataInputs)
        }
      } catch (e: ClassNotFoundException) {
        return mapOf("result" to "<ows:ExceptionReport version=\"1.1.0\" " +
          "xsi:schemaLocation=\"http://www.opengis.net/ows/1.1 " +
          "https://onomap-gs.noise-planet.org/geoserver/schemas/ows/1.1.0/owsAll.xsd\">\n" +
          "<ows:Exception exceptionCode=\"MissingParameterValue\" locator=\"request\">\n" +
          "<ows:ExceptionText>\n" +
          "This WPS process does not exists $wpsProcessName\n" +
          "</ows:ExceptionText>\n" +
          "</ows:Exception>\n" +
          "</ows:ExceptionReport>")
      }
    }
    return emptyMap()
  }

}
