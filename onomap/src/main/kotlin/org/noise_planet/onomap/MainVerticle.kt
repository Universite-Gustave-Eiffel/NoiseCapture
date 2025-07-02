/*
 *  This file is part of the NoiseCapture application and OnoMap system.
 *
 *  The 'OnoMaP' system is led by Lab-STICC and Univ Eiffel - UMRAE and generates noise maps via
 *  citizen-contributed noise data.
 *
 *  This application is co-funded by the ENERGIC-OD Project (European Network for
 *  Redistributing Geospatial Information to user Communities - Open Data). ENERGIC-OD
 *  (http://www.energic-od.eu/) is partially funded under the ICT Policy Support Programme (ICT
 *  PSP) as part of the Competitiveness and Innovation Framework Programme by the European
 *  Community. The application work is also supported by the French geographic portal GEOPAL of the
 *  Pays de la Loire region (http://www.geopal.org).
 *
 *  Copyright (C) Univ Eiffel - UMRAE and Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
 *
 *  NoiseCapture is a free software; you can redistribute it and/or modify it under the terms of the
 *  GNU General Public License as published by the Free Software Foundation; either version 3 of
 *  the License, or(at your option) any later version. NoiseCapture is distributed in the hope that
 *  it will be useful,but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 *  more details.You should have received a copy of the GNU General Public License along with this
 *  program; if not, write to the Free Software Foundation,Inc., 51 Franklin Street, Fifth Floor,
 *  Boston, MA 02110-1301  USA or see For more information,  write to Université Gustave Eiffel,
 *  14-20 Boulevard Newton Cite Descartes, Champs sur Marne F-77447 Marne la Vallee Cedex 2 FRANCE
 *   or write to scientific.computing@univ-eiffel.fr
 */

package org.noise_planet.onomap

 import com.zaxxer.hikari.HikariDataSource
 import groovy.lang.Script
 import io.vertx.config.ConfigRetriever
 import io.vertx.core.AbstractVerticle
 import io.vertx.core.Promise
 import io.vertx.core.json.Json
 import io.vertx.ext.web.Router
 import io.vertx.ext.web.RoutingContext
 import io.vertx.ext.web.handler.BodyHandler
 import io.vertx.launcher.application.VertxApplication
 import kotlinx.coroutines.DelicateCoroutinesApi
 import net.opengis.ows11.Ows11Factory
 import net.opengis.wps10.ExecuteType
 import net.opengis.wps10.InputType
 import org.apache.log4j.PatternLayout
 import org.apache.log4j.PropertyConfigurator
 import org.apache.log4j.RollingFileAppender
 import org.geotools.ows.v1_1.OWS
 import org.geotools.ows.v1_1.OWSConfiguration
 import org.geotools.wps.WPSConfiguration
 import org.geotools.xsd.Encoder
 import org.geotools.xsd.Parser
 import org.locationtech.jts.geom.Geometry
 import org.locationtech.jts.io.WKTReader
 import org.noise_planet.onomap.database.DataBaseManagement
 import org.noise_planet.onomap.sensitive.nc_dump_records
 import org.noise_planet.onomap.sensitive.nc_get_stats
 import org.noise_planet.onomap.sensitive.nc_parse
 import org.noise_planet.onomap.sensitive.nc_process
 import org.slf4j.Logger
 import org.slf4j.LoggerFactory
 import java.io.ByteArrayInputStream
 import java.io.File
 import java.lang.Double
 import java.lang.Float
 import java.lang.Long
 import java.util.concurrent.atomic.AtomicLong
 import kotlin.Any
 import kotlin.Array
 import kotlin.Exception
 import kotlin.IllegalArgumentException
 import kotlin.OptIn
 import kotlin.String
 import kotlin.apply
 import kotlin.arrayOf
 import kotlin.io.use
 import kotlin.to
 import kotlin.toString
 import kotlin.use


const val ONOMAP_DEFAULT_PORT = 8888

const val MS_DELAY_PROCESS_MEASUREMENTS = 5000L

class MainVerticle : AbstractVerticle() {
  val log: Logger = LoggerFactory.getLogger(MainVerticle::class.java)
  var ds: HikariDataSource? = null
  // Jobs to process noisecapture measurements
  // such jobs must no process in parallel so it should be called only after the last call is complete
  val parsePendingJob = AtomicLong()
  val processPendingJob = AtomicLong()

  companion object {
    @JvmStatic fun main(args : Array<String>) {
      PropertyConfigurator.configure(MainVerticle::class.java.getResource("log4j.properties"));
      VertxApplication.main(arrayOf<String?>(MainVerticle::class.java.getName()) + args)
    }
  }

