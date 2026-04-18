---
title: Request
description: Accessing request data — params, headers, body, and context
---

The `Request` is an immutable case class that carries all information about an incoming HTTP request.

## Properties

```scala
case class Request(
  method: String,                        // HTTP method (GET, POST, etc.)
  url: String,                           // Full URL including query string
  path: String,                          // URL path only
  headers: Map[String, String],          // Request headers (lowercase keys)
  params: Map[String, String],           // Path parameters from route
  query: Map[String, String],            // Query string parameters
  context: Map[String, Any],             // Extensible context for middleware data
  rawRequest: ServerRequest,             // Underlying Node.js request object
  basePath: String,                      // Accumulated base path from subrouters
  finalizers: List[Finalizer],           // Response transformers (LIFO)
  cookies: Map[String, String],          // Parsed cookies
)
```

## Path Parameters

Extract named segments from the route pattern:

```scala
server.get("/users/:id/posts/:postId", request => {
  val userId = request.params("id")
  val postId = request.params("postId")
  s"User $userId, Post $postId".asText
})
```

## Query Parameters

Access query string values:

```scala
// GET /search?q=scala&page=2
server.get("/search", request => {
  val query = request.query.getOrElse("q", "")
  val page = request.query.getOrElse("page", "1").toInt
  s"Searching '$query' page $page".asText
})
```

## Headers

Headers are stored with lowercase keys for case-insensitive access:

```scala
val token = request.header("authorization")
  .filter(_.startsWith("Bearer "))
  .map(_.substring(7))

val contentType = request.header("content-type")
```

## Body Parsing

Request body is read lazily as a stream. Four methods are available:

### Raw Body

```scala
request.body.flatMap { buffer: Buffer =>
  // Process binary data
  buffer.asBinary
}
```

### Text Body

Handles charset detection from the Content-Type header:

```scala
request.text.flatMap { text: String =>
  s"Received: $text".asText
}
```

### JSON Body

Type-safe parsing with zio-json. Define your types with `derives`:

```scala
case class User(name: String, email: String) derives JsonDecoder

request.json[User].flatMap {
  case Some(user) => user.asJson(201)
  case None       => "Invalid JSON".asText(400)
}
```

### Form Data

URL-encoded form bodies (`application/x-www-form-urlencoded`):

```scala
request.form.flatMap { formData: Map[String, String] =>
  val username = formData.getOrElse("username", "")
  val password = formData.getOrElse("password", "")
  processLogin(username, password)
}
```

## Body Limits

Configure maximum body size and read timeout:

```scala
// Global defaults
Request.maxBodySize = 50 * 1024 * 1024  // 50 MB (default)
Request.bodyTimeout = 30000             // 30 seconds (default)
```

For per-route limits, use `BodyLimitMiddleware`:

```scala
server.post("/upload", BodyLimitMiddleware(10 * 1024 * 1024), uploadHandler)
```

## Connection Info

```scala
request.ip          // Remote IP address (String)
request.hostname    // Host header value (Option[String])
request.port        // Remote port (Option[Int])
request.protocol    // "http" or "https" (String)
request.secure      // true if HTTPS (Boolean)
request.httpVersion // HTTP version string
request.complete    // Whether the request has been fully received
request.aborted     // Whether the client aborted the connection
```

## Cookies

```scala
// Access a cookie by name
request.cookie("session")  // Option[String]

// All cookies
request.cookies  // Map[String, String]
```

With `CookieMiddleware` enabled, additional methods are available:

```scala
request.getSignedCookie("auth")          // Verified signed cookie
request.getJsonCookie[Settings]("prefs") // Parse JSON cookie
```

## Context

The context is a `Map[String, Any]` used by middleware to pass data to downstream handlers:

```scala
// Middleware adds data
val withUser: Handler = request => {
  val user = lookupUser(request.params("id"))
  Future.successful(Continue(
    request.copy(context = request.context + ("user" -> user))
  ))
}

// Handler reads it
val handler: Handler = request => {
  request.context.get("user") match {
    case Some(user: User) => user.asJson
    case _                => failNotFound("User not found")
  }
}
```

The `AuthMiddleware` stores an `Auth` object in context under the `"auth"` key:

```scala
request.context.get("auth") match {
  case Some(auth: AuthMiddleware.Auth) =>
    println(s"User: ${auth.user}, Roles: ${auth.roles}")
  case _ => // Not authenticated
}
```

## Adding Finalizers

Attach response transformers that run after a `Complete` result:

```scala
val modifiedRequest = request.addFinalizer { (req, response) =>
  Future.successful(response.copy(
    headers = response.headers.add("X-Custom", "value")
  ))
}
```

Finalizers execute in LIFO order — the last one added runs first.
