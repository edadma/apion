---
title: Response
layout: default
parent: API Reference
nav_order: 3
---

# Response Handling

The `Response` class represents an HTTP response in Apion. It provides a type-safe way to construct responses with headers, status codes, and various body types. The response system is designed to be immutable and composable.

## Basic Structure

```scala
case class Response(
    status: Int = 200,                           // HTTP status code
    headers: ResponseHeaders = ResponseHeaders.empty,  // Response headers
    body: ResponseBody = EmptyBody                    // Response body
)
```

## Creating Responses

### Basic Responses

```scala
// Text response
Response.text("Hello World")                     // 200 OK
Response.text("Created", status = 201)           // 201 Created

// JSON response
Response.json(data)                              // 200 OK with JSON
Response.json(errorData, status = 400)           // 400 Bad Request

// No content
Response.noContent()                             // 204 No Content

// Binary response
Response.binary(buffer)                          // Binary data
```

### Using Response DSL

```scala
// Text responses
"Hello World".asText                    // Future[Result] with 200 OK
"Created".asText(201)                   // Future[Result] with 201 Created

// JSON responses with automatic encoding
case class User(name: String) derives JsonEncoder
User("Alice").asJson                    // Future[Result] with JSON
User("Alice").asJson(201)               // Future[Result] with 201

// Common responses
notFound                                // 404 Not Found
badRequest                              // 400 Bad Request
serverError                             // 500 Internal Server Error

// Created with location
created(data, Some("/resources/123"))   // 201 with Location header
```

## Headers

The `ResponseHeaders` class provides case-insensitive header handling:

```scala
// Add single header
response.copy(headers = response.headers.add("Content-Type", "text/plain"))

// Add multiple headers
response.copy(headers = response.headers.addAll(Seq(
  "Cache-Control" -> "no-cache",
  "X-Custom-Header" -> "value"
)))

// Get header value
response.headers.get("content-type")  // Case-insensitive lookup
```

## Body Types

Apion supports multiple response body types:

```scala
sealed trait ResponseBody
case class StringBody(content: String, buffer: Buffer) extends ResponseBody
case class BufferBody(content: Buffer) extends ResponseBody
case class ReadableStreamBody(stream: ReadableStream) extends ResponseBody
case object EmptyBody extends ResponseBody
```

### Streaming Responses

For large responses or files:

```scala
// Create streaming response
Response.stream(
  stream = fileStream,
  status = 200,
  additionalHeaders = Seq(
    "Content-Type" -> "application/octet-stream"
  )
)
```

## Cookie Handling

Setting cookies in responses:

```scala
// Basic cookie
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

// Multiple cookies
response
  .withCookie("user", "alice")
  .withCookie("session", "abc123")

// Clear cookie
response.clearCookie("session")
```

## Common Patterns

### Error Responses

```scala
// Standard error response
case class ErrorResponse(error: String, details: Option[String] = None)
  derives JsonEncoder

ErrorResponse("Invalid input").asJson(400)

// With additional headers
Response.json(
  ErrorResponse("Unauthorized"),
  401,
  Seq("WWW-Authenticate" -> "Bearer")
)
```

### File Downloads

```scala
// File download response
Response.binary(
  content = fileBuffer,
  status = 200,
  additionalHeaders = Seq(
    "Content-Type" -> "application/pdf",
    "Content-Disposition" -> "attachment; filename=\"document.pdf\""
  )
)
```

### Caching Headers

```scala
response.copy(headers = response.headers.addAll(Seq(
  "Cache-Control" -> "public, max-age=31536000",
  "ETag" -> "\"123456\"",
  "Last-Modified" -> lastModified
)))
```

## Best Practices

1. **Immutability**: Always use `copy` or helper methods to create new responses rather than modifying existing ones.

2. **Type Safety**: Use `asJson` with proper type classes rather than manually serializing JSON.

3. **Streaming**: Use `Response.stream` for large files or long-running responses.

4. **Headers**: Use the built-in header normalization rather than managing case-sensitivity manually.

5. **Content Types**: Always set appropriate Content-Type headers for different response types.

## Performance Considerations

1. **Body Types**: Choose appropriate body type based on content:
    - `StringBody` for small text responses
    - `BufferBody` for binary data
    - `ReadableStreamBody` for large files or streams
    - `EmptyBody` for no content responses

2. **Headers**: Common headers are added automatically:
    - Server and date headers
    - Content-Type and Content-Length
    - Security headers (when using SecurityMiddleware)

3. **Compression**: Large responses are automatically compressed when using CompressionMiddleware

## Error Handling

```scala
// Type-safe error responses
sealed trait ServerError extends RuntimeException
case class ValidationError(msg: String) extends ServerError
case class AuthError(msg: String) extends ServerError

// Convert errors to responses
def errorToResponse(error: ServerError): Response = error match {
  case ValidationError(msg) => Response.json(
    Map("error" -> msg), 400)
  case AuthError(msg) => Response.json(
    Map("error" -> msg), 401)
  case _ => Response.json(
    Map("error" -> "Internal server error"), 500)
}
```

## Response Configuration

You can configure global default headers for all responses:

```scala
// Configure global headers
Response.configure(Seq(
  "X-Frame-Options" -> "DENY",
  "X-Content-Type-Options" -> "nosniff",
  "Referrer-Policy" -> "strict-origin-when-cross-origin"
))

// Reset to default headers
Response.resetDefaultHeaders()
```

The default headers include:
- `Server: Apion`
- `Cache-Control: no-store, no-cache, must-revalidate, max-age=0`
- `Pragma: no-cache`
- `Expires: 0`
- `Date: [current date in RFC 1123 format]`
- `X-Powered-By: Apion`

## Integration with Middleware

Responses can be transformed by middleware through finalizers:

```scala
// Compression middleware adds a finalizer
val compressionFinalizer: Finalizer = (req, res) => {
  if (shouldCompress(req, res)) {
    compressResponse(res)
  } else {
    Future.successful(res)
  }
}

// Security middleware adds security headers
val securityFinalizer: Finalizer = (req, res) => {
  Future.successful(
    res.copy(headers = res.headers.addAll(securityHeaders))
  )
}

// Finalizers are executed in reverse order
request.addFinalizer(compressionFinalizer)
       .addFinalizer(securityFinalizer)
```

## Testing Responses

```scala
// Create test response
val response = Response.json(
  User("test"),
  201,
  Seq("X-Custom" -> "value")
)

// Verify response
response.status shouldBe 201
response.headers.get("content-type") shouldBe Some("application/json")
response.headers.get("x-custom") shouldBe Some("value")
response.bodyText should include("test")
```

Remember that responses are immutable, so any modification creates a new instance. This ensures thread safety and makes responses easier to reason about, especially when working with middleware and finalizers.