  fun configureFileLogger(workingDir: String) {

    // configure file logger
    try {
      // Create rolling file appender
      val rollingAppender: RollingFileAppender = RollingFileAppender()

      // Configure appender properties
      rollingAppender.name = "rollingFile"
      rollingAppender.file = File(workingDir, "application.log").path
      rollingAppender.append = true
      rollingAppender.setMaxBackupIndex(5)
      rollingAppender.maximumFileSize = 10000000

      // Create and set pattern layout
      val layout = PatternLayout("[%t] %-5p %d{yyyy-MM-dd HH:mm:ss} - %m%n")
      rollingAppender.setLayout(layout)

      // init stream
      rollingAppender.activateOptions()

      // Configure root logger
      val rootLogger: org.apache.log4j.Logger = org.apache.log4j.Logger.getRootLogger()
      rootLogger.addAppender(rollingAppender)
    } catch (e: java.lang.Exception) {
      System.err.println("Failed to configure logger: " + e.message)
    }
  }

  @OptIn(DelicateCoroutinesApi::class)
  override fun start(startPromise: Promise<Void>) {
    // Init the configuration manager
    val retriever = ConfigRetriever.create(vertx)

    val router = Router.router(vertx).apply {
      post("/geoserver/wps").handler(BodyHandler.create()).handler(this@MainVerticle::noisecapture1WPS)
      get("/dumpData").handler(BodyHandler.create()).handler(this@MainVerticle::doDumpData)
      get("/dumpStats").handler(BodyHandler.create()).handler(this@MainVerticle::doDumpStats)
      get("/parse").handler(BodyHandler.create()).handler(this@MainVerticle::doParse)
    }

    retriever
      .config
      .compose { json ->
        val workingDir = json.getString("workingDir", File("").absolutePath)
        configureFileLogger(workingDir)
        if(workingDir.isNotEmpty() && (System.getProperty("workingDir") == null || System.getProperty("workingDir").isEmpty())) {
          System.setProperty("workingDir", File(workingDir).absolutePath)
        }
        try {
          ds = DataBaseManagement.initDataBaseConfiguration(json)
          DataBaseManagement.checkDataBaseState(vertx, ds, json)
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

  private fun doDumpData(context: RoutingContext) {
    ds?.connection.use { connection ->
      val scriptOutput = nc_dump_records().exec(connection, mapOf("exportTracks" to true,
        "exportMeasures" to true,"exportAreas" to true, "dayFilter" to 1) as Map<String, *>)
      encodeWpsResponse(context, scriptOutput)
    }
  }

  private fun doDumpStats(context: RoutingContext) {
    ds?.connection.use { connection ->
      val scriptOutput = nc_get_stats().exec(connection, emptyMap<String, Any>() as Map<String, *>)
      encodeWpsResponse(context, scriptOutput)
    }
  }

  private fun doParse(context: RoutingContext) {
    ds?.connection.use { connection ->
      val scriptOutput = nc_parse().exec(connection, mapOf("processFileLimit" to 20) as Map<String, *>)
      encodeWpsResponse(context, scriptOutput)
    }
  }

  private fun encodeWpsResponse(
      context: RoutingContext,
      scriptOutput: Any?
  ): String {
    // Convert output from WPS script to JSON Object if necessary
    context.response().putHeader("Content-Type", "application/json")
    // Send response to client
    val encodedResult =
      if (scriptOutput is Map<*, *> && scriptOutput.containsKey("result")) {
        scriptOutput["result"].toString()
      } else {
        Json.encode(scriptOutput)
      }
    // Send response to client
    context.response().end(encodedResult)
    return encodedResult
  }

  /**
   * Process WPS queries from NoiseCapture V1 Application
   */
  private fun noisecapture1WPS(context: RoutingContext) {
    try {
      val body = context.body()
      val wps = WPSConfiguration()
      val parser = Parser(wps)
      ByteArrayInputStream(body.buffer().bytes).use { inputStream ->
        val parsed = parser.parse(inputStream)
        if(parsed is ExecuteType) {
          runWPSScript(context, parsed)
        }
      }
    } catch (ex : Exception) {
      log.error("Exception while processing ${context.request().uri()}", ex)
      val exception = Ows11Factory.eINSTANCE.createExceptionType()
      exception.exceptionCode = ex::class.simpleName
      exception.exceptionText.add(ex.localizedMessage)
      exception.exceptionText.add(ex.cause?.localizedMessage)
      val exReport = Ows11Factory.eINSTANCE.createExceptionReportType()
      exReport.exception.add(exception)
      val encoder = Encoder(OWSConfiguration())
      encoder.isIndenting = true
      encoder.setIndentSize(2)
      val reportXml = encoder.encodeAsString(exReport, OWS.ExceptionReport)
      context.response().putHeader("Content-Type", "text/xml")
      context.response().statusCode = 500
      context.response().end(reportXml)
    }
  }


  fun runWPSScript(context: RoutingContext, wpsQuery: ExecuteType) {
    val wpsProcess = wpsQuery.identifier.value
    if (wpsProcess.startsWith("groovy:")) {
      val scriptName = wpsProcess.substringAfterLast(":").replace(".", "")
      val groovyClass = javaClass.classLoader.loadClass("org.noise_planet.onomap.$scriptName")
      val instance = groovyClass.getConstructor().newInstance()
      if (instance is Script) {
        // invoke the script
        instance.invokeMethod("run", null)
        val inputs = instance.evaluate("inputs") as Map<*, *>
        val title = instance.evaluate("title") as String
        val description = instance.evaluate("description") as String
        val wpsInput = HashMap<String, Any>()
        wpsQuery.dataInputs.input.forEach { input ->
          if(input is InputType) {
            try {
              val inputId = input.identifier.value
              var inputContent: Any = input.data.literalData.value
              if (inputId in inputs && inputs[inputId] is Map<*, *> &&
                (inputs[inputId] as Map<*, *>).containsKey("type")
              ) {
                val entry: Map<*, *> = inputs[inputId] as Map<*, *>
                val dataType = entry["type"]
                if (dataType is Class<*>) {
                  // found expected input, try to cast to expect type if not null
                  when (dataType.name) {
                    Long::class.java.name -> inputContent = input.data.literalData.value.toLong()
                    Integer::class.java.name -> inputContent = input.data.literalData.value.toInt()
                    Float::class.java.name -> inputContent = input.data.literalData.value.toFloat()
                    Double::class.java.name -> inputContent = input.data.literalData.value.toDouble()
                    Geometry::class.java.name -> inputContent = WKTReader().read(input.data.literalData.value)
                  }
                }
              }
              wpsInput.put(inputId, inputContent)

            } catch (ex: Exception) {
              log.warn("Warning, ignore input as there was an exception while converting input '${input.identifier.value}' processing WPS ${context.request().uri()} ", ex)
            }
          }
        }
        ds?.connection.use { connection ->
          context.response().putHeader("Content-Type", "application/json")
          val scriptOutput = instance.invokeMethod("exec", listOf(connection, wpsInput))
          val encodedResult = encodeWpsResponse(context, scriptOutput)
          log.info("Executed $wpsProcess with result $encodedResult")
          // Caller ask to not automatically call other wps process (for unit test)
          if(!wpsInput.containsKey("triggerWpsEvent") || wpsInput["triggerWpsEvent"] == true) {
            onEndCallWps(scriptName)
          }
        }
      } else {
        throw IllegalArgumentException("Not a script")
      }
    }
  }

  fun parseFiles() {
    ds?.connection?.use { connection ->
      val result = nc_parse().exec(connection, mapOf("processFileLimit" to 20) as Map<String, *>)
      log.info("Measurements parsed, result $result")
    }
  }

  fun processFiles() {
    ds?.connection?.use { connection ->
      val result = nc_process().exec(connection, mapOf("locationPrecisionFilter" to 15.0, "processTracksLimit" to 20))
      log.info("Measurements processed, result $result")
    }
  }

  fun onEndCallWps(scriptName: String) {
    when(scriptName.lowercase()) {
      "nc_upload" -> {
        vertx.cancelTimer(parsePendingJob.getAndSet(vertx.setTimer(MS_DELAY_PROCESS_MEASUREMENTS) {
          parseFiles()
          onEndCallWps("nc_parse")
        }))
        log.info("Detect uploading job is done, parse it in $MS_DELAY_PROCESS_MEASUREMENTS milliseconds")
      }
      "nc_parse" -> {
        vertx.cancelTimer(processPendingJob.getAndSet(vertx.setTimer(MS_DELAY_PROCESS_MEASUREMENTS) {
          processFiles()
          onEndCallWps("nc_process")
        }))
        log.info("Detect parse job is done, process it in $MS_DELAY_PROCESS_MEASUREMENTS milliseconds")
      }
    }
  }
}

