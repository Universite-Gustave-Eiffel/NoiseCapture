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

package org.noise_planet.onomap.database

import org.h2gis.api.ProgressVisitor
import org.h2gis.functions.io.geojson.GeoJsonReaderDriver
import org.h2gis.utilities.TableLocation
import org.h2gis.utilities.dbtypes.DBTypes
import org.h2gis.utilities.dbtypes.DBUtils
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import java.io.InputStream
import java.net.URI
import java.sql.Connection
import java.sql.SQLException
import java.util.zip.GZIPInputStream

fun GeoJsonReaderDriver.readGzipFromUri(uri: URI, tableReference: String, prog : ProgressVisitor): String? {
  var conn: Connection
  with(this.javaClass.getDeclaredField("connection")) {
    isAccessible = true
    conn = get(this@readGzipFromUri) as Connection
  }
  var dbType : DBTypes
  with(this.javaClass.getDeclaredField("dbType")) {
    isAccessible = true
    dbType = DBUtils.getDBType(conn)
    set(this@readGzipFromUri, dbType)
  }
  with(this.javaClass.getDeclaredField("tableLocation")) {
    isAccessible = true
    set(this@readGzipFromUri, TableLocation.parse(tableReference, dbType).toString())
  }
  with(this.javaClass.getDeclaredField("progress")) {
    isAccessible = true
    set(this@readGzipFromUri, prog)
  }
  with(this.javaClass.getDeclaredMethod("init")) {
    isAccessible = true
    invoke(this@readGzipFromUri)
  }
  with(this.javaClass.getDeclaredMethod("parseMetadata", InputStream::class.java)) {
    isAccessible = true
    uri.toURL().openStream().use { inputStream ->
      val res = invoke(this@readGzipFromUri, GZIPInputStream(inputStream))
      if(res is Boolean && !res) {
        throw SQLException("Cannot create the table $tableReference to import the GeoJSON data")
      }
    }
  }
  var srid = 0
  with(this.javaClass.getDeclaredField("parsedSRID")) {
    isAccessible = true
    srid = get(this@readGzipFromUri) as Int
  }
  conn.autoCommit = false
  with(this.javaClass.getDeclaredField("GF")) {
    isAccessible = true
    set(this@readGzipFromUri, GeometryFactory(PrecisionModel(), srid))
  }

  with(this.javaClass.getDeclaredMethod("parseData", InputStream::class.java)) {
    isAccessible = true
    uri.toURL().openStream().use { inputStream ->
      invoke(this@readGzipFromUri, GZIPInputStream(inputStream))
    }
  }
  conn.autoCommit = true

  return tableReference
}
