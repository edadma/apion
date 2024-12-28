package io.github.edadma.apion

import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.{Future, ExecutionContext}

class RouterTests extends AsyncBaseSpec:
  // Need implicit EC for Future transformations
  override implicit def executionContext: ExecutionContext = ExecutionContext.global

  "Router" - {
    "basic routing" - {
      "should match exact paths" in {
        val router   = Router()
        val endpoint = (req: Request) => Future.successful(Response.text("Hello"))

        router.get("/hello", endpoint)

        val request         = Request("GET", "/hello", Map())
        val notFoundRequest = Request("GET", "/not-found", Map())

        for
          response         <- router.handle(request)
          notFoundResponse <- router.handle(notFoundRequest)
        yield
          response.status shouldBe 200
          response.body shouldBe "Hello"
          notFoundResponse.status shouldBe 404
      }

      "should support method chaining and different HTTP methods" in /*withDebugLogging(
        "should support method chaining and different HTTP methods",
      )*/ {
        val router       = Router()
        val getEndpoint  = (req: Request) => Future.successful(Response.text("GET"))
        val postEndpoint = (req: Request) => Future.successful(Response.text("POST"))

        // Chain method calls
        router
          .get("/test", getEndpoint)
          .post("/test", postEndpoint)

        val getRequest    = Request("GET", "/test", Map())
        val postRequest   = Request("POST", "/test", Map())
        val deleteRequest = Request("DELETE", "/test", Map())

        for
          getResponse    <- router.handle(getRequest)
          postResponse   <- router.handle(postRequest)
          deleteResponse <- router.handle(deleteRequest)
        yield
          getResponse.status shouldBe 200
          getResponse.body shouldBe "GET"
          postResponse.status shouldBe 200
          postResponse.body shouldBe "POST"
          deleteResponse.status shouldBe 405 // Method not allowed
      }

      "should handle path parameters" in {
        val router = Router()
        val endpoint = (req: Request) =>
          Future.successful(Response.text(s"User: ${req.context("userId")}"))

        router.get("/users/:userId", endpoint)

        val request = Request("GET", "/users/123", Map())

        router.handle(request).map { response =>
          response.status shouldBe 200
          response.body shouldBe "User: 123"
        }
      }

      "should handle multiple path parameters" in {
        val router = Router()
        val endpoint = (req: Request) =>
          Future.successful(Response.text(
            s"Post ${req.context("postId")} by ${req.context("userId")}",
          ))

        router.get("/users/:userId/posts/:postId", endpoint)

        val request = Request("GET", "/users/123/posts/456", Map())

        router.handle(request).map { response =>
          response.status shouldBe 200
          response.body shouldBe "Post 456 by 123"
        }
      }
    }

    "middleware" - {
      "should allow adding global middleware" in {
        val router = Router()

        // Add logging middleware
        router.use { endpoint => request =>
          endpoint(request.copy(
            context = request.context + ("requestId" -> "test-id"),
          ))
        }

        val endpoint = (req: Request) =>
          Future.successful(Response.text(s"RequestId: ${req.context("requestId")}"))

        router.get("/test", endpoint)

        val request = Request("GET", "/test", Map())

        router.handle(request).map { response =>
          response.status shouldBe 200
          response.body shouldBe "RequestId: test-id"
        }
      }
    }
  }
