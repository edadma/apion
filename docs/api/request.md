---
title: Request
layout: default
parent: API Reference
nav_order: 2
---

# Request Handling

The `Request` class represents an incoming HTTP request in Apion. It provides a type-safe interface for accessing request data and is designed to be immutable, with all modifications creating new instances.

## Basic Properties

```scala
case class Request(
    method: String,          // HTTP method (GET, POST, etc.)
    url: String,             // Full request URL
    path: String,            // URL path component
    headers: Map[String, String], // Request headers (case-insensitive)
    params: Map[String, String],  // Path parameters
    query: Map[String, String],   // Query parameters
    context: Map[String, Any],    // Request context for middleware
    cookies: Map[String, String], // Request cookies
    finalizers: List[Finalizer]   // Response transform functions
)
```

## Connection Information

Access details about the client connection:

```scala
request.ip        // Client IP address
request.protocol  // "http" or "https"
request.secure    // true if HTTPS
request.hostname  // Server hostname from Host header
request.port      // Server port number

// Raw connection details
request.rawRequest.socket.remoteAddress  // Raw IP address
request.rawRequest.socket.remotePort     // Client port
```

## Headers

Headers are case-insensitive and accessible through multiple methods:

```scala
// Single header access
request.header("content-type")  // Option[String]
request.header("Authorization") // Same as above - case insensitive
```

## Parameters

### Path Parameters
Path parameters are extracted from URL patterns:

```scala
// Route: /users/:id/posts/:postId
// URL: /users/123/posts/456
request.params("id")      // "123"
request.params("postId")  // "456"
```

### Query Parameters
Query string parameters are parsed automatically:

```scala
// URL: /search?q=test&page=2&sort=desc
request.query.get("q")     // Some("test")
request.query.get("page")  // Some("2")
request.query("sort")      // "desc"
```

## Body Handling

Apion provides multiple ways to access the request body:

### Raw Body Access
```scala
// Get raw body as Buffer
request.body: Future[Buffer]
```

### Text Content
```scala
// Parse body as text with charset detection
request.text: Future[String]
```

### JSON Parsing
Type-safe JSON parsing with automatic decoding:

```scala
import zio.json._

// Define your data type
case class User(name: String, email: String) derives JsonDecoder

// Parse JSON body
request.json[User].flatMap {
  case Some(user) => 
    // Successfully parsed User object
    user.asJson
  case None => 
    // Invalid JSON or wrong format
    "Invalid user data".asText(400)
}
```

### Form Data
Parse URL-encoded form data:

```scala
request.form.flatMap { formData =>
  val username = formData.get("username")
  val password = formData.get("password")
  // Process form fields...
}
```

## Cookie Handling

Access request cookies:

```scala
// Basic cookie access
request.cookie("session")  // Option[String]

// With CookieMiddleware
request.getSignedCookie("auth")      // Option[String] - verified signature
request.getJsonCookie[User]("user")  // Option[User] - parsed JSON cookie
```

## Context

The context map allows middleware to store and share data:

```scala
// Store data in context (typically done by middleware)
request.copy(context = request.context + ("user" -> userData))

// Access context data
request.context.get("user").map(_.asInstanceOf[UserData])

// Type-safe context access with pattern matching
request.context.get("auth") match {
  case Some(auth: Auth) => 
    // Use auth context
  case _ =>
    "Unauthorized".asText(401)
}
```

## Finalizers

Finalizers allow middleware to transform the final response:

```scala
// Add a finalizer
val reqWithFinalizer = request.addFinalizer { (req, res) =>
  Future.successful(res.withHeader("X-Custom", "value"))
}

// Finalizers are executed in reverse order when generating response
```

## Error Handling

Request operations can fail in type-safe ways:

```scala
// Validation errors
request.failValidation("Invalid input")  // Returns Future[Result]

// Authentication errors
request.failAuth("Unauthorized")         // Returns Future[Result]

// Not found errors
request.failNotFound("Resource not found") // Returns Future[Result]

// Generic errors
request.fail(CustomError("Something went wrong"))
```

## Best Practices

1. **Immutability**: Never modify request fields directly, use `copy` to create new instances.

2. **Type Safety**: Use the type-safe methods like `json[T]` instead of manually parsing.

3. **Error Handling**: Use the provided error methods instead of throwing exceptions.

4. **Context Usage**: Store middleware data in context with clear key names to avoid conflicts.

5. **Finalizers**: Add finalizers for response modifications instead of trying to modify responses directly.

## Common Patterns

### Combining Operations
```scala
def handler(request: Request): Future[Result] = {
  for {
    userData <- request.json[User]  // Parse body
    result <- userData match {
      case Some(user) if request.header("X-API-Key").isDefined =>
        // Process authenticated user
        processUser(user)
      case Some(_) =>
        request.failAuth("Missing API key")
      case None =>
        request.failValidation("Invalid user data")
    }
  } yield result
}
```

### Middleware Data Access
```scala
def protectedHandler(request: Request): Future[Result] = {
  request.context.get("auth") match {
    case Some(auth: Auth) if auth.hasRequiredRoles(Set("admin")) =>
      // Handle admin request
      adminOperation(request)
    case Some(_) =>
      "Insufficient permissions".asText(403)
    case None =>
      "Unauthorized".asText(401)
  }
}
```

### Request Validation
```scala
def validateRequest(request: Request): Future[Result] = {
  val requiredHeaders = Set("content-type", "authorization")
  val missingHeaders = requiredHeaders.filterNot(request.headers.contains)
  
  if (missingHeaders.nonEmpty)
    request.failValidation(s"Missing required headers: ${missingHeaders.mkString(", ")}")
  else
    // Continue processing
    processRequest(request)
}
```
