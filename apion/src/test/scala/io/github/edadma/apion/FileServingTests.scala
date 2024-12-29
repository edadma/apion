package io.github.edadma.apion

import scala.concurrent.{Future, ExecutionContext}

class FileServingTests extends AsyncBaseSpec:
  override implicit def executionContext: ExecutionContext = ExecutionContext.global

  // Test data setup
  // Test data setup with both root index.html and directory structure
  val testFiles = Map(
    "public/test.txt"            -> mockFile("Hello World", false, "644"),
    "public/index.html"          -> mockFile("<html><body>Index Page</body></html>", false, "644"),
    "public/test.custom"         -> mockFile("Custom content", false, "644"),
    "public/subdir/welcome.html" -> mockFile("<html><body>Welcome</body></html>", false, "644"),
    "public/subdir"              -> mockFile("", true, "755"),
    // Note: Intentionally not including public/subdir/index.html to test fallback
  )

  logger.debug("MockFS Contents:")
  testFiles.foreach { case (path, file) =>
    logger.debug(s"Path: $path, IsDir: ${file.stats.isDirectory()}, Content: ${new String(file.content)}")
  }

  val mockFs = new MockFS(testFiles)

  "FileServing" - {
    "basic file serving" - {
      "should serve static files with correct content type" in /*withDebugLogging(
        "should serve static files with correct content type",
      )*/ {
        val router = Router()

        logger.debug("[Test] Setting up file serving")
        Middlewares.fileServing(
          path = "/static",
          root = "public",
          fs = mockFs,
        )(router)

        logger.debug("[Test] Starting test case")
        val request = Request("GET", "/static/test.txt", Map())

        router.handle(request).map { response =>
          logger.debug(s"[Test] Got response status: ${response.status}")
          logger.debug(s"[Test] Response headers: ${response.headers}")
          logger.debug(s"[Test] Response body: ${response.body}")

          response.status shouldBe 200
          response.headers("Content-Type") shouldBe "text/plain"
          response.body shouldBe "Hello World"
        }
      }

      "should serve index.html for directory requests" in withDebugLogging(
        "should serve index.html for directory requests",
      ) {
        val router = Router()

        Middlewares.fileServing(
          path = "/static",
          root = "public",
          fs = mockFs,
        )(router)

        val request = Request("GET", "/static/subdir/", Map())

        router.handle(request).map { response =>
          response.status shouldBe 200
          response.headers("Content-Type") shouldBe "text/html"
          response.body shouldBe "<html><body>Index Page</body></html>"
        }
      }

      "should prevent directory traversal attacks" in {
        val router = Router()

        Middlewares.fileServing(
          path = "/static",
          root = "public",
          fs = mockFs,
        )(router)

        // Test a single traversal case first for debugging
        val request = Request("GET", "/static/../secrets.txt", Map())

        logger.debug("[Test] Testing directory traversal with path: /static/../secrets.txt")

        router.handle(request).map { response =>
          logger.debug(s"[Test] Got response: ${response.status} - ${response.body}")
          response.status shouldBe 403
          response.body should include("Forbidden")
        }
      }

      "should handle missing files correctly" in {
        val router = Router()

        Middlewares.fileServing(
          path = "/static",
          root = "public",
          fs = mockFs,
        )(router)

        val request = Request("GET", "/static/nonexistent.txt", Map())

        router.handle(request).map { response =>
          response.status shouldBe 404
          response.body should include("Not Found")
        }
      }
    }

    "custom configuration" - {
      "should respect custom MIME types" in {
        val router = Router()

        Middlewares.fileServing(
          path = "/static",
          root = "public",
          mimeTypes = Map(
            "custom" -> "application/x-custom",
          ),
          fs = mockFs,
        )(router)

        val request = Request("GET", "/static/test.custom", Map())

        router.handle(request).map { response =>
          response.status shouldBe 200
          response.headers("Content-Type") shouldBe "application/x-custom"
          response.body shouldBe "Custom content"
        }
      }

      "should use custom index file name" in {
        val router = Router()

        Middlewares.fileServing(
          path = "/static",
          root = "public",
          options = Middlewares.FileServingOptions(index = "welcome.html"),
          fs = mockFs,
        )(router)

        val request = Request("GET", "/static/subdir/", Map())

        router.handle(request).map { response =>
          response.status shouldBe 200
          response.headers("Content-Type") shouldBe "text/html"
          response.body shouldBe "<html><body>Welcome</body></html>"
        }
      }
    }
  }
