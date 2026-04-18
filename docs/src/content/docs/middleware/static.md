---
title: Static Files
description: Serve static files with caching, ETags, and range requests
---

`StaticMiddleware` serves files from a directory with support for caching, ETags, directory indexes, and HTTP range requests.

## Basic Usage

```scala
// Serve files from the "public" directory
server.use(StaticMiddleware("public"))
```

## Configuration

```scala
server.use(StaticMiddleware("public", StaticMiddleware.Options(
  index = true,          // Serve index.html for directories
  dotfiles = "ignore",   // How to handle dotfiles
  etag = true,           // Enable ETag generation
  maxAge = 3600,         // Cache-Control max-age in seconds
  redirect = true,       // Redirect directories to trailing slash
  fallthrough = true,    // Continue to next handler if not found
  acceptRanges = true,   // Support byte range requests
)))
```

### Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `index` | `Boolean` | `true` | Serve `index.html` for directory requests |
| `dotfiles` | `String` | `"ignore"` | `"ignore"`, `"allow"`, or `"deny"` |
| `etag` | `Boolean` | `true` | Generate ETag headers for caching |
| `maxAge` | `Int` | `0` | Cache-Control max-age in seconds |
| `redirect` | `Boolean` | `true` | Redirect `/dir` to `/dir/` |
| `fallthrough` | `Boolean` | `true` | If file not found, continue to next handler |
| `acceptRanges` | `Boolean` | `true` | Support HTTP Range requests (206) |

## Path Mounting

Serve files under a URL prefix:

```scala
server.use("/assets", StaticMiddleware("public/assets"))
server.use("/images", StaticMiddleware("uploads/images"))
server.use("/css", StaticMiddleware("public/styles"))
```

## Dotfile Handling

| Value | Behavior |
|-------|----------|
| `"ignore"` | Pretend dotfiles don't exist (404) |
| `"allow"` | Serve dotfiles normally |
| `"deny"` | Return 403 Forbidden |

## Range Requests

When `acceptRanges` is enabled, the middleware supports HTTP Range requests for partial content:

- Returns `206 Partial Content` with the requested byte range
- Returns `416 Range Not Satisfiable` for invalid ranges
- Sets `Content-Range` and `Accept-Ranges` headers

This is useful for video/audio streaming and resumable downloads.

## ETag Caching

When `etag` is enabled:

- An ETag is generated from the file's modification time and size
- If the client sends `If-None-Match` with a matching ETag, a `304 Not Modified` is returned

## Fallthrough

- **`fallthrough = true`** (default): If the file isn't found, the request passes to the next handler. This allows combining static files with API routes.
- **`fallthrough = false`**: Returns 404 immediately if the file isn't found.

## Security

Directory traversal is prevented — requests containing `..` segments are rejected.

## MIME Types

Content-Type is set automatically based on file extension, with sensible defaults for common file types.
