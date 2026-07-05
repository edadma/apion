package io.github.edadma.apion

import io.github.edadma.nodejs.{fetch, Server => NodeServer}
import org.scalatest.BeforeAndAfterAll
import scala.compiletime.uninitialized

class PerServerConfigIntegrationTests extends AsyncBaseSpec with BeforeAndAfterAll {
  // Two servers in the same process with different configs: proves default headers
  // are per-server (stamped at each server's send boundary), not global.
  var defaultServer: Server  = uninitialized
  var customServer: Server   = uninitialized
  var defaultHttp: NodeServer = uninitialized
  var customHttp: NodeServer  = uninitialized
  val defaultPort            = 3020
  val customPort             = 3021

  override def beforeAll(): Unit = {
    defaultServer = Server().get("/", _ => "ok".asText)
    defaultHttp = defaultServer.listen(defaultPort) {}

    customServer = Server(ServerConfig(defaultHeaders = Seq("Server" -> "Custom", "X-Env" -> "test")))
      .get("/", _ => "ok".asText)
    customHttp = customServer.listen(customPort) {}
  }

  override def afterAll(): Unit = {
    if (defaultHttp != null) defaultHttp.close(() => ())
    if (customHttp != null) customHttp.close(() => ())
  }

  "Per-server default headers" - {
    "default server stamps the built-in Server header" in {
      fetch(s"http://localhost:$defaultPort/").toFuture.map { response =>
        response.headers.get("server") shouldBe "Apion"
      }
    }

    "custom server stamps its own headers and not the built-ins" in {
      fetch(s"http://localhost:$customPort/").toFuture.map { response =>
        response.headers.get("server") shouldBe "Custom"
        response.headers.get("x-env") shouldBe "test"
        response.headers.get("x-powered-by") shouldBe null
      }
    }
  }
}
