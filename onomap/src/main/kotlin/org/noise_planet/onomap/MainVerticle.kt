package org.noise_planet.onomap

import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext

class MainVerticle : AbstractVerticle() {

  private val users = JsonObject().put(
    "users",
    JsonObject().put(
      "tonys",
      JsonObject().apply {
        put("user_id", "tonys")
        put("user_name", "Tony Stark")
        put("name_alias", "Iron Man")
        put("company", "Stark Industries")
      }))

  override fun start(startPromise: Promise<Void>) {

    val router = Router.router(vertx).apply {
      get("/onomap/api/users").handler(this@MainVerticle::getUsers)
    }

    vertx
      .createHttpServer()
      .requestHandler(router)
      .listen(8888).onComplete { http ->
        if (http.succeeded()) {
          startPromise.complete()
          println("HTTP server started on port 8888")
        } else {
          startPromise.fail(http.cause());
        }
      }
  }

  private fun getUsers(context: RoutingContext) {
    context.response().statusCode = 200

    context.response().putHeader("Content-Type", "application/json")
    context.response().end(users.encode())
  }
}
