---
title: Authentication Middleware
layout: default
parent: Middleware
grand_parent: API Reference
nav_order: 1
---

# Authentication Middleware

The Authentication middleware provides JWT-based authentication with role-based access control (RBAC). It handles token verification, role checking, token refresh, and token revocation.

## Basic Usage

```scala
import io.github.edadma.apion._
import AuthMiddleware._

// Configure authentication
val config = Config(
  secretKey = "your-secret-key",
  requireAuth = true,
  excludePaths = Set("/public", "/auth"),
  maxTokenLifetime = 86400,    // 24 hours
  tokenRefreshThreshold = 300, // 5 minutes before expiration
  issuer = "your-service",
  audience = Some("your-app")
)

// Create and use middleware
val auth = AuthMiddleware(config)
server.use(auth)
```

## Configuration Options

The `Config` case class supports these options:

```scala
case class Config(
    secretKey: String,              // Required secret key for JWT signing
    requireAuth: Boolean = true,    // Require auth for all routes by default
    excludePaths: Set[String] = Set.empty, // Paths that skip auth
    tokenRefreshThreshold: Long = 300,     // Seconds before expiration to suggest refresh
    maxTokenLifetime: Long = 86400,        // Maximum token lifetime in seconds
    issuer: String = "apion-auth",         // Token issuer claim
    audience: Option[String] = None,        // Optional audience claim
)
```

## Token Payload

The JWT payload includes these claims:

```scala
case class TokenPayload(
    sub: String,         // Subject (user identifier)
    roles: Set[String],  // User roles for authorization
    exp: Long,           // Expiration timestamp
    iat: Long,           // Issued at timestamp
    iss: String,         // Token issuer
    aud: Option[String], // Audience
    jti: String,         // Unique token identifier
)
```

## Creating Tokens

### Access Tokens
```scala
// Create an access token
val token = AuthMiddleware.createAccessToken(
  subject = "user123",
  roles = Set("admin", "user"),
  config = config
)

// Create a refresh token
val refreshToken = AuthMiddleware.createRefreshToken(
  subject = "user123",
  config = config,
  validityPeriod = 30 * 24 * 3600 // 30 days
)
```

## Protected Routes

The middleware adds an `Auth` object to the request context:

```scala
// Access auth context in handlers
server.get("/protected", request => {
  request.context.get("auth") match {
    case Some(auth: Auth) =>
      // User is authenticated
      Map(
        "user" -> auth.user,
        "roles" -> auth.roles
      ).asJson
    case _ =>
      // Should not happen if using auth middleware
      "Unauthorized".asText(401)
  }
})
```

## Role-Based Access Control

Check user roles in your handlers:

```scala
// Require specific roles
server.get("/admin", request => {
  request.context.get("auth") match {
    case Some(auth: Auth) if auth.hasRequiredRoles(Set("admin")) =>
      // User has admin role
      adminDashboard(request)
    case Some(auth: Auth) =>
      // User is authenticated but lacks admin role
      "Insufficient permissions".asText(403)
    case _ =>
      "Unauthorized".asText(401)
  }
})
```

## Token Refresh

The middleware automatically adds a refresh header when tokens are near expiration:

```scala
// Check if token needs refresh
response.headers.get("X-Token-Refresh") match {
  case Some("true") =>
    // Token is near expiration, request new token
    refreshToken(currentRefreshToken)
  case _ =>
    // Token is still valid
    useCurrentToken()
}
```

Implement a refresh endpoint:

```scala
server.post("/auth/refresh", request => {
  request.header("authorization") match {
    case Some(header) if header.toLowerCase.startsWith("bearer ") =>
      val token = header.substring(7)
      AuthMiddleware.refreshToken(token, config, tokenStore)
    case _ =>
      "Invalid Authorization header".asText(400)
  }
})
```

## Token Revocation

The middleware supports token revocation through a token store:

```scala
// Create a token store (use your own implementation in production)
val tokenStore = new InMemoryTokenStore()

// Add logout endpoint
server.post("/auth/logout", request => {
  request.header("authorization") match {
    case Some(header) if header.toLowerCase.startsWith("bearer ") =>
      val token = header.substring(7)
      AuthMiddleware.logout(token, config.secretKey, tokenStore)
    case _ =>
      "Invalid Authorization header".asText(400)
  }
})
```

