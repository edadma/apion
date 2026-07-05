# API Server Design Document

## Overview
A lightweight, Express-inspired API server framework for Scala.js, providing a familiar developer experience while leveraging Scala's type safety and immutability.

## Core Design Principles
- Express-like chainable API
- Pure functions with immutable types
- Type-safe request/response handling
- Minimal JavaScript interop
- Unified handler/middleware system
- Linear request processing

## Key Components

### Request Processing Pipeline
- Single unified type for all request processors (middleware, routes, error handlers)
- Sequential processing until response generated
- Immutable request objects with mutation via new instances
- Future-based async handling

### Handler Type
```scala
trait ServerError extends Throwable:
  def message: String
  def toResponse: Response          // each error renders itself
case class ValidationError(message: String) extends ServerError
case class AuthError(message: String) extends ServerError
case class NotFoundError(message: String) extends ServerError

// What a single handler returns:
sealed trait Result
case class Continue(request: Request) extends Result
case class Complete(response: Response) extends Result
case class Fail(error: ServerError) extends Result
case object Skip extends Result

type Handler = Request => Future[Result]

// An error handler is just a handler with the current error in hand:
type ErrorHandler = (ServerError, Request) => Future[Result]
```

Return values signify:
- Skip: decline; try the next entry in the pipeline
- Continue(Request): proceed with a (possibly modified) request
- Complete(Response): finish and send this response
- Fail(ServerError): raise an error, diverting to the error handlers

A `Handler` describes one step. Running a whole `Router` yields an internal
`Outcome` — either `Handled(request, response)` (the request is carried out so its
finalizers can run) or `Missed` (nothing matched; the server sends 404). `Outcome`
is deliberately separate from `Result`: handlers speak Continue/Complete/Fail/Skip,
the router as a whole reports Handled/Missed.

### Server Configuration
Fully chainable API supporting:
- Global middleware
- Path-specific middleware
- Route handlers
- Error handlers

Example flow:
- Global logging middleware
- Authentication for specific paths
- Route handlers for business logic
- Not found handler

### Request/Response Models

Request contains:
- HTTP method
- Path information
- Extracted path parameters
- Parsed query parameters
- Headers
- Body (with type-safe parsing)
- Extension context for middleware

Response contains:
- Status code with standard HTTP status text
- Customizable status messages
- Headers
- Body
- Standard content type handling
- Built-in mapping of codes to messages (e.g., 200 → "OK")

### Routing System

#### Route Matching Implementation
A router holds an **ordered pipeline** of entries — middleware, routes, endpoints,
sub-routers, and error handlers — in registration order (Express-style). Handling a
request walks the pipeline in that order; there is no separate compiled dispatch
tree, because middleware and routes are interleaved and their relative order is
significant.

Each registered path is parsed **once, at registration time**, into a list of
segments; matching is then a linear, segment-wise comparison against the request
path:

```scala
sealed trait RouteSegment
case class StaticSegment(value: String) extends RouteSegment  // exact match
case class ParamSegment(name: String) extends RouteSegment    // captures one segment
case object WildcardSegment extends RouteSegment              // matches one segment
```

Matching behaviour:
- Segments are compared left to right; a static segment must match exactly, a
  parameter segment captures the corresponding path segment, a wildcard matches any
  single segment.
- Endpoints require a full match (all segments consumed); sub-routers and
  path-scoped middleware match a prefix and pass the remainder down.
- Paths are normalised by dropping empty segments, so `/users` and `/users/` are
  equivalent.
- The `*` method matches any HTTP method (`all`).

Features:
- Path parameter extraction
- Query string parsing (multi-valued)
- Route grouping and sub-router mounting (including multi-segment mount paths)
- Path prefixing with accumulated `basePath`
- HTTP method handling (GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS/ALL)
- Single-segment wildcards

Note: this is a linear pipeline, not a radix/trie router. For the route counts a
typical service registers, the cost is dominated by middleware anyway; a compiled
index would be a future optimisation, not a current claim.

### Type Safety Features
- Type-safe request extension context via typed keys (`TypedKey[A]` / `Context`) —
  no stringly-typed `Map[String, Any]` casts
- Type-safe body parsing (`request.json[T]`)
- Typed path/query parameter access
- Typed error propagation (`ServerError` subtypes render themselves)

### Error Handling
- Error boundary middleware
- Type-safe error responses
- Standard error formats
- Error propagation control
- Status code mapping

### Middleware Capabilities
- Request modification
- Response generation
- Side effects
- Early termination
- Context addition
- Chained processing

### Performance Considerations
- Immutable request threaded through the pipeline; the streamed body is memoised
  once on the connection, so `copy`-derived requests never re-read it
- Linear pipeline walk (see Route Matching Implementation); paths parsed once at
  registration
- Lightweight, immutable request-extension context keyed by identity
- Scala.js dead-code elimination keeps unused middleware out of the final bundle

## Usage Patterns

### Basic Server Setup
```scala
val server = Server()
  .use(logger)
  .use(cors)
  .use("/api", auth)
  .get("/api/users", getUsers)
  .post("/api/users", createUser)
```

### Middleware Examples
- Logging
- Authentication
- Request parsing
- Error handling
- CORS
- Rate limiting
- Response compression

### Route Organization
- Feature grouping
- Version prefixing
- Resource nesting
- Middleware scoping
- Error boundaries

## Implementation Notes
- Based on Node.js
- Uses ZIO JSON
- Future for effects
- Pure Scala implementation
- Minimal dependencies

## Extension Points
- Custom middleware creation
- Route parameter parsing
- Body parser plugins
- Error handler customization
- Response transformers

## Request Flow and Handler Results

