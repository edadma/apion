---
title: Custom Middleware
description: How to write your own middleware
---

Middleware in Apion is just a `Handler` — a function from `Request` to `Future[Result]`. There are three common patterns.

## Pattern 1: Modify the Request

Add data to the request context for downstream handlers:

```scala
def userLookup(userService: UserService): Handler = request =>
  request.header("authorization") match {
    case Some(auth) if auth.startsWith("Bearer ") =>
      val token = auth.substring(7)
      userService.getUserFromToken(token).map {
        case Some(user) =>
          Continue(request.copy(
            context = request.context + ("user" -> user)
          ))
        case None =>
          Fail(AuthError("Invalid token"))
      }
    case _ =>
      Future.successful(Continue(request))
  }
```

## Pattern 2: Short-Circuit

Return a response immediately, stopping the handler chain:

```scala
val requireApiKey: Handler = request =>
  request.header("x-api-key") match {
    case Some(key) if isValidKey(key) =>
      Future.successful(Continue(request))
    case _ =>
      "Missing or invalid API key".asText(401)
  }
```

## Pattern 3: Transform the Response

Use a finalizer to modify the response after a downstream handler completes:

```scala
def timingMiddleware: Handler = request => {
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

Finalizers run in LIFO order — the last one added runs first.

## Combining Patterns

A single middleware can modify the request, add a finalizer, and conditionally short-circuit:

```scala
def requestId: Handler = request => {
  val id = generateUUID()

  // Add to context
  val updated = request.copy(
    context = request.context + ("requestId" -> id)
  )

  // Add to response headers via finalizer
  val finalizer: Finalizer = (_, response) =>
    Future.successful(response.copy(
      headers = response.headers.add("X-Request-Id", id)
    ))

  Future.successful(Continue(updated.addFinalizer(finalizer)))
}
```

## Configurable Middleware

Follow the pattern used by built-in middleware — a companion object with an `Options` case class and an `apply` method:

```scala
object ApiKeyMiddleware {
  case class Options(
    headerName: String = "x-api-key",
    keys: Set[String] = Set.empty,
    skip: Request => Boolean = _ => false,
  )

  def apply(options: Options = Options()): Handler = request => {
    if (options.skip(request)) {
      Future.successful(Continue(request))
    } else {
      request.header(options.headerName) match {
        case Some(key) if options.keys.contains(key) =>
          Future.successful(Continue(request))
        case _ =>
          "Invalid API key".asText(401)
      }
    }
  }
}
```

Usage:

```scala
server.use(ApiKeyMiddleware(ApiKeyMiddleware.Options(
  keys = Set("key-1", "key-2"),
  skip = _.path == "/health",
)))
```

## Middleware Ordering

Middleware executes in registration order. Put middleware that should run first (logging, CORS, security) before middleware that depends on them (auth, rate limiting):

```scala
server
  .use(LoggingMiddleware())      // 1. Log all requests
  .use(CorsMiddleware())         // 2. Handle CORS
  .use(SecurityMiddleware())     // 3. Add security headers
  .use(authMiddleware)           // 4. Verify authentication
  .use(rateLimiter)              // 5. Rate limit authenticated requests
```
