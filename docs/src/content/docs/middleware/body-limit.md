---
title: Body Limit
description: Per-route request body size limits
---

`BodyLimitMiddleware` enforces a maximum request body size for specific routes. This is useful when different endpoints need different size limits.

## Basic Usage

```scala
// Limit uploads to 10 MB
server.post("/upload", BodyLimitMiddleware(10 * 1024 * 1024), uploadHandler)

// Limit JSON bodies to 1 MB
server.post("/api/data", BodyLimitMiddleware(1 * 1024 * 1024), dataHandler)
```

## With Custom Timeout

```scala
// 5 MB limit, 60 second timeout
server.post("/upload",
  BodyLimitMiddleware(5 * 1024 * 1024, timeoutMs = 60000),
  uploadHandler,
)
```

## How It Works

The middleware sets the `maxBodySize` and optionally `bodyTimeout` values on the request context. When the request body is read (via `request.body`, `request.text`, `request.json`, or `request.form`), the body reader enforces these limits.

If the body exceeds the limit, the body read fails with an error.

## Global Defaults

You can also set global defaults that apply to all requests:

```scala
Request.maxBodySize = 50 * 1024 * 1024  // 50 MB (default)
Request.bodyTimeout = 30000             // 30 seconds (default)
```

`BodyLimitMiddleware` overrides these defaults on a per-route basis.
