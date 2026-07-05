# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

### Fixed

### Changed

### Removed

### Deprecated

## [0.2.1] - 2026-07-05

Re-release of 0.2.0 with no source changes. The 0.2.0 artifacts published to Maven
Central successfully, but a Central Portal status-check timeout made the publish
appear to fail, so 0.2.1 was cut to retry. Both versions are on Central and identical.

## [0.2.0] - 2026-07-04

Hardening pass. Contains breaking API changes (no backward compatibility).

### Added
- Multi-valued query and form parsing (`Map[String, Seq[String]]`) with `queryParam` / `formField` first-value accessors
- Type-safe request context via `TypedKey[A]` / `Context`, replacing the untyped `Map[String, Any]`
- Per-server configuration via `ServerConfig` (default headers, body size, read timeout); `Server(config)`
- Route helpers `head`, `options`, `all`, and single-segment `*` wildcard matching

### Fixed
- `decodeURIComponent` corrupted multi-byte UTF-8 (decoded one char per `%xx` byte); now uses the JS runtime, with `+`-as-space handled for query/form
- Request body could be re-read after `Request.copy` reset its promise; the body is now memoised on the connection and consumed once
- Multiple `WWW-Authenticate` headers were overwritten (a typo in the multi-value header set)
- `AuthMiddleware.excludePaths` bypass: matching is now segment-aware on `request.path` (was `url.startsWith`, which included the query string and matched character-prefixes like `/publicfoo` against `/public`)
- Multi-segment sub-router mounts (`use("/api/v1", r)`) only stripped one segment
- The router froze its route table on the first request, silently ignoring later registrations
- Wildcard (`*`) routes never matched (the matcher had no wildcard case)

### Changed
- Default response headers are applied per-server at the send boundary rather than baked into `Response` factories
- The router is documented as the ordered pipeline it is (removed the "pre-compiled matching" framing)

### Removed
- `Response.configure` / `Response.resetDefaultHeaders` and the global default-header var — configure via `ServerConfig`
- Global mutable `Request.maxBodySize` / `Request.bodyTimeout` vars — now `val` fallbacks; set defaults via `ServerConfig`
- `InternalComplete` from the public `Result` type (the router uses an internal `Outcome`)

## [0.1.0] - 2026-04-18

### Added
- Native multipart/form-data parser (`MultipartParser`) — pure Scala.js, no npm dependencies
- BodyLimitMiddleware for configurable per-route body size limits
- Configurable body size limit and read timeout on Request
- Node.js fs facade: writeFile, mkdir, readdir, rm, rename, copyFile, access + option types
- ReadableStream.destroy method in Node.js stream facade
- 57 new tests (151 total): MultipartParser (30), ResponseDSL (19), Router subrouter edge cases (3), RateLimiter enforcement (2), JWT edge cases (3)

### Fixed
- Router: unsafe asInstanceOf[StaticSegment] cast replaced with pattern match
- StaticMiddleware: add missing parentheses on getTime() call (Scala 3.8.3 warning)

### Changed
- FileUploadMiddleware now uses native MultipartParser instead of busboy npm package
- Update Scala to 3.8.3 (from 3.8.1)
- Update sbt to 1.12.9 (from 1.12.1)
- Update Scala.js to 1.21.0 (from 1.20.2)
- Update zio-json to 0.9.0 (from 0.7.44)
- Update pprint to 0.9.6 (from 0.9.3)

### Removed
- busboy npm dependency — Apion now has zero external npm dependencies

[0.2.1]: https://github.com/edadma/apion/releases/tag/v0.2.1

[0.2.0]: https://github.com/edadma/apion/releases/tag/v0.2.0

[0.1.0]: https://github.com/edadma/apion/releases/tag/v0.1.0

## [0.0.13] - 2026-04-17

### Added
- BodyLimitMiddleware for configurable per-route body size limits
- Configurable body size limit and read timeout on Request (via context or global defaults)
- Node.js fs facade: writeFile, mkdir, readdir, rm, rename, copyFile, access + option types
- 27 new tests: ResponseDSL, Router subrouter edge cases, RateLimiter enforcement, JWT edge cases (121 total)

### Fixed
- Router: unsafe asInstanceOf[StaticSegment] cast replaced with pattern match (prevents ClassCastException on non-static subrouter prefixes)
- StaticMiddleware: add missing parentheses on getTime() call (Scala 3.8.3 warning)

