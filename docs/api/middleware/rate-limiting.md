---
title: Rate Limiting
layout: default
parent: Middleware
grand_parent: API Reference
nav_order: 4
---

# Rate Limiting Middleware

The Rate Limiting middleware provides request throttling to protect your API from abuse and ensure fair resource usage.

## Quick Start

```scala
import io.github.edadma.apion._
import scala.concurrent.duration._

// Use default rate limiting (60 requests per minute)
server.use(RateLimiterMiddleware())

// Customize rate limiting
server.use(RateLimiterMiddleware(Options(
  limit = RateLimit(
    maxRequests = 100,   // 100 requests
    window = 1.minute,   // per minute
    burst = 10          // allow 10 extra requests
  )
)))

// Common configurations
server.use(RateLimiterMiddleware.moderate(100, 1.minute))  // 100/minute
server.use(RateLimiterMiddleware.strict(50, 1.minute))     // 50/minute, no burst
server.use(RateLimiterMiddleware.flexible(200, 1.minute))  // 200/minute with burst
```

## Configuration

### Rate Limit Options

```scala
case class RateLimit(
    maxRequests: Int,              // Maximum requests allowed in window
    window: Duration,              // Time window
    burst: Int = 0,               // Additional burst allowance
    skipFailedRequests: Boolean = false,    // Don't count failed requests
    skipSuccessfulRequests: Boolean = false, // Don't count successful requests
    statusCode: Int = 429,        // Status code for rate limit errors
    errorMessage: String = "Too many requests. Please try again later.",
    headers: Boolean = true       // Send rate limit headers
)
```

### Middleware Options

```scala
case class Options(
    limit: RateLimit = RateLimit(60, 1.minute),  // Rate limit configuration
    store: RateLimitStore = new InMemoryStore,   // Storage backend
    ipSources: Seq[IpSource.Value] = Seq(        // IP resolution order
      IpSource.Forward,  // X-Forwarded-For
      IpSource.Real,     // X-Real-IP
      IpSource.Direct    // Socket IP
    ),
    keyGenerator: Request => Future[String] = defaultKeyGenerator,  // Rate limit key
    skip: Request => Boolean = _ => false,  // Skip rate limiting
    onRateLimited: (Request, RateLimitError, Options) => Future[Response] = defaultErrorHandler
)
```

## Storage Options

### In-Memory Store (Default)
```scala
// Basic in-memory storage
val store = new InMemoryStore()

server.use(RateLimiterMiddleware(Options(
  store = store
)))
```

### Custom Store Implementation
```scala
class RedisStore extends RateLimitStore {
  override def increment(key: String, window: Duration): Future[(Int, Int)] = {
    // Implement Redis-based rate limiting
    // Returns (current count, remaining requests)
    redisClient.increment(key, window)
  }

  override def reset(key: String): Future[Unit] = {
    // Reset counter for key
    redisClient.delete(key)
  }

  override def cleanup(): Future[Unit] = {
    // Clean up expired entries
    redisClient.cleanup()
  }
}

// Use custom store
server.use(RateLimiterMiddleware(Options(
  store = new RedisStore()
)))
```

## Rate Limit Keys

### Default Key Generation
By default, rate limits are applied per IP address:

```scala
// Default key generator uses client IP
val defaultKeyGenerator: Request => Future[String] = request => {
  // Try different IP sources in order
  Future.successful(getClientIP(request))
}
```

### Custom Key Generation
You can customize how rate limit keys are generated:

```scala
// Rate limit by API key
val apiKeyGenerator: Request => Future[String] = request => {
  request.header("X-API-Key") match {
    case Some(key) => Future.successful(s"apikey:$key")
    case None => Future.successful(getClientIP(request))
  }
}

// Rate limit by user ID
val userKeyGenerator: Request => Future[String] = request => {
  request.context.get("userId") match {
    case Some(id) => Future.successful(s"user:$id")
    case None => Future.successful(getClientIP(request))
  }
}

// Use custom key generator
server.use(RateLimiterMiddleware(Options(
  keyGenerator = apiKeyGenerator
)))
```

## Headers

The middleware adds these response headers:

```
X-RateLimit-Limit: 100       // Max requests per window
X-RateLimit-Remaining: 99    // Remaining requests
X-RateLimit-Reset: 1234567   // Window reset timestamp
X-RateLimit-Used: 1          // Requests used in current window
Retry-After: 60              // Seconds until next request allowed
```

## Common Patterns