### Request State Management
- Request objects are immutable; there is no mutable "current request" reference.
- The request is threaded functionally through the pipeline: a handler that returns
  `Continue(req)` hands `req` to the next entry; `Skip` passes the request through
  unchanged; `Complete`/`Fail` end the walk.
- Middleware add data by returning `Continue(request.copy(...))` — e.g. attaching a
  value under a `TypedKey`, or registering a finalizer that post-processes the
  response on the way out.

## Pattern Matching System

### Route Pattern Structure
```scala
sealed trait RouteSegment
case class StaticSegment(value: String) extends RouteSegment
case class ParamSegment(name: String) extends RouteSegment
case object WildcardSegment extends RouteSegment
```

### Route Parsing
- Split the registered path into segments (empty segments dropped).
- Classify each as static / parameter / wildcard.
- Parse once, at registration time (not per request).

### Matching Process
1. Split the incoming path into segments.
2. Walk the router's ordered pipeline; for each route/endpoint/sub-router, compare
   its segments against the path.
3. Capture parameters into the request's `params` map.
4. Endpoints require the whole path to be consumed; sub-routers and path-scoped
   middleware match a prefix and pass the remainder down.
5. If nothing in the pipeline produces a response, the outcome is `Missed`.

## Router Implementation

### Core Router Interface
```scala
class Router:
  def use(path: String, router: Router): Router     // mount a sub-router
  def use(handler: Handler): Router                 // add middleware
  def use(path: String, handler: Handler): Router   // path-scoped middleware
  def use(handler: ErrorHandler): Router            // add an error handler
  def get(path: String, handlers: Handler*): Router // register a route (one or more handlers)
  // post, put, delete, patch, head, options, all ...
```

### Request Processing Example
```scala
// Walk the pipeline in order, threading the (immutable) request along.
def walk(entries: List[Process], req: Request, path: List[String]): Future[Outcome] =
  entries match
    case Nil => Future.successful(Outcome.Missed)
    case entry :: rest =>
      run(entry, req, path).flatMap {
        case Continue(next)     => walk(rest, next, path) // proceed with updated request
        case Skip               => walk(rest, req, path)  // decline; try the next entry
        case Complete(response) => Future.successful(Outcome.Handled(req, response))
        case Fail(error)        => handleError(error, req)
      }
```

### Example Router Structure
```scala
val orders = Router()
  .get("/:id", getOrder)
  .post("/", createOrder)

val api = Router() 
  .use(auth)          // Can modify request
  .get("/users", listUsers)
  .use("/orders", orders)

val app = Router()
  .use(logging)       // Can modify request
  .use("/api", api)
```

## Header Handling

The framework provides consistent header handling across all requests and responses, ensuring compatibility with HTTP/1.1 specifications and common browser behaviors.

### Design Goals
- Case-insensitive header matching
- Consistent internal representation
- Preservation of original header casing in responses
- Compliance with HTTP standards
- Reliable handling of special cases

### Implementation Details

#### Header Normalization Rules
- All header names stored internally in lowercase
- Multiple headers with same name combined with comma delimiter
- Leading and trailing whitespace removed from header values
- Original header case preserved for outbound responses

#### Internal Representation
```scala
case class Request(
  headers: Map[String, String]  // All keys lowercase
)
```

#### Header Processing Flow
1. Inbound headers normalized on request creation
2. All internal header operations use lowercase keys
3. Original casing recorded for response headers
4. Response headers follow original casing convention

#### Special Cases
- Content-Type headers preserved exactly
- Set-Cookie headers handled as separate headers
- Transfer-Encoding and Connection headers managed by server

### Examples

#### Header Normalization
```scala
// Original headers
Authorization: Bearer token
CONTENT-TYPE: application/json
accept: text/plain

// Normalized internal representation
authorization: Bearer token
content-type: application/json
accept: text/plain
```

#### Multiple Header Handling
```scala
// Original headers
Accept: text/html
Accept: application/json

// Normalized
accept: text/html, application/json
```

This design ensures consistent header handling while maintaining compatibility with existing HTTP clients and servers.

## Body Parser Design

### Overview
The body parser handles two main content types:
- Application/JSON using zio-json
- Multipart/form-data for file uploads

### JSON Parsing
Uses Node.js streams with zio-json:

```scala
def json[A: JsonDecoder](): Middleware = 
  endpoint => request => {
    // Stream chunks
    // Accumulate body
    // Parse with zio-json
    // Add parsed body to request context
  }
```

### File Upload Handling

#### Design Goals
- Memory efficient streaming
- Progress tracking
- File size limits
- MIME type validation
- Concurrent upload handling

#### Implementation
```scala
def multipart(): Middleware =
  endpoint => request => {
    // Parse boundaries
    // Stream file chunks
    // Handle metadata
    // Validate file types
    // Store temporary files
    // Add file info to context
  }
```

#### File Processing Flow
1. Detect multipart boundary
2. Stream chunks to temp storage
3. Track upload progress
4. Validate completed files
5. Clean up temp files
6. Add file metadata to request

#### Configuration Options
- Max file size
- Allowed MIME types
- Temp directory
- Concurrent upload limit
- Cleanup timing

### Usage Example
```scala
server
  .use(BodyParser.json[User]())
  .post("/users", createUser)
  
server  
  .use(BodyParser.multipart())
  .post("/upload", handleFileUpload)
```

### Error Handling
- Invalid JSON
- File too large
- Invalid file type
- Storage errors
- Corrupt uploads

The parser converts errors into appropriate ServerError types for consistent error handling.

## Implementation Notes
- Single unified Handler type
- Linear processing through handlers
- Clear request state management
- Immutable Request objects
- Mutable request state in router
- Type-safe param extraction