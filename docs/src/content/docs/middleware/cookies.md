---
title: Cookies
description: Cookie parsing, signing, and JSON cookie support
---

`CookieMiddleware` adds cookie parsing with optional signing (HMAC-SHA256) and JSON cookie support.

## Basic Usage

```scala
server.use(CookieMiddleware())
```

## Configuration

```scala
server.use(CookieMiddleware(CookieMiddleware.Options(
  secret = Some("cookie-secret"),  // Enable signed cookies
  parseJSON = true,                // Parse JSON-encoded cookies
)))
```

## Presets

```scala
// Signed cookies
server.use(CookieMiddleware(CookieMiddleware.Presets.signed("my-secret")))

// JSON cookies
server.use(CookieMiddleware(CookieMiddleware.Presets.json))
```

## Reading Cookies

### Basic Cookies

```scala
// Single cookie
request.cookie("session")  // Option[String]

// All cookies
request.cookies  // Map[String, String]
```

### Signed Cookies

With a secret configured, verify signed cookies:

```scala
request.getSignedCookie("auth")  // Option[String] — None if tampered
```

### JSON Cookies

Parse cookies containing JSON values:

```scala
case class Settings(theme: String, lang: String) derives JsonDecoder

request.getJsonCookie[Settings]("prefs")  // Option[Settings]
```

## Setting Cookies

Use the response extension methods:

```scala
// Simple cookie
Response.text("OK").withCookie("session", "abc123")

// Cookie with attributes
Response.text("OK").withCookie(
  name = "session",
  value = "abc123",
  maxAge = Some(3600),
  path = Some("/"),
  secure = true,
  httpOnly = true,
  sameSite = Some("Strict"),
)

// Using a Cookie object
Response.text("OK").withCookie(Cookie(
  name = "session",
  value = "abc123",
  maxAge = Some(3600),
  httpOnly = true,
))

// Clear a cookie
Response.text("Logged out").clearCookie("session")
```

## Signing Cookies

Create a signed cookie with HMAC-SHA256:

```scala
request.signCookie("auth", "user123") match {
  case Some(cookie) => Response.text("OK").withCookie(cookie)
  case None         => "Signing failed".asText(500)
}
```

## Cookie Attributes

The `Cookie` case class supports all standard attributes:

```scala
case class Cookie(
  name: String,
  value: String,
  domain: Option[String] = None,
  path: Option[String] = None,
  maxAge: Option[Int] = None,         // Seconds
  expires: Option[Instant] = None,
  secure: Boolean = false,
  httpOnly: Boolean = false,
  sameSite: Option[String] = None,    // "Strict", "Lax", or "None"
)
```
