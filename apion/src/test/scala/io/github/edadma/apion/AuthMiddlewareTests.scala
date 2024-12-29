package io.github.edadma.apion

import scala.concurrent.{Future, ExecutionContext}

class AuthMiddlewareTests extends AsyncBaseSpec:
  // Use global execution context for Future transformations
  override implicit def executionContext: ExecutionContext = ExecutionContext.global

  // Constant test secret key
  val TEST_SECRET_KEY = "test-secret-key-for-auth-middleware-tests"

  // Helper method to create a valid token
  def createValidToken(
      user: String,
      roles: Set[String] = Set("user"),
      expiration: Long = System.currentTimeMillis() / 1000 + 3600,
  ): String =
    val payload = AuthMiddleware.TokenPayload(
      sub = user,
      roles = roles,
      exp = expiration,
    )
    JWT.sign(payload, TEST_SECRET_KEY)

  "AuthMiddleware" - {
    "authentication flow" - {
      "should allow authenticated requests to non-excluded paths" in withDebugLogging(
        "should allow authenticated requests to non-excluded paths",
      ) {
        val testEndpoint: Endpoint = req =>
          Future.successful(Response.text(s"Authenticated: ${req.auth.map(_.user).getOrElse("unknown")}"))

        val middleware = AuthMiddleware(
          requireAuth = true,
          excludePaths = Set("/public"),
          secretKey = TEST_SECRET_KEY,
        )

        val authenticatedEndpoint = middleware(testEndpoint)

        // Create a token with a future expiration time
        val validToken = createValidToken("testuser", expiration = System.currentTimeMillis() / 1000 + 3600)

        val request = Request(
          method = "GET",
          url = "/protected",
          headers = Map("Authorization" -> s"Bearer $validToken"),
        )

        authenticatedEndpoint(request).map { response =>
          response.status shouldBe 200
          response.body shouldBe "Authenticated: testuser"
        }
      }

      "should reject requests without token when authentication is required" in {
        val testEndpoint: Endpoint = req =>
          Future.successful(Response.text("This should not be reached"))

        val middleware = AuthMiddleware(
          requireAuth = true,
          excludePaths = Set("/public"),
          secretKey = TEST_SECRET_KEY,
        )

        val authenticatedEndpoint = middleware(testEndpoint)

        val request = Request(
          method = "GET",
          url = "/protected",
          headers = Map.empty,
        )

        authenticatedEndpoint(request).map { response =>
          response.status shouldBe 401
          response.body shouldBe "Authorization header required"
        }
      }

      "should allow requests to excluded paths without token" in {
        val testEndpoint: Endpoint = req =>
          Future.successful(Response.text("Public endpoint accessed"))

        val middleware = AuthMiddleware(
          requireAuth = true,
          excludePaths = Set("/public"),
          secretKey = TEST_SECRET_KEY,
        )

        val authenticatedEndpoint = middleware(testEndpoint)

        val request = Request(
          method = "GET",
          url = "/public",
          headers = Map.empty,
        )

        authenticatedEndpoint(request).map { response =>
          response.status shouldBe 200
          response.body shouldBe "Public endpoint accessed"
        }
      }

      "should reject requests with expired tokens" in {
        val testEndpoint: Endpoint = req =>
          Future.successful(Response.text("This should not be reached"))

        val middleware = AuthMiddleware(
          requireAuth = true,
          excludePaths = Set("/public"),
          secretKey = TEST_SECRET_KEY,
        )

        val authenticatedEndpoint = middleware(testEndpoint)

        // Create an expired token (1 hour in the past)
        val expiredToken = createValidToken(
          "expireduser",
          expiration = System.currentTimeMillis() / 1000 - 3600,
        )

        val request = Request(
          method = "GET",
          url = "/protected",
          headers = Map("Authorization" -> s"Bearer $expiredToken"),
        )

        authenticatedEndpoint(request).map { response =>
          response.status shouldBe 401
          response.body shouldBe "Unauthorized"
        }
      }

      "should reject requests with malformed tokens" in {
        val testEndpoint: Endpoint = req =>
          Future.successful(Response.text("This should not be reached"))

        val middleware = AuthMiddleware(
          requireAuth = true,
          excludePaths = Set("/public"),
          secretKey = TEST_SECRET_KEY,
        )

        val authenticatedEndpoint = middleware(testEndpoint)

        val request = Request(
          method = "GET",
          url = "/protected",
          headers = Map("Authorization" -> "Bearer malformed-token"),
        )

        authenticatedEndpoint(request).map { response =>
          response.status shouldBe 401
          response.body shouldBe "Unauthorized"
        }
      }

      "should support multiple roles in token" in {
        val testEndpoint: Endpoint = req =>
          Future.successful(Response.text(s"Roles: ${req.auth.map(_.roles).getOrElse(Set.empty)}"))

        val middleware = AuthMiddleware(
          requireAuth = true,
          excludePaths = Set("/public"),
          secretKey = TEST_SECRET_KEY,
        )

        val authenticatedEndpoint = middleware(testEndpoint)

        val multiRoleToken = createValidToken(
          "multiuser",
          roles = Set("admin", "editor"),
        )

        val request = Request(
          method = "GET",
          url = "/protected",
          headers = Map("Authorization" -> s"Bearer $multiRoleToken"),
        )

        authenticatedEndpoint(request).map { response =>
          response.status shouldBe 200
          response.body should include("admin")
          response.body should include("editor")
        }
      }

      "should handle authentication optional paths" in {
        val testEndpoint: Endpoint = req =>
          Future.successful(Response.text(s"User: ${req.auth.map(_.user).getOrElse("anonymous")}"))

        val middleware = AuthMiddleware(
          requireAuth = false,
          excludePaths = Set("/public"),
          secretKey = TEST_SECRET_KEY,
        )

        val authenticatedEndpoint = middleware(testEndpoint)

        val request = Request(
          method = "GET",
          url = "/optional",
          headers = Map.empty,
        )

        authenticatedEndpoint(request).map { response =>
          response.status shouldBe 200
          response.body shouldBe "User: anonymous"
        }
      }

      "should handle optional auth with valid token" in {
        val testEndpoint: Endpoint = req =>
          Future.successful(Response.text(s"User: ${req.auth.map(_.user).getOrElse("anonymous")}"))

        val middleware = AuthMiddleware(
          requireAuth = false,
          excludePaths = Set("/public"),
          secretKey = TEST_SECRET_KEY,
        )

        val authenticatedEndpoint = middleware(testEndpoint)

        val validToken = createValidToken("optionaluser")

        val request = Request(
          method = "GET",
          url = "/optional",
          headers = Map("Authorization" -> s"Bearer $validToken"),
        )

        authenticatedEndpoint(request).map { response =>
          response.status shouldBe 200
          response.body shouldBe "User: optionaluser"
        }
      }
    }
  }
