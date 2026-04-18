---
title: CORS
description: Cross-origin resource sharing middleware
---

`CorsMiddleware` handles CORS preflight requests and response headers.

## Basic Usage

```scala
// Allow all origins (development)
server.use(CorsMiddleware())

// Or use the development preset
server.use(CorsMiddleware.development())
```

## Configuration

```scala
server.use(CorsMiddleware(CorsMiddleware.Options(
  origin = Origin.Multiple(Set(
    "https://app.example.com",
    "https://admin.example.com",
  )),
  methods = Set("GET", "POST", "PUT", "DELETE"),
  allowedHeaders = Set("Content-Type", "Authorization"),
  exposedHeaders = Set("X-Total-Count"),
  credentials = true,
  maxAge = Some(3600),
)))
```

### Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `origin` | `Origin` | `Origin.Any` | Allowed origins |
| `methods` | `Set[String]` | GET, HEAD, PUT, PATCH, POST, DELETE | Allowed methods |
| `allowedHeaders` | `Set[String]` | Content-Type, Authorization | Allowed request headers |
| `exposedHeaders` | `Set[String]` | `Set.empty` | Headers exposed to the browser |
| `credentials` | `Boolean` | `false` | Allow credentials |
| `maxAge` | `Option[Int]` | `Some(86400)` | Preflight cache duration (seconds) |
| `preflightSuccessStatus` | `Int` | `204` | Status code for preflight responses |

## Origin Types

```scala
// Any origin
Origin.Any

// Single origin
Origin("https://example.com")

// Multiple origins
Origin(Set("https://app.example.com", "https://admin.example.com"))

// Regex pattern
Origin.pattern("""https://.*\.example\.com""")

// Custom validation function
Origin.validate(origin =>
  origin.endsWith(".example.com") && origin.startsWith("https://")
)
```

## Presets

### Development

Permissive settings for local development:

```scala
server.use(CorsMiddleware.development())
```

### Production

Strict settings with specific allowed origins:

```scala
server.use(CorsMiddleware.production(Set(
  "https://app.example.com",
  "https://admin.example.com",
)))
```

## How It Works

- **Preflight requests** (OPTIONS with `Origin` and `Access-Control-Request-Method`) are handled automatically and return the configured CORS headers
- **Simple requests** get CORS headers added via a response finalizer
- Origin validation is checked against the configured `Origin` type
