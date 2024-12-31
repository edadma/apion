package io.github.edadma.apion

import org.scalatest.BeforeAndAfterAll
import scala.compiletime.uninitialized
import scala.concurrent.Future
import scala.scalajs.js
import io.github.edadma.nodejs.{Server => NodeServer, fetch, Response, FetchOptions}
import zio.json._
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

class CookieIntegrationTests extends AsyncBaseSpec with BeforeAndAfterAll {
  var server: Server         = uninitialized
  var httpServer: NodeServer = uninitialized
  val port                   = 3003
  val testSecret             = "test-secret-key-123"

  override def beforeAll(): Unit = {
    server = Server()
      .use(CookieMiddleware(CookieMiddleware.Options(
        secret = testSecret,
        secure = false, // Allow non-HTTPS for testing
      )))
      .get(
        "/set-cookie",
        request => {
          logger.debug("Handler /set-cookie called")
          val withCookie = request.setCookie(Cookie(
            name = "test-cookie",
            value = "test-value",
            maxAge = Some(3600),
          ))
          logger.debug(s"Cookie context: ${withCookie.context}")
          Future.successful(InternalComplete(withCookie, Response.text("Cookie set")))
        },
      )
      .get(
        "/read-cookie",
        request => {
          request.cookie("test-cookie") match {
            case Some(value) => Future.successful(Complete(Response.text(value)))
            case None        => Future.successful(Complete(Response(404, body = "No cookie")))
          }
        },
      )
      .get(
        "/read-all-cookies",
        request => {
          Future.successful(Complete(Response.json(request.cookies)))
        },
      )
      .get(
        "/clear-cookie",
        request => {
          val cookieRequest = request.clearCookie("test-cookie")
          Future.successful(Complete(Response.text("Cookie cleared")))
        },
      )
      .get(
        "/multiple-cookies",
        request => {
          val cookieRequest = request
            .setCookie(Cookie("cookie1", "value1"))
            .setCookie(Cookie("cookie2", "value2"))
          Future.successful(Complete(Response.text("Multiple cookies set")))
        },
      )

    httpServer = server.listen(port) {}
  }

  override def afterAll(): Unit = {
    if (httpServer != null) {
      httpServer.close(() => ())
    }
  }

  "Cookie Middleware" - {
    "should set a cookie" in {
      fetch(s"http://localhost:$port/set-cookie")
        .toFuture
        .map { response =>
          logger.debug(s"Response headers: ${response.headers}")
          Option(response.headers.get("set-cookie")) match {
            case Some(cookieHeader) =>
              logger.debug(s"Cookie header: $cookieHeader")
              cookieHeader should include("test-cookie=test-value")
              cookieHeader should include("Max-Age=3600")
              cookieHeader should include("HttpOnly")
            case None =>
              fail("No Set-Cookie header found")
          }
        }
    }

    "should read a cookie" in {
      val cookieHeader = js.Dictionary("cookie" -> "test-cookie=test-value")
      val options      = FetchOptions(headers = cookieHeader)

      fetch(s"http://localhost:$port/read-cookie", options)
        .toFuture
        .flatMap(response => response.text().toFuture)
        .map { text =>
          text shouldBe "test-value"
        }
    }

    "should return 404 when cookie not found" in {
      fetch(s"http://localhost:$port/read-cookie")
        .toFuture
        .map { response =>
          response.status shouldBe 404
        }
    }

    "should clear a cookie" in {
      fetch(s"http://localhost:$port/clear-cookie")
        .toFuture
        .map { response =>
          Option(response.headers.get("set-cookie")) match {
            case Some(cookieHeader) =>
              logger.debug(s"Clear cookie header: $cookieHeader")
              cookieHeader should include("test-cookie=")
              cookieHeader should include("Expires=Thu, 01 Jan 1970")
            case None =>
              fail("No Set-Cookie header found")
          }
        }
    }

    "should handle multiple cookies" in {
      fetch(s"http://localhost:$port/multiple-cookies")
        .toFuture
        .map { response =>
          Option(response.headers.get("set-cookie")) match {
            case Some(cookieHeader) =>
              logger.debug(s"Multiple cookies header: $cookieHeader")
              cookieHeader should include("cookie1=value1")
              cookieHeader should include("cookie2=value2")
            case None =>
              fail("No Set-Cookie header found")
          }
        }
    }

    "should read all cookies" in {
      val cookieHeader = js.Dictionary(
        "cookie" -> "cookie1=value1; cookie2=value2",
      )
      val options = FetchOptions(headers = cookieHeader)

      fetch(s"http://localhost:$port/read-all-cookies", options)
        .toFuture
        .flatMap(response => response.text().toFuture)
        .map { text =>
          text should include("cookie1")
          text should include("value1")
          text should include("cookie2")
          text should include("value2")
        }
    }
  }
}
