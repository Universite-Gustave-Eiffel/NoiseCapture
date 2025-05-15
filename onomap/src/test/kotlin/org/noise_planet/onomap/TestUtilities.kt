package org.noise_planet.onomap

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.AsyncFile
import java.util.Base64

fun AsyncFile.toBase64(): Future<String> {
  val promise = Promise.promise<String>()
  val buffer = Buffer.buffer()

  this.handler { chunk ->
    buffer.appendBuffer(chunk)
  }.endHandler {
    val base64 = buffer.bytes.base64Encode()
    promise.complete(base64)
  }.exceptionHandler { err ->
    promise.fail(err)
  }

  return promise.future()
}

// Extension for converting ByteArray to Base64
fun ByteArray.base64Encode(): String {
  return Base64.getEncoder().encodeToString(this)
}
