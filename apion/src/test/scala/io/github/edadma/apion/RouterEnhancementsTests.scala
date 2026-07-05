package io.github.edadma.apion

import scala.concurrent.Future
import scala.scalajs.js
import io.github.edadma.nodejs.ServerRequest

class RouterEnhancementsTests extends AsyncBaseSpec {
  private def request(method: String, url: String): Request = {
    val req = js.Dynamic.literal(
      method = method,
      url = url,
      headers = js.Dictionary[String](),
      on = (_: String, _: js.Function1[js.Any, Unit]) => js.Dynamic.literal(),
    )
    Request.fromServerRequest(req.asInstanceOf[ServerRequest])
  }

  private def bodyOf(outcome: Outcome): String = outcome match
    case Outcome.Handled(_, response) => response.bodyText
    case Outcome.Missed               => fail("Expected a completed response, got Missed")

  "Multi-segment sub-router mounts" - {
    // Regression: the old router only stripped a single segment of the mount path,
    // so a multi-segment mount like "/api/v1" left "v1" in the child's path.
    "should mount a sub-router at a multi-segment path" in {
      val api = new Router().get("/users", _ => "users".asText)
      val app = new Router().use("/api/v1", api)

      app(request("GET", "/api/v1/users")).map(bodyOf(_) shouldBe "users")
    }

    "should accumulate the full multi-segment mount into basePath" in {
      val api = new Router().get("/users", _.basePath.asText)
      val app = new Router().use("/api/v1", api)

      app(request("GET", "/api/v1/users")).map(bodyOf(_) shouldBe "/api/v1")
    }

    "should skip a multi-segment mount when only the first segment matches" in {
      val api = new Router().get("/users", _ => "users".asText)
      val app = new Router().use("/api/v1", api)

      app(request("GET", "/api/v2/users")).map(_ shouldBe Outcome.Missed)
    }
  }

  "Wildcard segments" - {
    // Regression: the active matchSegments had no WildcardSegment case, so "*" routes
    // never matched at all.
    "should match a single wildcard segment" in {
      val router = new Router().get("/files/*", _ => "file".asText)
      router(request("GET", "/files/readme")).map(bodyOf(_) shouldBe "file")
    }

    "should not match when there are extra segments beyond the wildcard" in {
      val router = new Router().get("/files/*", _ => "file".asText)
      router(request("GET", "/files/a/b")).map(_ shouldBe Outcome.Missed)
    }
  }

  "Method helpers" - {
    "all should match any HTTP method" in {
      val router = new Router().all("/ping", _ => "pong".asText)
      for {
        g <- router(request("GET", "/ping"))
        p <- router(request("POST", "/ping"))
      } yield {
        bodyOf(g) shouldBe "pong"
        bodyOf(p) shouldBe "pong"
      }
    }

    "head and options should route by method" in {
      val router = new Router()
        .head("/resource", _ => "head".asText)
        .options("/resource", _ => "options".asText)
      for {
        h <- router(request("HEAD", "/resource"))
        o <- router(request("OPTIONS", "/resource"))
      } yield {
        bodyOf(h) shouldBe "head"
        bodyOf(o) shouldBe "options"
      }
    }
  }

  "Path normalization" - {
    "should treat a trailing slash as equivalent" in {
      val router = new Router().get("/users", _ => "ok".asText)
      router(request("GET", "/users/")).map(bodyOf(_) shouldBe "ok")
    }
  }

  "Late route registration" - {
    // Regression: the old router snapshotted its routes into a `lazy val` on the
    // first request, so any route added afterward was silently ignored.
    "should honor routes added after the first request is served" in {
      val router = new Router().get("/a", _ => "a".asText)
      router(request("GET", "/a")).flatMap { first =>
        bodyOf(first) shouldBe "a"
        router.get("/b", _ => "b".asText)
        router(request("GET", "/b")).map(bodyOf(_) shouldBe "b")
      }
    }
  }
}
