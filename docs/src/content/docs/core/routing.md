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

Use `*` for catch-all segments:

```scala
server.get("/files/*", request => {
  // Matches /files/foo, /files/foo/bar, etc.
  StaticMiddleware("uploads")(request)
})
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

If any handler returns `Complete`, `Fail`, or `Skip`, the chain stops.

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

Routes are matched by splitting paths into segments:

1. Static segments are matched literally (`/users` matches `/users`)
2. Parameter segments (`:name`) match any single segment
3. Wildcard (`*`) matches remaining segments
4. Static segments take priority over parameter segments

Route patterns are compiled once at server startup for efficient matching.

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
