---
title: API Reference
layout: default
nav_order: 2
has_children: true
---

# API Reference

Welcome to the Apion API documentation. This guide provides detailed information about Apion's core components and features.

## Core Concepts

Apion is built around a few key concepts:

- **Server & Router**: The main building blocks for creating your application
- **Handlers**: Functions that process requests and return responses
- **Middleware**: Reusable components that can modify requests or responses
- **Request/Response**: Immutable types representing HTTP messages

## Documentation Sections

### Foundation
- [Server & Router](server.md) - Creating and configuring your server
- [Request Handling](request.md) - Working with HTTP requests
- [Response Building](response.md) - Creating and sending responses
- [Error Handling](errors.md) - Managing and responding to errors

### Middleware
- [Built-in Middleware](middleware/index.md) - Overview of included middleware
- [Authentication](middleware/auth.md) - JWT-based authentication
- [Security](middleware/security.md) - Security headers and protection
- [Static Files](middleware/static.md) - Serving static content
- [Compression](middleware/compression.md) - Response compression
- [CORS](middleware/cors.md) - Cross-Origin Resource Sharing
- [Cookies](middleware/cookies.md) - Cookie management
- [Logging](middleware/logging.md) - Request logging
- [Rate Limiting](middleware/rate-limiting.md) - Request rate limiting

### Extensions
- [Response DSL](extensions/response-dsl.md) - Simplified response creation
- [Cookie DSL](extensions/cookie-dsl.md) - Cookie handling utilities

## Getting Started

For a quick start, check out these common use cases:

```scala
// Create a basic server
Server()
  .use(LoggingMiddleware())
  .get("/hello", _ => "Hello World!".asText)
  .listen(3000) { println("Server running!") }

// Add type-safe JSON handling
case class User(name: String) derives JsonEncoder, JsonDecoder

Server()
  .post("/users", request => 
    request.json[User].flatMap {
      case Some(user) => user.asJson(201)
      case None => "Invalid data".asText(400)
    }
  )
```

See the individual documentation sections for detailed information about each component.
