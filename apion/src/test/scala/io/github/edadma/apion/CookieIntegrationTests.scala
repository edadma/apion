package io.github.edadma.apion

import scala.concurrent.Future
import scala.scalajs.js
import io.github.edadma.nodejs.{FetchOptions, fetch, Server as NodeServer}
import org.scalatest.BeforeAndAfterAll
import zio.json.*

import scala.compiletime.uninitialized
import CookieMiddleware.CookieManagementOps // Import cookie extension methods

class CookieIntegrationTests extends AsyncBaseSpec with BeforeAndAfterAll {
  var server: Server         = uninitialized
  var httpServer: NodeServer = uninitialized
  val port                   = 3003 // Different port than other tests
  val secretKey              = "test-secret-key-1234"

  case class TestData(value: String, number: Int) derives JsonEncoder, JsonDecoder

  override def beforeAll(): Unit = {
    server = Server()
      .use(CookieMiddleware(CookieMiddleware.Options(
        secret = Some(secretKey),
        parseJSON = true,
      )))
      // Echo cookie value back
      .get(
        "/echo-cookie",
        request => {
          request.cookie("test-cookie") match {
            case Some(value) => text(value)
            case None        => text("no cookie")
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
          json(cookies)
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
      // Echo signed cookie value
      .get(
        "/echo-signed-cookie",
        request => {
          request.getSignedCookie("signed-cookie") match {
            case Some(value) => text(value)
            case None        => text("no signed cookie")
          }
        },
      )
      // Set signed cookie
      .get(
        "/set-signed-cookie",
        request => {
          request.signCookie("signed-cookie", "secret-value") match {
            case Some(cookie) =>
              Future.successful(Complete(
                Response.text("signed cookie set").withCookie(cookie),
              ))
            case None => text("signing failed", 500)
          }
        },
      )
      // Echo JSON cookie
      .get(
        "/echo-json-cookie",
        request => {
          request.getJsonCookie[TestData]("json-cookie") match {
            case Some(data) => json(data)
            case None       => text("no json cookie")
          }
        },
      )
      // Set JSON cookie
      .get(
        "/set-json-cookie",
        _ => {
          val data = TestData("test", 123)
          Future.successful(Complete(
            Response.text("json cookie set")
              .withCookie("json-cookie", data.toJson),
          ))
        },
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
          method = "GET",
          headers = js.Dictionary(
            "Cookie" -> "test-cookie=hello-world",
          ),
          body = null,
        )

        fetch(s"http://localhost:$port/echo-cookie", options)
          .toFuture
          .flatMap(response => response.text().toFuture)
          .map(text => text shouldBe "hello-world")
      }

      "should read multiple cookies from request" in {
        val options = FetchOptions(
          method = "GET",
          headers = js.Dictionary(
            "Cookie" -> "cookie1=value1; cookie2=value2",
          ),
          body = null,
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
          method = "GET",
          headers = js.Dictionary(
            "Cookie" -> "test-cookie=hello%20world",
          ),
          body = null,
        )

        fetch(s"http://localhost:$port/echo-cookie", options)
          .toFuture
          .flatMap(response => response.text().toFuture)
          .map(text => text shouldBe "hello world")
      }
    }

    "response cookies" - {
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
            val setCookie = response.headers.get("Set-Cookie")
            setCookie should (
              include("cookie1=value1") and
                include("cookie2=value2")
            )
          }
      }
    }

    "signed cookies" - {
      "should set and verify signed cookies" in {
        // First set a signed cookie
        fetch(s"http://localhost:$port/set-signed-cookie")
          .toFuture
          .flatMap { response =>
            val setCookie = response.headers.get("Set-Cookie")
            setCookie should include("signed-cookie=")

            // Then try to read it back
            val options = FetchOptions(
              method = "GET",
              headers = js.Dictionary(
                "Cookie" -> setCookie,
              ),
              body = null,
            )

            fetch(s"http://localhost:$port/echo-signed-cookie", options)
              .toFuture
              .flatMap(_.text().toFuture)
          }
          .map { text =>
            text shouldBe "secret-value"
          }
      }

      "should handle requests with tampered signed cookies" in {
        val options = FetchOptions(
          method = "GET",
          headers = js.Dictionary(
            "Cookie" -> "signed-cookie=tampered-value",
          ),
          body = null,
        )

        fetch(s"http://localhost:$port/echo-signed-cookie", options)
          .toFuture
          .flatMap(_.text().toFuture)
          .map { text =>
            text shouldBe "no signed cookie"
          }
      }
    }

    "JSON cookies" - {
      "should set and parse JSON cookies" in {
        // First set a JSON cookie
        fetch(s"http://localhost:$port/set-json-cookie")
          .toFuture
          .flatMap { response =>
            val setCookie = response.headers.get("Set-Cookie")
            setCookie should include("json-cookie=")

            // Then try to read it back
            val options = FetchOptions(
              method = "GET",
              headers = js.Dictionary(
                "Cookie" -> setCookie,
              ),
              body = null,
            )

            fetch(s"http://localhost:$port/echo-json-cookie", options)
              .toFuture
              .flatMap(_.text().toFuture)
          }
          .map { text =>
            text should include(""""value":"test"""")
            text should include(""""number":123""")
          }
      }

      "should handle invalid JSON cookies" in {
        val options = FetchOptions(
          method = "GET",
          headers = js.Dictionary(
            "Cookie" -> """json-cookie={"invalid": json}""",
          ),
          body = null,
        )

        fetch(s"http://localhost:$port/echo-json-cookie", options)
          .toFuture
          .flatMap(_.text().toFuture)
          .map { text =>
            text shouldBe "no json cookie"
          }
      }
    }
  }
}
