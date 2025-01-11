---
title: CORS
layout: default
parent: Middleware
grand_parent: API Reference
nav_order: 3
---

# CORS Middleware

The CORS middleware handles Cross-Origin Resource Sharing (CORS) headers to control which domains can access your API.

## Quick Start

```scala
import io.github.edadma.apion._
import CorsMiddleware._

// Use default CORS settings (permissive)
server.use(CorsMiddleware())

// Or customize CORS options
server.use(CorsMiddleware(Options(
  origin = Origin.Multiple(Set("https://app.example.com", "https://admin.example.com")),
  methods = Set("GET", "POST", "PUT", "DELETE"),
  allowedHeaders = Set("Content-Type", "Authorization"),
  credentials = true
)))

// Development preset (permissive settings)
server.use(CorsMiddleware.development())

// Production preset (strict settings)
server.use(CorsMiddleware.production(Set(
  "https://app.example.com",
  "https://admin.example.com"
)))
```

## Configuration Options

```scala
case class Options(
    // Origin handling
    origin: Origin = Origin.Any,
    
    // Allowed methods
    methods: Set[String] = Set("GET", "HEAD", "PUT", "PATCH", "POST", "DELETE"),
    
    // Headers
    allowedHeaders: Set[String] = Set("Content-Type", "Authorization"),
    exposedHeaders: Set[String] = Set.empty,
    
    // Credentials
    credentials: Boolean = false,
    
    // Preflight
    maxAge: Option[Int] = Some(86400), // 24 hours
    preflightSuccessStatus: Int = 204,
    optionsSuccessStatus: Int = 204
)
```

## Origin Configuration

The middleware supports multiple ways to specify allowed origins:

```scala
sealed trait Origin
object Origin {
    case object Any                           // "*"
    case class Single(origin: String)         // Single origin
    case class Multiple(origins: Set[String]) // Multiple origins
    case class Pattern(regex: String)         // Regex pattern
    case class Function(f: String => Boolean) // Custom validation
    
    // Helper methods
    def apply(origin: String): Origin          = Single(origin)
    def apply(origins: Set[String]): Origin    = Multiple(origins)
    def pattern(regex: String): Origin         = Pattern(regex)
    def validate(f: String => Boolean): Origin = Function(f)
}
```

### Examples

```scala
// Allow any origin
CorsMiddleware(Options(
  origin = Origin.Any
))

// Single origin
CorsMiddleware(Options(
  origin = Origin("https://example.com")
))

// Multiple origins
CorsMiddleware(Options(
  origin = Origin(Set(
    "https://app.example.com",
    "https://admin.example.com"
  ))
))

// Pattern matching
CorsMiddleware(Options(
  origin = Origin.pattern("""https:\/\/.*\.example\.com""")
))

// Custom validation
CorsMiddleware(Options(
  origin = Origin.validate(origin =>
    origin.endsWith(".example.com") && 
    origin.startsWith("https://")
  )
))
```

## Common Configurations

### API Server
```scala
server.use(CorsMiddleware(Options(
  origin = Origin.Multiple(Set(
    "https://app.example.com",
    "https://admin.example.com"
  )),
  methods = Set("GET", "POST", "PUT", "DELETE"),
  allowedHeaders = Set(
    "Content-Type",
    "Authorization",
    "X-Requested-With"
  ),
  exposedHeaders = Set("X-Total-Count"),
  credentials = true,
  maxAge = Some(3600) // 1 hour
)))
```

### Development Server
```scala
server.use(CorsMiddleware(Options(
  origin = Origin.Any,
  credentials = true,
  exposedHeaders = Set("*"),
  maxAge = Some(86400)
)))
```

### Single Page Application (SPA)
```scala
server.use(CorsMiddleware(Options(
  origin = Origin.Single("https://app.example.com"),
  methods = Set("GET", "POST"),
  allowedHeaders = Set(
    "Content-Type",
    "Authorization"
  ),
  credentials = true,
  maxAge = Some(86400)
)))
```

## Request Flow

### Simple Requests
For basic requests (GET, POST, HEAD), the middleware:
1. Validates the Origin header
2. Adds appropriate CORS headers to response

### Preflight Requests
For OPTIONS requests, the middleware:
1. Validates the Origin
2. Checks Access-Control-Request-Method
3. Validates Access-Control-Request-Headers
4. Responds with appropriate CORS headers

## Response Headers

The middleware sets these headers based on configuration:

```
Access-Control-Allow-Origin: [origin]
Access-Control-Allow-Methods: GET, POST, ...
Access-Control-Allow-Headers: Content-Type, ...
Access-Control-Expose-Headers: X-Custom, ...
Access-Control-Allow-Credentials: true
Access-Control-Max-Age: 86400
Vary: Origin
```

## Testing CORS Configuration

Example tests:

```scala
"CORSMiddleware" - {
  "should handle preflight requests" in {
    val options = FetchOptions(
      method = "OPTIONS",
      headers = js.Dictionary(
        "Origin" -> "http://example.com",
        "Access-Control-Request-Method" -> "POST",
        "Access-Control-Request-Headers" -> "Content-Type"
      )
    )

    fetch(s"http://localhost:$port/api/test", options)
      .toFuture
      .map { response =>
        response.status shouldBe 204
        
        val headers = response.headers
        headers.get("Access-Control-Allow-Origin") shouldBe "http://example.com"
        headers.get("Access-Control-Allow-Methods") should include("POST")
        headers.get("Access-Control-Allow-Headers") should include("Content-Type")
      }
  }

  "should block disallowed origins" in {
    val options = FetchOptions(
      headers = js.Dictionary(
        "Origin" -> "http://evil.com"
      )
    )

    fetch(s"http://localhost:$port/api/test", options)
      .toFuture
      .map { response =>
        response.headers.has("Access-Control-Allow-Origin") shouldBe false
      }
  }
}
```

## Best Practices

1. **Origin Configuration**
    - Use explicit origins in production
    - Avoid `Origin.Any` except in development
    - Use HTTPS origins only in production

2. **Credentials**
    - Enable only if needed
    - Cannot be used with `Origin.Any`
    - Requires explicit origin configuration

3. **Headers**
    - Only expose necessary headers
    - Minimize allowed headers list
    - Consider security implications

4. **Preflight Caching**
    - Set appropriate maxAge
    - Consider browser limits
    - Balance security vs. performance

## Security Considerations

1. **Origin Validation**
    - Always validate origins in production
    - Use exact matches when possible
    - Consider subdomain requirements

2. **Credentials**
    - Enables cookies and auth headers
    - Increases security requirements
    - Must use specific origins

3. **Headers**
    - Minimize exposed headers
    - Validate custom headers
    - Consider header implications

4. **Methods**
    - Only allow needed methods
    - Consider method implications
    - Validate method requirements

## Troubleshooting

Common issues and solutions:

1. **Requests Blocked**
    - Check Origin configuration
    - Verify allowed methods
    - Check required headers
    - Enable credentials if needed

2. **Credentials Issues**
    - Cannot use `Origin.Any`
    - Must set `credentials: true`
    - Check browser configuration

3. **Preflight Failures**
    - Check OPTIONS handling
    - Verify allowed headers
    - Check method permissions

4. **Header Issues**
    - Check allowedHeaders list
    - Verify exposedHeaders
    - Check header case sensitivity