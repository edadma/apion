package io.github.edadma.apion

import scala.scalajs.js
import io.github.edadma.nodejs.{fetch, Server as NodeServer, FetchOptions}
import org.scalatest.BeforeAndAfterAll

import scala.compiletime.uninitialized
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class RateLimiterMiddlewareIntegrationTests extends AsyncBaseSpec with BeforeAndAfterAll {
  var server: Server         = uninitialized
  var httpServer: NodeServer = uninitialized
  val port                   = 3007

  override def beforeAll(): Unit = {
    server = Server()
      .use(RateLimiterMiddleware(RateLimiterMiddleware.Options(
        RateLimiterMiddleware.RateLimit(
          maxRequests = 3,
          window = 10.seconds,
          burst = 0,
          headers = true,
        ),
      )))
      .get("/test", _ => "success".asText)

    httpServer = server.listen(port) {}
  }

  override def afterAll(): Unit = {
    if (httpServer != null) {
      httpServer.close(() => ())
    }
  }

  "RateLimiterMiddleware" - {
    "should allow traffic within limit" in {
      fetch(s"http://localhost:$port/test")
        .toFuture
        .flatMap { response =>
          response.status shouldBe 200
          response.headers.has("x-ratelimit-limit") shouldBe true
          response.headers.get("x-ratelimit-limit") shouldBe "3"
          response.text().toFuture
        }
        .map { text =>
          text shouldBe "success"
        }
    }

    "should include rate limit headers" in {
      fetch(s"http://localhost:$port/test")
        .toFuture
        .map { response =>
          response.headers.has("x-ratelimit-limit") shouldBe true
          response.headers.has("x-ratelimit-remaining") shouldBe true
          response.headers.has("x-ratelimit-reset") shouldBe true
          response.headers.has("x-ratelimit-used") shouldBe true
          response.status shouldBe 200
        }
    }

    "should return 429 when limit exceeded" in {
      // Send requests until we exceed the limit
      val requests = (1 to 5).map { _ =>
        fetch(s"http://localhost:$port/test").toFuture
      }

      Future.sequence(requests).map { responses =>
        // At least one should be rate limited (429)
        val statuses = responses.map(_.status)
        statuses should contain(429)
      }
    }
  }
}
