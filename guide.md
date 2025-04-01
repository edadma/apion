# Apion: Scala.js API Server Framework - Programmer's Guide

## Table of Contents

1. [Introduction](#introduction)
2. [Core Concepts](#core-concepts)
3. [Server Setup](#server-setup)
4. [Request Handling](#request-handling)
5. [Response Creation](#response-creation)
6. [Routing](#routing)
7. [Middleware](#middleware)
8. [Error Handling](#error-handling)
9. [Authentication](#authentication)
10. [Static File Serving](#static-file-serving)
11. [Cookies](#cookies)
12. [CORS](#cors)
13. [Compression](#compression)
14. [Security Headers](#security-headers)
15. [Rate Limiting](#rate-limiting)
16. [Advanced Topics](#advanced-topics)
17. [Testing](#testing)
18. [Best Practices](#best-practices)
19. [Common Patterns](#common-patterns)

## Introduction

Apion is a lightweight, type-safe HTTP server framework for Scala.js designed to provide an Express-like API with the benefits of Scala's type system. It offers a familiar programming model for JavaScript/Node.js developers while leveraging Scala's immutability, pattern matching, and type safety.

### Features

- Express-like chainable API
- Pure functions with immutable types
- Type-safe request/response handling
- Linear request processing
- Unified handler/middleware system
- Comprehensive middleware ecosystem
- JWT-based authentication
- Error handling system

### Simple Example

```scala
import io.github.edadma.apion._
import zio.json._

case class User(name: String, email: String) derives JsonEncoder, JsonDecoder

@main
def run(): Unit =
  Server()
    .use(LoggingMiddleware())
    .get("/hello", _ => "Hello World!".asText)
    .post(
      "/users",
      request => request.json[User].flatMap {
        case Some(user) => user.asJson(201)
        case None => "Invalid user data".asText(400)
      }
    )
    .listen(3000) { println("Server running at http://localhost:3000") }
```

## Core Concepts

### Handler

The central building block in Apion is the `Handler` type:

```scala
type Handler = Request => Future[Result]
```

A handler is a function that takes a `Request` and returns a `Future[Result]`.

### Result Types

```scala
sealed trait Result
case class Continue(request: Request) extends Result   // Continue to next handler with modified request
case class Complete(response: Response) extends Result // End chain with response
case class Fail(error: ServerError) extends Result     // Propagate error
case object Skip extends Result                       // Skip to next handler
```

### Request Flow

1. Router receives a request
2. Middleware processes request sequentially
3. Handler generates a response
4. Response flows back through finalizers
5. Response sent to client

## Server Setup

### Creating a Server

```scala
val server = Server()
```

### Adding Routes

```scala
server
  .get("/users", listUsers)
  .post("/users", createUser)
  .put("/users/:id", updateUser)
  .delete("/users/:id", deleteUser)
```

### Using Middleware

```scala
server
  .use(LoggingMiddleware())
  .use(SecurityMiddleware())
  .use(CorsMiddleware())
```

### Starting the Server

```scala
server.listen(3000) {
  println("Server running at http://localhost:3000")
}
```

### Closing the Server

```scala
server.close(() => println("Server closed"))
```

## Request Handling

### Request Properties

```scala
case class Request(
    method: String,                     // HTTP method
    url: String,                        // Full URL
    path: String,                       // URL path
    headers: Map[String, String],       // Request headers (case-insensitive)
    params: Map[String, String],        // Path parameters
    query: Map[String, String],         // Query parameters
    context: Map[String, Any],          // Request context
    rawRequest: ServerRequest,          // Node.js request object
    basePath: String = "",              // Accumulated base path
    finalizers: List[Finalizer] = Nil,  // Response transformers
    cookies: Map[String, String] = Map() // Request cookies
)
```

### Accessing Request Data

```scala
// Path parameters
val userId = request.params("id")

// Query parameters
val page = request.query.getOrElse("page", "1").toInt

// Headers (case-insensitive)
val token = request.header("authorization")
  .filter(_.startsWith("Bearer "))
  .map(_.substring(7))

// IP address and connection info
val ip = request.ip
val isSecure = request.secure

// Raw request access
val nodeRequest = request.rawRequest
```

### Request Body Processing

```scala
// Raw body as Buffer
request.body.flatMap { buffer =>
  // Process binary data
  processBuffer(buffer)
}

// Text body
request.text.flatMap { text =>
  // Process text
  processText(text)
}

// JSON parsing with type safety
case class User(name: String, email: String) derives JsonDecoder

request.json[User].flatMap {
  case Some(user) => 
    // Successfully parsed User object
    processUser(user)
  case None => 
    // Invalid JSON or wrong format
    "Invalid user data".asText(400)
}

// Form data
request.form.flatMap { formData =>
  val username = formData.getOrElse("username", "")
  val password = formData.getOrElse("password", "")
  // Process form data
  processForm(username, password)
}
```

### Context Management

The request context is a map that middleware can use to store data:

```scala
// Middleware adds data to context
val withUser: Handler = request => {
  val userId = request.params("id")
  getUserById(userId).map { user =>
    Continue(request.copy(
      context = request.context + ("user" -> user)
    ))
  }
}

// Handler accesses context data
val requireUser: Handler = request => {
  request.context.get("user") match {
    case Some(user: User) =>
      // User found in context
      Future.successful(Continue(request))
    case _ =>
      // No user in context
      "Unauthorized".asText(401)
  }
}
```

## Response Creation

### Response Properties

```scala
case class Response(
    status: Int = 200,                           // HTTP status code
    headers: ResponseHeaders = ResponseHeaders.empty,  // Response headers
    body: ResponseBody = EmptyBody               // Response body
)
```

### Body Types

```scala
sealed trait ResponseBody
case class StringBody(content: String, buffer: Buffer) extends ResponseBody
case class BufferBody(content: Buffer) extends ResponseBody
case class ReadableStreamBody(stream: ReadableStream) extends ResponseBody
case object EmptyBody extends ResponseBody
```

### Creating Responses

The `Response` object provides factory methods:

```scala
// Text response
Response.text("Hello World")                    // 200 OK
Response.text("Created", status = 201)          // 201 Created

// JSON response
Response.json(data)                             // 200 OK with JSON
Response.json(errorData, status = 400)          // 400 Bad Request

// No content
Response.noContent()                            // 204 No Content

// Binary response
Response.binary(buffer)                         // Binary data

// Streaming response
Response.stream(fileStream)                     // Streaming response
```

### Response DSL

Easier response creation with extension methods:

```scala
// Text responses
"Hello World".asText                       // Future[Result] with 200 OK
"Created".asText(201)                      // Future[Result] with 201 Created

// JSON responses with automatic encoding
case class User(name: String) derives JsonEncoder
User("Alice").asJson                       // Future[Result] with JSON
User("Alice").asJson(201)                  // Future[Result] with 201

// Common responses
notFound                                   // 404 Not Found
badRequest                                 // 400 Bad Request
serverError                                // 500 Internal Server Error

// Created with location
created(data, Some("/resources/123"))      // 201 with Location header
```

### Headers

The `ResponseHeaders` class handles case-insensitive headers:

```scala
// Adding headers
response.copy(headers = response.headers.add("Content-Type", "text/plain"))

// Adding multiple headers
response.copy(headers = response.headers.addAll(Seq(
  "Cache-Control" -> "no-cache",
  "X-Custom-Header" -> "value"
)))

// Checking headers
response.headers.get("content-type")  // Case-insensitive lookup
response.headers.contains("cache-control")
```

### Cookies

```scala
// Set a cookie
response.withCookie("session", "abc123")

// Cookie with attributes
response.withCookie(
  name = "session",
  value = "abc123",
  maxAge = Some(3600),
  path = Some("/"),
  secure = true,
  httpOnly = true
)

// Clear a cookie
response.clearCookie("session")
```

## Routing

### Basic Routes

```scala
server
  .get("/users", listUsers)
  .post("/users", createUser)
  .put("/users/:id", updateUser)
  .delete("/users/:id", deleteUser)
  .patch("/users/:id", patchUser)
```

### Path Parameters

```scala
// Route with path parameters
server.get("/users/:id/posts/:postId", request => {
  val userId = request.params("id")
  val postId = request.params("postId")
  // Use parameters
  getUserPost(userId, postId).asJson
})
```

### Nested Routes

```scala
// Create subrouter
val usersRouter = Router()
  .get("/", listUsers)
  .post("/", createUser)
  .get("/:id", getUser)
  .put("/:id", updateUser)
  .delete("/:id", deleteUser)

// Mount subrouter
server.use("/api/users", usersRouter)
```

### Route with Multiple Handlers

```scala
// Chain handlers for a route
server.get("/protected",
  auth,          // Authentication middleware
  requireAdmin,  // Authorization middleware
  adminHandler   // Actual handler
)
```

## Middleware

### Creating Middleware

```scala
// Simple logging middleware
val logger: Handler = request => {
  println(s"${request.method} ${request.path}")
  Future.successful(Continue(request)) // Continue to next handler
}

// Middleware that adds a header to response
val addHeader: Handler = request => {
  val headerFinalizer: Finalizer = (req, res) =>
    Future.successful(res.copy(
      headers = res.headers.add("X-Custom", "value")
    ))
  
  Future.successful(Continue(
    request.addFinalizer(headerFinalizer)
  ))
}
```

### Middleware Applications

```scala
// Global middleware
server.use(logger)

// Path-specific middleware
server.use("/api", auth)

// Subrouter middleware
val apiRouter = Router()
  .use(auth)
  .get("/users", listUsers)

server.use("/api", apiRouter)
```

### Built-in Middleware

```scala
// Logging
server.use(LoggingMiddleware())

// Security headers
server.use(SecurityMiddleware())

// CORS
server.use(CorsMiddleware())

// Authentication
server.use(AuthMiddleware(config))

// Static files
server.use(StaticMiddleware("public"))

// Compression
server.use(CompressionMiddleware())

// Cookies
server.use(CookieMiddleware())

// Rate limiting
server.use(RateLimiterMiddleware())
```

## Error Handling

### Error Types

```scala
trait ServerError extends Throwable {
  def message: String
  def toResponse: Response
  def logLevel: LogLevel = LogLevel.ERROR
}

// Built-in error types
case class ValidationError(message: String) extends ServerError {
  def toResponse: Response = Response.json(
    Map("error" -> "validation_error", "message" -> message),
    400
  )
  override def logLevel: LogLevel = LogLevel.WARN
}

case class AuthError(message: String) extends ServerError {
  def toResponse: Response = Response.json(
    Map("error" -> "auth_error", "message" -> message),
    401
  )
}

case class NotFoundError(message: String) extends ServerError {
  def toResponse: Response = Response.json(
    Map("error" -> "not_found", "message" -> message),
    404
  )
  override def logLevel: LogLevel = LogLevel.INFO
}
```

### Creating Errors

```scala
// Using DSL
request.failValidation("Invalid input data")
request.failAuth("Missing or invalid token")
request.failNotFound("User not found")

// Direct creation
Future.successful(Fail(ValidationError("Invalid input")))
Future.successful(Fail(AuthError("Unauthorized")))
Future.successful(Fail(NotFoundError("Resource not found")))
```

### Custom Error Types

```scala
case class CustomError(message: String, code: String) extends ServerError {
  def toResponse: Response = Response.json(
    Map(
      "error" -> code,
      "message" -> message,
      "timestamp" -> System.currentTimeMillis()
    ),
    422
  )
  
  override def logLevel: LogLevel = LogLevel.WARN
}

// Using custom errors
request.fail(CustomError("Invalid state", "STATE_ERROR"))
```

### Error Handlers

```scala
// Global error handler
server.use { (error: ServerError, request: Request) =>
  error match {
    case ValidationError(msg) =>
      Map("error" -> msg, "type" -> "validation").asJson(400)
    case AuthError(msg) =>
      Map("error" -> msg, "type" -> "auth").asJson(401)
    case _ =>
      Map("error" -> "Internal server error").asJson(500)
  }
}

// Specialized error handler
val validationHandler = { (error: ServerError, request: Request) =>
  error match {
    case e: ValidationError =>
      Map(
        "status" -> "error",
        "code" -> "VALIDATION_FAILED",
        "message" -> e.message,
        "path" -> request.path
      ).asJson(400)
    case _ => Skip
  }
}

server.use(validationHandler)
```

### Error Handler Chaining

```scala
server
  // Handle validation errors
  .use { (error: ServerError, request: Request) =>
    error match {
      case e: ValidationError => 
        Map("validation_error" -> e.message).asJson(400)
      case _ => Skip
    }
  }
  // Handle auth errors
  .use { (error: ServerError, request: Request) =>
    error match {
      case e: AuthError =>
        Map("auth_error" -> e.message).asJson(401)
      case _ => Skip
    }
  }
  // Catch-all handler
  .use { (error: ServerError, request: Request) =>
    Map("error" -> error.message).asJson(500)
  }
```

### Error Transformation

```scala
// Transform validation errors into custom errors
server.use { (error: ServerError, request: Request) =>
  error match {
    case ValidationError(msg) =>
      Fail(CustomError(s"Validation failed: $msg", "VALIDATION"))
    case _ => Skip
  }
}
```

## Authentication

### JWT Configuration

```scala
import io.github.edadma.apion._
import AuthMiddleware._

// Configure authentication
val config = Config(
  secretKey = "your-secret-key",  // Required for JWT signing
  requireAuth = true,             // Require auth for all routes
  excludePaths = Set("/public", "/auth"),  // Paths that skip auth
  tokenRefreshThreshold = 300,    // 5 minutes before expiration
  maxTokenLifetime = 86400,       // 24 hours max token lifetime
  issuer = "your-service",        // Token issuer claim
  audience = Some("your-app")     // Optional audience claim
)

// Create and use middleware
val auth = AuthMiddleware(config)
server.use(auth)
```

### Token Management

```scala
// Create a token store (use your own implementation in production)
val tokenStore = new InMemoryTokenStore()

// Create an access token
val token = AuthMiddleware.createAccessToken(
  subject = "user123",            // User identifier
  roles = Set("admin", "user"),   // User roles for authorization
  config = config                 // Auth configuration
)

// Create a refresh token
val refreshToken = AuthMiddleware.createRefreshToken(
  subject = "user123",
  config = config,
  validityPeriod = 30 * 24 * 3600 // 30 days validity
)
```

### Authentication Flow

```scala
// Login endpoint
server.post("/auth/login", request => {
  request.json[LoginRequest].flatMap {
    case Some(LoginRequest(username, password)) =>
      // Validate credentials (implement your own validation)
      if (validateCredentials(username, password)) {
        // Create tokens
        val accessToken = createAccessToken(
          username,
          getUserRoles(username),
          config
        )
        val refreshToken = createRefreshToken(username, config)
        
        // Return tokens
        TokenResponse(accessToken, refreshToken).asJson(200)
      } else {
        "Invalid credentials".asText(401)
      }
    case None =>
      "Invalid request body".asText(400)
  }
})

// Token refresh endpoint
server.post("/auth/refresh", request => {
  request.header("authorization") match {
    case Some(header) if header.toLowerCase.startsWith("bearer ") =>
      val token = header.substring(7)
      AuthMiddleware.refreshToken(token, config, tokenStore)
    case _ =>
      "Invalid Authorization header".asText(400)
  }
})

// Logout endpoint
server.post("/auth/logout", request => {
  request.header("authorization") match {
    case Some(header) if header.toLowerCase.startsWith("bearer ") =>
      val token = header.substring(7)
      AuthMiddleware.logout(token, config.secretKey, tokenStore)
    case _ =>
      "Invalid Authorization header".asText(400)
  }
})
```

### Role-Based Access Control

```scala
// Accessing auth in handlers
server.get("/profile", request => {
  request.context.get("auth") match {
    case Some(auth: Auth) =>
      // User is authenticated
      getUserProfile(auth.user).asJson
    case _ =>
      // Should not happen if using auth middleware
      "Unauthorized".asText(401)
  }
})

// Checking roles
server.get("/admin", request => {
  request.context.get("auth") match {
    case Some(auth: Auth) if auth.hasRequiredRoles(Set("admin")) =>
      // User has admin role
      getAdminData().asJson
    case Some(auth: Auth) =>
      // User is authenticated but lacks admin role
      "Insufficient permissions".asText(403)
    case _ =>
      "Unauthorized".asText(401)
  }
})
```

## Static File Serving

### Basic Static Files

```scala
// Serve files from the "public" directory
server.use(StaticMiddleware("public"))

// With custom options
server.use(StaticMiddleware(
  "public",
  StaticMiddleware.Options(
    index = true,          // Serve index.html for directories
    dotfiles = "ignore",   // How to handle dotfiles (ignore|allow|deny)
    etag = true,           // Enable ETag generation
    maxAge = 3600,         // Cache max-age in seconds
    redirect = true,       // Redirect directories to trailing slash
    fallthrough = true,    // Continue to next handler if file not found
    acceptRanges = true    // Support byte range requests
  )
))
```

### Path-Specific Static Files

```scala
// Mount static files on a specific path
server.use("/assets", StaticMiddleware("public/assets"))

// Serve different directories for different paths
server.use("/images", StaticMiddleware("uploads/images"))
server.use("/css", StaticMiddleware("public/styles"))
server.use("/js", StaticMiddleware("public/scripts"))
```

### Advanced Static File Configuration

```scala
// Serve static files with more restrictive options
server.use(StaticMiddleware(
  "restricted",
  StaticMiddleware.Options(
    index = false,         // Don't serve index.html
    dotfiles = "deny",     // Return 403 for dotfiles
    etag = true,           // Enable ETags
    maxAge = 86400,        // 1 day cache
    redirect = false,      // Don't redirect directories
    fallthrough = false    // Return 404 instead of continuing
  )
))
```

## Cookies

### Cookie Middleware

```scala
// Basic cookie middleware
server.use(CookieMiddleware())

// With options
server.use(CookieMiddleware(CookieMiddleware.Options(
  secret = Some("cookie-secret"),  // For signed cookies
  parseJSON = true                 // Parse JSON cookies
)))

// Presets
server.use(CookieMiddleware.Presets.signed("cookie-secret"))
server.use(CookieMiddleware.Presets.json)
```

### Reading Cookies

```scala
// Basic cookie access
val sessionId = request.cookie("session")

// With CookieMiddleware
request.getSignedCookie("auth")        // Verified signed cookie
request.getJsonCookie[User]("user")    // Parse JSON cookie
```

### Setting Cookies

```scala
// Set a cookie
Response.text("Hello").withCookie("session", "abc123")

// Cookie with attributes
Response.text("Hello").withCookie(
  name = "session",
  value = "abc123",
  maxAge = Some(3600),
  path = Some("/"),
  secure = true,
  httpOnly = true,
  sameSite = Some("strict")
)

// Signed cookie
request.signCookie("auth", userData) match {
  case Some(cookie) =>
    Response.text("Signed").withCookie(cookie)
  case None =>
    "Signing failed".asText(500)
}

// Clear a cookie
Response.text("Logged out").clearCookie("session")
```

## CORS

### CORS Configuration

```scala
import io.github.edadma.apion._
import CorsMiddleware._

// Default CORS settings
server.use(CorsMiddleware())

// Custom CORS settings
server.use(CorsMiddleware(Options(
  origin = Origin.Multiple(Set(
    "https://app.example.com",
    "https://admin.example.com"
  )),
  methods = Set("GET", "POST", "PUT", "DELETE"),
  allowedHeaders = Set("Content-Type", "Authorization"),
  exposedHeaders = Set("X-Total-Count"),
  credentials = true,
  maxAge = Some(3600) // 1 hour
)))
```

### Origin Configuration

```scala
// Allow any origin
Origin.Any

// Single origin
Origin("https://example.com")

// Multiple origins
Origin(Set(
  "https://app.example.com",
  "https://admin.example.com"
))

// Pattern matching
Origin.pattern("""https:\/\/.*\.example\.com""")

// Custom validation
Origin.validate(origin =>
  origin.endsWith(".example.com") && 
  origin.startsWith("https://")
)
```

### Presets

```scala
// Development preset (permissive)
server.use(CorsMiddleware.development())

// Production preset (strict)
server.use(CorsMiddleware.production(Set(
  "https://app.example.com",
  "https://admin.example.com"
)))
```

## Compression

### Basic Compression

```scala
// Default compression
server.use(CompressionMiddleware())

// Custom compression options
server.use(CompressionMiddleware(CompressionMiddleware.Options(
  level = 6,                // Compression level (0-9)
  threshold = 1024,         // Min size to compress (bytes)
  memLevel = 8,             // Memory usage (1-9)
  windowBits = 15,          // Window size (9-15)
  
  // Brotli-specific options
  brotliQuality = 11,       // Quality (0-11)
  brotliBlockSize = 4096,   // Block size
  
  // Filter what to compress
  filter = req => true,     // Function to determine what to compress
  
  // Supported encodings (in order of preference)
  encodings = List("br", "gzip", "deflate")
)))
```

## Security Headers

### Basic Security

```scala
// Default security headers
server.use(SecurityMiddleware())

// Custom security options
server.use(SecurityMiddleware(SecurityMiddleware.Options(
  // Content Security Policy
  contentSecurityPolicy = true,
  cspDirectives = Map(
    "default-src" -> "'self'",
    "script-src" -> "'self'",
    "img-src" -> "'self' data:",
    "style-src" -> "'self' 'unsafe-inline'"
  ),
  
  // Frame protection
  frameguard = true,
  frameguardAction = "DENY",  // or "SAMEORIGIN"
  
  // HSTS
  hsts = true,
  hstsMaxAge = 15552000,      // 180 days
  hstsIncludeSubDomains = true,
  
  // XSS protection
  xssFilter = true,
  
  // Sniffing protection
  noSniff = true,
  
  // Referrer policy
  referrerPolicy = true,
  referrerPolicyDirective = "no-referrer"
)))
```

### Presets

```scala
// Essential security headers
server.use(SecurityMiddleware.essential())

// API-specific security headers
server.use(SecurityMiddleware.api())
```

## Rate Limiting

### Basic Rate Limiting

```scala
// Default rate limiting (60 requests per minute)
server.use(RateLimiterMiddleware())

// Custom rate limiting
server.use(RateLimiterMiddleware(Options(
  limit = RateLimit(
    maxRequests = 100,      // 100 requests
    window = 1.minute,      // per minute
    burst = 20,             // allow 20 extra requests
    skipFailedRequests = true,  // don't count failures
    statusCode = 429,           // 429 Too Many Requests
    errorMessage = "Rate limit exceeded",
    headers = true              // send rate limit headers
  ),
  // Configure key generation (default is by IP)
  keyGenerator = request => Future.successful(request.ip),
  // Skip specific requests
  skip = request => request.path.startsWith("/health")
)))
```

### Presets

```scala
import scala.concurrent.duration._

// Moderate rate limiting
server.use(RateLimiterMiddleware.moderate(100, 1.minute))

// Strict rate limiting (no burst)
server.use(RateLimiterMiddleware.strict(50, 1.minute))

// Flexible rate limiting (with burst)
server.use(RateLimiterMiddleware.flexible(200, 1.minute))
```

## Advanced Topics

### Response Finalizers

Finalizers transform responses after handlers complete:

```scala
// Add a finalizer
val addHeader: Finalizer = (req, res) =>
  Future.successful(res.copy(
    headers = res.headers.add("X-Custom", "value")
  ))

// Add finalizer to request
val reqWithFinalizer = request.addFinalizer(addHeader)

// Middleware that adds a finalizer
val headerMiddleware: Handler = request =>
  Future.successful(Continue(request.addFinalizer(addHeader)))
```

### Custom JWT Implementation

For custom JWT handling:

```scala
import io.github.edadma.apion.JWT

// Define your payload
case class TokenPayload(
    sub: String,         // Subject (user ID)
    roles: Set[String],  // User roles
    exp: Long            // Expiration timestamp
) derives JsonEncoder, JsonDecoder

// Create a token
val payload = TokenPayload(
  sub = "user123",
  roles = Set("admin"),
  exp = System.currentTimeMillis() / 1000 + 3600  // 1 hour
)

val token = JWT.sign(payload, "secret-key")

// Verify a token
JWT.verify[TokenPayload](token, "secret-key") match {
  case Right(payload) =>
    // Token is valid
    processPayload(payload)
  case Left(error) =>
    // Token is invalid
    handleError(error)
}
```

### Custom Middleware

Create custom middleware for specific needs:

```scala
// Timing middleware
def timingMiddleware: Handler = request => {
  val start = System.currentTimeMillis()
  
  val timingFinalizer: Finalizer = (req, res) => {
    val duration = System.currentTimeMillis() - start
    Future.successful(res.copy(
      headers = res.headers.add("X-Response-Time", s"${duration}ms")
    ))
  }
  
  Future.successful(Continue(request.addFinalizer(timingFinalizer)))
}

// User lookup middleware
def userLookup(userService: UserService): Handler = request => {
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
}
```

## Testing

### Unit Testing

Testing individual handlers:

```scala
// Test a handler
"MyHandler" - {
  "should handle successful request" in {
    // Create mock request
    val request = Request.fromServerRequest(mockServerRequest(
      method = "GET",
      url = "/users/123",
      headers = Map("Content-Type" -> "application/json")
    ))
    
    // Call handler
    myHandler(request).map {
      case Complete(response) =>
        response.status shouldBe 200
        response.bodyText should include("user")
      case _ =>
        fail("Expected Complete result")
    }
  }
}
```

### Integration Testing

Testing the full server:

```scala
class IntegrationTest extends AsyncBaseSpec with BeforeAndAfterAll {
  var server: Server = null
  var httpServer: NodeServer = null
  val port = 3001
  
  // Set up server before tests
  override def beforeAll(): Unit = {
    server = Server()
      .use(LoggingMiddleware())
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
          val jsonStr = js.JSON.stringify(json)
          jsonStr should include("123")
        })
    }
  }
}
```

### Testing Middleware

```scala
"AuthMiddleware" - {
  "should require authentication" in {
    fetch(s"http://localhost:$port/api/profile")
      .toFuture
      .map { response =>
        response.status shouldBe 401
      }
  }

  "should allow access with valid token" in {
    val token = createAccessToken("test", Set("user"), config)
    val options = FetchOptions(
      headers = js.Dictionary(
        "Authorization" -> s"Bearer $token"
      )
    )

    fetch(s"http://localhost:$port/api/profile", options)
      .toFuture
      .map { response =>
        response.status shouldBe 200
      }
  }
}
```

### Mock Request Helpers

Creating mock requests for testing:

```scala
// Helper to create mock requests
def mockServerRequest(
  method: String = "GET",
  url: String = "/",
  headers: Map[String, String] = Map()
): ServerRequest = {
  val req = js.Dynamic.literal(
    method = method,
    url = url,
    headers = js.Dictionary(headers.toSeq*),
    on = (_: String, _: js.Function1[js.Any, Unit]) => js.Dynamic.literal(),
    socket = js.Dynamic.literal(
      remoteAddress = "127.0.0.1",
      remotePort = 12345
    )
  )
  req.asInstanceOf[ServerRequest]
}

// Create test request
val request = Request.fromServerRequest(mockServerRequest(
  method = "POST",
  url = "/api/users?active=true",
  headers = Map(
    "Content-Type" -> "application/json",
    "Authorization" -> "Bearer token123"
  )
))
```

### Mock File System

For testing file operations:

```scala
// Create mock files for testing
val mockFiles = Map(
  "public/index.html" -> mockFile("<html><body>Hello</body></html>", false, "644"),
  "public/style.css" -> mockFile("body { color: black; }", false, "644"),
  "public/image.png" -> mockFile("binary-image-data", false, "644")
)

// Create mock FS
val mockFs = new MockFS(mockFiles)

// Test static middleware with mock FS
val staticMiddleware = StaticMiddleware(
  "public",
  StaticMiddleware.Options(index = true),
  mockFs
)
```

### Test Base Classes

Useful base classes for testing:

```scala
// Base class for async tests
class AsyncBaseSpec extends AsyncFreeSpec with BaseSpec with JSEventually {
  // Use MacroTaskExecutor for async execution
  implicit override def executionContext: ExecutionContext = MacrotaskExecutor

  // Default patience config
  implicit override val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = Span(200, Millis),
    interval = Span(50, Millis)
  )

  // Helper for debug logging
  def withDebugLogging[T](testName: String)(test: => Future[T]): Future[T] = {
    logger.setLogLevel(LogLevel.DEBUG)
    logger.debug(s"<<<< Starting Test: $testName >>>>", category = "Test")

    test transform { result =>
      logger.setLogLevel(LogLevel.OFF)
      result
    }
  }
}

// Base class for sync tests
class AnyBaseSpec extends AnyFreeSpec with BaseSpec:
  def withDebugLogging(testName: String)(test: => Unit): Unit = {
    logger.setLogLevel(LogLevel.DEBUG)
    logger.debug(s"<<<< Starting Test: $testName >>>>", category = "Test")

    try {
      test
    } finally {
      logger.setLogLevel(LogLevel.OFF)
    }
  }
```

## Best Practices

### Request Handling

1. **Use Type-Safe JSON**:
   ```scala
   // Define data types with JSON codecs
   case class User(name: String, email: String) derives JsonEncoder, JsonDecoder
   
   // Parse JSON requests
   request.json[User].flatMap {
     case Some(user) => // Handle valid user
     case None => request.failValidation("Invalid user data")
   }
   ```

2. **Validate Early**:
   ```scala
   def validateInput(request: Request): Future[Result] = {
     for {
       userData <- request.json[UserData]
       result <- userData match {
         case Some(data) if isValid(data) => 
           // Continue with valid data
           processData(data)
         case Some(_) => 
           // Return validation error
           request.failValidation("Invalid data format")
         case None => 
           // Return parsing error
           request.failValidation("Invalid JSON")
       }
     } yield result
   }
   ```

3. **Separate Concerns**:
   ```scala
   // Authentication middleware
   val auth: Handler = ...
   
   // Authorization middleware
   val requireAdmin: Handler = ...
   
   // Business logic handler
   val createUser: Handler = ...
   
   // Chain handlers
   server.post("/admin/users", auth, requireAdmin, createUser)
   ```

### Error Handling

1. **Custom Error Types**:
   ```scala
   // Domain-specific errors
   sealed trait DomainError extends ServerError
   case class UserError(message: String) extends DomainError {
     def toResponse: Response = Response.json(
       Map("error" -> "user_error", "message" -> message),
       400
     )
   }
   ```

2. **Consistent Error Responses**:
   ```scala
   // Reusable error response format
   object ErrorFormatter {
     def format(code: String, message: String, details: Option[Map[String, Any]] = None): Response =
       Response.json(
         Map(
           "error" -> code,
           "message" -> message,
           "timestamp" -> System.currentTimeMillis()
         ) ++ details.map("details" -> _),
         getStatusForCode(code)
       )
   }
   ```

3. **Error Transformation**:
   ```scala
   // Transform low-level errors into domain errors
   server.use { (error: ServerError, request: Request) =>
     error match {
       case e: IOException => 
         Fail(StorageError(s"Storage error: ${e.getMessage}"))
       case _ => Skip
     }
   }
   ```

### Authentication

1. **Short-Lived Tokens**:
   ```scala
   // Create access token with short lifetime
   val accessToken = AuthMiddleware.createAccessToken(
     subject = userId,
     roles = userRoles,
     config = config.copy(maxTokenLifetime = 900) // 15 minutes
   )
   
   // Create long-lived refresh token
   val refreshToken = AuthMiddleware.createRefreshToken(
     subject = userId,
     config = config,
     validityPeriod = 30 * 24 * 3600 // 30 days
   )
   ```

2. **Token Storage**:
   ```scala
   // Implement secure token storage (e.g., Redis)
   class RedisTokenStore extends TokenStore {
     override def isTokenRevoked(jti: String): Future[Boolean] =
       redisClient.exists(s"revoked:$jti")
       
     override def revokeToken(jti: String): Future[Unit] =
       redisClient.set(s"revoked:$jti", "1", expiry = 30 * 24 * 3600)
   }
   ```

3. **Role-Based Access Control**:
   ```scala
   // Define role requirements
   def requireRoles(roles: Set[String]): Handler = request => {
     request.context.get("auth") match {
       case Some(auth: Auth) if auth.hasRequiredRoles(roles) =>
         Future.successful(Continue(request))
       case Some(_) =>
         "Insufficient permissions".asText(403)
       case None =>
         "Unauthorized".asText(401)
     }
   }
   
   // Use role middleware
   server.get("/admin", auth, requireRoles(Set("admin")), adminHandler)
   ```

### Performance

1. **Streaming Responses**:
   ```scala
   // Stream large files
   server.get("/download", request => {
     val fileStream = fs.createReadStream("large-file.mp4")
     Future.successful(Complete(Response.stream(
       stream = fileStream,
       status = 200,
       additionalHeaders = Seq(
         "Content-Type" -> "video/mp4",
         "Content-Disposition" -> "attachment; filename=\"video.mp4\""
       )
     )))
   })
   ```

2. **Compression**:
   ```scala
   // Only compress text-based content types
   server.use(CompressionMiddleware(CompressionMiddleware.Options(
     threshold = 1024,  // Only compress responses larger than 1KB
     filter = request => {
       val contentType = request.header("accept").getOrElse("")
       contentType.contains("text/") || 
       contentType.contains("application/json") ||
       contentType.contains("application/javascript")
     }
   )))
   ```

3. **Efficient Context Usage**:
   ```scala
   // Store only necessary data in context
   val withUser: Handler = request => {
     getUserById(request.params("id")).map { user =>
       // Store only essential user data, not the entire user object
       Continue(request.copy(
         context = request.context + 
           ("userId" -> user.id) + 
           ("userRoles" -> user.roles)
       ))
     }
   }
   ```

## Common Patterns

### API Server

Full API server example:

```scala
import io.github.edadma.apion._
import zio.json._

// Data models
case class User(id: String, name: String, email: String) derives JsonEncoder, JsonDecoder
case class CreateUserRequest(name: String, email: String) derives JsonDecoder
case class UpdateUserRequest(name: Option[String], email: Option[String]) derives JsonDecoder
case class LoginRequest(email: String, password: String) derives JsonDecoder
case class TokenResponse(accessToken: String, refreshToken: String) derives JsonEncoder

// Mock user service
class UserService {
  private var users = Map[String, User]()
  
  def getUsers(): Future[List[User]] = Future.successful(users.values.toList)
  def getUser(id: String): Future[Option[User]] = Future.successful(users.get(id))
  def createUser(req: CreateUserRequest): Future[User] = {
    val id = java.util.UUID.randomUUID().toString
    val user = User(id, req.name, req.email)
    users += (id -> user)
    Future.successful(user)
  }
  def updateUser(id: String, req: UpdateUserRequest): Future[Option[User]] = {
    users.get(id).map { user =>
      val updated = user.copy(
        name = req.name.getOrElse(user.name),
        email = req.email.getOrElse(user.email)
      )
      users += (id -> updated)
      updated
    } match {
      case Some(u) => Future.successful(Some(u))
      case None => Future.successful(None)
    }
  }
  def deleteUser(id: String): Future[Boolean] = {
    val exists = users.contains(id)
    if (exists) users -= id
    Future.successful(exists)
  }
}

object ApiServer {
  def main(args: Array[String]): Unit = {
    val userService = new UserService()
    
    // Auth configuration
    val authConfig = AuthMiddleware.Config(
      secretKey = "your-secret-key",
      requireAuth = true,
      excludePaths = Set("/auth/login", "/auth/refresh", "/health"),
      maxTokenLifetime = 3600,
      tokenRefreshThreshold = 300
    )
    
    // Token store
    val tokenStore = new AuthMiddleware.InMemoryTokenStore()
    
    // Auth middleware
    val auth = AuthMiddleware(authConfig, tokenStore)
    
    // Create authentication router
    val authRouter = Router()
      .post("/login", request => {
        request.json[LoginRequest].flatMap {
          case Some(LoginRequest(email, password)) =>
            // In a real app, validate credentials against a database
            if (password == "password") {
              // User ID would come from authentication
              val userId = "user-123"
              val roles = Set("user")
              
              // Create tokens
              val accessToken = AuthMiddleware.createAccessToken(
                userId, roles, authConfig
              )
              val refreshToken = AuthMiddleware.createRefreshToken(
                userId, authConfig
              )
              
              TokenResponse(accessToken, refreshToken).asJson
            } else {
              "Invalid credentials".asText(401)
            }
          case None =>
            "Invalid request format".asText(400)
        }
      })
      .post("/refresh", request => {
        request.header("authorization") match {
          case Some(header) if header.startsWith("Bearer ") =>
            val token = header.substring(7)
            AuthMiddleware.refreshToken(token, authConfig, tokenStore)
          case _ =>
            "Invalid token".asText(400)
        }
      })
      .post("/logout", request => {
        request.header("authorization") match {
          case Some(header) if header.startsWith("Bearer ") =>
            val token = header.substring(7)
            AuthMiddleware.logout(token, authConfig.secretKey, tokenStore)
          case _ =>
            "Invalid token".asText(400)
        }
      })
    
    // Create users router
    val usersRouter = Router()
      .get("/", _ => 
        userService.getUsers().flatMap(users => users.asJson)
      )
      .post("/", request => 
        request.json[CreateUserRequest].flatMap {
          case Some(createReq) =>
            userService.createUser(createReq).flatMap(user => user.asJson(201))
          case None =>
            "Invalid request format".asText(400)
        }
      )
      .get("/:id", request => {
        val id = request.params("id")
        userService.getUser(id).flatMap {
          case Some(user) => user.asJson
          case None => notFound
        }
      })
      .put("/:id", request => {
        val id = request.params("id")
        request.json[UpdateUserRequest].flatMap {
          case Some(updateReq) =>
            userService.updateUser(id, updateReq).flatMap {
              case Some(user) => user.asJson
              case None => notFound
            }
          case None =>
            "Invalid request format".asText(400)
        }
      })
      .delete("/:id", request => {
        val id = request.params("id")
        userService.deleteUser(id).flatMap {
          case true => noContent
          case false => notFound
        }
      })
    
    // Create and configure server
    val server = Server()
      .use(LoggingMiddleware(LoggingMiddleware.Options(
        format = LoggingMiddleware.Format.Dev
      )))
      .use(CompressionMiddleware())
      .use(CorsMiddleware(CorsMiddleware.Options(
        origin = CorsMiddleware.Origin.Any,
        credentials = true
      )))
      .use(SecurityMiddleware.api())
      
      // Health endpoint (no auth required)
      .get("/health", _ => 
        Map("status" -> "ok", "version" -> "1.0.0").asJson
      )
      
      // Mount auth router
      .use("/auth", authRouter)
      
      // Protected API routes with auth
      .use("/api", Router()
        .use(auth)
        .use("/users", usersRouter)
      )
      
      // Error handling
      .use { (error: ServerError, request: Request) =>
        error match {
          case ValidationError(msg) =>
            Map("error" -> "validation_error", "message" -> msg).asJson(400)
          case AuthError(msg) =>
            Map("error" -> "auth_error", "message" -> msg).asJson(401)
          case NotFoundError(msg) =>
            Map("error" -> "not_found", "message" -> msg).asJson(404)
          case _ =>
            logger.error(s"Unhandled error: $error")
            Map("error" -> "internal_error", "message" -> "Internal server error").asJson(500)
        }
      }
    
    // Start server
    server.listen(3000) {
      println("API server running at http://localhost:3000")
    }
  }
}
```

### File Upload Server

Example server handling file uploads:

```scala
import io.github.edadma.apion._
import io.github.edadma.nodejs._
import zio.json._

case class FileInfo(
  name: String,
  size: Long,
  mimeType: String,
  path: String
) derives JsonEncoder

object FileUploadServer {
  def main(args: Array[String]): Unit = {
    // Create a unique temp file name
    def createTempFile(originalName: String): String = {
      val filename = java.util.UUID.randomUUID().toString + 
        "-" + originalName.replaceAll("[^a-zA-Z0-9.-]", "_")
      s"uploads/$filename"
    }
    
    // Handle file upload
    def uploadHandler(request: Request): Future[Result] = {
      // Get content type
      val contentType = request.header("content-type").getOrElse("")
      
      if (!contentType.startsWith("multipart/form-data")) {
        return "Expected multipart/form-data".asText(400)
      }
      
      // In a real implementation, you would parse the multipart data here
      // This is a simplified example
      request.body.flatMap { buffer =>
        // Get original filename from headers
        val filename = "example.jpg" // In a real app, extracted from multipart headers
        val tempPath = createTempFile(filename)
        
        // Write the file
        val writeStream = fs.createWriteStream(tempPath)
        
        // Create a promise that resolves when writing is complete
        val writePromise = Promise[Unit]()
        
        writeStream.on("finish", () => {
          writePromise.success(())
        })
        
        writeStream.on("error", (err: js.Error) => {
          writePromise.failure(new Exception(s"Write error: ${err.message}"))
        })
        
        // Write the buffer to the file
        writeStream.write(buffer)
        writeStream.end()
        
        // Wait for write to complete
        writePromise.future.map { _ =>
          // Get file stats
          fs.promises.stat(tempPath).toFuture.map { stats =>
            val fileInfo = FileInfo(
              name = filename,
              size = stats.size.toLong,
              mimeType = "image/jpeg", // In a real app, determined from file
              path = tempPath
            )
            
            Complete(Response.json(
              Map("file" -> fileInfo),
              201
            ))
          }
        }.flatten
      }
    }
    
    // Create upload directory if it doesn't exist
    fs.mkdirSync("uploads", js.Dynamic.literal(recursive = true))
    
    // Create server
    val server = Server()
      .use(LoggingMiddleware())
      .use(SecurityMiddleware())
      
      // Upload endpoint
      .post("/upload", uploadHandler)
      
      // Serve uploaded files
      .use("/files", StaticMiddleware("uploads"))
      
      // List uploaded files
      .get("/files", request => {
        fs.promises.readdir("uploads").toFuture.map { files =>
          Complete(Response.json(
            Map("files" -> files.toList),
            200
          ))
        }
      })
    
    // Start server
    server.listen(3000) {
      println("File upload server running at http://localhost:3000")
      println("Upload files to: http://localhost:3000/upload")
      println("View files at: http://localhost:3000/files")
    }
  }
}
```

### WebSocket Echo Server

Example WebSocket server:

```scala
import io.github.edadma.apion._
import io.github.edadma.nodejs._

object WebSocketEchoServer {
  def main(args: Array[String]): Unit = {
    // Create HTTP server
    val server = Server()
      .use(LoggingMiddleware())
      .get("/", _ => 
        // Serve HTML page with WebSocket client
        Response.text(
          """
          |<!DOCTYPE html>
          |<html>
          |<head>
          |  <title>WebSocket Echo Test</title>
          |</head>
          |<body>
          |  <h1>WebSocket Echo Test</h1>
          |  <input id="message" type="text" placeholder="Enter message">
          |  <button id="send">Send</button>
          |  <div id="output"></div>
          |  
          |  <script>
          |    const ws = new WebSocket('ws://' + window.location.host + '/ws');
          |    const output = document.getElementById('output');
          |    const input = document.getElementById('message');
          |    const sendBtn = document.getElementById('send');
          |    
          |    ws.onopen = () => {
          |      output.innerHTML += '<p>Connected</p>';
          |    };
          |    
          |    ws.onmessage = (event) => {
          |      output.innerHTML += `<p>Server: ${event.data}</p>`;
          |    };
          |    
          |    ws.onclose = () => {
          |      output.innerHTML += '<p>Disconnected</p>';
          |    };
          |    
          |    sendBtn.onclick = () => {
          |      const message = input.value;
          |      ws.send(message);
          |      output.innerHTML += `<p>You: ${message}</p>`;
          |      input.value = '';
          |    };
          |  </script>
          |</body>
          |</html>
          """.stripMargin,
          200
        )
      )
    
    // Create HTTP server
    val httpServer = server.listen(3000) {
      println("WebSocket Echo Server running at http://localhost:3000")
    }
    
    // Create WebSocket server using 'ws' module
    // Note: In a real app, you would need to add the 'ws' module to your project
    val WebSocket = js.Dynamic.global.require("ws")
    val wss = js.Dynamic.newInstance(WebSocket)(js.Dynamic.literal(
      server = httpServer
    ))
    
    // Handle WebSocket connections
    wss.on("connection", (ws: js.Dynamic, req: js.Dynamic) => {
      println(s"Client connected from ${req.socket.remoteAddress}")
      
      // Echo messages back to client
      ws.on("message", (message: js.Dynamic) => {
        println(s"Received: $message")
        ws.send(message)
      })
      
      // Handle connection close
      ws.on("close", () => {
        println("Client disconnected")
      })
    })
  }
}
```

### Monitoring and Metrics

Example with basic monitoring:

```scala
import io.github.edadma.apion._
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration._

object MonitoringServer {
  def main(args: Array[String]): Unit = {
    // Simple metrics store
    object Metrics {
      val requestCounts = TrieMap[String, Int]()
      val requestTimes = TrieMap[String, List[Long]]()
      val errorCounts = TrieMap[String, Int]()
      
      def recordRequest(path: String): Unit =
        requestCounts.updateWith(path) {
          case Some(count) => Some(count + 1)
          case None => Some(1)
        }
        
      def recordRequestTime(path: String, timeMs: Long): Unit =
        requestTimes.updateWith(path) {
          case Some(times) => Some((timeMs :: times).take(100)) // Keep last 100
          case None => Some(List(timeMs))
        }
        
      def recordError(errorType: String): Unit =
        errorCounts.updateWith(errorType) {
          case Some(count) => Some(count + 1)
          case None => Some(1)
        }
        
      def getStats(): Map[String, Any] = {
        // Calculate average response times
        val avgTimes = requestTimes.map { case (path, times) =>
          path -> (if (times.isEmpty) 0 else times.sum / times.size)
        }.toMap
        
        Map(
          "requests" -> requestCounts.toMap,
          "avgResponseTime" -> avgTimes,
          "errors" -> errorCounts.toMap,
          "timestamp" -> System.currentTimeMillis()
        )
      }
    }
    
    // Monitoring middleware
    val monitoringMiddleware: Handler = request => {
      val path = request.path
      val startTime = System.currentTimeMillis()
      
      // Record request
      Metrics.recordRequest(path)
      
      // Add finalizer to record response time
      val timingFinalizer: Finalizer = (req, res) => {
        val duration = System.currentTimeMillis() - startTime
        Metrics.recordRequestTime(path, duration)
        
        // If error response, record error
        if (res.status >= 400) {
          val errorType = if (res.status >= 500) "server" else "client"
          Metrics.recordError(errorType)
        }
        
        Future.successful(res)
      }
      
      Future.successful(Continue(request.addFinalizer(timingFinalizer)))
    }
    
    // Error tracking middleware
    val errorMiddleware: (ServerError, Request) => Future[Result] = (error, request) => {
      // Record specific error type
      Metrics.recordError(error.getClass.getSimpleName)
      Skip
    }
    
    // Create server
    val server = Server()
      .use(monitoringMiddleware)
      .use(errorMiddleware)
      .use(LoggingMiddleware())
      
      // Normal endpoints
      .get("/", _ => "Hello World".asText)
      .get("/slow", request => {
        // Simulate slow endpoint
        Thread.sleep(500)
        "Slow response".asText
      })
      .get("/error", _ => 
        // Simulate error
        Future.successful(Fail(ValidationError("Test error")))
      )
      
      // Metrics endpoint
      .get("/metrics", _ => 
        Metrics.getStats().asJson
      )
    
    // Start server
    server.listen(3000) {
      println("Monitoring server running at http://localhost:3000")
      println("View metrics at: http://localhost:3000/metrics")
    }
  }
}
```

This comprehensive guide covers all the essential aspects of using the Apion library for building Scala.js-based HTTP servers. The guide provides detailed explanations, examples, and best practices that should be helpful for future development with this library.
