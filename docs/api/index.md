---
title: API Reference
layout: default
nav_order: 2
has_children: true
---

# API Documentation

## Core Types

### Request

The `Request` class represents an HTTP request with immutable properties:

```scala
case class Request(
    method: String,          // HTTP method (GET, POST, etc.)
    url: String,             // Full request URL
    path: String,            // URL path component
    headers: Map[String, String], // Request headers (case-insensitive)
    params: Map[String, String],  // Path parameters
    query: Map[String, String],   // Query parameters
    context: Map[String, Any],    // Request context for middleware
    cookies: Map[String, String], // Request cookies
    finalizers: List[Finalizer],  // Response transform functions
    basePath: String = ""         // Base path from router mounting
)
```

#### Methods

- `body: Future[Buffer]` - Get raw request body as Buffer
- `text: Future[String]` - Get request body as text
- `json[T: JsonDecoder]: Future[Option[T]]` - Parse body as JSON into type T
- `form: Future[Map[String, String]]` - Parse body as form data
- `header(name: String): Option[String]` - Get header value
- `cookie(name: String): Option[String]` - Get cookie value

### Response

The `Response` class represents an HTTP response:

```scala
case class Response(
    status: Int = 200,
    headers: ResponseHeaders = ResponseHeaders.empty,
    body: ResponseBody = EmptyBody
)
```

#### Companion Object Methods

- `text(content: String, status: Int = 200): Response` - Create text response
- `json[A: JsonEncoder](data: A, status: Int = 200): Response` - Create JSON response
- `binary(content: Buffer, status: Int = 200): Response` - Create binary response
- `noContent(): Response` - Create 204 No Content response

#### Extension Methods

```scala
response.withCookie(cookie: Cookie): Response
response.withCookie(
    name: String,
    value: String,
    domain: Option[String] = None,
    path: Option[String] = None,
    maxAge: Option[Int] = None,
    expires: Option[Instant] = None,
    secure: Boolean = false,
    httpOnly: Boolean = false,
    sameSite: Option[String] = None
): Response
```

### Handler

The core request processing type:

```scala
type Handler = Request => Future[Result]

sealed trait Result
case class Continue(request: Request) extends Result    // Continue processing
case class Complete(response: Response) extends Result  // End with response
case class Fail(error: ServerError) extends Result     // Propagate error
case object Skip extends Result                        // Try next route
```

## Router

The `Router` class provides routing functionality:

```scala
class Router {
  def use(handler: Handler): Router                     // Add middleware
  def use(path: String, handler: Handler): Router       // Mount path-specific middleware
  def use(path: String, router: Router): Router         // Mount subrouter
  
  def get(path: String, handlers: Handler*): Router     // Add GET route
  def post(path: String, handlers: Handler*): Router    // Add POST route
  def put(path: String, handlers: Handler*): Router     // Add PUT route
  def delete(path: String, handlers: Handler*): Router  // Add DELETE route
  def patch(path: String, handlers: Handler*): Router   // Add PATCH route
}
```

## Built-in Middleware

### Authentication

```scala
object AuthMiddleware {
  case class Config(
    secretKey: String,
    requireAuth: Boolean = true,
    excludePaths: Set[String] = Set.empty,
    tokenRefreshThreshold: Long = 300,
    maxTokenLifetime: Long = 86400,
    issuer: String = "apion-auth",
    audience: Option[String] = None
  )

  def apply(config: Config): Handler
}
```

### Compression

```scala
object CompressionMiddleware {
  case class Options(
    level: Int = 6,
    threshold: Int = 1024,
    memLevel: Int = 8,
    windowBits: Int = 15,
    brotliQuality: Int = 11,
    brotliBlockSize: Int = 4096,
    filter: Request => Boolean = _ => true,
    encodings: List[String] = List("br", "gzip", "deflate")
  )

  def apply(options: Options = Options()): Handler
}
```

### CORS

```scala
object CorsMiddleware {
  case class Options(
    origin: Origin = Origin.Any,
    methods: Set[String] = Set("GET", "HEAD", "PUT", "PATCH", "POST", "DELETE"),
    allowedHeaders: Set[String] = Set("Content-Type", "Authorization"),
    exposedHeaders: Set[String] = Set.empty,
    credentials: Boolean = false,
    maxAge: Option[Int] = Some(86400),
    preflightSuccessStatus: Int = 204,
    optionsSuccessStatus: Int = 204
  )

  def apply(options: Options = Options()): Handler
}
```

