package io.github.edadma.apion

import scala.concurrent.{Future, ExecutionContext}

class SubrouterTests extends AsyncBaseSpec:
  // Need implicit EC for Future transformations
  override implicit def executionContext: ExecutionContext = ExecutionContext.global

  "Subrouter" - {
    "basic subrouting" - {
      "should handle routes with prefixes" in {
        val router    = Router()
        val apiRouter = router.route("/api")

        val endpoint = (req: Request) =>
          Future.successful(Response.text("API endpoint"))

        apiRouter.get("/test", endpoint)

        // Test that /api/test works
        val request = Request("GET", "/api/test", Map())

        router.handle(request).map { response =>
          response.status shouldBe 200
          response.body shouldBe "API endpoint"
        }
      }

      "should support all HTTP methods" in {
        val router    = Router()
        val apiRouter = router.route("/api")

        val endpoint = (req: Request) =>
          Future.successful(Response.text(s"${req.method} endpoint"))

        apiRouter.get("/test", endpoint)
        apiRouter.post("/test", endpoint)
        apiRouter.put("/test", endpoint)
        apiRouter.delete("/test", endpoint)
        apiRouter.patch("/test", endpoint)
        apiRouter.options("/test", endpoint)
        apiRouter.head("/test", endpoint)

        val methods = List("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD")

        Future.sequence(methods.map { method =>
          val request = Request(method, "/api/test", Map())
          router.handle(request).map { response =>
            response.status shouldBe 200
            if method != "HEAD" then // HEAD responses should not have a body
              response.body shouldBe s"$method endpoint"
          }
        }).map(_ => succeed)
      }

      "should return 404 for non-existent subroutes" in {
        val router    = Router()
        val apiRouter = router.route("/api")

        val endpoint = (req: Request) =>
          Future.successful(Response.text("API endpoint"))

        apiRouter.get("/test", endpoint)

        // Test that /wrong/test returns 404
        val request = Request("GET", "/wrong/test", Map())

        router.handle(request).map { response =>
          response.status shouldBe 404
        }
      }

      "should pass along path parameters" in withDebugLogging("should pass along path parameters") {
        val router      = Router()
        val usersRouter = router.route("/users")

        val endpoint = (req: Request) =>
          Future.successful(Response.text(s"User ID: ${req.context("id")}"))

        usersRouter.get("/:id", endpoint)

        val request = Request("GET", "/users/123", Map())

        router.handle(request).map { response =>
          response.status shouldBe 200
          response.body shouldBe "User ID: 123"
        }
      }
    }

    "middleware inheritance" - {
      "should apply parent middleware to subroutes" in {
        val router = Router()

        // Add middleware to parent router
        router.use { endpoint => request =>
          endpoint(request.copy(
            context = request.context + ("trace" -> "parent"),
          ))
        }

        val apiRouter = router.route("/api")

        val endpoint = (req: Request) =>
          Future.successful(Response.text(s"Trace: ${req.context("trace")}"))

        apiRouter.get("/test", endpoint)

        val request = Request("GET", "/api/test", Map())

        router.handle(request).map { response =>
          response.status shouldBe 200
          response.body shouldBe "Trace: parent"
        }
      }

      "should apply middleware in correct order" in {
        val router = Router()

        // Add middleware to parent router - starts trace chain
        router.use { endpoint => request =>
          endpoint(request.copy(
            context = request.context + ("trace" -> List("parent")),
          ))
        }

        val apiRouter = router.route("/api")

        // Add middleware to subrouter - appends to trace chain
        apiRouter.use { endpoint => request =>
          val currentTrace = request.context.getOrElse("trace", List.empty[String]).asInstanceOf[List[String]]
          endpoint(request.copy(
            context = request.context + ("trace" -> (currentTrace :+ "child")),
          ))
        }

        val endpoint = (req: Request) =>
          val trace = req.context.getOrElse("trace", List.empty[String]).asInstanceOf[List[String]].mkString("-")
          Future.successful(Response.text(s"Trace: $trace"))

        apiRouter.get("/test", endpoint)

        val request = Request("GET", "/api/test", Map())

        router.handle(request).map { response =>
          response.status shouldBe 200
          response.body shouldBe "Trace: parent-child"
        }
      }
    }

    "nested routers" - {
      "should handle multiple levels of nesting" in /*withDebugLogging("should handle multiple levels of nesting")*/ {
        val router    = Router()
        val apiRouter = router.route("/api")
        val v1Router  = apiRouter.route("/v1")

        val endpoint = (req: Request) =>
          Future.successful(Response.text("v1 API endpoint"))

        v1Router.get("/test", endpoint)

        val request = Request("GET", "/api/v1/test", Map())

        router.handle(request).map { response =>
          response.status shouldBe 200
          response.body shouldBe "v1 API endpoint"
        }
      }

      "should handle path parameters at each level" in {
        val router      = Router()
        val usersRouter = router.route("/users/:userId")
        val postsRouter = usersRouter.route("/posts/:postId")

        val endpoint = (req: Request) =>
          Future.successful(Response.text(
            s"User ${req.context("userId")} Post ${req.context("postId")}",
          ))

        postsRouter.get("/comments", endpoint)

        val request = Request("GET", "/users/123/posts/456/comments", Map())

        router.handle(request).map { response =>
          response.status shouldBe 200
          response.body shouldBe "User 123 Post 456"
        }
      }
    }
  }
