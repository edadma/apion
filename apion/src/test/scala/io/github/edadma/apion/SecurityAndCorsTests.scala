package io.github.edadma.apion

import scala.concurrent.Future
import scala.scalajs.js
import io.github.edadma.nodejs.{fetch, Server => NodeServer, FetchOptions}
import org.scalatest.BeforeAndAfterAll
import scala.compiletime.uninitialized

class SecurityAndCorsTests extends AsyncBaseSpec with BeforeAndAfterAll {
  var server: Server         = uninitialized
  var httpServer: NodeServer = uninitialized
  val port                   = 3002 // Different port than other tests

  override def beforeAll(): Unit = {
    server = Server()
      // Test endpoint with security headers - apply security middleware last
      .use(SecurityMiddleware(SecurityMiddleware.Options(
        contentSecurityPolicy = true,
        cspDirectives = Map(
          "default-src" -> "'self'",
          "script-src"  -> "'self'",
          "object-src"  -> "'none'",
        ),
        frameguard = true,
        frameguardAction = "DENY",
        xssFilter = true,
        xssFilterMode = "1; mode=block",
        noSniff = true,
        referrerPolicy = true,
        referrerPolicyDirective = "no-referrer",
      )))
      .get(
        "/secure",
        request => {
          logger.debug(s"Handling /secure request with ${request.finalizers.length} finalizers")
          "secure endpoint".asText
        },
      )

      // Test endpoint with CORS
      .use(CorsMiddleware(CorsMiddleware.Options(
        origin = CorsMiddleware.Origin.Multiple(Set("http://allowed-origin.com")),
        methods = Set("GET", "POST"),
        allowedHeaders = Set("Content-Type", "X-Custom-Header"),
        exposedHeaders = Set("X-Test-Header"),
        credentials = true,
      )))
      .get("/cors", _ => "cors endpoint".asText)

      // Test endpoint with both
      .use(SecurityMiddleware())
      .use(CorsMiddleware())
      .get("/both", _ => "both middlewares".asText)

    httpServer = server.listen(port) {}
  }

  override def afterAll(): Unit = {
    if (httpServer != null) {
      httpServer.close(() => ())
    }
  }

  "SecurityMiddleware" - {
    "should set basic security headers" in withDebugLogging("security-headers") {
      fetch(s"http://localhost:$port/secure")
        .toFuture
        .map { response =>
          val headers = response.headers

          println(headers)
          // Log response headers for debugging
          logger.debug(s"CSP header present: ${headers.has("content-security-policy")}")
          val headerChecks = List(
            "content-security-policy",
            "x-frame-options",
            "x-content-type-options",
            "x-xss-protection",
            "referrer-policy",
          ).map { h =>
            val exists = headers.has(h)
            val value  = if (exists) headers.get(h) else "not present"
            s"$h: exists=$exists, value=$value"
          }

          logger.debug(s"Header checks: ${headerChecks.mkString("\n")}")

          headers.has("content-security-policy") shouldBe true
          headers.has("x-frame-options") shouldBe true
          headers.has("x-content-type-options") shouldBe true
          headers.has("x-xss-protection") shouldBe true
          headers.has("referrer-policy") shouldBe true
        }
    }

    "should set CSP header with default directives" in {
      fetch(s"http://localhost:$port/secure")
        .toFuture
        .map { response =>
          val csp = Option(response.headers.get("Content-Security-Policy")).getOrElse("")

          csp should (
            include("default-src 'self'") and
              include("script-src 'self'") and
              include("object-src 'none'")
          )
        }
    }
  }

  "CorsMiddleware" - {
    "should handle preflight requests" in {
      val options = FetchOptions(
        method = "OPTIONS",
        headers = js.Dictionary(
          "Origin"                         -> "http://allowed-origin.com",
          "Access-Control-Request-Method"  -> "POST",
          "Access-Control-Request-Headers" -> "Content-Type",
        ),
      )

      fetch(s"http://localhost:$port/cors", options)
        .toFuture
        .map { response =>
          response.status shouldBe 204

          val headers = response.headers
          Option(headers.get("Access-Control-Allow-Origin")).getOrElse("") shouldBe "http://allowed-origin.com"

          val allowMethods = Option(headers.get("Access-Control-Allow-Methods")).getOrElse("")
          allowMethods should (include("GET") and include("POST"))

          val allowHeaders = Option(headers.get("Access-Control-Allow-Headers")).getOrElse("")
          allowHeaders should include("Content-Type")

          headers.has("Vary") shouldBe true
        }
    }

    "should block disallowed origins" in {
      val options = FetchOptions(
        method = "GET",
        headers = js.Dictionary(
          "Origin" -> "http://disallowed-origin.com",
        ),
      )

      fetch(s"http://localhost:$port/cors", options)
        .toFuture
        .map { response =>
          val headers = response.headers
          headers.has("Access-Control-Allow-Origin") shouldBe false
        }
    }

    "should allow requests from allowed origins" in {
      val options = FetchOptions(
        method = "GET",
        headers = js.Dictionary(
          "Origin" -> "http://allowed-origin.com",
        ),
      )

      fetch(s"http://localhost:$port/cors", options)
        .toFuture
        .map { response =>
          val headers = response.headers
          Option(headers.get("Access-Control-Allow-Origin")).getOrElse("") shouldBe "http://allowed-origin.com"

          val exposeHeaders = Option(headers.get("Access-Control-Expose-Headers")).getOrElse("")
          exposeHeaders should include("X-Test-Header")

          Option(headers.get("Access-Control-Allow-Credentials")).getOrElse("") shouldBe "true"
        }
    }
  }

  "Combined Middlewares" - {
    "should apply both security and CORS headers" in {
      val options = FetchOptions(
        method = "GET",
        headers = js.Dictionary(
          "Origin" -> "http://localhost",
        ),
      )

      fetch(s"http://localhost:$port/both", options)
        .toFuture
        .map { response =>
          val headers = response.headers

          // Security headers
          headers.has("Content-Security-Policy") shouldBe true
          headers.has("X-Frame-Options") shouldBe true
          headers.has("X-Content-Type-Options") shouldBe true

          // CORS headers
          headers.has("Access-Control-Allow-Origin") shouldBe true

          response.status shouldBe 200
        }
    }
  }
}
