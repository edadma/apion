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
sealed trait ServerError extends Throwable
case class ValidationError(msg: String) extends ServerError
case class AuthError(msg: String) extends ServerError
case class NotFoundError(msg: String) extends ServerError

type ErrorHandler = ServerError => Response

sealed trait Result
case class Continue(request: Request) extends Result
case class Complete(response: Response) extends Result
case class Fail(error: ServerError) extends HandlerResult
case object Skip extends Result

type Handler = Request => Future[Result]
```

Return values signify:
- Skip: Try next route
- Continue(Request): Continue with modified request
- Complete(Response): End processing and send response
- Fail(ServerError): propagate error

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
Routes are compiled during server setup into an efficient matching structure:

Internal representation:
```scala
case class RoutePattern(
  segments: List[Segment],
  handler: Handler
)

sealed trait Segment
case class StaticSegment(value: String) extends Segment  // Fast exact matches
case class ParamSegment(name: String) extends Segment    // Named parameters
case object WildcardSegment extends Segment             // Wildcards
```

Matching strategy:
- Pre-compiled patterns for performance
- Static segments matched first (fastest)
- Parameter extraction in single pass
- Early exit on non-matches
- Efficient parameter storage

Features:
- Path parameter extraction
- Query string parsing
- Route grouping
- Path prefixing
- HTTP method handling
- Wildcard support

### Type Safety Features
- Compile-time route validation
- Type-safe body parsing
- Header validation
- Response type checking
- Error type handling

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
- Minimal object creation
- Efficient route matching
- Fast middleware composition
- Lightweight request context
- Smart path parameter extraction

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
- Router maintains mutable reference to current request state
- Request objects themselves are immutable
- Each handler receives latest request state
- When handler returns Some(newRequest), router updates state
- Subsequent handlers receive updated state

## Pattern Matching System

### Route Pattern Structure
```scala
sealed trait PathSegment
case class StaticSegment(value: String) extends PathSegment    
case class ParamSegment(name: String) extends PathSegment      
case class WildcardSegment() extends PathSegment              
```

### Route Compilation
- Split path into segments
- Convert to pattern matching tree
- Store param names for extraction
- Compile once at router creation

### Matching Process
1. Split incoming path into segments
2. Match against compiled patterns
3. Extract parameters into Map
4. Store in Request context
5. None if no match found

## Router Implementation

### Core Router Interface
```scala
trait Router:
  def use(path: String, router: Router): Router   // Mount subrouter
  def use(handler: Handler): Router               // Add handler
  def get(path: String, handler: Handler): Router // Add route handler
  // other HTTP methods...
```

### Request Processing Example
```scala
// Initial request state
var currentRequest = incomingRequest

// Process handlers in order
for 
  handler <- handlers
  result <- handler(currentRequest)
yield result match
  case Some(req: Request) => 
    currentRequest = req  // Update state
    continue           
  case Some(res: Response) => 
    return res        // End chain
  case None =>
    continue          // Try next handler
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

## Implementation Notes
- Single unified Handler type
- Linear processing through handlers
- Clear request state management
- Immutable Request objects
- Mutable request state in router
- Type-safe param extraction