### Cookie

```scala
object CookieMiddleware {
  case class Options(
    secret: Option[String] = None,
    parseJSON: Boolean = false
  )

  def apply(options: Options = Options()): Handler
}
```

### Logging

```scala
object LoggingMiddleware {
  case class Options(
    format: String = Format.Dev,
    immediate: Boolean = false,
    skip: Request => Boolean = _ => false,
    handler: String => Unit = println,
    debug: Boolean = false
  )

  object Format {
    val Combined = ":remote-addr - :remote-user [:date] \":method :url HTTP/:http-version\" :status :res[content-length] \":referrer\" \":user-agent\""
    val Common = ":remote-addr - :remote-user [:date] \":method :url HTTP/:http-version\" :status :res[content-length]"
    val Dev = ":method :url :status :response-time ms - :res[content-length]"
    val Short = ":remote-addr :remote-user :method :url HTTP/:http-version :status :res[content-length] - :response-time ms"
    val Tiny = ":method :url :status :res[content-length] - :response-time ms"
  }

  def apply(options: Options = Options()): Handler
}
```

### Rate Limiter

```scala
object RateLimiterMiddleware {
  case class RateLimit(
    maxRequests: Int,
    window: Duration,
    burst: Int = 0,
    skipFailedRequests: Boolean = false,
    skipSuccessfulRequests: Boolean = false,
    statusCode: Int = 429,
    errorMessage: String = "Too many requests",
    headers: Boolean = true
  )

  case class Options(
    limit: RateLimit,
    store: RateLimitStore = new InMemoryStore,
    ipSources: Seq[IpSource.Value] = Seq(IpSource.Forward, IpSource.Real, IpSource.Direct),
    keyGenerator: Request => Future[String] = defaultKeyGenerator,
    skip: Request => Boolean = _ => false
  )

  def apply(options: Options = Options()): Handler
}
```

### Security

```scala
object SecurityMiddleware {
  case class Options(
    contentSecurityPolicy: Boolean = true,
    cspDirectives: Map[String, String] = Map(
      "default-src" -> "'self'",
      "base-uri" -> "'self'",
      "font-src" -> "'self' https: data:",
      "form-action" -> "'self'",
      "frame-ancestors" -> "'self'",
      "img-src" -> "'self' data: https:",
      "object-src" -> "'none'",
      "script-src" -> "'self'",
      "script-src-attr" -> "'none'",
      "style-src" -> "'self' https: 'unsafe-inline'",
      "upgrade-insecure-requests" -> ""
    ),
    crossOriginEmbedderPolicy: Boolean = true,
    crossOriginOpenerPolicy: Boolean = true,
    crossOriginResourcePolicy: Boolean = true,
    dnsPrefetchControl: Boolean = true,
    expectCt: Boolean = true,
    frameguard: Boolean = true,
    hsts: Boolean = true,
    ieNoOpen: Boolean = true,
    noSniff: Boolean = true,
    referrerPolicy: Boolean = true
  )

  def apply(options: Options = Options()): Handler
}
```

### Static Files

```scala
object StaticMiddleware {
  case class Options(
    index: Boolean = true,
    dotfiles: String = "ignore",
    etag: Boolean = true,
    maxAge: Int = 0,
    redirect: Boolean = true,
    fallthrough: Boolean = true
  )

  def apply(root: String, options: Options = Options()): Handler
}
```

## DSL Extensions

### Response DSL

```scala
// Extension methods on any type with JsonEncoder
def asJson: Future[Complete]
def asJson(status: Int): Future[Complete]

// Extension methods on String
def asText: Future[Complete]
def asText(status: Int): Future[Complete]

// Extension methods on Buffer
def asBinary: Future[Complete]
def asBinary(status: Int): Future[Complete]

// Common responses
val notFound: Future[Complete]
val badRequest: Future[Complete]
val serverError: Future[Complete]

// Created with location
def created[A: JsonEncoder](data: A, location: Option[String]): Future[Result]
```

### Cookie DSL

```scala
// Request extensions
def getSignedCookie(name: String): Option[String]
def getJsonCookie[A: JsonDecoder](name: String): Option[A]
def signCookie(name: String, value: String): Option[Cookie]
```