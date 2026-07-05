---
title: Middleware Overview
description: How middleware works in Apion and how to compose it
---

In Apion, middleware is just a `Handler` — there's no separate middleware type. A middleware handler typically returns `Continue(modifiedRequest)` to pass control to the next handler, but can also return `Complete`, `Fail`, or `Skip`.

## Applying Middleware

### Global

Applies to all requests:

```scala
server
  .use(LoggingMiddleware())
  .use(CorsMiddleware())
```

### Path-Scoped

Applies only to routes matching the prefix:

```scala
server.use("/api", authMiddleware)
```

### Router-Scoped

Applies only within a router:

```scala
val adminRouter = Router()
  .use(requireAdmin)
  .get("/dashboard", dashboardHandler)

server.use("/admin", adminRouter)
```

### Route-Level

Chain handlers for a specific route:

```scala
server.get("/protected", authMiddleware, handler)
```

## Middleware Patterns

### Modify the Request

Add data to the context for downstream handlers:

```scala
val UserKey: TypedKey[User] = TypedKey("user")

val withUser: Handler = request => {
  val user = lookupUser(request.params("id"))
  Future.successful(Continue(
    request.copy(context = request.context.updated(UserKey, user))
  ))
}
```

### Short-Circuit

Return a response immediately, stopping the chain:

```scala
val requireAuth: Handler = request =>
  request.header("authorization") match {
    case Some(_) => Future.successful(Continue(request))
    case None    => "Unauthorized".asText(401)
  }
```

### Transform the Response

Use finalizers to modify the response after a handler completes:

```scala
val addHeader: Handler = request => {
  val finalizer: Finalizer = (_, response) =>
    Future.successful(response.copy(
      headers = response.headers.add("X-Custom", "value")
    ))
  Future.successful(Continue(request.addFinalizer(finalizer)))
}
```

## Execution Order

1. Global middleware runs in registration order
2. Path-scoped middleware runs if the path matches
3. Route handlers run if the method and path match
4. Finalizers run in LIFO order on the response

## Built-in Middleware

| Middleware | Purpose |
|-----------|---------|
| [`AuthMiddleware`](../auth/) | JWT authentication with RBAC |
| [`CorsMiddleware`](../cors/) | Cross-origin resource sharing |
| [`SecurityMiddleware`](../security/) | Security headers |
| [`LoggingMiddleware`](../logging/) | Request/response logging |
| [`CompressionMiddleware`](../compression/) | Response compression |
| [`StaticMiddleware`](../static/) | Static file serving |
| [`CookieMiddleware`](../cookies/) | Cookie management |
| [`RateLimiterMiddleware`](../rate-limiting/) | Request throttling |
| [`FileUploadMiddleware`](../file-uploads/) | Multipart file uploads |
| [`BodyLimitMiddleware`](../body-limit/) | Request body size limits |
