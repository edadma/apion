---
title: Security Headers
description: Comprehensive security headers middleware
---

`SecurityMiddleware` adds security-related HTTP headers to protect against common web vulnerabilities.

## Basic Usage

```scala
// All security headers with defaults
server.use(SecurityMiddleware())
```

## Configuration

```scala
server.use(SecurityMiddleware(SecurityMiddleware.Options(
  // Content Security Policy
  contentSecurityPolicy = true,
  cspDirectives = Map(
    "default-src" -> "'self'",
    "script-src" -> "'self'",
    "img-src" -> "'self' data:",
    "style-src" -> "'self' 'unsafe-inline'",
  ),

  // Frame protection
  frameguard = true,
  frameguardAction = "DENY",  // or "SAMEORIGIN"

  // HSTS
  hsts = true,
  hstsMaxAge = 15552000,       // 180 days
  hstsIncludeSubDomains = true,
  hstsPreload = false,

  // Cross-Origin policies
  crossOriginEmbedderPolicy = true,
  crossOriginOpenerPolicy = true,
  crossOriginResourcePolicy = true,

  // Other protections
  noSniff = true,
  xssFilter = true,
  xssFilterMode = "1; mode=block",
  referrerPolicy = true,
  referrerPolicyDirective = "no-referrer",
  dnsPrefetchControl = true,
  ieNoOpen = true,
  originAgentCluster = true,
  permittedCrossDomainPolicies = "none",
  expectCt = true,
  expectCtMaxAge = 86400,
  expectCtEnforce = true,
)))
```

## Options Reference

| Option | Default | Description |
|--------|---------|-------------|
| `contentSecurityPolicy` | `true` | Enable CSP header |
| `cspDirectives` | `default-src 'self'` | CSP directive map |
| `frameguard` | `true` | X-Frame-Options |
| `frameguardAction` | `"DENY"` | DENY or SAMEORIGIN |
| `hsts` | `true` | Strict-Transport-Security |
| `hstsMaxAge` | `15552000` | HSTS max-age (seconds) |
| `hstsIncludeSubDomains` | `true` | Include subdomains in HSTS |
| `hstsPreload` | `false` | HSTS preload flag |
| `crossOriginEmbedderPolicy` | `true` | COEP header |
| `crossOriginOpenerPolicy` | `true` | COOP header |
| `crossOriginResourcePolicy` | `true` | CORP header |
| `noSniff` | `true` | X-Content-Type-Options: nosniff |
| `xssFilter` | `true` | X-XSS-Protection |
| `referrerPolicy` | `true` | Referrer-Policy |
| `referrerPolicyDirective` | `"no-referrer"` | Referrer policy value |
| `dnsPrefetchControl` | `true` | X-DNS-Prefetch-Control |
| `expectCt` | `true` | Expect-CT header |

## Presets

### Essential

A minimal set of important security headers:

```scala
server.use(SecurityMiddleware.essential())
```

### API

Security headers optimized for JSON API servers:

```scala
server.use(SecurityMiddleware.api())
```

## Headers Applied

When fully enabled, the following headers are set:

- `Content-Security-Policy`
- `Cross-Origin-Embedder-Policy: require-corp`
- `Cross-Origin-Opener-Policy: same-origin`
- `Cross-Origin-Resource-Policy: same-origin`
- `Strict-Transport-Security`
- `X-Frame-Options`
- `X-Content-Type-Options: nosniff`
- `X-XSS-Protection`
- `Referrer-Policy`
- `X-DNS-Prefetch-Control: off`
- `X-Download-Options: noopen`
- `Origin-Agent-Cluster: ?1`
- `X-Permitted-Cross-Domain-Policies: none`
- `Expect-CT`
