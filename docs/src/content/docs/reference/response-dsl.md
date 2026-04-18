---
title: Response DSL
description: Convenience methods for building responses
---

Apion provides extension methods and helper functions for concise response creation. These are available via `import io.github.edadma.apion._`.

## Text Responses

```scala
"Hello World".asText          // Future[Complete] — 200 OK, text/plain
"Created".asText(201)         // Future[Complete] — 201 Created
```

## JSON Responses

Any type with a `JsonEncoder` instance:

```scala
case class User(name: String) derives JsonEncoder

User("Alice").asJson          // Future[Complete] — 200 OK, application/json
User("Alice").asJson(201)     // Future[Complete] — 201 Created
```

Works with Maps too:

```scala
Map("status" -> "ok").asJson
Map("error" -> "oops").asJson(400)
```

## Binary Responses

```scala
buffer.asBinary               // Future[Complete] — 200 OK, application/octet-stream
buffer.asBinary(200)
```

## Standalone Functions

These return `Future[Result]` and can be used as handler return values:

```scala
// JSON
json(data)                    // 200 OK
json(data, 201)               // 201 Created

// Text
text("Hello")                 // 200 OK
text("Created", 201)          // 201 Created

// No content
noContent                     // 204 No Content

// Created with optional Location header
created(data)                 // 201 with JSON body
created(data, Some("/users/123"))  // 201 with Location header
```

## Common Error Responses

Pre-built responses for common HTTP errors:

```scala
notFound                      // 404 — {"error":"Not Found"}
badRequest                    // 400 — {"error":"Bad Request"}
serverError                   // 500 — {"error":"Internal Server Error"}
```

## Error Helpers

Create `Fail` results with typed errors:

```scala
skip                          // Future[Skip]
fail(error)                   // Future[Fail(error)]
failValidation("Bad input")   // Future[Fail(ValidationError(...))]
failAuth("Unauthorized")      // Future[Fail(AuthError(...))]
failNotFound("Not found")     // Future[Fail(NotFoundError(...))]
```

## Result Extension Methods

Add headers to any `Future[Result]`:

```scala
"Hello".asText
  .withHeader("X-Custom", "value")

data.asJson
  .withHeaders(
    "X-Request-Id" -> id,
    "X-Duration" -> s"${ms}ms",
  )
```

## Response Extension Methods

Modify `Response` objects directly:

```scala
// Cookies
response.withCookie("session", "abc123")
response.withCookie(
  name = "session",
  value = "abc123",
  maxAge = Some(3600),
  path = Some("/"),
  httpOnly = true,
  secure = true,
  sameSite = Some("Strict"),
)
response.withCookie(Cookie(name = "k", value = "v"))
response.clearCookie("session")
```

## Complete Example

```scala
server
  .get("/hello", _ => "Hello World".asText)
  .get("/user/:id", req => {
    findUser(req.params("id")) match {
      case Some(user) => user.asJson
      case None       => failNotFound("User not found")
    }
  })
  .post("/users", req => req.json[CreateUser].flatMap {
    case Some(data) =>
      val user = createUser(data)
      created(user, Some(s"/users/${user.id}"))
    case None =>
      failValidation("Invalid JSON body")
  })
  .delete("/users/:id", req => {
    deleteUser(req.params("id"))
    noContent
  })
```
