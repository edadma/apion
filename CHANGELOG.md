# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
