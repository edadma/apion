---
title: Changelog
description: Version history and release notes
---

## Unreleased

Hardening pass with breaking API changes (no backward compatibility).

### Added
- Multi-valued query and form parsing (`Map[String, Seq[String]]`) with `queryParam` / `formField` accessors
- Type-safe request context via `TypedKey` / `Context` (replaces `Map[String, Any]`)
- Per-server configuration via `ServerConfig` (default headers, body size, timeout); `Server(config)`
- `head`, `options`, `all` route helpers; single-segment `*` wildcard matching

### Fixed
- `decodeURIComponent` corrupted multi-byte UTF-8
- Request body could be re-read after `Request.copy`; it is now consumed once per connection
- Multiple `WWW-Authenticate` headers were overwritten
- `AuthMiddleware.excludePaths` bypass — matching is now segment-aware on the path
- Multi-segment sub-router mounts and wildcard routes now match correctly
- The router no longer freezes its route table on the first request

### Changed
- Default headers applied per-server at the send boundary, not by `Response` factories

### Removed
- `Response.configure` / `resetDefaultHeaders` (use `ServerConfig`)
- Global mutable `Request.maxBodySize` / `bodyTimeout` vars (use `ServerConfig`)
- `InternalComplete` from the public `Result` type

---

## 0.1.0 — April 18, 2026

### Added
- Native multipart/form-data parser (`MultipartParser`) — pure Scala.js, no npm dependencies
- `BodyLimitMiddleware` for configurable per-route body size limits
- Configurable body size limit and read timeout on `Request`
- Node.js fs facade: writeFile, mkdir, readdir, rm, rename, copyFile, access
- `ReadableStream.destroy` method in Node.js stream facade
- 57 new tests (151 total): MultipartParser (30), ResponseDSL (19), Router subrouter edge cases (3), RateLimiter enforcement (2), JWT edge cases (3)

### Fixed
- Router: unsafe `asInstanceOf[StaticSegment]` cast replaced with pattern match
- StaticMiddleware: missing parentheses on `getTime()` call (Scala 3.8.3 warning)

### Changed
- `FileUploadMiddleware` now uses native `MultipartParser` instead of busboy npm package
- Updated Scala to 3.8.3, Scala.js to 1.21.0, zio-json to 0.9.0, sbt to 1.12.9

### Removed
- busboy npm dependency — Apion now has **zero external npm dependencies**

---

## 0.0.13 — April 17, 2026

### Added
- `BodyLimitMiddleware` for configurable per-route body size limits
- Configurable body size limit and read timeout on `Request`
- Node.js fs facade extensions
- 27 new tests (121 total)

### Fixed
- Router: unsafe cast replaced with pattern match
- StaticMiddleware: missing parentheses on `getTime()` call

### Changed
- Updated Scala to 3.8.3, Scala.js to 1.21.0, zio-json to 0.9.0, sbt to 1.12.9

---

## 0.0.12 — February 22, 2026

### Changed
- Updated Scala to 3.8.1, sbt to 1.12.1, Scala.js to 1.20.2
- Modernized Maven Central publishing to `sonatypePublishToBundle`
- Replaced deprecated `-Xfatal-warnings` with `-Werror`

---

## 0.0.7 — January 11, 2025

### Added
- Range header support in static file middleware
- Error handler support
- Error handler integration tests
- Implicit `ExecutionContext` in package object

### Fixed
- Handling of errors not caught by an error handler
- Handling of internally generated errors

---

## 0.0.6 — January 7, 2025

### Fixed
- Improved memory usage and responsiveness for large file downloads

---

## 0.0.5 — January 5, 2025

### Added
- Request tests
- Rate limiter middleware
- Proper streaming response handling
- Request connection information methods

### Fixed
- Content-Type header handling
- Request stream processing

### Changed
- Static file reader uses streaming reader

---

## 0.0.4 — January 4, 2025

### Added
- Streaming request body parsing

### Removed
- Body parser middleware (replaced by streaming)

---

## 0.0.3 — January 3, 2025

### Added
- Middleware chaining in route handlers
- Compression middleware integration tests

### Fixed
- Response header casing
- Response body handling
- Logging middleware timestamp
- Content-length for 404 JSON errors

---

## 0.0.2 — January 1, 2025

### Added
- Security and CORS middleware integration tests
- Cookie middleware
- Multi-valued response headers
- Authentication middleware integration tests

### Fixed
- Request finalizer handling
- Case-insensitive header handling

---

## 0.0.1 — December 30, 2024

Initial release.

### Added
- Express-style chainable API with type safety
- Request/response handling with immutable types
- JWT-based authentication middleware
- Body parsing for JSON and form data
- Static file serving
- Response compression (Brotli, Gzip, Deflate)
- CORS and security headers middleware
- Path parameter support
- Nested routing
- Error handling system
- Logging middleware
- Testing utilities
