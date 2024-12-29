package io.github.edadma.apion

import scala.concurrent.{Future, ExecutionContext}
import scala.scalajs.js
import scala.scalajs.js.typedarray.{Int8Array, Uint8Array}

class FileServingTests extends AsyncBaseSpec:
  // Need implicit EC for Future transformations
  override implicit def executionContext: ExecutionContext = ExecutionContext.global

  // Mock Node.js fs module responses
  private def mockFileResponse(content: String): js.Promise[js.typedarray.Uint8Array] =
    js.Promise.resolve(
      new Uint8Array(js.Array[Short](content.getBytes("UTF-8").map(b => (b & 0xff).toShort)*)),
    )

  private def mockFileStats(isDirectory: Boolean = false): js.Promise[js.Dynamic] =
    js.Promise.resolve(
      js.Dynamic.literal(
        isDirectory = () => isDirectory,
        size = 1024,
      ),
    )

  "FileServing" - {
    "basic file serving" - {
      "should serve static files with correct content type" in {
        val router = Router()

        logger.debug("Setting up file serving middleware")
        router.use(Middlewares.fileServing(
          path = "/static",
          root = "./public",
        ))

        // Mock file system calls
        val originalFs = js.Dynamic.global.global.fs

        logger.debug(s"Original fs: ${js.JSON.stringify(originalFs)}")

        val mockFs = js.Dynamic.literal(
          promises = js.Dynamic.literal(
            readFile = (path: String) => {
              logger.debug(s"Mock readFile called with path: $path")
              if (path == "./public/test.txt") mockFileResponse("Hello World")
              else js.Promise.reject(new js.Error("File not found"))
            },
            stat = (path: String) => {
              logger.debug(s"Mock stat called with path: $path")
              mockFileStats()
            },
          ),
        )

        js.Dynamic.global.global.fs = mockFs
        logger.debug(s"Mocked fs: ${js.JSON.stringify(js.Dynamic.global.global.fs)}")

        try {
          val request = Request("GET", "/static/test.txt", Map())

          router.handle(request).map { response =>
            logger.debug(s"Response received: status=${response.status}, body=${response.body}")
            response.status shouldBe 200
            response.headers("Content-Type") shouldBe "text/plain"
            response.body shouldBe "Hello World"
          }
        } finally {
          js.Dynamic.global.global.fs = originalFs
        }
      }

      "should return 404 for non-existent files" in {
        val router = Router()

        router.use(Middlewares.fileServing(
          path = "/static",
          root = "./public",
        ))

        // Mock file system calls
        val originalFs = js.Dynamic.global.fs
        js.Dynamic.global.fs = js.Dynamic.literal(
          promises = js.Dynamic.literal(
            readFile = (path: String) =>
              js.Promise.reject(new js.Error("File not found")),
            stat = (path: String) =>
              js.Promise.reject(new js.Error("File not found")),
          ),
        )

        try {
          val request = Request("GET", "/static/nonexistent.txt", Map())

          router.handle(request).map { response =>
            response.status shouldBe 404
            response.body should include("Not Found")
          }
        } finally {
          js.Dynamic.global.fs = originalFs
        }
      }

      "should prevent directory traversal attacks" in {
        val router = Router()

        router.use(Middlewares.fileServing(
          path = "/static",
          root = "./public",
        ))

        val request = Request("GET", "/static/../secrets.txt", Map())

        router.handle(request).map { response =>
          response.status shouldBe 403
          response.body should include("Forbidden")
        }
      }

      "should handle directories correctly" in {
        val router = Router()

        router.use(Middlewares.fileServing(
          path = "/static",
          root = "./public",
          index = "index.html",
        ))

        // Mock file system calls
        val originalFs = js.Dynamic.global.fs
        js.Dynamic.global.fs = js.Dynamic.literal(
          promises = js.Dynamic.literal(
            readFile = (path: String) =>
              if (path == "./public/test/index.html") mockFileResponse("<html>Index</html>")
              else js.Promise.reject(new js.Error("File not found")),
            stat = (path: String) =>
              if (path.endsWith("/test")) mockFileStats(true)
              else mockFileStats(),
          ),
        )

        try {
          val request = Request("GET", "/static/test/", Map())

          router.handle(request).map { response =>
            response.status shouldBe 200
            response.headers("Content-Type") shouldBe "text/html"
            response.body shouldBe "<html>Index</html>"
          }
        } finally {
          js.Dynamic.global.fs = originalFs
        }
      }
    }

    "content types" - {
      "should detect common file types" in {
        val router = Router()

        router.use(Middlewares.fileServing(
          path = "/static",
          root = "./public",
        ))

        // Mock file system calls
        val originalFs = js.Dynamic.global.fs
        js.Dynamic.global.fs = js.Dynamic.literal(
          promises = js.Dynamic.literal(
            readFile = (path: String) => mockFileResponse("test content"),
            stat = (path: String) => mockFileStats(),
          ),
        )

        try {
          val tests = List(
            ("/static/test.html", "text/html"),
            ("/static/test.css", "text/css"),
            ("/static/test.js", "application/javascript"),
            ("/static/test.json", "application/json"),
            ("/static/test.png", "image/png"),
            ("/static/test.jpg", "image/jpeg"),
            ("/static/test.svg", "image/svg+xml"),
            ("/static/test.pdf", "application/pdf"),
            ("/static/test.unknown", "application/octet-stream"),
          )

          Future.sequence(
            tests.map { case (path, expectedType) =>
              val request = Request("GET", path, Map())
              router.handle(request).map { response =>
                response.status shouldBe 200
                response.headers("Content-Type") shouldBe expectedType
              }
            },
          ).map(_ => succeed)
        } finally {
          js.Dynamic.global.fs = originalFs
        }
      }
    }

    "configuration" - {
      "should respect custom MIME types" in {
        val router = Router()

        router.use(Middlewares.fileServing(
          path = "/static",
          root = "./public",
          mimeTypes = Map(
            "custom" -> "application/x-custom",
          ),
        ))

        // Mock file system calls
        val originalFs = js.Dynamic.global.fs
        js.Dynamic.global.fs = js.Dynamic.literal(
          promises = js.Dynamic.literal(
            readFile = (path: String) => mockFileResponse("custom content"),
            stat = (path: String) => mockFileStats(),
          ),
        )

        try {
          val request = Request("GET", "/static/test.custom", Map())

          router.handle(request).map { response =>
            response.status shouldBe 200
            response.headers("Content-Type") shouldBe "application/x-custom"
          }
        } finally {
          js.Dynamic.global.fs = originalFs
        }
      }
    }
  }
