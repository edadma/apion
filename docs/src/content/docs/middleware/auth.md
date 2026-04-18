---
title: Authentication
description: JWT-based authentication with role-based access control
---

`AuthMiddleware` provides JWT authentication with token refresh, role-based access control, and token revocation.

## Basic Setup

```scala
import io.github.edadma.apion._
import AuthMiddleware._

val config = Config(
  secretKey = "your-secret-key",
  requireAuth = true,
  excludePaths = Set("/auth/login", "/auth/refresh", "/health"),
)

server.use(AuthMiddleware(config))
```

## Configuration

```scala
case class Config(
  secretKey: String,                    // Required: JWT signing key
  requireAuth: Boolean = true,         // Require auth on all routes
  excludePaths: Set[String] = Set(),   // Paths that skip auth
  tokenRefreshThreshold: Long = 300,   // Seconds before expiry to auto-refresh
  maxTokenLifetime: Long = 86400,      // Max token lifetime in seconds (24h)
  issuer: String = "apion-auth",       // JWT issuer claim
  audience: Option[String] = None,     // Optional audience claim
)
```

## Token Management

### Create Tokens

```scala
// Access token (short-lived)
val accessToken = AuthMiddleware.createAccessToken(
  subject = "user123",
  roles = Set("user", "admin"),
  config = config,
)

// Refresh token (long-lived)
val refreshToken = AuthMiddleware.createRefreshToken(
  subject = "user123",
  config = config,
  validityPeriod = 30 * 24 * 3600, // 30 days
)
```

### Token Store

Token revocation requires a `TokenStore`:

```scala
trait TokenStore {
  def isTokenRevoked(jti: String): Future[Boolean]
  def revokeToken(jti: String): Future[Unit]
}
```

An `InMemoryTokenStore` is provided for development. Implement your own for production (e.g., Redis-backed).

```scala
val tokenStore = new InMemoryTokenStore()
val auth = AuthMiddleware(config, tokenStore)
```

## Authentication Flow

### Login Endpoint

```scala
case class LoginRequest(email: String, password: String) derives JsonDecoder
case class TokenResponse(accessToken: String, refreshToken: String) derives JsonEncoder

server.post("/auth/login", request => {
  request.json[LoginRequest].flatMap {
    case Some(LoginRequest(email, password)) =>
      if (validateCredentials(email, password)) {
        val userId = getUserId(email)
        val roles = getUserRoles(userId)
        TokenResponse(
          AuthMiddleware.createAccessToken(userId, roles, config),
          AuthMiddleware.createRefreshToken(userId, config),
        ).asJson
      } else {
        "Invalid credentials".asText(401)
      }
    case None => "Invalid request".asText(400)
  }
})
```

### Token Refresh

```scala
server.post("/auth/refresh", request => {
  request.header("authorization") match {
    case Some(header) if header.toLowerCase.startsWith("bearer ") =>
      val token = header.substring(7)
      AuthMiddleware.refreshToken(token, config, tokenStore)
    case _ => "Invalid token".asText(400)
  }
})
```

### Logout

```scala
server.post("/auth/logout", request => {
  request.header("authorization") match {
    case Some(header) if header.toLowerCase.startsWith("bearer ") =>
      val token = header.substring(7)
      AuthMiddleware.logout(token, config.secretKey, tokenStore)
    case _ => "Invalid token".asText(400)
  }
})
```

## Accessing Auth Data

When authentication succeeds, an `Auth` object is placed in the request context:

```scala
case class Auth(user: String, roles: Set[String]) {
  def hasRequiredRoles(required: Set[String]): Boolean
}
```

```scala
server.get("/profile", request => {
  request.context.get("auth") match {
    case Some(auth: Auth) =>
      getUserProfile(auth.user).asJson
    case _ =>
      "Unauthorized".asText(401)
  }
})
```

## Role-Based Access Control

Check roles in handlers:

```scala
server.get("/admin", request => {
  request.context.get("auth") match {
    case Some(auth: Auth) if auth.hasRequiredRoles(Set("admin")) =>
      getAdminData().asJson
    case Some(_) =>
      "Insufficient permissions".asText(403)
    case _ =>
      "Unauthorized".asText(401)
  }
})
```

Create a reusable role-checking middleware:

```scala
def requireRoles(roles: Set[String]): Handler = request =>
  request.context.get("auth") match {
    case Some(auth: Auth) if auth.hasRequiredRoles(roles) =>
      Future.successful(Continue(request))
    case Some(_) =>
      "Insufficient permissions".asText(403)
    case None =>
      "Unauthorized".asText(401)
  }

server.get("/admin", requireRoles(Set("admin")), adminHandler)
```

## Token Payload

The JWT payload contains:

```scala
case class TokenPayload(
  sub: String,             // Subject (user ID)
  roles: Set[String],      // User roles
  exp: Long,               // Expiration timestamp
  iat: Long,               // Issued-at timestamp
  iss: String,             // Issuer
  aud: Option[String],     // Audience
  jti: String,             // Unique token ID
)
```
