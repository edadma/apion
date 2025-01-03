package io.github.edadma.apion

import scala.concurrent.Future
import scala.scalajs.js
import io.github.edadma.nodejs.ServerRequest
import org.scalatest.compatible.Assertion

class LoggingMiddlewareTests extends AsyncBaseSpec:
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

  val testResponse: Response = Response.text("test response")

  // Create a handler that logs and returns the response
  def createLoggingHandler(): Handler = request =>
    // Get stored timing info from context
    (for
      startTime <- request.context.get("logging-start-time").map(_.asInstanceOf[Long])
      format    <- request.context.get("logging-format").map(_.asInstanceOf[String])
      handler   <- request.context.get("logging-handler").map(_.asInstanceOf[String => Unit])
    yield (startTime, format, handler)) match
      case Some((startTime, format, handler)) =>
        val logMsg = LoggingMiddleware.formatRequestLog(format, request, startTime, testResponse)
        handler(logMsg) // Use the original handler
      case None =>
        ()
    Future.successful(Complete(testResponse))

  "LoggingMiddleware" - {
    "with default options" - {
      "should log requests" in {
        var capturedLog                = ""
        val logHandler: String => Unit = msg => capturedLog = msg

        val request = Request.fromServerRequest(mockServerRequest(
          method = "GET",
          url = "/test",
          headers = Map(
            "user-agent"     -> "test-agent",
            "content-length" -> "123",
          ),
        ))

        val middleware = LoggingMiddleware(LoggingMiddleware.Options(
          handler = logHandler,
          debug = true,
        ))

        for
          result <- middleware(request)
          finalResult <- result match
            case Continue(req) => createLoggingHandler()(req)
            case other         => Future.successful(other)
        yield
          logger.debug(s"Final captured log: $capturedLog")
          capturedLog should include("GET /test")
          capturedLog should include("200")
          capturedLog should include("13")
          succeed
      }

      "should respect skip option" in {
        var loggedCalled               = false
        val logHandler: String => Unit = _ => loggedCalled = true

        val request = Request.fromServerRequest(mockServerRequest(
          method = "GET",
          url = "/health",
        ))

        val middleware = LoggingMiddleware(LoggingMiddleware.Options(
          handler = logHandler,
          skip = req => req.url.startsWith("/health"),
          debug = true,
        ))

        for
          result <- middleware(request)
          finalResult <- result match
            case Continue(req) => createLoggingHandler()(req)
            case other         => Future.successful(other)
        yield
          loggedCalled shouldBe false
          succeed
      }

      "should handle immediate logging" in {
        var capturedLog                = ""
        val logHandler: String => Unit = msg => capturedLog = msg

        val request = Request.fromServerRequest(mockServerRequest(
          method = "POST",
          url = "/api/users",
        ))

        val middleware = LoggingMiddleware(LoggingMiddleware.Options(
          handler = logHandler,
          immediate = true,
          debug = true,
        ))

        middleware(request).map { result =>
          result shouldBe Continue(request)
          capturedLog should include("POST /api/users")
          capturedLog should include("-")
          succeed
        }
      }
    }

    "format tokens" - {
      "should handle all predefined formats" in /*withDebugLogging("should handle all predefined formats")*/ {
        var capturedLogs               = List[String]()
        val logHandler: String => Unit = msg => capturedLogs = msg :: capturedLogs

        val request = Request.fromServerRequest(mockServerRequest(
          method = "GET",
          url = "/test",
          headers = Map(
            "user-agent"      -> "test-agent",
            "referer"         -> "http://test.com",
            "x-forwarded-for" -> "1.2.3.4",
          ),
        ))

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
              handler = logHandler,
              debug = true,
            ))
            for
              result <- middleware(request)
              finalResult <- result match
                case Continue(req) => createLoggingHandler()(req)
                case other         => Future.successful(other)
            yield finalResult
          },
        ).map { results =>
          logger.debug(s"All captured logs: ${capturedLogs.mkString("\n")}")
          capturedLogs.length shouldBe formats.length
          logger.debug(s"Checking logs:\n${capturedLogs.mkString("\n")}")
          capturedLogs.foreach { msg =>
            logger.debug(s"Checking log: $msg")
            // Use regex to handle quoted and unquoted methods
            msg should (include("GET /test") or include("\"GET /test\""))
            msg should include("200")
            msg should include("42")
          }
          succeed
        }
      }
    }
  }
