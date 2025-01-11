---
title: Security
layout: default
parent: Middleware
grand_parent: API Reference
nav_order: 2
---

# Security Middleware

The Security middleware adds essential HTTP security headers to your responses to help protect against common web vulnerabilities.

## Quick Start

```scala
import io.github.edadma.apion._

// Use default security settings
server.use(SecurityMiddleware())

// Or customize security options
server.use(SecurityMiddleware(SecurityMiddleware.Options(
  contentSecurityPolicy = true,
  frameguard = true,
  xssFilter = true,
  noSniff = true,
  referrerPolicy = true
)))
```

## Configuration Options

The middleware supports extensive configuration through the `Options` case class:

```scala
case class Options(
    // Content Security Policy
    contentSecurityPolicy: Boolean = true,
    cspDirectives: Map[String, String] = Map(
        "default-src"               -> "'self'",
        "base-uri"                  -> "'self'",
        "font-src"                  -> "'self' https: data:",
        "form-action"               -> "'self'",
        "frame-ancestors"           -> "'self'",
        "img-src"                   -> "'self' data: https:",
        "object-src"                -> "'none'",
        "script-src"                -> "'self'",
        "script-src-attr"           -> "'none'",
        "style-src"                 -> "'self' https: 'unsafe-inline'",
        "upgrade-insecure-requests" -> ""
    ),

    // Cross-Origin options
    crossOriginEmbedderPolicy: Boolean = true,
    crossOriginOpenerPolicy: Boolean = true,
    crossOriginResourcePolicy: Boolean = true,

    // DNS Prefetch Control
    dnsPrefetchControl: Boolean = true,

    // Expect-CT
    expectCt: Boolean = true,
    expectCtMaxAge: Int = 86400,
    expectCtEnforce: Boolean = true,

    // Frame protection
    frameguard: Boolean = true,
    frameguardAction: String = "DENY",

    // HSTS
    hsts: Boolean = true,
    hstsMaxAge: Int = 15552000,     // 180 days
    hstsIncludeSubDomains: Boolean = true,
    hstsPreload: Boolean = false,

    // IE protections
    ieNoOpen: Boolean = true,

    // MIME type sniffing protection
    noSniff: Boolean = true,

    // Origin-Agent-Cluster
    originAgentCluster: Boolean = true,

    // Cross-domain policies
    permittedCrossDomainPolicies: String = "none",

    // Referrer Policy
    referrerPolicy: Boolean = true,
    referrerPolicyDirective: String = "no-referrer",

    // XSS Protection
    xssFilter: Boolean = true,
    xssFilterMode: String = "1; mode=block"
)
```

## Security Headers

Here's what each security header does:

### Content Security Policy
Controls which resources the browser can load:

```scala
// Default CSP
server.use(SecurityMiddleware())

// Custom CSP
server.use(SecurityMiddleware(Options(
  contentSecurityPolicy = true,
  cspDirectives = Map(
    "default-src" -> "'self'",
    "script-src" -> "'self' 'unsafe-inline'",
    "style-src" -> "'self' 'unsafe-inline'",
    "img-src" -> "'self' data: https:",
    "connect-src" -> "'self' https://api.example.com"
  )
)))
```

### Cross-Origin Policies
Protects against cross-origin attacks:

```scala
server.use(SecurityMiddleware(Options(
  crossOriginEmbedderPolicy = true,   // require-corp
  crossOriginOpenerPolicy = true,     // same-origin
  crossOriginResourcePolicy = true    // same-origin
)))
```

### Frame Protection
Controls how your site can be embedded in frames:

```scala
server.use(SecurityMiddleware(Options(
  frameguard = true,
  frameguardAction = "DENY"  // or "SAMEORIGIN"
)))
```

### HSTS (HTTP Strict Transport Security)
Forces HTTPS connections:

```scala
server.use(SecurityMiddleware(Options(
  hsts = true,
  hstsMaxAge = 31536000,         // 1 year
  hstsIncludeSubDomains = true,
  hstsPreload = true
)))
```

