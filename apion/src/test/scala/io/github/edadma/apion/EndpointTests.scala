package io.github.edadma.apion

import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.ExecutionContext

class EndpointTests extends AsyncFreeSpec with Matchers:
  // Need implicit EC for Future transformations in tests
  override implicit val executionContext: ExecutionContext = ExecutionContext.global

  "Endpoints" - {
    "health check endpoint" - {
      "should return 200 OK with status information" in {
        // Define the endpoint
        def healthCheck: Endpoint = req =>
          val status = Map(
            "status"    -> "ok",
            "timestamp" -> System.currentTimeMillis.toString,
          )

          scala.concurrent.Future.successful(
            Response.json(status),
          )

        // Test the endpoint
        val request = Request(
          method = "GET",
          url = "/health",
          headers = Map(),
        )

        healthCheck(request).map { response =>
          response.status shouldBe 200
          response.headers("Content-Type") shouldBe "application/json"
          response.body should include("ok")
        }
      }
    }
  }