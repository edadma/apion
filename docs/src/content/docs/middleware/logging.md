---
title: Logging
description: Request/response logging with configurable formats
---

`LoggingMiddleware` provides Morgan-style request logging with predefined and custom formats.

## Basic Usage

```scala
// Default format (Dev)
server.use(LoggingMiddleware())
```

## Configuration

```scala
server.use(LoggingMiddleware(LoggingMiddleware.Options(
  format = LoggingMiddleware.Format.Combined,
  immediate = false,
  skip = request => request.path.startsWith("/health"),
  handler = msg => println(msg),
  debug = false,
)))
```

### Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `format` | `String` | `Format.Dev` | Log format string |
| `immediate` | `Boolean` | `false` | Log on request (true) or response (false) |
| `skip` | `Request => Boolean` | `_ => false` | Skip logging for matching requests |
| `handler` | `String => Unit` | `println` | Custom log output handler |
| `debug` | `Boolean` | `false` | Enable debug output |

## Predefined Formats

### Combined (Apache combined)

```
:remote-addr - :remote-user [:date] ":method :url HTTP/:http-version" :status :res[content-length] ":referrer" ":user-agent"
```

### Common (Apache common)

```
:remote-addr - :remote-user [:date] ":method :url HTTP/:http-version" :status :res[content-length]
```

### Dev

Concise colored output for development:

```
:method :url :status :response-time ms - :res[content-length]
```

### Short

```
:remote-addr :remote-user :method :url HTTP/:http-version :status :res[content-length] - :response-time ms
```

### Tiny

Minimal output:

```
:method :url :status :res[content-length] - :response-time ms
```

## Format Tokens

Use these tokens in custom format strings:

| Token | Description |
|-------|-------------|
| `:method` | HTTP method |
| `:url` | Request URL |
| `:status` | Response status code |
| `:response-time` | Response time in milliseconds |
| `:date` | Timestamp |
| `:remote-addr` | Client IP address |
| `:remote-user` | Remote user |
| `:http-version` | HTTP version |
| `:referrer` | Referrer header |
| `:user-agent` | User-Agent header |
| `:res[header]` | Response header value |

### Custom Format

```scala
server.use(LoggingMiddleware(LoggingMiddleware.Options(
  format = ":method :url -> :status (:response-time ms)"
)))
```

## Immediate vs Deferred Logging

- **Deferred** (default): Logs after the response is sent, includes status code and response time
- **Immediate**: Logs when the request arrives, no response data available

```scala
// Log immediately on request
server.use(LoggingMiddleware(LoggingMiddleware.Options(
  immediate = true
)))
```
