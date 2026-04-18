---
title: Rate Limiting
description: Per-IP request throttling with burst support
---

`RateLimiterMiddleware` provides request rate limiting based on client IP address with configurable windows, burst allowance, and rate limit headers.

## Basic Usage

```scala
// Default: 60 requests per minute
server.use(RateLimiterMiddleware())
```

## Configuration

```scala
import scala.concurrent.duration._

server.use(RateLimiterMiddleware(RateLimiterMiddleware.Options(
  limit = RateLimiterMiddleware.RateLimit(
    maxRequests = 100,
    window = 1.minute,
    burst = 20,
    skipFailedRequests = false,
    skipSuccessfulRequests = false,
    statusCode = 429,
    errorMessage = "Too many requests. Please try again later.",
    headers = true,
  ),
  keyGenerator = request => Future.successful(request.ip),
  skip = request => request.path.startsWith("/health"),
)))
```

### RateLimit Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `maxRequests` | `Int` | `60` | Max requests per window |
| `window` | `Duration` | `1.minute` | Time window |
| `burst` | `Int` | `0` | Extra requests allowed above max |
| `skipFailedRequests` | `Boolean` | `false` | Don't count failed requests |
| `skipSuccessfulRequests` | `Boolean` | `false` | Don't count successful requests |
| `statusCode` | `Int` | `429` | Status code when limited |
| `errorMessage` | `String` | `"Too many requests..."` | Error message |
| `headers` | `Boolean` | `true` | Send rate limit headers |

### Top-Level Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `store` | `RateLimitStore` | `InMemoryStore` | Storage backend |
| `keyGenerator` | `Request => Future[String]` | By IP | Key generation function |
| `skip` | `Request => Boolean` | `_ => false` | Skip rate limiting |
| `onRateLimited` | Function | Default handler | Custom response on limit |
| `ipSources` | `Seq[IpSource.Value]` | Forward, Real, Direct | IP detection order |

## Presets

```scala
import scala.concurrent.duration._

// Moderate: specified max, with burst allowance
server.use(RateLimiterMiddleware.moderate(100, 1.minute))

// Strict: specified max, no burst
server.use(RateLimiterMiddleware.strict(50, 1.minute))

// Flexible: specified max, with generous burst
server.use(RateLimiterMiddleware.flexible(200, 1.minute))
```

## Rate Limit Headers

When `headers = true`, responses include:

| Header | Description |
|--------|-------------|
| `X-RateLimit-Limit` | Maximum requests allowed |
| `X-RateLimit-Remaining` | Requests remaining in window |
| `X-RateLimit-Reset` | Seconds until window resets |
| `Retry-After` | Seconds to wait (only on 429) |

## IP Detection

The `ipSources` option controls how client IPs are detected:

| Source | Header/Property |
|--------|----------------|
| `IpSource.Forward` | `X-Forwarded-For` |
| `IpSource.Real` | `X-Real-IP` |
| `IpSource.Cloudflare` | `CF-Connecting-IP` |
| `IpSource.Direct` | Socket remote address |

## Custom Key Generator

Rate limit by something other than IP:

```scala
// Rate limit by API key
val options = RateLimiterMiddleware.Options(
  limit = RateLimiterMiddleware.RateLimit(100, 1.minute),
  keyGenerator = request =>
    Future.successful(request.header("x-api-key").getOrElse(request.ip)),
)
```

## Custom Store

Implement `RateLimitStore` for a persistent backend (e.g., Redis):

```scala
trait RateLimitStore {
  def increment(key: String, window: Duration): Future[(Int, Int)]
  def reset(key: String): Future[Unit]
  def cleanup(): Future[Unit]
}
```

The built-in `InMemoryStore` runs cleanup every 24 hours.
