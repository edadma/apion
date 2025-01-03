package io.github.edadma.apion

import scala.concurrent.Future
import scala.scalajs.js
import io.github.edadma.nodejs.{fetch, Server => NodeServer, FetchOptions}
import org.scalatest.BeforeAndAfterAll
import scala.compiletime.uninitialized
import zio.json._
import AuthMiddleware._

class AuthIntegrationTests extends AsyncBaseSpec with BeforeAndAfterAll {
  var server: Server         = uninitialized
  var httpServer: NodeServer = uninitialized
  val port                   = 3004 // Different port than other tests
  val testSecretKey          = "test-secret-key-for-auth-tests-1234"

  case class SecureData(message: String) derives JsonEncoder, JsonDecoder

  // Test data
  val testUser     = "testuser123"
  val testRoles    = Set("user", "admin")
  val testAudience = "test-client"
  val config = Config(
    secretKey = testSecretKey,
    requireAuth = true,
    excludePaths = Set("/public"),
    maxTokenLifetime = 3600,     // 1 hour for testing
    tokenRefreshThreshold = 300, // 5 minutes
    audience = Some(testAudience),
    issuer = "test-auth",
  )

  // Create a shared token store for testing revocation
  val tokenStore = new InMemoryTokenStore()

  override def beforeAll(): Unit = {
    val auth = AuthMiddleware(config, tokenStore)

    server = Server()
      // Public endpoint - no auth needed
      .get("/public/hello", _ => "Hello Public!".asText)

      // Protected endpoints
      .use(auth)
      .get(
        "/secure/data",
        request => {
          request.context.get("auth") match {
            case Some(auth: Auth) =>
              SecureData(s"Secret data for ${auth.user}").asJson
            case Some(_) =>
              "Invalid auth context type".asText(500)
            case None =>
              "No auth context found".asText(500)
          }
        },
      )
      .post(
        "/secure/data",
        request => {
          request.context.get("auth") match {
            case Some(auth: Auth) =>
              SecureData(s"Secret data for ${auth.user}").asJson
            case Some(_) =>
              "Invalid auth context type".asText(500)
            case None =>
              "No auth context found".asText(500)
          }
        },
      )
      // Admin only endpoint
      .get(
        "/secure/admin",
        request => {
          request.context.get("auth") match {
            case Some(auth: Auth) if auth.hasRequiredRoles(Set("admin")) =>
              SecureData("Admin console").asJson
            case Some(auth: Auth) =>
              "Insufficient permissions".asText(403)
            case Some(_) =>
              "Invalid auth context type".asText(500)
            case None =>
              "No auth context found".asText(500)
          }
        },
      )
      // Logout endpoint
      .post(
        "/auth/logout",
        request => {
          request.header("authorization") match {
            case Some(header) if header.toLowerCase.startsWith("bearer ") =>
              val token = header.substring(7)
              AuthMiddleware.logout(token, testSecretKey, tokenStore)
            case _ =>
              "Invalid Authorization header".asText(400)
          }
        },
      )
      // Token refresh endpoint
      .post(
        "/auth/refresh",
        request => {
          request.header("authorization") match {
            case Some(header) if header.toLowerCase.startsWith("bearer ") =>
              val token = header.substring(7)
              AuthMiddleware.refreshToken(token, config, tokenStore)
            case _ =>
              "Invalid Authorization header".asText(400)
          }
        },
      )

    httpServer = server.listen(port) {}
  }

  override def afterAll(): Unit = {
    if (httpServer != null) {
      httpServer.close(() => ())
    }
  }

