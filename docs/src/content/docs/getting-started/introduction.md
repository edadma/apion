---
title: Introduction
description: A lightweight, type-safe HTTP server framework for Scala.js
---

Apion is a lightweight HTTP server framework for Scala.js that provides an Express-like developer experience with the benefits of Scala's type system, immutability, and pattern matching.

## Why Apion?

- **Express-like API** — Familiar chainable API for JavaScript/Node.js developers
- **Type safety** — Compile-time validation with Scala's type system and zio-json
- **Immutable by design** — Pure functions with immutable request/response types
- **Unified handler system** — Middleware, routes, and error handlers all share a single type
- **Zero npm dependencies** — Pure Scala.js implementation running on Node.js
- **Comprehensive middleware** — Authentication, CORS, compression, static files, and more

## How It Works

Every request processor in Apion — whether it's a route handler, middleware, or error handler — is a `Handler`:

```scala
type Handler = Request => Future[Result]
```

A handler receives an immutable `Request` and returns one of four `Result` types:

| Result | Meaning |
|--------|---------|
| `Continue(request)` | Pass a (possibly modified) request to the next handler |
| `Complete(response)` | End the chain and send a response |
| `Fail(error)` | Propagate a typed error |
| `Skip` | Skip this handler and try the next one |

This unified design means middleware and routes compose naturally — there's no separate middleware API to learn.

## At a Glance

```scala
import io.github.edadma.apion._
import zio.json._

case class User(name: String, email: String) derives JsonEncoder, JsonDecoder

@main def run(): Unit =
  Server()
    .use(LoggingMiddleware())
    .use(CorsMiddleware())
    .get("/hello", _ => "Hello World!".asText)
    .post("/users", _.json[User].flatMap {
      case Some(user) => user.asJson(201)
      case _          => "Invalid user data".asText(400)
    })
    .listen(3000) { println("Server running at http://localhost:3000") }
```

## Built-in Middleware

| Middleware | Purpose |
|-----------|---------|
| `AuthMiddleware` | JWT authentication with role-based access control |
| `CorsMiddleware` | Cross-origin resource sharing |
| `SecurityMiddleware` | Security headers (CSP, HSTS, XSS protection, etc.) |
| `LoggingMiddleware` | Request/response logging (Morgan-style formats) |
| `CompressionMiddleware` | Response compression (Brotli, Gzip, Deflate) |
| `StaticMiddleware` | Static file serving with caching and range requests |
| `CookieMiddleware` | Cookie parsing, signing, and JSON cookies |
| `RateLimiterMiddleware` | Per-IP rate limiting with burst support |
| `FileUploadMiddleware` | Multipart file uploads with validation |
| `BodyLimitMiddleware` | Per-route request body size limits |

## Tech Stack

- **Scala 3.8.3** with **Scala.js 1.21.0**
- **zio-json** for type-safe JSON encoding/decoding
- **Node.js** as the runtime (the only external dependency)
- Published to **Maven Central** under `io.github.edadma:apion_sjs1_3`
