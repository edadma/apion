---
title: Home
layout: home
nav_order: 1
---

# Apion Documentation

Apion is a lightweight, type-safe HTTP server framework for Scala.js that combines Express-style ergonomics with Scala's powerful type system.

## Key Features

- Express-like chainable API with full type safety
- Pure functions and immutable types
- JWT authentication with role-based access control
- Comprehensive middleware system
- Static file serving with directory support
- Response compression (Brotli, Gzip, Deflate)
- Type-safe request/response handling

## Quick Start

Add to your `build.sbt`:
```scala
libraryDependencies += "io.github.edadma" %%% "apion" % "0.0.7"
```

Create a basic server:
```scala
import io.github.edadma.apion._

@main
def run(): Unit =
  Server()
    .use(LoggingMiddleware())
    .get("/hello", _ => "Hello World!".asText)
    .listen(3000) { println("Server running at http://localhost:3000") }
```