### Changed
- Update Scala to 3.8.3 (from 3.8.1)
- Update sbt to 1.12.9 (from 1.12.1)
- Update Scala.js to 1.21.0 (from 1.20.2)
- Update zio-json to 0.9.0 (from 0.7.44)
- Update pprint to 0.9.6 (from 0.9.3)

[0.0.13]: https://github.com/edadma/apion/releases/tag/v0.0.13

## [0.0.12] - 2026-02-22

### Changed
- Update Scala to 3.8.1 (from 3.7.2)
- Update sbt to 1.12.1 (from 1.11.4)
- Update sbt-scalajs to 1.20.2 (from 1.19.0)
- Update sbt-pgp to 2.3.1 (from 2.2.1)
- Update sbt-sonatype to 3.12.2 (from 3.10.0)
- Update sbt-site-paradox to 1.7.0 (from 1.5.0)
- Update scalafmt to 3.10.7 (from 3.9.9)
- Modernize Maven Central publishing to use `sonatypePublishToBundle`
- Replace deprecated `-Xfatal-warnings` with `-Werror`

[0.0.12]: https://github.com/edadma/apion/releases/tag/v0.0.12

## [0.0.7] - 2025-01-11

### Added
- add support for range headers in the static file server middleware
- add support for error handlers
- add error handler integration tests
- add implicit ExecutionContext to package object, removing the need for users to import MacrotaskExecutor.Implicits.global

### Fixed
- fix handling of errors not caught by an error handler
- fix handling of internally generated errors

[0.0.7]: https://github.com/edadma/apion/releases/tag/v0.0.7

## [0.0.6] - 2025-01-07

### Added
- add more documentation

### Fixed
- improve memory usage and responsiveness for large file downloads

[0.0.6]: https://github.com/edadma/apion/releases/tag/v0.0.6

## [0.0.5] - 2025-01-05

Improve request and response streaming.

### Added
- add request tests
- add rate limiter middleware
- add proper streaming response handling
- add missing request connection information methods

### Fixed
- fix request handling of Content-Type header
- fix request stream processing

### Changed
- static file reader now uses streaming file reader and provides streaming response

[0.0.5]: https://github.com/edadma/apion/releases/tag/v0.0.5

## [0.0.4] - 2025-01-04

Add streaming request body parsing.

### Added
- add streaming request body parsing

### Removed
- remove body parser middleware

[0.0.4]: https://github.com/edadma/apion/releases/tag/v0.0.4

## [0.0.3] - 2025-01-03

Fix response handling, and add support for middleware chaining.

### Added

- add support for middleware chaining in route handlers
- add compression middleware integration tests

### Fixed

- fix response header casing for both errors and successful response
- fix response body handling
- fix logging middleware not displaying timestamp
- content-length header not generated for 404 with json error

### Changed

- AuthConfig -> Config
- StaticOptions -> Options

[0.0.3]: https://github.com/edadma/apion/releases/tag/v0.0.3

## [0.0.2] - 2025-01-01

Add missing basic features and more integration testing.

### Added
- add security and CORS middleware integration tests
- add basic (not using any middleware) integration tests
- add cookie middleware
- add integration tests for cookie handling
- add support for multi-valued response headers
- add integration test for authentication middleware

### Fixed
- fix handling of request finalizers
- fix handling of headers for case-insensitivity

### Changed
- improve security middleware
- improve CORS middleware
- improve authentication middleware
- improve Node.js facade

[0.0.2]: https://github.com/edadma/apion/releases/tag/v0.0.2

## [0.0.1] - 2024-12-30

Initial release of Apion, a lightweight HTTP API server framework for Scala.js.

### Added
- Express-style chainable API with type safety
- Request/response handling with immutable types
- JWT-based authentication middleware
- Body parsing for JSON and form data
- Static file serving with directory support
- Response compression (Brotli, Gzip, Deflate)
- CORS and security headers middleware
- Path parameter support with type-safe extraction
- Nested routing capabilities
- Comprehensive error handling system
- Logging middleware with configurable formats
- Testing utilities for unit and integration tests

[0.0.1]: https://github.com/edadma/apion/releases/tag/v0.0.1
