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


package org.noise_planet.onomap.utilities

import org.apache.logging.log4j.core.util.FileUtils.isFile
import org.h2gis.api.ProgressVisitor
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

fun URL.downloadFile(outputFile: File, progressVisitor: ProgressVisitor) {
  val progressSteps = 10000
  val connection = openConnection()
  if(isFile(this)) {
    Files.copy(Path.of(this.toURI()), Path.of(outputFile.toURI()))
  } else if(connection is HttpURLConnection) {
    connection.connect()
    try {
      // Check if the response code is OK (200)
      if (connection.responseCode != HttpURLConnection.HTTP_OK) {
        throw Exception("Server returned HTTP ${connection.responseCode} ${connection.responseMessage}")
      }

      // Get the total file size from the Content-Length header
      val contentLength = connection.contentLengthLong
      val progress = progressVisitor.subProcess(progressSteps)
      if (contentLength == -1L) {
        throw Exception("Content-Length header not provided, cannot track progress")
      }

      // Create input and output streams
      connection.inputStream.use { input ->
        outputFile.outputStream().use { output ->
          val buffer = ByteArray(8 * 1024) // 8KB buffer
          var bytesRead = 0L

          while (true) {
            val bytes = input.read(buffer)
            if (bytes == -1) break

            // Write the downloaded data to the file
            output.write(buffer, 0, bytes)
            bytesRead += bytes

            // Calculate progress
            progress.setStep((bytesRead * progressSteps / contentLength).toInt())

          }
        }
      }
    } finally {
      connection.disconnect()
    }
  }
}