## Preset Configurations

### Essential Security
Basic security headers for most applications:

```scala
server.use(SecurityMiddleware.essential())
```

This enables:
- Content Security Policy
- Frame protection
- HSTS
- MIME type sniffing protection
- XSS protection
- Referrer Policy

### API Security
Tailored for API servers:

```scala
server.use(SecurityMiddleware.api())
```

This configuration:
- Disables browser-specific protections
- Enables strict CORS policies
- Sets restrictive CSP
- Enables cross-origin protections

## Common Patterns

### SPA Configuration
For Single Page Applications:

```scala
server.use(SecurityMiddleware(Options(
  cspDirectives = Map(
    "default-src" -> "'self'",
    "script-src" -> "'self' 'unsafe-inline'",
    "style-src" -> "'self' 'unsafe-inline'",
    "connect-src" -> "'self' https://api.example.com",
    "img-src" -> "'self' data: https:",
    "font-src" -> "'self' data: https:",
    "frame-ancestors" -> "'none'"
  ),
  frameguard = true,
  frameguardAction = "DENY",
  hstsPreload = true
)))
```

### API Gateway Configuration
For API gateways:

```scala
server.use(SecurityMiddleware(Options(
  cspDirectives = Map(
    "default-src" -> "'none'",
    "frame-ancestors" -> "'none'"
  ),
  crossOriginEmbedderPolicy = true,
  crossOriginOpenerPolicy = true,
  crossOriginResourcePolicy = true,
  frameguard = false,
  ieNoOpen = false,
  xssFilter = false
)))
```

## Testing Security Headers

Example test cases:

```scala
"SecurityMiddleware" - {
  "should set basic security headers" in {
    fetch(s"http://localhost:$port/test")
      .toFuture
      .map { response =>
        val headers = response.headers
        
        headers.has("content-security-policy") shouldBe true
        headers.has("x-frame-options") shouldBe true
        headers.has("strict-transport-security") shouldBe true
        headers.has("x-content-type-options") shouldBe true
        headers.has("referrer-policy") shouldBe true
      }
  }

  "should use custom CSP directives" in {
    val customCSP = SecurityMiddleware(Options(
      cspDirectives = Map(
        "default-src" -> "'self'",
        "script-src" -> "'self' 'unsafe-inline'"
      )
    ))

    server.use(customCSP)

    fetch(s"http://localhost:$port/test")
      .toFuture
      .map { response =>
        val csp = response.headers.get("content-security-policy")
        csp should include("default-src 'self'")
        csp should include("script-src 'self' 'unsafe-inline'")
      }
  }
}
```

## Best Practices

1. **Enable HSTS**: Always enable HSTS in production
2. **Restrict CSP**: Use strict CSP directives
3. **Frame Protection**: Use DENY unless frames are needed
4. **XSS Protection**: Keep XSS filter enabled
5. **Testing**: Regularly test security headers

## Security Considerations

1. **SSL/TLS**: Security headers work best with HTTPS
2. **CSP Reporting**: Consider using CSP reporting in production
3. **Header Order**: Headers are applied in middleware order
4. **Browser Support**: Headers have varying browser support

## Performance Impact

The security middleware has minimal performance impact:
- Headers are added through response finalizers
- No request processing or blocking operations
- Headers are computed once per response
- No async operations or I/O

## Troubleshooting

Common issues and solutions:

1. **Content not loading**:
    - Check CSP directives
    - Use browser dev tools to see blocked resources
    - Adjust CSP based on requirements

2. **Frames not working**:
    - Check frameguard settings
    - Adjust frame-ancestors in CSP
    - Consider using SAMEORIGIN instead of DENY

3. **External resources blocked**:
    - Add domains to CSP directives
    - Check connect-src for API calls
    - Verify img-src for external images

4. **HSTS issues**:
    - Ensure SSL/TLS is properly configured
    - Start with shorter max-age
    - Be cautious with preload flag