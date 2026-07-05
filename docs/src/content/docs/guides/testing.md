---
title: Testing
description: Unit and integration testing strategies for Apion applications
---

Apion uses ScalaTest with async support and provides test utilities for both unit and integration testing.

## Test Setup

Add ScalaTest to your `build.sbt`:

```scala
libraryDependencies += "org.scalatest" %%% "scalatest" % "3.2.19" % Test
```

## Base Test Classes

### Async Tests

For testing handlers and middleware that return `Future`:

```scala
import io.github.edadma.apion._
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

class MyTests extends AsyncFreeSpec with Matchers {
  implicit override def executionContext =
    org.scalajs.macrotaskexecutor.MacrotaskExecutor

  "my handler" - {
    "returns 200" in {
      myHandler(mockRequest()).map {
        case Complete(response) =>
          response.status shouldBe 200
        case other =>
          fail(s"Expected Complete, got $other")
      }
    }
  }
}
```

## Unit Testing Handlers

Test individual handlers with mock requests:

```scala
def mockServerRequest(
  method: String = "GET",
  url: String = "/",
  headers: Map[String, String] = Map(),
): ServerRequest = {
  val req = js.Dynamic.literal(
    method = method,
    url = url,
    headers = js.Dictionary(headers.toSeq*),
    on = (_: String, _: js.Function1[js.Any, Unit]) => js.Dynamic.literal(),
    socket = js.Dynamic.literal(
      remoteAddress = "127.0.0.1",
      remotePort = 12345,
    ),
  )
  req.asInstanceOf[ServerRequest]
}

val request = Request.fromServerRequest(mockServerRequest(
  method = "POST",
  url = "/api/users?active=true",
  headers = Map(
    "Content-Type" -> "application/json",
    "Authorization" -> "Bearer token123",
  ),
))
```

## Integration Testing

Spin up a real server and test with fetch:

```scala
class ApiTests extends AsyncFreeSpec with Matchers
    with BeforeAndAfterAll {
  
  var httpServer: NodeServer = _
  val port = 3001

  override def beforeAll(): Unit = {
    val server = Server()
      .use(LoggingMiddleware())
      .get("/users/:id", request => {
        val id = request.params("id")
        Map("id" -> id).asJson
      })

    httpServer = server.listen(port) {}
  }

  override def afterAll(): Unit = {
    if (httpServer != null) httpServer.close(() => ())
  }

  "GET /users/:id" - {
    "returns user" in {
      fetch(s"http://localhost:$port/users/123")
        .toFuture
        .flatMap { response =>
          response.status shouldBe 200
          response.json().toFuture
        }
        .map { json =>
          val str = js.JSON.stringify(json)
          str should include("123")
        }
    }
  }
}
```

## Testing Middleware

Test that middleware modifies requests or blocks them:

```scala
"AuthMiddleware" - {
  "rejects unauthenticated requests" in {
    fetch(s"http://localhost:$port/api/protected")
      .toFuture
      .map(_.status shouldBe 401)
  }

  "allows authenticated requests" in {
    val token = AuthMiddleware.createAccessToken("test", Set("user"), config)
    val options = js.Dynamic.literal(
      headers = js.Dictionary("Authorization" -> s"Bearer $token")
    )

    fetch(s"http://localhost:$port/api/protected", options)
      .toFuture
      .map(_.status shouldBe 200)
  }
}
```

## Mock File System

For testing `StaticMiddleware`:

```scala
val mockFiles = Map(
  "public/index.html" -> mockFile("<html>Hello</html>", isDirectory = false, "644"),
  "public/style.css" -> mockFile("body{}", isDirectory = false, "644"),
)

val mockFs = new MockFS(mockFiles)

val middleware = StaticMiddleware("public", StaticMiddleware.Options(), mockFs)
```

## Tips

- Use different ports for each test suite to avoid conflicts
- Clean up servers in `afterAll` to prevent port leaks
- For async assertions, return the `Future` from the test — ScalaTest handles it
- Use `eventually` blocks for testing asynchronous state changes
