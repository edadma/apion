---
title: Error Handling
layout: default
parent: API Reference
nav_order: 4
---

# Error Handling in Apion

Apion provides a comprehensive error handling system that allows you to handle errors in a type-safe manner throughout your application. The system is built around the `ServerError` trait and integrates with Apion's middleware and routing system.

## Core Error Types

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

## Creating Errors

### Using Built-in Error Types
```scala
// In your handlers
request.failValidation("Invalid input data")
request.failAuth("Missing or invalid token")
request.failNotFound("User not found")

// Or create errors directly
Fail(ValidationError("Invalid input"))
Fail(AuthError("Unauthorized"))
Fail(NotFoundError("Resource not found"))
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

## Error Handlers

Error handlers are special middleware that process errors:

```scala
// Basic error handler
server.use { (error: ServerError, request: Request) =>
  error match {
    case ValidationError(msg) =>
      // Handle validation errors
      Map("error" -> msg, "type" -> "validation").asJson(400)
    case AuthError(msg) =>
      // Handle auth errors
      Map("error" -> msg, "type" -> "auth").asJson(401)
    case _ =>
      // Handle unknown errors
      Map("error" -> "Internal server error").asJson(500)
  }
}

// Specialized error handler
val validationHandler = { (error: ServerError, request: Request) =>
  error match {
    case e: ValidationError =>
      // Custom validation error response
      Map(
        "status" -> "error",
        "code" -> "VALIDATION_FAILED",
        "message" -> e.message,
        "path" -> request.path
      ).asJson(400)
    case _ => skip
  }
}

server.use(validationHandler)
```

## Error Handler Chaining

Error handlers can be chained, with each handler choosing whether to handle an error or pass it to the next handler:

```scala
server
  // Handle validation errors
  .use { (error: ServerError, request: Request) =>
    error match {
      case e: ValidationError => 
        Map("validation_error" -> e.message).asJson(400)
      case _ => skip
    }
  }
  // Handle auth errors
  .use { (error: ServerError, request: Request) =>
    error match {
      case e: AuthError =>
        Map("auth_error" -> e.message).asJson(401)
      case _ => skip
    }
  }
  // Catch-all handler
  .use { (error: ServerError, request: Request) =>
    Map("error" -> error.message).asJson(500)
  }
```

## Error Transformation

Error handlers can transform errors into different types:

```scala
// Transform validation errors into custom errors
server.use { (error: ServerError, request: Request) =>
  error match {
    case ValidationError(msg) =>
      // Transform to custom error
      Fail(CustomError(s"Validation failed: $msg", "VALIDATION"))
    case _ => skip
  }
}
```

## Router-Specific Error Handling

You can add error handlers to specific routers:

```scala
val apiRouter = Router()
  .use(authMiddleware)
  .get("/users", listUsers)
  .use { (error: ServerError, request: Request) =>
    // Handle errors specific to this router
    Map(
      "error" -> error.message,
      "api_version" -> "1.0",
      "path" -> request.path
    ).asJson(500)
  }

server.use("/api", apiRouter)
```

## Best Practices

1. **Type Safety**: Use specific error types rather than generic strings or status codes.
   ```scala
   // Good
   request.failValidation("Invalid email format")
   
   // Avoid
   "Invalid email format".asText(400)
   ```

2. **Error Hierarchy**: Create an error hierarchy for your domain.
   ```scala
   sealed trait DomainError extends ServerError
   case class UserError(message: String) extends DomainError
   case class OrderError(message: String) extends DomainError
   ```

3. **Consistent Error Responses**: Use a consistent format for error responses.
   ```scala
   trait DomainError extends ServerError {
     def code: String
     def toResponse: Response = Response.json(
       Map(
         "error" -> code,
         "message" -> message,
         "timestamp" -> System.currentTimeMillis()
       ),
       status
     )
   }
   ```

4. **Appropriate Log Levels**: Set appropriate log levels for different error types.
   ```scala
   case class ConfigError(message: String) extends ServerError {
     override def logLevel: LogLevel = LogLevel.FATAL
   }
   ```

5. **Context Preservation**: Include relevant context in error responses.
   ```scala
   case class ValidationError(
     message: String,
     field: Option[String] = None,
     details: Map[String, String] = Map()
   ) extends ServerError
   ```

## Common Patterns

### Request Validation
```scala
def validateUser(request: Request): Future[Result] = {
  request.json[User].flatMap {
    case Some(user) if user.email.contains("@") =>
      // Valid user
      processUser(user)
    case Some(_) =>
      // Invalid email
      request.failValidation("Invalid email format")
    case None =>
      // Invalid JSON
      request.failValidation("Invalid JSON payload")
  }
}
```

### Authentication Errors
```scala
def requireAdmin(request: Request): Future[Result] = {
  request.context.get("auth") match {
    case Some(auth: Auth) if auth.hasRequiredRoles(Set("admin")) =>
      // Process admin request
      adminOperation(request)
    case Some(_) =>
      request.failAuth("Insufficient permissions")
    case None =>
      request.failAuth("Authentication required")
  }
}
```

### Error Recovery
```scala
def handleDatabaseOperation(request: Request): Future[Result] = {
  databaseOperation()
    .map(data => data.asJson)
    .recover {
      case e: DatabaseError =>
        Fail(CustomError(s"Database error: ${e.message}", "DB_ERROR"))
      case e: Exception =>
        logger.error("Unexpected error", e)
        Fail(CustomError("Internal server error", "INTERNAL_ERROR"))
    }
}
```

## Testing Error Handling

```scala
// Test error handler
"ErrorHandler" - {
  "should handle validation errors" in {
    val handler = createTestHandler()
    val request = createTestRequest()
    
    handler(Fail(ValidationError("test error")))
      .map { result =>
        result match {
          case Complete(response) =>
            response.status shouldBe 400
            response.bodyText should include("test error")
          case _ =>
            fail("Expected Complete result")
        }
      }
  }
}
```

## Performance Considerations

1. **Error Creation**: Error instances are lightweight and can be created freely.
2. **Handler Chain**: Error handlers are processed in order until one handles the error.
3. **Response Generation**: Error responses are generated only when needed.
4. **Logging**: Different log levels allow for efficient log processing.

Remember that Apion's error handling system is designed to be:
- Type-safe: Errors are typed and checked at compile time
- Composable: Handlers can be chained and combined
- Extensible: Custom error types and handlers can be added
- Performant: Errors are handled efficiently