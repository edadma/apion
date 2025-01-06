---
title: Release History
layout: default
nav_order: 5
---

# Release History

## Latest Release

### [0.0.5] - January 2025

Improve request and response streaming.

#### Added
- Add rate limiter middleware
- Add proper streaming response handling
- Add missing request connection information methods

#### Fixed
- Fix request handling of Content-Type header
- Fix request stream processing

#### Changed
- Static file reader now uses streaming file reader and provides streaming response

## Previous Releases

### [0.0.4] - January 2025

#### Added
- Add streaming request body parsing

#### Removed
- Remove body parser middleware

### [0.0.3] - January 2025

#### Added
- Add support for middleware chaining in route handlers
- Add compression middleware integration tests

#### Fixed
- Fix response header casing
- Fix response body handling
- Fix logging middleware timestamp display
- Fix content-length header for 404 JSON errors

### [0.0.2] - January 2025
- Add cookie middleware
- Add integration tests
- Improve security and CORS middleware
- Fix request finalizers

### [0.0.1] - December 2024
Initial release of Apion, a lightweight HTTP API server framework for Scala.js.

[0.0.5]: https://github.com/edadma/apion/releases/tag/v0.0.5
[0.0.4]: https://github.com/edadma/apion/releases/tag/v0.0.4
[0.0.3]: https://github.com/edadma/apion/releases/tag/v0.0.3
[0.0.2]: https://github.com/edadma/apion/releases/tag/v0.0.2
[0.0.1]: https://github.com/edadma/apion/releases/tag/v0.0.1
