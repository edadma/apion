package io.github.edadma.apion

import scala.concurrent.Future
import scala.scalajs.js
import io.github.edadma.nodejs.{fetch, Server => NodeServer, FetchOptions}
import org.scalatest.BeforeAndAfterAll
import scala.compiletime.uninitialized

class CookieIntegrationTests extends AsyncBaseSpec with BeforeAndAfterAll {
  var server: Server         = uninitialized
  var httpServer: NodeServer = uninitialized
  val port                   = 3003 // Different port than other tests

  override def beforeAll(): Unit = {
    server = Server()
      // Echo cookie value back
      .get(
        "/echo-cookie",
        request => {
          request.cookie("test-cookie") match {
            case Some(value) => value.asText
            case None        => "no cookie".asText
          }
        },
      )
      // Echo multiple cookies back as JSON
      .get(
        "/echo-cookies",
        request => {
          val cookies = Map(
            "cookie1" -> request.cookie("cookie1"),
            "cookie2" -> request.cookie("cookie2"),
          ).collect { case (name, Some(value)) => name -> value }
          cookies.asJson
        },
      )
      // Set a simple cookie
      .get(
        "/set-cookie",
        _ =>
          Future.successful(Complete(
            Response.text("cookie set")
              .withCookie("test-cookie", "hello"),
          )),
      )
      // Set cookie with attributes
      .get(
        "/set-cookie-attrs",
        _ =>
          Future.successful(Complete(
            Response.text("cookie set with attributes")
              .withCookie(
                "session",
                "abc123",
                maxAge = Some(3600),
                httpOnly = true,
                secure = true,
                path = Some("/api"),
              ),
          )),
      )
      // Set multiple cookies
      .get(
        "/set-multiple-cookies",
        _ =>
          Future.successful(Complete(
            Response.text("multiple cookies set")
              .withCookie("cookie1", "value1")
              .withCookie("cookie2", "value2"),
          )),
      )

    httpServer = server.listen(port) {}
  }

  override def afterAll(): Unit = {
    if (httpServer != null) {
      httpServer.close(() => ())
    }
  }

  "Cookie handling" - {
    "request cookies" - {
      "should handle request with no cookies" in {
        fetch(s"http://localhost:$port/echo-cookie")
          .toFuture
          .flatMap(response => response.text().toFuture)
          .map(text => text shouldBe "no cookie")
      }

      "should read single cookie from request" in {
        val options = FetchOptions(
          headers = js.Dictionary(
            "Cookie" -> "test-cookie=hello-world",
          ),
        )

        fetch(s"http://localhost:$port/echo-cookie", options)
          .toFuture
          .flatMap(response => response.text().toFuture)
          .map(text => text shouldBe "hello-world")
      }

      "should read multiple cookies from request" in {
        val options = FetchOptions(
          headers = js.Dictionary(
            "Cookie" -> "cookie1=value1; cookie2=value2",
          ),
        )

        fetch(s"http://localhost:$port/echo-cookies", options)
          .toFuture
          .flatMap(response => response.text().toFuture)
          .map { text =>
            val expected = """{"cookie1":"value1","cookie2":"value2"}"""
            text shouldBe expected
          }
      }

      "should handle URL-encoded cookie values" in {
        val options = FetchOptions(
          headers = js.Dictionary(
            "Cookie" -> "test-cookie=hello%20world",
          ),
        )

        fetch(s"http://localhost:$port/echo-cookie", options)
          .toFuture
          .flatMap(response => response.text().toFuture)
          .map(text => text shouldBe "hello world")
      }
    }

    "response cookies" - {
      "should set a basic cookie" in {
        fetch(s"http://localhost:$port/set-cookie")
          .toFuture
          .map { response =>
            val setCookie = response.headers.get("Set-Cookie")
            setCookie shouldBe "test-cookie=hello"
          }
      }

      "should set cookie with attributes" in {
        fetch(s"http://localhost:$port/set-cookie-attrs")
          .toFuture
          .map { response =>
            val setCookie = response.headers.get("Set-Cookie")
            setCookie should (
              include("session=abc123") and
                include("Max-Age=3600") and
                include("HttpOnly") and
                include("Secure") and
                include("Path=/api")
            )
          }
      }

      "should set multiple cookies" in {
        fetch(s"http://localhost:$port/set-multiple-cookies")
          .toFuture
          .map { response =>
            // Get all Set-Cookie headers
            val setCookies = response.headers
              .get("Set-Cookie")
              .split(",")
              .map(_.trim)
              .toSet

            setCookies should contain("cookie1=value1")
            setCookies should contain("cookie2=value2")
          }
      }
    }
  }
}
