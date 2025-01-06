---
title: API Reference
layout: default
nav_order: 2
has_children: true
---

# API Reference

Welcome to the Apion API documentation. This guide provides comprehensive information about Apion's components, patterns, and design principles.

## Design Philosophy

Apion is built on three core principles:

1. **Type Safety**: Leverage Scala's type system to prevent errors at compile time
2. **Immutability**: Use immutable data structures for predictable behavior
3. **Composability**: Build complex applications from simple, reusable components

## Core Architecture

```
Request → Router → Middleware Chain → Handler → Response
                         ↓
                   Error Handler
```

### Request/Response Pipeline

1. Router receives request and matches route
2. Middleware processes request in order
3. Handler generates response
4. Response flows back through middleware
5. Finalizers transform final response

## Core Components

### Server & Router

The entry point for your application:

```scala
val server = Server()
  .use(globalMiddleware)
  .get("/users", listUsers)
  .post("/users", createUser)
  
// Start server
server.listen(3000) { 
  println("Server running") 
}
```

Key features:
- Chainable API
- Type-safe route handling
- Middleware support
- Error propagation

### Request Processing

Type-safe request handling:

```scala
def handler(request: Request): Future[Result] = {
  // Access request data safely
  val userId = request.params("id")
  val auth = request.context.get("auth")
  
  // Type-safe body parsing
  request.json[User].flatMap {
    case Some(user) => processUser(user)
    case None => request.failValidation("Invalid user data")
  }
}
```

Components:
- [Request](request.md) - Request data and operations
- [Response](response.md) - Response building
- [Error Handling](errors.md) - Error types and handling

## Middleware System

### Middleware Types

1. **Request Transformation**
   ```scala
   request => Future.successful(Continue(
     request.copy(context = request.context + ("key" -> "value"))
   ))
   ```

2. **Response Generation**
   ```scala
   request => "Response".asText
   ```

3. **Error Handling**
   ```scala
   request => request.failAuth("Unauthorized")
   ```

4. **Response Transformation**
   ```scala
   val finalizer: Finalizer = (req, res) => 
     Future.successful(res.withHeader("X-Custom", "value"))
   ```

### Built-in Middleware

Security:
- [Authentication](middleware/auth.md) - JWT-based auth
- [Security Headers](middleware/security.md) - Security best practices
- [CORS](middleware/cors.md) - Cross-origin resource sharing
- [Rate Limiting](middleware/rate-limiting.md) - Request throttling

Content:
- [Static Files](middleware/static.md) - File serving
- [Compression](middleware/compression.md) - Response compression

Utilities:
- [Cookies](middleware/cookies.md) - Cookie management
- [Logging](middleware/logging.md) - Request logging

## Type Safety Features

### Route Parameters
```scala
// Type-safe parameter extraction
server.get("/users/:id", request => {
  val userId: String = request.params("id")
  getUserById(userId).asJson
})
```

### JSON Handling
```scala
case class User(name: String, email: String) derives JsonEncoder, JsonDecoder

// Compile-time JSON validation
request.json[User].flatMap {
  case Some(user) => user.asJson(201)
  case None => request.failValidation("Invalid JSON")
}
```

### Error Propagation
```scala
sealed trait ServerError
case class ValidationError(msg: String) extends ServerError
case class AuthError(msg: String) extends ServerError

// Type-safe error handling
def secured(handler: Handler): Handler = request =>
  request.context.get("auth") match {
    case Some(_) => handler(request)
    case None => request.failAuth("Unauthorized")
  }
```

## Common Patterns

### Middleware Composition

```scala
// Compose multiple middleware
val api = Router()
  .use(auth)        // Authentication
  .use(rateLimit)   // Rate limiting
  .use(compress)    // Compression
  
server.use("/api", api)
```

### Error Handling Chain

```scala
// Chain of responsibility pattern
def errorHandler(error: ServerError): Response = error match {
  case ValidationError(msg) => Response.json(
    Map("error" -> msg), 400)
  case AuthError(msg) => Response.json(
    Map("error" -> msg), 401)
  case _ => Response.json(
    Map("error" -> "Internal error"), 500)
}
```

### Request Context

```scala
// Share data between middleware
val withUser = request => {
  val userId = request.params("id")
  val user = getUserById(userId)
  
  Future.successful(Continue(
    request.copy(context = request.context + ("user" -> user))
  ))
}

val requireAdmin = request => {
  request.context.get("user")
    .collect { case user: User if user.isAdmin => 
      Continue(request)
    }
    .getOrElse(Fail(AuthError("Admin required")))
}
```

## Testing

### Unit Testing
```scala
// Test handler directly
val request = Request.mock(
  method = "GET",
  url = "/users/123",
  params = Map("id" -> "123")
)

handler(request).map { result =>
  result shouldBe Complete(
    Response.json(user, 200))
}
```

### Integration Testing
```scala
// Test full server
val port = 3000
var httpServer: NodeServer = null

// Set up server before tests
override def beforeAll(): Unit = {
  val server = Server()
    .use(authMiddleware)
    .get("/users/:id", handler)

  httpServer = server.listen(port) {}
}

// Clean up after tests
override def afterAll(): Unit = {
  if (httpServer != null) {
    httpServer.close(() => ())
  }
}

// Test endpoints
"Server endpoints" - {
  "should handle GET /users/:id" in {
    fetch(s"http://localhost:$port/users/123")
      .toFuture
      .flatMap(response => {
        response.status shouldBe 200
        response.json().toFuture
      })
      .map(json => {
        // Verify response JSON
        json.id shouldBe "123"
      })
  }
}
```

## Performance Considerations

1. **Request Processing**
    - Use streaming for large bodies
    - Avoid unnecessary parsing
    - Leverage type-safe parsing

2. **Middleware**
    - Order middleware by frequency
    - Use conditional middleware
    - Minimize context modifications

3. **Response Generation**
    - Use compression when appropriate
    - Stream large responses
    - Leverage caching headers

## Node.js Integration

### Using Node.js Modules
```scala
// Access Node.js functionality
import io.github.edadma.nodejs.fs

val stats = fs.promises.stat(path)
val stream = fs.createReadStream(path)
```

### Express.js Migration
```scala
// Express.js
app.use(express.json())
app.post("/users", (req, res) => {
  const user = req.body
  res.json(user)
})
app.get("/users/:id", (req, res) => {
  res.json({ id: req.params.id })
})

// Apion equivalent
server
  .post("/users", request => {
    // Built-in body parsing
    request.json[User].flatMap {
      case Some(user) => user.asJson
      case None => "Invalid JSON".asText(400)
    }
  })
  .get("/users/:id", request => 
    Map("id" -> request.params("id")).asJson
  )
```

## Additional Resources

- [GitHub Repository](https://github.com/edadma/apion)
- [Bug Reports](https://github.com/edadma/apion/issues)
- [Release Notes](../releases)
