---
title: Routing
description: Route definition, path parameters, and nested routers
---

## Defining Routes

Register handlers for specific HTTP methods and paths:

```scala
server
  .get("/users", listUsers)
  .post("/users", createUser)
  .put("/users/:id", updateUser)
  .delete("/users/:id", deleteUser)
  .patch("/users/:id", patchUser)
```

## Path Parameters

Named segments prefixed with `:` are captured into `request.params`:

```scala
server.get("/users/:id", request => {
  val userId = request.params("id")
  s"User: $userId".asText
})

// Multiple parameters
server.get("/users/:userId/posts/:postId", request => {
  val userId = request.params("userId")
  val postId = request.params("postId")
  getPost(userId, postId).asJson
})
```

## Wildcards

`*` matches exactly one path segment:

```scala
server.get("/files/*", request => "matched one segment".asText)
// Matches /files/report — but not /files/2024/report
```

To serve everything under a prefix (at any depth), mount path-scoped middleware
instead of a wildcard route:

```scala
server.use("/files", StaticMiddleware("uploads"))
```

## Multiple Handlers Per Route

Chain handlers for a single route — they execute sequentially:

```scala
server.get("/admin/users",
  authMiddleware,       // First: verify authentication
  requireAdmin,         // Second: check admin role
  listUsersHandler      // Third: return data
)
```

Handlers run in order: `Continue` passes the (possibly modified) request to the next
handler, and `Skip` moves to the next handler unchanged. `Complete` ends the request
and `Fail` diverts to the error handlers — both stop the chain. If every handler
skips, routing continues to the next matching route.

## Subrouters

Group related routes under a common prefix using `Router`:

```scala
val usersRouter = Router()
  .get("/", listUsers)          // GET /api/users
  .post("/", createUser)        // POST /api/users
  .get("/:id", getUser)         // GET /api/users/:id
  .put("/:id", updateUser)      // PUT /api/users/:id
  .delete("/:id", deleteUser)   // DELETE /api/users/:id

server.use("/api/users", usersRouter)
```

### Nested Subrouters

Routers can be nested arbitrarily:

```scala
val postsRouter = Router()
  .get("/", listPosts)
  .post("/", createPost)

val usersRouter = Router()
  .get("/", listUsers)
  .use("/:userId/posts", postsRouter)  // Nest posts under users

val apiRouter = Router()
  .use("/users", usersRouter)

server.use("/api", apiRouter)
// Resolves: GET /api/users/:userId/posts
```

### Router-Scoped Middleware

Middleware added to a router applies only to its routes:

```scala
val adminRouter = Router()
  .use(requireAdmin)        // Only applies to admin routes
  .get("/dashboard", dashboardHandler)
  .get("/users", adminListUsers)

val publicRouter = Router()
  .get("/status", statusHandler)   // No admin check

server
  .use("/admin", adminRouter)
  .use("/public", publicRouter)
```

## Path-Scoped Middleware

Apply middleware to all routes matching a prefix:

```scala
// All /api/* routes require authentication
server.use("/api", authMiddleware)

// Static files under /assets
server.use("/assets", StaticMiddleware("public/assets"))
```

## Route Matching

Paths are split into segments and matched left to right:

1. Static segments match literally (`/users` matches `/users`)
2. Parameter segments (`:name`) capture any single segment
3. A wildcard (`*`) matches any single segment

The router is an **ordered pipeline**: entries are tried in the order you register
them, and the first one that fully matches and returns a response wins. There is no
static-over-parameter precedence, so register more specific routes first. Paths are
parsed into segments once at registration; matching itself is a linear walk, not a
compiled dispatch tree.

## Global Middleware

Middleware registered with `.use(handler)` (no path) applies to all requests:

```scala
server
  .use(LoggingMiddleware())     // Runs on every request
  .use(CorsMiddleware())        // Runs on every request
  .get("/hello", helloHandler)  // Only matches GET /hello
```

## Error Handlers

Register error handlers with the two-argument form of `.use`:

```scala
server.use { (error: ServerError, request: Request) =>
  error match {
    case ValidationError(msg) => Map("error" -> msg).asJson(400)
    case _                    => skip
  }
}
```

Error handlers are tried in order. Return `skip` to pass to the next one.
