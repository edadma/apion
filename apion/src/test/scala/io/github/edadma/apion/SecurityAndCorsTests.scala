//package io.github.edadma.apion
//
//import scala.concurrent.Future
//import scala.scalajs.js
//import io.github.edadma.nodejs.{fetch, Server => NodeServer, FetchOptions}
//import org.scalatest.BeforeAndAfterAll
//import scala.compiletime.uninitialized
//
//class SecurityAndCorsTests extends AsyncBaseSpec with BeforeAndAfterAll {
//  var server: Server         = uninitialized
//  var httpServer: NodeServer = uninitialized
//  val port                   = 3002 // Different port than other tests
//
//  override def beforeAll(): Unit = {
//    server = Server()
//      // Test endpoint with security headers
//      .use(SecurityMiddleware())
//      .get("/secure", _ => "secure endpoint".asText)
//
//      // Test endpoint with CORS
//      .use(CorsMiddleware(CorsMiddleware.Options(
//        origin = CorsMiddleware.Origin.Multiple(Set("http://allowed-origin.com")),
//        methods = Set("GET", "POST"),
//        allowedHeaders = Set("Content-Type", "X-Custom-Header"),
//        exposedHeaders = Set("X-Test-Header"),
//        credentials = true,
//      )))
//      .get("/cors", _ => "cors endpoint".asText)
//
//      // Test endpoint with both
//      .use(SecurityMiddleware())
//      .use(CorsMiddleware())
//      .get("/both", _ => "both middlewares".asText)
//
//    httpServer = server.listen(port) {}
//  }
//
//  override def afterAll(): Unit = {
//    if (httpServer != null) {
//      httpServer.close(() => ())
//    }
//  }
//
//  "SecurityMiddleware" - {
//    "should set basic security headers" in {
//      fetch(s"http://localhost:$port/secure")
//        .toFuture
//        .map { response =>
//          // Verify essential security headers
//          val headers = response.headers
//
//          headers.has("Content-Security-Policy") shouldBe true
//          headers.has("X-Frame-Options") shouldBe true
//          headers.has("X-Content-Type-Options") shouldBe true
//          headers.has("X-XSS-Protection") shouldBe true
//          headers.has("Referrer-Policy") shouldBe true
//
//          // Verify specific values
//          headers.get("X-Frame-Options") shouldBe "DENY"
//          headers.get("X-Content-Type-Options") shouldBe "nosniff"
//          headers.get("Referrer-Policy") shouldBe "no-referrer"
//        }
//    }
//
//    "should set CSP header with default directives" in {
//      fetch(s"http://localhost:$port/secure")
//        .toFuture
//        .map { response =>
//          val csp = response.headers.get("Content-Security-Policy")
//
//          csp should include("default-src 'self'")
//          csp should include("script-src 'self'")
//          csp should include("object-src 'none'")
//        }
//    }
//  }
//
//  "CorsMiddleware" - {
//    "should handle preflight requests" in {
//      val options = FetchOptions(
//        method = "OPTIONS",
//        headers = js.Dictionary(
//          "Origin"                         -> "http://allowed-origin.com",
//          "Access-Control-Request-Method"  -> "POST",
//          "Access-Control-Request-Headers" -> "Content-Type",
//        ),
//      )
//
//      fetch(s"http://localhost:$port/cors", options)
//        .toFuture
//        .map { response =>
//          response.status shouldBe 204
//
//          val headers = response.headers
//          headers.get("Access-Control-Allow-Origin") shouldBe "http://allowed-origin.com"
//          headers.get("Access-Control-Allow-Methods") should include("GET")
//          headers.get("Access-Control-Allow-Methods") should include("POST")
//          headers.get("Access-Control-Allow-Headers") should include("Content-Type")
//          headers.has("Vary") shouldBe true // Since using specific origin
//        }
//    }
//
//    "should block disallowed origins" in {
//      val options = FetchOptions(
//        method = "GET",
//        headers = js.Dictionary(
//          "Origin" -> "http://disallowed-origin.com",
//        ),
//      )
//
//      fetch(s"http://localhost:$port/cors", options)
//        .toFuture
//        .map { response =>
//          val headers = response.headers
//          headers.has("Access-Control-Allow-Origin") shouldBe false
//        }
//    }
//
//    "should allow requests from allowed origins" in {
//      val options = FetchOptions(
//        method = "GET",
//        headers = js.Dictionary(
//          "Origin" -> "http://allowed-origin.com",
//        ),
//      )
//
//      fetch(s"http://localhost:$port/cors", options)
//        .toFuture
//        .map { response =>
//          val headers = response.headers
//          headers.get("Access-Control-Allow-Origin") shouldBe "http://allowed-origin.com"
//          headers.get("Access-Control-Expose-Headers") should include("X-Test-Header")
//          headers.get("Access-Control-Allow-Credentials") shouldBe "true"
//        }
//    }
//  }
//
//  "Combined Middlewares" - {
//    "should apply both security and CORS headers" in {
//      val options = FetchOptions(
//        method = "GET",
//        headers = js.Dictionary(
//          "Origin" -> "http://localhost",
//        ),
//      )
//
//      fetch(s"http://localhost:$port/both", options)
//        .toFuture
//        .map { response =>
//          val headers = response.headers
//
//          // Security headers
//          headers.has("Content-Security-Policy") shouldBe true
//          headers.has("X-Frame-Options") shouldBe true
//          headers.has("X-Content-Type-Options") shouldBe true
//
//          // CORS headers
//          headers.has("Access-Control-Allow-Origin") shouldBe true
//
//          // Response should still work
//          response.status shouldBe 200
//        }
//    }
//  }
//}
