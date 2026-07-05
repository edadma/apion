---
title: Response
description: Building HTTP responses — JSON, text, binary, and streaming
---

The `Response` is an immutable case class representing an HTTP response.

## Structure

```scala
case class Response(
  status: Int = 200,
  headers: ResponseHeaders = ResponseHeaders.empty,
  body: ResponseBody = EmptyBody,
)
```

## Factory Methods

The `Response` companion object provides factory methods for common response types:

### JSON

```scala
case class User(name: String) derives JsonEncoder

Response.json(User("Alice"))              // 200 with JSON body
Response.json(User("Alice"), status = 201) // 201 Created
Response.json(Map("error" -> "oops"), status = 400)
```

### Text

```scala
Response.text("Hello World")              // 200 with text/plain
Response.text("Created", status = 201)
```

### Binary

```scala
Response.binary(buffer)                   // 200 with application/octet-stream
Response.binary(buffer, status = 200)
```

### Stream

```scala
val fileStream = fs.createReadStream("video.mp4")
Response.stream(fileStream, additionalHeaders = Seq(
  "Content-Type" -> "video/mp4"
))
```

### No Content

```scala
Response.noContent()  // 204 with empty body
```

## Response Body Types

```scala
sealed trait ResponseBody

case class StringBody(content: String, data: Buffer) extends ResponseBody
case class BufferBody(content: Buffer) extends ResponseBody
case class ReadableStreamBody(readable: ReadableStream) extends ResponseBody
case object EmptyBody extends ResponseBody
```

## Headers

`ResponseHeaders` is a case-insensitive, multi-value header container:

```scala
// Add a single header
response.copy(headers = response.headers.add("X-Custom", "value"))

// Add multiple headers
response.copy(headers = response.headers.addAll(Seq(
  "Cache-Control" -> "no-cache",
  "X-Request-Id" -> "abc123"
)))

// Read a header
response.headers.get("content-type")    // Option[String]
response.headers.contains("x-custom")   // Boolean

// Remove a header
response.copy(headers = response.headers.remove("x-custom"))
```

Headers are normalized with smart casing — `content-type` becomes `Content-Type`, common acronyms like `API`, `JWT`, `CORS` are uppercased correctly.

### Default Headers

Factory methods set only the response-specific headers (`Content-Type`,
`Content-Length`). The server stamps its default headers — and a fresh `Date` — on
every response as it's sent, without overwriting any header the response already set.

By default these are:

| Header | Value |
|--------|-------|
| `Server` | `Apion` |
| `Cache-Control` | `no-store, no-cache, must-revalidate, max-age=0` |
| `Pragma` | `no-cache` |
| `Expires` | `0` |
| `X-Powered-By` | `Apion` |
| `Date` | RFC 1123 formatted current time |

Defaults are per-server, configured on `ServerConfig` (no global mutable state):

```scala
val server = Server(ServerConfig(
  defaultHeaders = Seq(
    "Server"       -> "MyApp",
    "X-Powered-By" -> "MyApp",
  ),
))
```

## Cookies

Set cookies on responses using extension methods:

```scala
// Simple cookie
response.withCookie("session", "abc123")

// Cookie with attributes
response.withCookie(
  name = "session",
  value = "abc123",
  maxAge = Some(3600),      // 1 hour
  path = Some("/"),
  secure = true,
  httpOnly = true,
  sameSite = Some("Strict")
)

// Using a Cookie object
response.withCookie(Cookie(
  name = "session",
  value = "abc123",
  maxAge = Some(3600),
  httpOnly = true
))

// Clear a cookie
response.clearCookie("session")
```

## Result Extension Methods

Add headers to a `Future[Result]`:

```scala
"Hello".asText.withHeader("X-Custom", "value")

data.asJson.withHeaders(
  "X-Request-Id" -> requestId,
  "X-Duration" -> s"${duration}ms"
)
```

## Reading the Body

For testing or inspection:

```scala
response.bodyText  // String content of the response body
```
