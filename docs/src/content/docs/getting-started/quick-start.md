---
title: Quick Start
description: Build your first Apion server in minutes
---

## Hello World

Create a simple server that responds to GET and POST requests:

```scala
import io.github.edadma.apion._
import zio.json._

case class User(name: String, email: String) derives JsonEncoder, JsonDecoder

@main def run(): Unit =
  Server()
    .use(LoggingMiddleware())
    .use(CorsMiddleware())
    .get("/hello", _ => "Hello World!".asText)
    .post("/users", _.json[User].flatMap {
      case Some(user) => user.asJson(201)
      case _          => "Invalid user data".asText(400)
    })
    .listen(3000) { println("Server running at http://localhost:3000") }
```

## Test It

```bash
# GET request
curl http://localhost:3000/hello
# => Hello World!

# POST JSON
curl -X POST http://localhost:3000/users \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice", "email": "alice@example.com"}'
# => {"name":"Alice","email":"alice@example.com"}
```

## Add Path Parameters

```scala
server.get("/users/:id", request => {
  val userId = request.params("id")
  s"User $userId".asText
})
```

## Use Subrouters

Group related routes under a common prefix:

```scala
val usersRouter = Router()
  .get("/", _ => "List users".asText)
  .get("/:id", req => s"User ${req.params("id")}".asText)
  .post("/", req => req.json[User].flatMap {
    case Some(user) => user.asJson(201)
    case _          => "Invalid data".asText(400)
  })

Server()
  .use("/api/users", usersRouter)
  .listen(3000) { println("Running") }
```

## Add Error Handling

```scala
Server()
  .get("/users/:id", request => {
    val id = request.params("id")
    findUser(id) match {
      case Some(user) => user.asJson
      case None       => failNotFound(s"User $id not found")
    }
  })
  .use { (error: ServerError, request: Request) =>
    error match {
      case NotFoundError(msg) =>
        Map("error" -> "not_found", "message" -> msg).asJson(404)
      case ValidationError(msg) =>
        Map("error" -> "validation", "message" -> msg).asJson(400)
      case _ =>
        Map("error" -> "internal").asJson(500)
    }
  }
  .listen(3000) { println("Running") }
```

## Add Middleware Stack

A typical production-ready middleware setup:

```scala
Server()
  .use(LoggingMiddleware())
  .use(CompressionMiddleware())
  .use(CorsMiddleware.production(Set("https://myapp.com")))
  .use(SecurityMiddleware.api())
  .use(RateLimiterMiddleware.moderate(100, 1.minute))
  .get("/health", _ => Map("status" -> "ok").asJson)
  .use("/api", apiRouter)
  .listen(3000) { println("Running") }
```

## Next Steps

- Learn about [Handlers & Results](/core/handlers/) — the unified type system
- Explore [Request](/core/request/) and [Response](/core/response/) APIs
- Set up [Authentication](/middleware/auth/) with JWT
- See a full [API Server example](/guides/api-server/)
