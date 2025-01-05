package io.github.edadma.apion

import scala.scalajs.js
import io.github.edadma.nodejs.{fetch, Server as NodeServer, FetchOptions}
import org.scalatest.BeforeAndAfterAll

import scala.compiletime.uninitialized

class RateLimiterMiddlewareIntegrationTests extends AsyncBaseSpec with BeforeAndAfterAll {
  var server: Server         = uninitialized
  var httpServer: NodeServer = uninitialized
  val port                   = 3007 // Different port for rate limiter tests

  override def beforeAll(): Unit = {
    server = Server()
      .use(RateLimiterMiddleware()) // Test default configuration
      .get("/test", _ => "success".asText)

    httpServer = server.listen(port) {}
  }

  override def afterAll(): Unit = {
    if (httpServer != null) {
      httpServer.close(() => ())
    }
  }

  "RateLimiterMiddleware" - {
    "with default configuration" - {
      "should allow normal traffic" in {
        fetch(s"http://localhost:$port/test")
          .toFuture
          .flatMap { response =>
            response.status shouldBe 200
            // Verify rate limit headers are present
            response.headers.has("x-ratelimit-limit") shouldBe true
            response.headers.has("x-ratelimit-remaining") shouldBe true
            response.text().toFuture
          }
          .map { text =>
            text shouldBe "success"
          }
      }
    }
  }
}