  "AuthMiddleware" - {
    "basic auth flow" - {
      "should allow access to public endpoints without token" in {
        fetch(s"http://localhost:$port/public/hello")
          .toFuture
          .flatMap(response => response.text().toFuture)
          .map { text =>
            text shouldBe "Hello Public!"
          }
      }

      "should deny access to secure endpoints without token" in {
        fetch(s"http://localhost:$port/secure/data")
          .toFuture
          .map { response =>
            response.status shouldBe 401
            response.headers.get("WWW-Authenticate") shouldBe """Bearer realm="api""""
          }
      }

      "should allow access to secure endpoints with valid token" in {
        val token = AuthMiddleware.createAccessToken(testUser, testRoles, config)
        val options = FetchOptions(
          headers = js.Dictionary(
            "Authorization" -> s"Bearer $token",
          ),
        )

        fetch(s"http://localhost:$port/secure/data", options)
          .toFuture
          .flatMap(response => response.text().toFuture)
          .map { text =>
            text should include(testUser)
            text should include("Secret data")
          }
      }

      "should enforce role-based access control" in {
        // Create token without admin role
        val token = AuthMiddleware.createAccessToken(
          testUser,
          Set("user"), // Only user role
          config,
        )
        val options = FetchOptions(
          headers = js.Dictionary(
            "Authorization" -> s"Bearer $token",
          ),
        )

        fetch(s"http://localhost:$port/secure/admin", options)
          .toFuture
          .map { response =>
            response.status shouldBe 403
          }
      }
    }

    "enhanced token features" - {
      "should include refresh header when token is near expiration" in /*withDebugLogging(
        "should include refresh header when token is near expiration",
      )*/ {
        // Create token that's close to refresh threshold
        val nearExpiryConfig = config.copy(
          maxTokenLifetime = config.tokenRefreshThreshold,
        )
        val token = AuthMiddleware.createAccessToken(testUser, testRoles, nearExpiryConfig)
        val options = FetchOptions(
          headers = js.Dictionary(
            "Authorization" -> s"Bearer $token",
          ),
        )

        fetch(s"http://localhost:$port/secure/data", options)
          .toFuture
          .map { response =>
            response.headers.get("X-Token-Refresh") shouldBe "true"
          }
      }

      "should validate audience claim" in {
        // Create token with wrong audience
        val wrongAudienceConfig = config.copy(audience = Some("wrong-audience"))
        val token               = AuthMiddleware.createAccessToken(testUser, testRoles, wrongAudienceConfig)
        val options = FetchOptions(
          headers = js.Dictionary(
            "Authorization" -> s"Bearer $token",
          ),
        )

        fetch(s"http://localhost:$port/secure/data", options)
          .toFuture
          .flatMap { response =>
            response.status shouldBe 401
            // Verify error is about invalid claims
            response.text().toFuture.map { text =>
              text.toLowerCase should include("claims")
            }
          }
      }

      "should validate issuer claim" in {
        // Create token with wrong issuer
        val wrongIssuerConfig = config.copy(issuer = "wrong-issuer")
        val token             = AuthMiddleware.createAccessToken(testUser, testRoles, wrongIssuerConfig)
        val options = FetchOptions(
          headers = js.Dictionary(
            "Authorization" -> s"Bearer $token",
          ),
        )

        fetch(s"http://localhost:$port/secure/data", options)
          .toFuture
          .flatMap { response =>
            response.status shouldBe 401
            response.text().toFuture.map { text =>
              text.toLowerCase should include("claims")
            }
          }
      }

      "should generate unique JTI claims" in {
        // Create multiple tokens and verify unique JTIs
        val token1 = AuthMiddleware.createAccessToken(testUser, testRoles, config)
        val token2 = AuthMiddleware.createAccessToken(testUser, testRoles, config)

        def extractJti(token: String): String = {
          val parts = token.split("\\.")
          val payload = new String(
            java.util.Base64.getDecoder.decode(
              parts(1).replace("-", "+").replace("_", "/") + "==",
            ),
          )
          payload.fromJson[TokenPayload].toOption.get.jti
        }

        val jti1 = extractJti(token1)
        val jti2 = extractJti(token2)

        jti1 should not be jti2
      }

      "should properly chain finalizers" in {
        val token = AuthMiddleware.createAccessToken(testUser, testRoles, config)
        val options = FetchOptions(
          headers = js.Dictionary(
            "Authorization" -> s"Bearer $token",
          ),
        )

        // Fetch the existing endpoint to ensure the test uses an existing route
        fetch(s"http://localhost:$port/secure/data", options)
          .toFuture
          .map { response =>
            // The auth middleware should have added its own finalizer
            // So this test ensures finalizers are properly chained
            response.headers.has("X-Token-Refresh").shouldBe(false)
            response.status.shouldBe(200)
          }
      }
    }

    "token lifecycle" - {
      "should handle token refresh" in {
        // First create a refresh token
        val refreshToken = AuthMiddleware.createRefreshToken(testUser, config)
        val options = FetchOptions(
          method = "POST",
          headers = js.Dictionary(
            "Authorization" -> s"Bearer $refreshToken",
          ),
        )

        // Try to refresh the token
        fetch(s"http://localhost:$port/auth/refresh", options)
          .toFuture
          .flatMap(response => response.json().toFuture)
          .map { result =>
            val jsonStr = js.JSON.stringify(result)
            jsonStr should include("access_token")
          }
      }

      "should reject expired tokens" in {
        // Create token that's already expired
        val expiredConfig = config.copy(maxTokenLifetime = -3600) // Expired 1 hour ago
        val expiredToken  = AuthMiddleware.createAccessToken(testUser, testRoles, expiredConfig)

        val options = FetchOptions(
          headers = js.Dictionary(
            "Authorization" -> s"Bearer $expiredToken",
          ),
        )

        fetch(s"http://localhost:$port/secure/data", options)
          .toFuture
          .map { response =>
            response.status shouldBe 401
          }
      }

      "should handle token revocation" in {
        val token = AuthMiddleware.createAccessToken(testUser, testRoles, config)
        val options = FetchOptions(
          method = "POST",
          headers = js.Dictionary(
            "Authorization" -> s"Bearer $token",
          ),
        )

        // First try using the token
        fetch(s"http://localhost:$port/secure/data", options)
          .toFuture
          .flatMap { response =>
            response.status shouldBe 200

            // Then logout/revoke the token
            fetch(s"http://localhost:$port/auth/logout", options)
              .toFuture
              .flatMap { logoutResponse =>
                logoutResponse.status shouldBe 200

                // Finally verify token no longer works
                fetch(s"http://localhost:$port/secure/data", options)
                  .toFuture
              }
          }
          .map { finalResponse =>
            finalResponse.status shouldBe 401
          }
      }
    }

    "error handling" - {
      "should handle malformed tokens" in {
        val options = FetchOptions(
          headers = js.Dictionary(
            "Authorization" -> "Bearer not.a.validtoken",
          ),
        )

        fetch(s"http://localhost:$port/secure/data", options)
          .toFuture
          .map { response =>
            response.status shouldBe 401
          }
      }

      "should handle missing authorization header" in {
        fetch(s"http://localhost:$port/secure/data")
          .toFuture
          .map { response =>
            response.status shouldBe 401
          }
      }

      "should handle invalid authorization header format" in {
        val options = FetchOptions(
          headers = js.Dictionary(
            "Authorization" -> "NotBearer token123",
          ),
        )

        fetch(s"http://localhost:$port/secure/data", options)
          .toFuture
          .map { response =>
            response.status shouldBe 401
          }
      }
    }
  }
}
