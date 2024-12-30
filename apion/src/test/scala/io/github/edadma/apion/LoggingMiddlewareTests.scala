package io.github.edadma.apion

import scala.concurrent.Future
import scala.scalajs.js
import io.github.edadma.nodejs.ServerRequest
import org.scalatest.compatible.Assertion
import concurrent.ExecutionContext.Implicits.global

class LoggingMiddlewareTests extends AsyncBaseSpec:
  // Helper to create test requests
  def mockServerRequest(
      method: String = "GET",
      url: String = "/",
      headers: Map[String, String] = Map(),
  ): ServerRequest =
    val req = js.Dynamic.literal(
      method = method,
      url = url,
      headers = js.Dictionary(headers.toSeq*),
      on = (_: String, _: js.Function1[js.Any, Unit]) => js.Dynamic.literal(),
    )
    req.asInstanceOf[ServerRequest]

  // Test response for verifying logging
  val testResponse = Response(200, Map("Content-Length" -> "42"), "test response")

  // Test handler that returns our test response
  val testHandler: Handler = _ => Future.successful(Complete(testResponse))

  "LoggingMiddleware" - {
    "with default options" - {
      "should log requests" in {
        var capturedLog                 = ""
        val testHandler: String => Unit = msg => capturedLog = msg

        val request = Request.fromServerRequest(mockServerRequest(
          method = "GET",
          url = "/test",
          headers = Map(
            "user-agent"     -> "test-agent",
            "content-length" -> "123",
          ),
        ))

        val middleware = LoggingMiddleware(LoggingMiddleware.Options(handler = testHandler))
        val handler = middleware(request).flatMap {
          case Skip  => this.testHandler(request)
          case other => Future.successful(other)
        }

        handler.map { result =>
          result shouldBe Complete(testResponse)
          capturedLog should include("GET /test")
          capturedLog should include("200")
          capturedLog should include("42") // Content-Length from response
          succeed
        }
      }

      "should respect skip option" in {
        var loggedCalled                = false
        val testHandler: String => Unit = _ => loggedCalled = true

        val request = Request.fromServerRequest(mockServerRequest(
          method = "GET",
          url = "/health",
        ))

        val middleware = LoggingMiddleware(LoggingMiddleware.Options(
          handler = testHandler,
          skip = req => req.url.startsWith("/health"),
        ))

        val handler = middleware(request).flatMap {
          case Skip  => this.testHandler(request)
          case other => Future.successful(other)
        }

        handler.map { result =>
          result shouldBe Complete(testResponse)
          loggedCalled shouldBe false // Should not log skipped requests
          succeed
        }
      }

      "should handle immediate logging" in {
        var capturedLog                 = ""
        val testHandler: String => Unit = msg => capturedLog = msg

        val request = Request.fromServerRequest(mockServerRequest(
          method = "POST",
          url = "/api/users",
        ))

        val middleware = LoggingMiddleware(LoggingMiddleware.Options(
          handler = testHandler,
          immediate = true,
        ))

        middleware(request).map { result =>
          result shouldBe Skip
          capturedLog should include("POST /api/users")
          capturedLog should include("-") // Status code placeholder for immediate logging
          succeed
        }
      }
    }

    "format tokens" - {
      "should handle all predefined formats" in {
        var capturedLogs                = List[String]()
        val testHandler: String => Unit = msg => capturedLogs = msg :: capturedLogs

        val request = Request.fromServerRequest(mockServerRequest(
          method = "GET",
          url = "/test",
          headers = Map(
            "user-agent"      -> "test-agent",
            "referer"         -> "http://test.com",
            "x-forwarded-for" -> "1.2.3.4",
          ),
        ))

        // Test each predefined format
        val formats = List(
          LoggingMiddleware.Format.Combined,
          LoggingMiddleware.Format.Common,
          LoggingMiddleware.Format.Dev,
          LoggingMiddleware.Format.Short,
          LoggingMiddleware.Format.Tiny,
        )

        Future.sequence(
          formats.map { format =>
            val middleware = LoggingMiddleware(LoggingMiddleware.Options(
              format = format,
              handler = testHandler,
            ))
            middleware(request).flatMap {
              case Skip  => this.testHandler(request)
              case other => Future.successful(other)
            }
          },
        ).map { results =>
          // Verify each format produced a log
          capturedLogs.length shouldBe formats.length

          // Verify key elements present in logs
          capturedLogs.foreach { msg =>
            msg should include("GET")
            msg should include("/test")
            msg should include("200") // From test response
          }
          succeed
        }
      }
    }
  }
