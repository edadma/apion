package io.github.edadma.apion

import scala.concurrent.Future
import scala.scalajs.js
import io.github.edadma.nodejs.{fetch, Server => NodeServer, FetchOptions}
import org.scalatest.BeforeAndAfterAll
import scala.compiletime.uninitialized
import zio.json._

class ErrorHandlerIntegrationTests extends AsyncBaseSpec with BeforeAndAfterAll {
  var server: Server         = uninitialized
  var httpServer: NodeServer = uninitialized
  val port                   = 3008 // Different port for error handling tests

  // Custom error types for testing
  case class CustomError(message: String, code: String) extends ServerError {
    def toResponse: Response = Response.json(
      Map("error" -> code, "message" -> message),
      418, // I'm a teapot - distinctive status for testing
    )
  }

  case class TransformedError(message: String) extends ServerError {
    def toResponse: Response = Response.json(
      Map("error" -> "transformed", "message" -> message),
      422, // Another distinctive status
    )
  }

  override def beforeAll(): Unit = {
    server = Server()
      // Middleware that might error
      .use { request =>
        if (request.path == "/fail-middleware")
          Future.successful(Fail(ValidationError("Middleware failure")))
        else
          skip
      }

      // First error handler - only handles ValidationError
      .use { (error, request) =>
        error match {
          case e: ValidationError =>
            Future.successful(Complete(
              Response.json(
                Map("caught_by" -> "first_handler", "error" -> e.message),
                400,
              ),
            ))
          case _ => skip
        }
      }

      // Route that fails
      .get(
        "/fail-route",
        _ =>
          Future.successful(Fail(AuthError("Route failure"))),
      )

      // Another error handler - handles AuthError
      .use { (error, request) =>
        error match {
          case e: AuthError =>
            Future.successful(Complete(Response.json(
              Map("caught_by" -> "second_handler", "error" -> e.message),
              401,
            )))
          case _ => skip
        }
      }

      // Route with custom error
      .get(
        "/custom-error",
        _ =>
          Future.successful(Fail(CustomError("Custom failure", "CUSTOM_ERR"))),
      )

      // Route that transforms error
      .get(
        "/transform-error",
        _ =>
          Future.successful(Fail(ValidationError("Initial error"))),
      )

      // Error handler that transforms errors
      .use { (error, request) =>
        error match {
          case e: ValidationError =>
            Future.successful(Fail(TransformedError(s"Transformed: ${e.message}")))
          case _ => skip
        }
      }

      // Handler for transformed errors
      .use { (error, request) =>
        error match {
          case e: TransformedError => Future.successful(Complete(e.toResponse))
          case _                   => skip
        }
      }

      // Nested router with its own error handler
      .use(
        "/sub",
        Router()
          .get("/fail", _ => Future.successful(Fail(ValidationError("Nested failure"))))
          .use { (error, request) =>
            Future.successful(Complete(Response.json(
              Map("caught_by" -> "nested_handler", "error" -> error.message),
              400,
            )))
          },
      )

      // Final catch-all error handler
      .use { (error, request) =>
        Future.successful(Complete(Response.json(
          Map("caught_by" -> "final_handler", "error" -> error.message),
          500,
        )))
      }

    httpServer = server.listen(port) {}
  }

  override def afterAll(): Unit = {
    if (httpServer != null) {
      httpServer.close(() => ())
    }
  }

  "Error handlers" - {
    "should handle middleware errors" in {
      fetch(s"http://localhost:$port/fail-middleware")
        .toFuture
        .flatMap { response =>
          response.status shouldBe 400
          response.json().toFuture.map { result =>
            val json = js.JSON.stringify(result)
            json should include("first_handler")
            json should include("Middleware failure")
          }
        }
    }

    "should handle route errors" in {
      fetch(s"http://localhost:$port/fail-route")
        .toFuture
        .flatMap { response =>
          response.status shouldBe 401
          response.json().toFuture.map { result =>
            val json = js.JSON.stringify(result)
            json should include("second_handler")
            json should include("Route failure")
          }
        }
    }

//    "should handle custom errors" in {
//      fetch(s"http://localhost:$port/custom-error")
//        .toFuture
//        .flatMap { response =>
//          response.status shouldBe 418 // Custom status from CustomError
//          response.json().toFuture.map { result =>
//            val json = js.JSON.stringify(result)
//            json should include("CUSTOM_ERR")
//            json should include("Custom failure")
//          }
//        }
//    }

//    "should allow error transformation" in {
//      fetch(s"http://localhost:$port/transform-error")
//        .toFuture
//        .flatMap { response =>
//          response.status shouldBe 422 // Status from TransformedError
//          response.json().toFuture.map { result =>
//            val json = js.JSON.stringify(result)
//            json should include("transformed")
//            json should include("Initial error")
//          }
//        }
//    }

//    "should handle errors in nested routers" in {
//      fetch(s"http://localhost:$port/sub/fail")
//        .toFuture
//        .flatMap { response =>
//          response.status shouldBe 400
//          response.json().toFuture.map { result =>
//            val json = js.JSON.stringify(result)
//            json should include("nested_handler")
//            json should include("Nested failure")
//          }
//        }
//    }

    "should skip non-matching error handlers" in {
      // The ValidationError should skip the AuthError handler
      fetch(s"http://localhost:$port/fail-middleware")
        .toFuture
        .flatMap { response =>
          response.json().toFuture.map { result =>
            val json = js.JSON.stringify(result)
            json should include("first_handler")
            json should not include "second_handler"
          }
        }
    }
  }
}
