---
title: Handlers & Results
description: The unified type system at the core of Apion
---

Every request processor in Apion — routes, middleware, and error handlers — uses a single type:

```scala
type Handler = Request => Future[Result]
```

## Result Types

A handler returns one of four results:

```scala
sealed trait Result

case class Continue(request: Request) extends Result
case class Complete(response: Response) extends Result
case class Fail(error: ServerError) extends Result
case object Skip extends Result
```

### Continue

Pass a (possibly modified) request to the next handler. This is how middleware works — it transforms the request and passes it along:

```scala
val addTimestamp: Handler = request =>
  Future.successful(Continue(
    request.copy(context = request.context + ("startTime" -> System.currentTimeMillis()))
  ))
```

### Complete

End the handler chain and send a response to the client:

```scala
val hello: Handler = _ =>
  Future.successful(Complete(Response.text("Hello World")))

// Or using the DSL:
val hello: Handler = _ => "Hello World".asText
```

### Fail

Propagate a typed error. The error flows to the next registered error handler:

```scala
val requireId: Handler = request =>
  request.params.get("id") match {
    case Some(id) => Future.successful(Continue(request))
    case None     => failValidation("Missing id parameter")
  }
```

### Skip

Skip this handler and try the next one. Useful in error handlers that only handle specific error types:

```scala
server.use { (error: ServerError, request: Request) =>
  error match {
    case e: ValidationError => Map("error" -> e.message).asJson(400)
    case _                  => skip  // Let the next error handler deal with it
  }
}
```

## Request Flow

1. The server receives an HTTP request and creates an immutable `Request`
2. Handlers execute sequentially (middleware first, then routes)
3. Each handler returns a `Result` that determines what happens next
4. When a handler returns `Complete`, response finalizers run in LIFO order
5. The response is sent to the client

## Error Types

Apion provides three built-in error types:

```scala
trait ServerError extends Throwable

case class ValidationError(message: String) extends ServerError
case class AuthError(message: String) extends ServerError
case class NotFoundError(message: String) extends ServerError
```

Create errors using the DSL helpers:

```scala
failValidation("Invalid input")   // Future[Fail(ValidationError(...))]
failAuth("Unauthorized")          // Future[Fail(AuthError(...))]
failNotFound("Not found")         // Future[Fail(NotFoundError(...))]
fail(CustomError("oops"))         // Future[Fail(yourError)]
```

### Custom Error Types

Define domain-specific errors by extending `ServerError`:

```scala
case class RateLimitError(message: String, retryAfter: Long) extends ServerError
case class ConflictError(message: String) extends ServerError
```

## Error Handlers

Error handlers receive both the error and the original request:

```scala
server.use { (error: ServerError, request: Request) =>
  error match {
    case ValidationError(msg) =>
      Map("error" -> "validation", "message" -> msg).asJson(400)
    case AuthError(msg) =>
      Map("error" -> "auth", "message" -> msg).asJson(401)
    case NotFoundError(msg) =>
      Map("error" -> "not_found", "message" -> msg).asJson(404)
    case _ =>
      Map("error" -> "internal").asJson(500)
  }
}
```

Error handlers can also transform errors:

```scala
server.use { (error: ServerError, _: Request) =>
  error match {
    case e: ValidationError =>
      Future.successful(Fail(CustomError(s"Validation failed: ${e.message}")))
    case _ => skip
  }
}
```

## Composing Handlers

### Route-Level Chaining

Pass multiple handlers to a route — they execute sequentially:

```scala
server.get("/admin/users",
  authMiddleware,       // Verify JWT
  requireAdmin,         // Check admin role
  listUsersHandler      // Return data
)
```

### Global Middleware

Apply a handler to all routes:

```scala
server
  .use(LoggingMiddleware())
  .use(CorsMiddleware())
```

### Path-Scoped Middleware

Apply a handler only to routes matching a prefix:

```scala
server.use("/api", authMiddleware)
```

## Finalizers

Finalizers transform a response after a handler returns `Complete`. They execute in LIFO (last-in, first-out) order:

```scala
type Finalizer = (Request, Response) => Future[Response]
```

Middleware adds finalizers via `request.addFinalizer`:

```scala
val timing: Handler = request => {
  val start = System.currentTimeMillis()

  val finalizer: Finalizer = (_, response) => {
    val duration = System.currentTimeMillis() - start
    Future.successful(response.copy(
      headers = response.headers.add("X-Response-Time", s"${duration}ms")
    ))
  }

  Future.successful(Continue(request.addFinalizer(finalizer)))
}
```

This pattern is used by `CompressionMiddleware`, `LoggingMiddleware`, and `CookieMiddleware` to inspect or modify responses without breaking the linear handler flow.
