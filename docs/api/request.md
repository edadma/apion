---
title: Request
layout: default
parent: API Reference
nav_order: 2
---

# Request Handling

The `Request` class represents an incoming HTTP request. It provides a type-safe interface for accessing request data.

## Properties

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

## Common Operations

### Reading Headers
```scala
request.header("content-type")  // Option[String]
request.headers.get("accept")   // Option[String]
```

### Accessing Parameters
```scala
// URL: /users/123?sort=name
request.params("id")      // "123" from /users/:id
request.query("sort")     // Some("name") from query string
```

### Body Parsing
```scala
// Raw body
request.body: Future[Buffer]

// Text content
request.text: Future[String]

// JSON parsing
case class User(name: String)
request.json[User]: Future[Option[User]]

// Form data
request.form: Future[Map[String, String]]
```
