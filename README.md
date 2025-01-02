<p align="center">
  <img src="public/apion.png" width="200" alt="Fluxus Logo">
</p>

<h1 align="center">Apion</h1>

<p align="center">
  A lightweight, Express-inspired API server framework for Scala.js that provides a familiar developer experience while leveraging Scala's type safety and immutability.
</p>

<p align="center">
  <a href="https://github.com/edadma/fluxus"><img src="https://img.shields.io/github/v/release/edadma/fluxus?include_prereleases&sort=semver" alt="Version"></a>
  <a href="https://opensource.org/licenses/ISC"><img src="https://img.shields.io/badge/License-ISC-blue.svg" alt="License: ISC"></a>
  <a href="https://www.scala-js.org/"><img src="https://img.shields.io/badge/Scala.js-1.17.0-blue.svg" alt="Scala.js: 1.17.0">
  <a href="https://tailwindcss.com/"><img src="https://img.shields.io/badge/TailwindCSS-3.4-blue.svg" alt="TailwindCSS: 3.4">
  <a href="https://daisyui.com/"><img src="https://img.shields.io/badge/DaisyUI-4.12-blue.svg" alt="DaisyUI: 4.12">
</p>

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
libraryDependencies += "io.github.edadma" %%% "apion" % "0.0.2"
```

## Quick Start

```scala 3
import io.github.edadma.apion._
import zio.json._

case class User(name: String, email: String) derives JsonEncoder, JsonDecoder

@main
def run(): Unit =
  Server()
    .use(LoggingMiddleware())
    .use(CorsMiddleware())
    .get("/hello", _ => "Hello World!".asText)
    .use(BodyParser.json[User]())
    .post(
      "/users",
      request => {
        request.context.get("body") match {
          case Some(user: User) => user.asJson(201)
          case _                => "Invalid user data".asText(400)
        }
      },
    )
    .listen(3000) {
      println("Server running at http://localhost:3000")
    }
```

Test the server using curl:

```bash
# Test the hello endpoint
curl http://localhost:3000/hello

# Create a new user
curl -X POST http://localhost:3000/users \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice", "email": "alice@example.com"}'
```

Expected responses:

```
# GET /hello response:
Hello World!

# POST /users response (status 201):
{"name":"Alice","email":"alice@example.com"}
```

## Core Concepts

### Handlers

All request processors (middleware, routes, error handlers) share a unified type:

```scala
type Handler = Request => Future[Result]

sealed trait Result
case class Continue(request: Request) extends Result   // Continue processing
case class Complete(response: Response) extends Result // End with response
case class Fail(error: ServerError) extends Result     // Propagate error  
case object Skip extends Result                        // Try next route
```

### Middleware

Middleware can modify requests, generate responses, or handle errors:

```scala
val auth = AuthMiddleware(AuthMiddleware.AuthConfig(
  secretKey = "your-secret-key",
  requireAuth = true,
  excludePaths = Set("/public"),
  audience = Some("your-app"),
  issuer = "your-service"
))

// Cookie middleware
val cookies = CookieMiddleware(CookieMiddleware.Options(
  secret = Some("cookie-secret"),
  parseJSON = true
))

// Security headers
val security = SecurityMiddleware(SecurityMiddleware.Options(
  contentSecurityPolicy = true,
  frameguard = true,
  xssFilter = true
))

server.use(auth).use(cookies).use(security)
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
data.asJson                      // 200 OK
data.asJson(201)                 // Created
ErrorResponse("msg").asJson(400) // Bad Request

// Text responses
"Hello".asText                   // 200 OK
"Created".asText(201)            // Created

// Common responses
NotFound                         // 404 Not Found
BadRequest                       // 400 Bad Request
ServerError                      // 500 Internal Error
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
  index = true,           // Serve index.html for directories
  dotfiles = "ignore",    // How to handle dotfiles
  etag = true,           // Enable ETag generation
  maxAge = 3600,         // Cache max-age in seconds
  redirect = true,       // Redirect directories to trailing slash
  fallthrough = true     // Continue to next handler if file not found
)))
```

### Cookie Handling
```scala
server
  .use(CookieMiddleware(CookieMiddleware.Options(
    secret = Some("cookie-secret"),
    parseJSON = true
  )))
  .get("/set-cookie", request => 
    Future.successful(Complete(
      Response.text("Cookie set")
        .withCookie(
          name = "session",
          value = "abc123",
          maxAge = Some(3600),
          httpOnly = true,
          secure = true
        )
    ))
  )
  .get("/read-cookie", request => {
    request.cookie("session") match {
      case Some(value) => s"Cookie value: $value".asText
      case None => "No cookie found".asText(404)
    }
  })
```

### Compression

```scala
server.use(CompressionMiddleware(CompressionMiddleware.Options(
  // Compression filter options
  level = 6,          // compression level 0-9
  threshold = 1024,   // minimum size in bytes to compress
  memLevel = 8,       // memory usage level 1-9
  windowBits = 15,    // window size 9-15

  // Brotli-specific options
  brotliQuality = 11,      // compression quality 0-11
  brotliBlockSize = 4096,  // block size 16-24

  // Filter options
  filter = _ => true,  // function to determine if response should be compressed

  // Which encodings to support/prefer (in order of preference)
  encodings = List("br", "gzip", "deflate")
)))
```

### Body Parsing

```scala
// JSON parsing with type-safe handling
case class User(name: String, email: String) derives JsonEncoder, JsonDecoder

server
  .use(BodyParser.json[User]())
  .post("/users", request => {
    request.context.get("body") match {
      case Some(userData: User) =>
        // Body has been parsed and type-checked
        userData.asJson(201)
      case _ =>
        "Invalid request body".asText(400)
    }
  })

// URL-encoded form data parsing
server
  .use(BodyParser.urlencoded())
  .post("/form", request => {
    request.context.get("form") match {
      case Some(formData: Map[String, String]) =>
        // Access form fields
        val name = formData.getOrElse("name", "")
        formData.asJson
      case _ =>
        "Invalid form data".asText(400)
    }
  })
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Write your changes
4. Add appropriate tests:
    - Unit tests for pure functions and utilities
    - Integration tests for middleware, request handling chains, and complex features
    - Consider both kinds when changes affect multiple areas
5. Verify all tests pass with `sbt test`
6. Push to the branch
7. Create a Pull Request with:
    - Description of changes
    - Summary of tests added
    - Any necessary documentation updates

See `apion/src/test/scala/io/github/edadma/apion` for examples of:
- Unit tests: `JWTTests.scala` shows testing pure JWT functionality
- Integration tests: `AuthIntegrationTests.scala` shows testing middleware behavior in a running server

## License

This project is licensed under the ISC License.