### API Rate Limiting
```scala
// Different limits for public and authenticated routes
val publicLimiter = RateLimiterMiddleware(Options(
  limit = RateLimit(
    maxRequests = 30,
    window = 1.minute
  )
))

val authLimiter = RateLimiterMiddleware(Options(
  limit = RateLimit(
    maxRequests = 100,
    window = 1.minute,
    burst = 20
  ),
  keyGenerator = request =>
    Future.successful(request.header("Authorization").getOrElse("ip:" + request.ip))
))

// Apply limits to different routes
server
  .use("/public", Router()
    .use(publicLimiter)
    .get("/data", publicHandler))
  .use("/api", Router()
    .use(authLimiter)
    .get("/data", apiHandler))
```

### Tiered Rate Limiting
```scala
// Different limits based on user tier
def tierLimiter(tier: String): Handler = {
  val limits = Map(
    "free" -> RateLimit(100, 1.hour),
    "pro" -> RateLimit(1000, 1.hour),
    "enterprise" -> RateLimit(10000, 1.hour)
  )

  RateLimiterMiddleware(Options(
    limit = limits.getOrElse(tier, limits("free")),
    keyGenerator = request => Future.successful(
      request.context.get("userId").map(id => s"$tier:$id")
        .getOrElse(request.ip)
    )
  ))
}

// Apply tier-specific limits
server.use { request =>
  request.context.get("userTier") match {
    case Some(tier: String) => tierLimiter(tier)(request)
    case None => tierLimiter("free")(request)
  }
}
```

### Burst Handling
```scala
// Allow bursts for specific endpoints
val burstLimit = RateLimit(
  maxRequests = 100,
  window = 1.minute,
  burst = 50,  // Allow 50 extra requests
  skipFailedRequests = true  // Don't count failures
)

server.use(RateLimiterMiddleware(Options(
  limit = burstLimit,
  skip = request => request.path.startsWith("/webhooks")
)))
```

## Error Handling

### Custom Error Responses
```scala
case class CustomError(
    error: String,
    limit: Int,
    remaining: Int,
    reset: Long
) derives JsonEncoder

def customErrorHandler(
    request: Request,
    error: RateLimitError,
    options: Options
): Future[Response] = {
  CustomError(
    error = "Rate limit exceeded",
    limit = error.limit,
    remaining = error.remaining,
    reset = error.reset
  ).asJson(429)
}

server.use(RateLimiterMiddleware(Options(
  onRateLimited = customErrorHandler
)))
```

## Testing

Example tests for rate limiting:

```scala
"RateLimiterMiddleware" - {
  "should enforce rate limits" in {
    // Configure tight limits for testing
    val limiter = RateLimiterMiddleware(Options(
      limit = RateLimit(2, 1.second)
    ))

    server.use(limiter)

    for {
      // First request succeeds
      r1 <- fetch(s"http://localhost:$port/test").toFuture
      // Second request succeeds
      r2 <- fetch(s"http://localhost:$port/test").toFuture
      // Third request fails
      r3 <- fetch(s"http://localhost:$port/test").toFuture
    } yield {
      r1.status shouldBe 200
      r2.status shouldBe 200
      r3.status shouldBe 429
    }
  }

  "should include rate limit headers" in {
    val limiter = RateLimiterMiddleware()
    server.use(limiter)

    fetch(s"http://localhost:$port/test")
      .toFuture
      .map { response =>
        response.headers.has("x-ratelimit-limit") shouldBe true
        response.headers.has("x-ratelimit-remaining") shouldBe true
        response.headers.has("x-ratelimit-reset") shouldBe true
      }
  }
}
```

## Best Practices

1. **Storage**:
    - Use distributed storage in production
    - Consider persistence requirements
    - Handle storage failures gracefully

2. **Keys**:
    - Choose appropriate identifiers
    - Consider proxy IPs
    - Handle missing identifiers

3. **Windows**:
    - Use appropriate time windows
    - Consider burst requirements
    - Balance security vs. usability

4. **Headers**:
    - Always include rate limit headers
    - Use standard header formats
    - Include reset timestamps

5. **Error Handling**:
    - Provide clear error messages
    - Include retry information
    - Consider response format

## Performance Considerations

1. **Storage Performance**:
    - Use fast storage backends
    - Consider caching strategies
    - Monitor storage load

2. **Key Generation**:
    - Keep key generation fast
    - Cache computed keys
    - Use efficient identifiers

3. **Window Tracking**:
    - Use efficient time windows
    - Clean up expired entries
    - Monitor memory usage

4. **Request Processing**:
    - Minimize blocking operations
    - Handle concurrent requests
    - Consider rate limit overhead