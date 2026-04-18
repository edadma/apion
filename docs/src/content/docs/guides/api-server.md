---
title: Building an API Server
description: Complete example of a REST API server with authentication
---

This guide walks through building a full REST API with authentication, CRUD routes, error handling, and middleware.

## Data Models

```scala
import io.github.edadma.apion._
import zio.json._

case class User(id: String, name: String, email: String) derives JsonEncoder, JsonDecoder
case class CreateUserRequest(name: String, email: String) derives JsonDecoder
case class UpdateUserRequest(name: Option[String], email: Option[String]) derives JsonDecoder
case class LoginRequest(email: String, password: String) derives JsonDecoder
case class TokenResponse(accessToken: String, refreshToken: String) derives JsonEncoder
```

## Auth Configuration

```scala
val authConfig = AuthMiddleware.Config(
  secretKey = "your-secret-key",
  requireAuth = true,
  excludePaths = Set("/auth/login", "/auth/refresh", "/health"),
  maxTokenLifetime = 3600,
  tokenRefreshThreshold = 300,
)

val tokenStore = new AuthMiddleware.InMemoryTokenStore()
val auth = AuthMiddleware(authConfig, tokenStore)
```

## Auth Routes

```scala
val authRouter = Router()
  .post("/login", request => {
    request.json[LoginRequest].flatMap {
      case Some(LoginRequest(email, password)) =>
        if (validateCredentials(email, password)) {
          val userId = getUserId(email)
          val roles = getUserRoles(userId)
          TokenResponse(
            AuthMiddleware.createAccessToken(userId, roles, authConfig),
            AuthMiddleware.createRefreshToken(userId, authConfig),
          ).asJson
        } else {
          "Invalid credentials".asText(401)
        }
      case None => "Invalid request".asText(400)
    }
  })
  .post("/refresh", request => {
    request.header("authorization") match {
      case Some(h) if h.startsWith("Bearer ") =>
        AuthMiddleware.refreshToken(h.substring(7), authConfig, tokenStore)
      case _ => "Invalid token".asText(400)
    }
  })
  .post("/logout", request => {
    request.header("authorization") match {
      case Some(h) if h.startsWith("Bearer ") =>
        AuthMiddleware.logout(h.substring(7), authConfig.secretKey, tokenStore)
      case _ => "Invalid token".asText(400)
    }
  })
```

## CRUD Routes

```scala
val usersRouter = Router()
  .get("/", _ =>
    userService.getUsers().flatMap(_.asJson)
  )
  .post("/", request =>
    request.json[CreateUserRequest].flatMap {
      case Some(req) => userService.createUser(req).flatMap(_.asJson(201))
      case None      => "Invalid request".asText(400)
    }
  )
  .get("/:id", request => {
    userService.getUser(request.params("id")).flatMap {
      case Some(user) => user.asJson
      case None       => notFound
    }
  })
  .put("/:id", request => {
    request.json[UpdateUserRequest].flatMap {
      case Some(req) =>
        userService.updateUser(request.params("id"), req).flatMap {
          case Some(user) => user.asJson
          case None       => notFound
        }
      case None => "Invalid request".asText(400)
    }
  })
  .delete("/:id", request => {
    userService.deleteUser(request.params("id")).flatMap {
      case true  => noContent
      case false => notFound
    }
  })
```

## Server Assembly

```scala
val server = Server()
  // Global middleware
  .use(LoggingMiddleware(LoggingMiddleware.Options(
    format = LoggingMiddleware.Format.Dev,
  )))
  .use(CompressionMiddleware())
  .use(CorsMiddleware(CorsMiddleware.Options(
    origin = CorsMiddleware.Origin.Any,
    credentials = true,
  )))
  .use(SecurityMiddleware.api())

  // Public routes
  .get("/health", _ => Map("status" -> "ok").asJson)
  .use("/auth", authRouter)

  // Protected API routes
  .use("/api", Router()
    .use(auth)
    .use("/users", usersRouter)
  )

  // Error handling
  .use { (error: ServerError, _: Request) =>
    error match {
      case ValidationError(msg) =>
        Map("error" -> "validation_error", "message" -> msg).asJson(400)
      case AuthError(msg) =>
        Map("error" -> "auth_error", "message" -> msg).asJson(401)
      case NotFoundError(msg) =>
        Map("error" -> "not_found", "message" -> msg).asJson(404)
      case _ =>
        Map("error" -> "internal_error", "message" -> "Internal server error").asJson(500)
    }
  }

server.listen(3000) {
  println("API server running at http://localhost:3000")
}
```

## Testing the API

```bash
# Health check
curl http://localhost:3000/health

# Login
curl -X POST http://localhost:3000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@example.com","password":"password"}'

# Authenticated request
curl http://localhost:3000/api/users \
  -H "Authorization: Bearer <token>"

# Create a user
curl -X POST http://localhost:3000/api/users \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice","email":"alice@example.com"}'
```
