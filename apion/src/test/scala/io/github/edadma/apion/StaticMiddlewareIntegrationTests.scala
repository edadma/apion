package io.github.edadma.apion

import scala.scalajs.js
import io.github.edadma.nodejs.{FetchOptions, fetch, Server as NodeServer}
import org.scalatest.BeforeAndAfterAll

import scala.compiletime.uninitialized

class StaticMiddlewareIntegrationTests extends AsyncBaseSpec with BeforeAndAfterAll {
  var server: Server         = uninitialized
  var httpServer: NodeServer = uninitialized
  val port                   = 3005 // Different port for static file tests

  // Comprehensive mock filesystem
  private val mockFiles = Map(
    "project/public/index.html"       -> mockFile("<html><body>Welcome</body></html>", false, "644"),
    "project/public/styles.css"       -> mockFile("body { color: black; }", false, "644"),
    "project/public/.hidden.css"      -> mockFile("hidden styles", false, "644"),
    "project/private/.secret.txt"     -> mockFile("confidential", false, "600"),
    "project/restricted/.config.json" -> mockFile("{\"key\":\"value\"}", false, "600"),
    //
    "project/index.html"           -> mockFile("<html><body>Welcome</body></html>", false, "644"),
    "project/styles.css"           -> mockFile("body { color: black; }", false, "644"),
    "project/images/logo.png"      -> mockFile("binary-image-data", false, "644"),
    "project/private/secret.txt"   -> mockFile("confidential", false, "600"),
    "project/documents/report.txt" -> mockFile("Annual Report", false, "644"),
    "project/nested/deep/file.txt" -> mockFile("Deep nested content", false, "644"),
  )

  val mockFs = new MockFS(mockFiles)

  override def beforeAll(): Unit = {
    server = Server()
      // Public route with default dotfiles handling (ignore)
      .use(
        "/public",
        StaticMiddleware(
          "project/public",
          StaticMiddleware.Options(
            index = true,
            dotfiles = "ignore",
            etag = true,
            maxAge = 3600,
          ),
          mockFs,
        ),
      )

      // Restricted route with different dotfiles handling
      .use(
        "/private",
        StaticMiddleware(
          "project/private",
          StaticMiddleware.Options(
            index = true,
            dotfiles = "deny",
            etag = true,
            maxAge = 3600,
          ),
          mockFs,
        ),
      )

      // Another route with allow option
      .use(
        "/restricted",
        StaticMiddleware(
          "project/restricted",
          StaticMiddleware.Options(
            index = true,
            dotfiles = "allow",
            etag = true,
            maxAge = 3600,
          ),
          mockFs,
        ),
      )

    httpServer = server.listen(port) {}
  }

  override def afterAll(): Unit = {
    if (httpServer != null) {
      httpServer.close(() => ())
    }
  }

  "StaticMiddleware dotfile handling" - {
    "should ignore dot files by default" in {
      fetch(s"http://localhost:$port/public/.hidden.css")
        .toFuture
        .map { response =>
          response.status shouldBe 404
        }
    }

    "should deny dot files when configured" in /*withDebugLogging("should deny dot files when configured")*/ {
      fetch(s"http://localhost:$port/private/.secret.txt")
        .toFuture
        .map { response =>
          response.status shouldBe 403
        }
    }

    "should allow dot files when configured" in {
      fetch(s"http://localhost:$port/restricted/.config.json")
        .toFuture
        .map { response =>
          response.status shouldBe 200
        }
    }

    "should serve static files" - {
      "serve HTML file" in /*withDebugLogging("serve HTML file")*/ {
        fetch(s"http://localhost:$port/public/index.html")
          .toFuture
          .flatMap(response => response.text().toFuture)
          .map { body =>
            body should include("Welcome")
          }
      }
    }

    "handle ETag-based caching" in {
      // First request to get ETag
      fetch(s"http://localhost:$port/public/index.html")
        .toFuture
        .flatMap { firstResponse =>
          val etag = firstResponse.headers.get("ETag")

          // Second request with ETag
          val options = FetchOptions(
            method = "GET",
            headers = js.Dictionary("If-None-Match" -> etag),
          )

          fetch(s"http://localhost:$port/public/index.html", options)
            .toFuture
            .map { secondResponse =>
              secondResponse.status shouldBe 304 // Not Modified
            }
        }
    }
  }
}
