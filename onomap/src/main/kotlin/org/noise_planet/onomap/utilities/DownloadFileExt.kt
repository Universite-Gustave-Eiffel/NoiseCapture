package org.noise_planet.onomap.utilities

import org.h2gis.api.ProgressVisitor
import java.net.HttpURLConnection
import java.net.URL
import java.io.File

fun URL.downloadFile(outputFile: File, progressVisitor: ProgressVisitor) {
  val progressSteps = 10000
  val connection = openConnection()
  if(connection is HttpURLConnection) {
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
