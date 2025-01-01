//package io.github.edadma.apion
//
//import scala.concurrent.{Future, ExecutionContext}
//import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global
//import scala.scalajs.js
//import io.github.edadma.nodejs.ServerRequest
//
//class AuthMiddlewareTests extends AsyncBaseSpec:
//  val TEST_SECRET_KEY = "test-secret-key-for-auth-middleware-tests"
//
//  def createValidToken(
//      user: String,
//      roles: Set[String] = Set("user"),
//      expiration: Long = System.currentTimeMillis() / 1000 + 3600,
//  ): String =
//    val payload = AuthMiddleware.TokenPayload(
//      sub = user,
//      roles = roles,
//      exp = expiration,
//    )
//    JWT.sign(payload, TEST_SECRET_KEY)
//
//  def mockServerRequest(
//      method: String = "GET",
//      url: String = "/",
//      headers: Map[String, String] = Map(),
//  ): ServerRequest =
//    val req = js.Dynamic.literal(
//      method = method,
//      url = url,
//      headers = js.Dictionary(headers.toSeq*),
//      on = (_: String, _: js.Function1[js.Any, Unit]) => js.Dynamic.literal(),
//    )
//    req.asInstanceOf[ServerRequest]
//
//  "AuthMiddleware" - {
//    "authentication flow" - {
//      "should allow authenticated requests to non-excluded paths" in {
//        val validToken = createValidToken("testuser")
//        val request = Request.fromServerRequest(mockServerRequest(
//          method = "GET",
//          url = "/protected",
//          headers = Map("Authorization" -> s"Bearer $validToken"),
//        ))
//
//        val middleware = AuthMiddleware(
//          requireAuth = true,
//          excludePaths = Set("/public"),
//          secretKey = TEST_SECRET_KEY,
//        )
//
//        middleware(request).map {
//          case Continue(req) =>
//            req.auth.isDefined shouldBe true
//            req.auth.get.user shouldBe "testuser"
//            req.auth.get.roles shouldBe Set("user")
//          case other =>
//            fail(s"Expected Continue but got $other")
//        }
//      }
//
//      "should reject requests without token when authentication is required" in {
//        val request = Request.fromServerRequest(mockServerRequest(
//          method = "GET",
//          url = "/protected",
//          headers = Map.empty,
//        ))
//
//        val middleware = AuthMiddleware(
//          requireAuth = true,
//          excludePaths = Set("/public"),
//          secretKey = TEST_SECRET_KEY,
//        )
//
//        middleware(request).map {
//          case Complete(response) =>
//            response.status shouldBe 401
//            response.body should include("Missing Authorization header")
//          case other =>
//            fail(s"Expected Complete with 401 status but got $other")
//        }
//      }
//
//      "should allow requests to excluded paths without token" in {
//        val request = Request.fromServerRequest(mockServerRequest(
//          method = "GET",
//          url = "/public",
//          headers = Map.empty,
//        ))
//
//        val middleware = AuthMiddleware(
//          requireAuth = true,
//          excludePaths = Set("/public"),
//          secretKey = TEST_SECRET_KEY,
//        )
//
//        middleware(request).map { result =>
//          result shouldBe Skip
//        }
//      }
//
//      "should reject requests with expired tokens" in {
//        val expiredToken = createValidToken(
//          "expireduser",
//          expiration = System.currentTimeMillis() / 1000 - 3600,
//        )
//
//        val request = Request.fromServerRequest(mockServerRequest(
//          method = "GET",
//          url = "/protected",
//          headers = Map("Authorization" -> s"Bearer $expiredToken"),
//        ))
//
//        val middleware = AuthMiddleware(
//          requireAuth = true,
//          excludePaths = Set("/public"),
//          secretKey = TEST_SECRET_KEY,
//        )
//
//        middleware(request).map {
//          case Complete(response) =>
//            response.status shouldBe 401
//            response.body should include("Token expired")
//          case other =>
//            fail(s"Expected Complete with 401 status but got $other")
//        }
//      }
//
//      "should reject requests with malformed tokens" in {
//        val request = Request.fromServerRequest(mockServerRequest(
//          method = "GET",
//          url = "/protected",
//          headers = Map("Authorization" -> "Bearer malformed-token"),
//        ))
//
//        val middleware = AuthMiddleware(
//          requireAuth = true,
//          excludePaths = Set("/public"),
//          secretKey = TEST_SECRET_KEY,
//        )
//
//        middleware(request).map {
//          case Complete(response) =>
//            response.status shouldBe 401
//            response.body should include("Invalid token")
//          case other =>
//            fail(s"Expected Complete with 401 status but got $other")
//        }
//      }
//
//      "should support multiple roles in token" in {
//        val multiRoleToken = createValidToken(
//          "multiuser",
//          roles = Set("admin", "editor"),
//        )
//
//        val request = Request.fromServerRequest(mockServerRequest(
//          method = "GET",
//          url = "/protected",
//          headers = Map("Authorization" -> s"Bearer $multiRoleToken"),
//        ))
//
//        val middleware = AuthMiddleware(
//          requireAuth = true,
//          excludePaths = Set("/public"),
//          secretKey = TEST_SECRET_KEY,
//        )
//
//        middleware(request).map {
//          case Continue(req) =>
//            req.auth.isDefined shouldBe true
//            req.auth.get.user shouldBe "multiuser"
//            req.auth.get.roles should contain allOf ("admin", "editor")
//          case other =>
//            fail(s"Expected Continue but got $other")
//        }
//      }
//
//      "should handle optional authentication paths" in {
//        val request = Request.fromServerRequest(mockServerRequest(
//          method = "GET",
//          url = "/optional",
//          headers = Map.empty,
//        ))
//
//        val middleware = AuthMiddleware(
//          requireAuth = false,
//          excludePaths = Set("/public"),
//          secretKey = TEST_SECRET_KEY,
//        )
//
//        middleware(request).map { result =>
//          result shouldBe Skip
//        }
//      }
//
//      "should handle optional auth with valid token" in {
//        val validToken = createValidToken("optionaluser")
//        val request = Request.fromServerRequest(mockServerRequest(
//          method = "GET",
//          url = "/optional",
//          headers = Map("Authorization" -> s"Bearer $validToken"),
//        ))
//
//        val middleware = AuthMiddleware(
//          requireAuth = false,
//          excludePaths = Set("/public"),
//          secretKey = TEST_SECRET_KEY,
//        )
//
//        middleware(request).map {
//          case Continue(req) =>
//            req.auth.isDefined shouldBe true
//            req.auth.get.user shouldBe "optionaluser"
//            req.auth.get.roles shouldBe Set("user")
//          case other =>
//            fail(s"Expected Continue but got $other")
//        }
//      }
//    }
//  }
