/*
 * This file is part of the NoiseCapture application and OnoMap system.
 *
 * The 'OnoMaP' system is led by Lab-STICC and Ifsttar and generates noise maps via
 * citizen-contributed noise data.
 *
 * This application is co-funded by the ENERGIC-OD Project (European Network for
 * Redistributing Geospatial Information to user Communities - Open Data). ENERGIC-OD
 * (http://www.energic-od.eu/) is partially funded under the ICT Policy Support Programme (ICT
 * PSP) as part of the Competitiveness and Innovation Framework Programme by the European
 * Community. The application work is also supported by the French geographic portal GEOPAL of the
 * Pays de la Loire region (http://www.geopal.org).
 *
 * Copyright (C) IFSTTAR - LAE and Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
 *
 * NoiseCapture is a free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or(at your option) any later version. NoiseCapture is distributed in the hope that
 * it will be useful,but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.You should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation,Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301  USA or see For more information,  write to Ifsttar,
 * 14-20 Boulevard Newton Cite Descartes, Champs sur Marne F-77447 Marne la Vallee Cedex 2 FRANCE
 *  or write to scientific.computing@ifsttar.fr
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
 import net.opengis.ows11.Ows11Factory
 import net.opengis.wps10.ExecuteType
 import net.opengis.wps10.InputType
 import org.geotools.ows.v1_1.OWSConfiguration
 import org.geotools.wps.WPSConfiguration
 import org.geotools.xsd.Parser
 import org.slf4j.Logger
 import org.slf4j.LoggerFactory
 import java.io.ByteArrayInputStream
 import org.geotools.xsd.Encoder;
 import org.geotools.ows.v1_1.OWS
 import org.noise_planet.onomap.database.DataBaseManagement


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

  /**
   * Process WPS queries from NoiseCapture V1 Application
   */
  private fun noisecapture1WPS(context: RoutingContext) {
    try {
      val body = context.body()
      val wps: WPSConfiguration = WPSConfiguration()
      val parser: Parser = Parser(wps)
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
    val wpsInput = HashMap<String, Any>()
    wpsQuery.dataInputs.input.forEach { input ->
      if(input is InputType) {
        val inputId = input.identifier.value
        val inputContent = input.data.literalData.value
        wpsInput.put(inputId, inputContent)
      }
    }
    if (wpsProcess.startsWith("groovy:")) {
      val scriptName = wpsProcess.substring(wpsProcess.indexOf(":") + 1, wpsProcess.length)
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
          val result = instance.invokeMethod("exec", listOf(connection, wpsInput))
          context.response().end(Json.encode(result))
          log.info("Executed $wpsProcess with result $result")
        }
      } else {
        throw IllegalArgumentException("Not a script")
      }
    }
  }
}

data class WPSQuery(val wpsProcessName: String, val wpsInput: Map<String, Any>)
