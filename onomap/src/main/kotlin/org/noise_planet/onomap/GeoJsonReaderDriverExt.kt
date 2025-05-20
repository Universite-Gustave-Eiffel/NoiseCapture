package org.noise_planet.onomap

import org.h2gis.functions.io.geojson.GeoJsonReaderDriver
import org.h2gis.utilities.TableLocation
import org.h2gis.utilities.dbtypes.DBTypes
import org.h2gis.utilities.dbtypes.DBUtils
import java.io.InputStream
import java.sql.Connection
import java.util.zip.GZIPInputStream

fun GeoJsonReaderDriver.readGzipFromStream(conn: Connection, inputStream : InputStream, tableReference: String): String? {
  var dbType : DBTypes
  with(this.javaClass.getDeclaredField("dbType")) {
    isAccessible = true
    dbType = DBUtils.getDBType(conn)
    set(this, dbType)
  }
  with(this.javaClass.getDeclaredField("tableLocation")) {
    isAccessible = true
    set(this, TableLocation.parse(tableReference, dbType).toString())
  }
  with(this.javaClass.getDeclaredMethod("parseData")) {
    isAccessible = true
    invoke(this, GZIPInputStream(inputStream))
  }
  return ""
}
