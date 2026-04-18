---
title: Error Handling
description: Type-safe error propagation and custom error handlers
---

## Error Types

Apion provides a sealed trait hierarchy for typed errors:

```scala
trait ServerError extends Throwable {
  def message: String
}

case class ValidationError(message: String) extends ServerError
case class AuthError(message: String) extends ServerError
case class NotFoundError(message: String) extends ServerError
```

## Creating Errors

Use the DSL helpers to fail with a typed error:

```scala
// DSL helpers (return Future[Result])
failValidation("Invalid email format")
failAuth("Token expired")
failNotFound("User not found")
fail(CustomError("Something went wrong"))

// Direct construction
Future.successful(Fail(ValidationError("Invalid input")))
```

## Error Handlers

Register error handlers with the two-argument `.use` method:

```scala
server.use { (error: ServerError, request: Request) =>
  error match {
    case ValidationError(msg) =>
      Map("error" -> "validation_error", "message" -> msg).asJson(400)
    case AuthError(msg) =>
      Map("error" -> "auth_error", "message" -> msg).asJson(401)
    case NotFoundError(msg) =>
      Map("error" -> "not_found", "message" -> msg).asJson(404)
    case _ =>
      Map("error" -> "internal_error", "message" -> "Something went wrong").asJson(500)
  }
}
```

## Error Handler Chaining

Multiple error handlers are tried in registration order. Return `skip` to pass an error to the next handler:

```scala
server
  // Handle validation errors
  .use { (error: ServerError, _: Request) =>
    error match {
      case e: ValidationError =>
        Map("type" -> "validation", "message" -> e.message).asJson(400)
      case _ => skip
    }
  }
  // Handle auth errors
  .use { (error: ServerError, _: Request) =>
    error match {
      case e: AuthError =>
        Map("type" -> "auth", "message" -> e.message).asJson(401)
      case _ => skip
    }
  }
  // Catch-all
  .use { (error: ServerError, _: Request) =>
    Map("type" -> "error", "message" -> error.message).asJson(500)
  }
```

## Custom Error Types

Define domain-specific errors:

```scala
sealed trait DomainError extends ServerError

case class ConflictError(message: String) extends DomainError
case class RateLimitExceeded(message: String, retryAfter: Long) extends DomainError
case class StorageError(message: String, cause: Throwable) extends DomainError
```

Handle them in error handlers:

```scala
server.use { (error: ServerError, _: Request) =>
  error match {
    case e: ConflictError =>
      Map("error" -> "conflict", "message" -> e.message).asJson(409)
    case e: RateLimitExceeded =>
      Response.json(Map("error" -> e.message), 429)
        .withHeader("Retry-After", e.retryAfter.toString)
        .pipe(r => Future.successful(Complete(r)))
    case _ => skip
  }
}
```

## Error Transformation

Transform low-level errors into domain errors:

```scala
server.use { (error: ServerError, _: Request) =>
  error match {
    case e: ValidationError =>
      Future.successful(Fail(
        DomainError(s"Validation failed: ${e.message}", "VALIDATION_001")
      ))
    case _ => skip
  }
}
```

## Unhandled Errors

If no error handler handles an error (all return `skip`), Apion sends the error's default response. The built-in error types produce JSON responses:

| Error Type | Status | Response Body |
|-----------|--------|--------------|
| `ValidationError` | 400 | `{"error":"validation_error","message":"..."}` |
| `AuthError` | 401 | `{"error":"auth_error","message":"..."}` |
| `NotFoundError` | 404 | `{"error":"not_found","message":"..."}` |

## Errors in Middleware

Middleware can fail to stop the handler chain:

```scala
val requireApiKey: Handler = request =>
  request.header("x-api-key") match {
    case Some(key) if isValidKey(key) =>
      Future.successful(Continue(request))
    case Some(_) =>
      failAuth("Invalid API key")
    case None =>
      failAuth("Missing API key")
  }
```

## Best Practices

- **Fail early** — Validate inputs and return errors before doing work
- **Use specific error types** — `ValidationError` over generic `ServerError`
- **Always have a catch-all** — Register a final error handler that handles any `ServerError`
- **Include context** — Error messages should help the client fix the problem
- **Don't leak internals** — Catch-all handlers should return generic messages for unexpected errors
