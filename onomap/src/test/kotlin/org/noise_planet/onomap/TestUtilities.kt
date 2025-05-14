package org.noise_planet.onomap

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.AsyncFile
import java.util.Base64
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher
import java.io.File


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

class FileContentMatcher(private val expected: File) : TypeSafeMatcher<File>() {
  override fun matchesSafely(actual: File): Boolean {
    return actual.readBytes().contentEquals(expected.readBytes())
  }

  override fun describeTo(description: Description) {
    description.appendText("file with content matching ${expected.name}")
  }

  override fun describeMismatchSafely(item: File, description: Description) {
    description.appendText("found different content in ${item.name}")
  }
}

fun hasSameContentAs(expected: File) = FileContentMatcher(expected)
