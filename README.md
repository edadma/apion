# Apion

A lightweight, Express-inspired API server framework for Scala.js that provides a familiar developer experience while leveraging Scala's type safety and immutability.

## Key Features

- Express-like chainable API with type safety
- Pure functions and immutable types
- Unified handler/middleware system
- JWT authentication with role-based access control
- Body parsing for JSON and form data
- Type-safe request/response handling
- Comprehensive error handling
- Static file serving
- Request compression
- CORS and security headers

## Installation

Add to your `build.sbt`:

```scala
libraryDependencies += "io.github.edadma" %%% "apion" % "0.0.1"
```

## Quick Start

```scala
import io.github.edadma.apion._
import zio.json._
import scala.concurrent.Future

case class User(name: String, email: String) derives JsonEncoder, JsonDecoder

object ExampleServer {
  def main(args: Array[String]): Unit = {
    Server()
      .use(LoggingMiddleware())
      .use(CommonMiddleware.cors())
      .get("/hello", _ => "Hello World!".asText)
      .use(BodyParser.json[User]())
      .post("/users", request => {
        request.context.get("body") match {
          case Some(user: User) => user.asJson(201)
          case _ => "Invalid user data".asText(400)
        }
      })
      .listen(3000) {
        println("Server running at http://localhost:3000")
      }
  }
}
```

## Core Concepts

### Handlers

All request processors (middleware, routes, error handlers) share a unified type:

```scala
type Handler = Request => Future[Result]

sealed trait Result
case class Continue(request: Request) extends Result   // Continue processing
case class Complete(response: Response) extends Result // End with response
case class Fail(error: ServerError) extends Result    // Propagate error  
case object Skip extends Result                       // Try next route
```

### Middleware

Middleware can modify requests, generate responses, or handle errors:

```scala
// Authentication middleware
val auth = AuthMiddleware(
  requireAuth = true,
  excludePaths = Set("/public"),
  secretKey = "your-secret-key"
)

// Custom logging middleware
val logger: Handler = request => {
  println(s"${request.method} ${request.url}")
  Future.successful(Continue(request))
}

server.use(auth).use(logger)
```

### Routing

Supports path parameters, nested routes, and route grouping:

```scala
// Path parameters
server.get("/users/:id", request => {
  val userId = request.params("id")
  getUserById(userId).asJson
})

// Route grouping
val apiRouter = Router()
  .use(authMiddleware)
  .get("/users", listUsers)
  .post("/users", createUser)

server.use("/api", apiRouter)
```

### Request Processing

Access request data with type safety:

```scala
def handler(request: Request): Future[Result] = {
  // Access components
  val path = request.path
  val method = request.method
  val headers = request.headers
  val params = request.params
  val query = request.query
  
  // Get typed body from context
  request.context.get("body") match {
    case Some(user: User) => // Handle user data
    case _ => request.failValidation("Invalid body")
  }
}
```

### Response Building

Convenient response creation:

```scala
// JSON responses
data.asJson                    // 200 OK
data.asJson(201)              // Created
ErrorResponse("msg").asJson(400) // Bad Request

// Text responses
"Hello".asText                 // 200 OK
"Created".asText(201)         // Created

// Common responses
NotFound                      // 404 Not Found
BadRequest                    // 400 Bad Request
ServerError                   // 500 Internal Error
```

### Error Handling

Type-safe error propagation:

```scala
sealed trait ServerError extends RuntimeException
case class ValidationError(msg: String) extends ServerError
case class AuthError(msg: String) extends ServerError
case class NotFoundError(msg: String) extends ServerError

// In handlers
request.failValidation("Invalid input")
request.failAuth("Unauthorized")
request.failNotFound("Not found")
```

## Additional Features

### Static Files

```scala
server.use(StaticMiddleware("public", StaticMiddleware.StaticOptions(
  index = true,
  maxAge = 86400,
  etag = true
)))
```

### Compression

```scala
server.use(CompressionMiddleware(CompressionMiddleware.Options(
  level = 6,
  threshold = 1024,
  encodings = List("br", "gzip", "deflate")
)))
```

### Body Parsing

```scala
// JSON parsing
server.use(BodyParser.json[User]())

// Form data parsing
server.use(BodyParser.urlencoded())
```

## Testing

The framework includes testing utilities:

```scala
class APITests extends AsyncBaseSpec {
  "API" - {
    "should handle requests" in {
      val request = Request(
        method = "GET",
        url = "/test",
        headers = Map("Accept" -> "application/json")
      )
      
      val handler = request => "Test".asText
      
      handler(request).map {
        case Complete(response) => 
          response.status shouldBe 200
          response.body shouldBe "Test"
      }
    }
  }
}
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the ISC License.