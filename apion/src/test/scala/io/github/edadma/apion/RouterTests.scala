package io.github.edadma.apion

import scala.concurrent.Future
import scala.scalajs.js
import io.github.edadma.nodejs.ServerRequest

class RouterTests extends AsyncBaseSpec {
  // Helper to create a mock ServerRequest
  def mockServerRequest(method: String, url: String, headers: Map[String, String] = Map()): ServerRequest = {
    val req = js.Dynamic.literal(
      method = method,
      url = url,
      headers = js.Dictionary(headers.toSeq*),
      on = (_: String, _: js.Function1[js.Any, Unit]) => js.Dynamic.literal(),
    )
    req.asInstanceOf[ServerRequest]
  }

  "Router" - {
    "should handle a simple GET request" in {
      val router       = new Router()
      val testResponse = Response(200, Map(), "test response")

      // Add a simple route
      router.get("/test", request => Future.successful(Complete(testResponse)))

      // Create a test request
      val request = Request.fromServerRequest(mockServerRequest("GET", "/test"))

      // Test the route
      router(request).map { result =>
        result shouldBe Complete(testResponse)
      }
    }

    "should handle a POST request" in {
      val router       = new Router()
      val testResponse = Response(201, Map(), "created")

      router.post("/users", request => Future.successful(Complete(testResponse)))

      val request = Request.fromServerRequest(mockServerRequest("POST", "/users"))

      router(request).map { result =>
        result shouldBe Complete(testResponse)
      }
    }

    "should skip non-matching POST request" in {
      val router       = new Router()
      val testResponse = Response(201, Map(), "created")

      router.post("/users", request => Future.successful(Complete(testResponse)))

      val request = Request.fromServerRequest(mockServerRequest("POST", "/notusers"))

      router(request).map { result =>
        result shouldBe Skip
      }
    }

    "should handle a POST request with correct status code" in {
      val router       = new Router()
      val testResponse = Response(201, Map(), "created")

      router.post("/users", request => Future.successful(Complete(testResponse)))

      val request = Request.fromServerRequest(mockServerRequest("POST", "/users"))

      router(request).map {
        case Complete(response) =>
          response.status shouldBe 201
          response.body shouldBe "created"
        case _ =>
          fail("Expected Complete response")
      }
    }
  }
}
