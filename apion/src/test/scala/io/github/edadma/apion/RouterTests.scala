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
    "HTTP Methods" - {
      "should handle GET requests" in {
        val router       = new Router()
        val testResponse = Response(200, ResponseHeaders.empty, "get response")

        router.get("/test", request => Future.successful(Complete(testResponse)))
        val request = Request.fromServerRequest(mockServerRequest("GET", "/test"))

        router(request).map { result =>
          result should matchPattern {
            case InternalComplete(_, `testResponse`) =>
          }
        }
      }

      "should skip non-matching POST request" in {
        val router       = new Router()
        val testResponse = Response(201, ResponseHeaders.empty, "created")

        router.post("/users", request => Future.successful(Complete(testResponse)))

        val request = Request.fromServerRequest(mockServerRequest("POST", "/notusers"))

        router(request).map { result =>
          result shouldBe Skip
        }
      }

      "should handle a POST request" in {
        val router       = new Router()
        val testResponse = Response(201, ResponseHeaders.empty, "created")

        router.post("/users", request => Future.successful(Complete(testResponse)))

        val request = Request.fromServerRequest(mockServerRequest("POST", "/users"))

        router(request).map {
          case InternalComplete(_, response) =>
            response.status shouldBe 201
            response.body shouldBe "created"
          case _ =>
            fail("Expected Complete response")
        }
      }

      "should handle PUT requests" in {
        val router       = new Router()
        val testResponse = Response(200, ResponseHeaders.empty, "updated")

        router.put("/test", request => Future.successful(Complete(testResponse)))
        val request = Request.fromServerRequest(mockServerRequest("PUT", "/test"))

        router(request).map { result =>
          result should matchPattern {
            case InternalComplete(_, `testResponse`) =>
          }
        }
      }

      "should handle DELETE requests" in {
        val router       = new Router()
        val testResponse = Response(204, ResponseHeaders.empty, "")

        router.delete("/test", request => Future.successful(Complete(testResponse)))
        val request = Request.fromServerRequest(mockServerRequest("DELETE", "/test"))

        router(request).map { result =>
          result should matchPattern {
            case InternalComplete(_, `testResponse`) =>
          }
        }
      }

      "should handle PATCH requests" in {
        val router       = new Router()
        val testResponse = Response(200, ResponseHeaders.empty, "patched")

        router.patch("/test", request => Future.successful(Complete(testResponse)))
        val request = Request.fromServerRequest(mockServerRequest("PATCH", "/test"))

        router(request).map { result =>
          result should matchPattern {
            case InternalComplete(_, `testResponse`) =>
          }
        }
      }

      "should Skip when method doesn't match" in {
        val router       = new Router()
        val testResponse = Response(200, ResponseHeaders.empty, "test")

        router.get("/test", request => Future.successful(Complete(testResponse)))
        val request = Request.fromServerRequest(mockServerRequest("POST", "/test"))

        router(request).map { result =>
          result shouldBe Skip
        }
      }
    }

    "Path Parameters" - {
      "should extract single path parameter" in {
        val router = new Router()

        router.get(
          "/users/:id",
          request =>
            Future.successful(Complete(Response(200, ResponseHeaders.empty, request.params("id")))),
        )

        val request = Request.fromServerRequest(mockServerRequest("GET", "/users/123"))

        router(request).map {
          case InternalComplete(_, response) =>
            response.body shouldBe "123"
          case _ =>
            fail("Expected Complete with path parameter")
        }
      }

      "should extract multiple path parameters" in {
        val router = new Router()

        router.get(
          "/users/:userId/posts/:postId",
          request => {
            val result = s"${request.params("userId")}-${request.params("postId")}"
            Future.successful(Complete(Response(200, ResponseHeaders.empty, result)))
          },
        )

        val request = Request.fromServerRequest(mockServerRequest("GET", "/users/123/posts/456"))

        router(request).map {
          case InternalComplete(_, response) =>
            response.body shouldBe "123-456"
          case _ =>
            fail("Expected Complete with multiple parameters")
        }
      }

      "should handle mixed static and parameter segments" in {
        val router = new Router()

        router.get(
          "/api/users/:id/profile",
          request =>
            Future.successful(Complete(Response(200, ResponseHeaders.empty, request.params("id")))),
        )

        val request = Request.fromServerRequest(mockServerRequest("GET", "/api/users/123/profile"))

        router(request).map {
          case InternalComplete(_, response) =>
            response.body shouldBe "123"
          case _ =>
            fail("Expected Complete with parameter")
        }
      }

      "should Skip for non-matching parameter paths" in {
        val router = new Router()

        router.get(
          "/users/:id",
          request =>
            Future.successful(Complete(Response(200, ResponseHeaders.empty, request.params("id")))),
        )

        val request = Request.fromServerRequest(mockServerRequest("GET", "/posts/123"))

        router(request).map { result =>
          result shouldBe Skip
        }
      }
    }

    "Nested Routes" - {
      "should handle basic subrouter mounting" in {
        val router    = new Router()
        val subrouter = new Router()

        subrouter.get(
          "/test",
          request =>
            Future.successful(Complete(Response(200, ResponseHeaders.empty, "subroute"))),
        )

        router.use("/api", subrouter)
        val request = Request.fromServerRequest(mockServerRequest("GET", "/api/test"))

        router(request).map {
          case InternalComplete(_, response) =>
            response.body shouldBe "subroute"
          case _ =>
            fail("Expected Complete from subrouter")
        }
      }

      "should handle subrouter with path parameters" in {
        val router    = new Router()
        val subrouter = new Router()

        subrouter.get(
          "/:id",
          request =>
            Future.successful(Complete(Response(200, ResponseHeaders.empty, request.params("id")))),
        )

        router.use("/users", subrouter)
        val request = Request.fromServerRequest(mockServerRequest("GET", "/users/123"))

        router(request).map {
          case InternalComplete(_, response) =>
            response.body shouldBe "123"
          case _ =>
            fail("Expected Complete from subrouter with params")
        }
      }

      "should accumulate base path through nesting" in {
        val router      = new Router()
        val apiRouter   = new Router()
        val usersRouter = new Router()

        usersRouter.get(
          "/profile",
          request =>
            Future.successful(Complete(Response(200, ResponseHeaders.empty, request.basePath))),
        )

        apiRouter.use("/users", usersRouter)
        router.use("/api", apiRouter)

        val request = Request.fromServerRequest(mockServerRequest("GET", "/api/users/profile"))

        router(request).map {
          case InternalComplete(_, response) =>
            response.body shouldBe "/api/users"
          case _ =>
            fail("Expected Complete with accumulated base path")
        }
      }
    }

    "Middleware" - {
      "should execute global middleware" in {
        val router = new Router()

        router.use(request =>
          Future.successful(Continue(request.copy(
            headers = request.headers + ("X-Test" -> "middleware"),
          ))),
        )

        router.get(
          "/test",
          request =>
            Future.successful(Complete(Response(200, ResponseHeaders.empty, request.headers("X-Test")))),
        )

        val request = Request.fromServerRequest(mockServerRequest("GET", "/test"))

        router(request).map {
          case InternalComplete(_, response) =>
            response.body shouldBe "middleware"
          case _ =>
            fail("Expected Complete with middleware modification")
        }
      }

      "should handle path-specific middleware" in {
        val router = new Router()

        router.use(
          "/protected",
          request =>
            Future.successful(Continue(request.copy(
              headers = request.headers + ("X-Auth" -> "true"),
            ))),
        )

        router.get(
          "/protected/resource",
          request =>
            Future.successful(Complete(Response(200, ResponseHeaders.empty, request.headers("X-Auth")))),
        )

        val request = Request.fromServerRequest(mockServerRequest("GET", "/protected/resource"))

        router(request).map {
          case InternalComplete(_, response) =>
            response.body shouldBe "true"
          case _ =>
            fail("Expected Complete with path middleware")
        }
      }

      "should allow middleware to terminate early" in {
        val router = new Router()

        router.use(request =>
          Future.successful(Complete(Response(403, ResponseHeaders.empty, "blocked"))),
        )

        router.get(
          "/test",
          request =>
            Future.successful(Complete(Response(200, ResponseHeaders.empty, "should not reach"))),
        )

        val request = Request.fromServerRequest(mockServerRequest("GET", "/test"))

        router(request).map {
          case InternalComplete(_, response) =>
            response.status shouldBe 403
            response.body shouldBe "blocked"
          case _ =>
            fail("Expected Complete from middleware")
        }
      }
    }
  }
}