## Custom Token Store

Implement your own token store for persistence:

```scala
class DatabaseTokenStore extends TokenStore {
  override def isTokenRevoked(jti: String): Future[Boolean] =
    // Check database for revoked token
    database.exists("revoked_tokens", jti)

  override def revokeToken(jti: String): Future[Unit] =
    // Store revoked token in database
    database.insert("revoked_tokens", jti)
}
```

## Complete Authentication Flow Example

Here's a complete example showing login, refresh, and logout:

```scala
import io.github.edadma.apion._
import AuthMiddleware._
import zio.json._

// Data types
case class LoginRequest(username: String, password: String) derives JsonDecoder
case class TokenResponse(access_token: String, refresh_token: String) derives JsonEncoder

// Create auth router
val authRouter = Router()
  // Login endpoint
  .post("/login", request => {
    request.json[LoginRequest].flatMap {
      case Some(LoginRequest(username, password)) =>
        // Validate credentials (implement your own validation)
        if (validateCredentials(username, password)) {
          // Create tokens
          val accessToken = createAccessToken(
            username,
            getUserRoles(username),
            config
          )
          val refreshToken = createRefreshToken(username, config)
          
          // Return tokens
          TokenResponse(accessToken, refreshToken).asJson(200)
        } else {
          "Invalid credentials".asText(401)
        }
      case None =>
        "Invalid request body".asText(400)
    }
  })
  // Refresh endpoint
  .post("/refresh", request => {
    request.header("authorization") match {
      case Some(header) if header.toLowerCase.startsWith("bearer ") =>
        val token = header.substring(7)
        AuthMiddleware.refreshToken(token, config, tokenStore)
      case _ =>
        "Invalid Authorization header".asText(400)
    }
  })
  // Logout endpoint
  .post("/logout", request => {
    request.header("authorization") match {
      case Some(header) if header.toLowerCase.startsWith("bearer ") =>
        val token = header.substring(7)
        AuthMiddleware.logout(token, config.secretKey, tokenStore)
      case _ =>
        "Invalid Authorization header".asText(400)
    }
  })

// Protected API routes
val apiRouter = Router()
  .use(auth) // Apply auth middleware
  .get("/profile", request => {
    request.context.get("auth") match {
      case Some(auth: Auth) =>
        getUserProfile(auth.user).asJson
      case _ =>
        "Unauthorized".asText(401)
    }
  })
  .get("/admin", request => {
    request.context.get("auth") match {
      case Some(auth: Auth) if auth.hasRequiredRoles(Set("admin")) =>
        getAdminData().asJson
      case _ =>
        "Insufficient permissions".asText(403)
    }
  })

// Main server setup
val server = Server()
  .use("/auth", authRouter)  // Auth endpoints (no auth required)
  .use("/api", apiRouter)    // Protected API endpoints
```

## Security Considerations

1. **Secret Key**: Use a strong, unique secret key
2. **Token Lifetime**: Keep access tokens short-lived
3. **HTTPS**: Always use HTTPS in production
4. **Token Storage**: Store tokens securely on client
5. **Refresh Token Handling**: Treat refresh tokens with extra care

## Error Handling

The middleware provides specific error responses:

```scala
case class ErrorResponse(
    error: String,
    message: String,
    details: Option[String] = None,
)

// Example errors:
- Invalid token format (400)
- Token verification failed (401)
- Token expired (401)
- Token revoked (401)
- Invalid claims (401)
```

## Testing

Example of testing protected routes:

```scala
"Protected routes" - {
  "should require authentication" in {
    fetch(s"http://localhost:$port/api/profile")
      .toFuture
      .map { response =>
        response.status shouldBe 401
      }
  }

  "should allow access with valid token" in {
    val token = createAccessToken("test", Set("user"), config)
    val options = FetchOptions(
      headers = js.Dictionary(
        "Authorization" -> s"Bearer $token"
      )
    )

    fetch(s"http://localhost:$port/api/profile", options)
      .toFuture
      .map { response =>
        response.status shouldBe 200
      }
  }
}
```