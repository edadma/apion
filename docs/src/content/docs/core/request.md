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
  query: Map[String, Seq[String]],       // Query string parameters (multi-valued)
  context: Context,                      // Type-safe extensible context for middleware data
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

Query strings are multi-valued (repeated keys are preserved). Use `queryParam` for
the first value, or index `query` for all values of a key:

```scala
// GET /search?q=scala&page=2&tag=fp&tag=web
server.get("/search", request => {
  val q    = request.queryParam("q").getOrElse("")            // first value: Option[String]
  val page = request.queryParam("page").map(_.toInt).getOrElse(1)
  val tags = request.query.getOrElse("tag", Nil)              // all values: Seq[String]
  s"Searching '$q' page $page tags ${tags.mkString(",")}".asText
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

Form bodies are multi-valued too (`Map[String, Seq[String]]`); `formField` returns
the first value of a field:

```scala
request.form.flatMap { formData: Map[String, Seq[String]] =>
  val username = formData.get("username").flatMap(_.headOption).getOrElse("")
  val password = formData.get("password").flatMap(_.headOption).getOrElse("")
  processLogin(username, password)
}
```

## Body Limits

Configure maximum body size and read timeout:

```scala
// Per-server defaults via ServerConfig
val server = Server(ServerConfig(
  maxBodySize = 50 * 1024 * 1024,  // 50 MB (default)
  bodyTimeout = 30000,             // 30 seconds (default)
))
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

The context is a type-safe, immutable store (`Context`) that middleware use to pass
data to downstream handlers. Values are keyed by a `TypedKey[A]`, so reads recover
the value's type with no casting — define each key once and share it between the
writer and the reader:

```scala
// Define a key (typically a val in a companion object)
val UserKey: TypedKey[User] = TypedKey("user")

// Middleware adds data
val withUser: Handler = request => {
  val user = lookupUser(request.params("id"))
  Future.successful(Continue(
    request.copy(context = request.context.updated(UserKey, user))
  ))
}

// Handler reads it — typed as Option[User], no cast needed
val handler: Handler = request =>
  request.context.get(UserKey) match {
    case Some(user) => user.asJson
    case None       => failNotFound("User not found")
  }
```

`AuthMiddleware` stores its `Auth` under `AuthMiddleware.authKey`:

```scala
request.context.get(AuthMiddleware.authKey) match {
  case Some(auth) => println(s"User: ${auth.user}, Roles: ${auth.roles}")
  case None       => // Not authenticated
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
