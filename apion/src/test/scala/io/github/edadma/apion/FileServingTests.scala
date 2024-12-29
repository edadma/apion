package io.github.edadma.apion

import scala.concurrent.{Future, ExecutionContext}
import scala.scalajs.js
import scala.scalajs.js.typedarray.{Int8Array, Uint8Array}

class FileServingTests extends AsyncBaseSpec:
  // Need implicit EC for Future transformations
  override implicit def executionContext: ExecutionContext = ExecutionContext.global

  // Mock filesystem state for testing
  case class MockFile(
      content: String,
      isDirectory: Boolean = false,
      size: Int = 1024,
  )

  class MockFS(files: Map[String, MockFile]):
    def createMockImpl(): js.Dynamic =
      js.Dynamic.literal(
        promises = js.Dynamic.literal(
          readFile = (path: String, options: js.UndefOr[js.Dynamic]) => {
            logger.debug(s"Mock readFile called with path: $path")
            val normalizedPath = normalizePath(path)
            logger.debug(s"Normalized path: $normalizedPath")
            files.get(normalizedPath) match
              case Some(file) if !file.isDirectory =>
                // Handle options for encoding if provided
                options.toOption match
                  case Some(opts)
                      if js.typeOf(opts.encoding) == "string" && opts.encoding.asInstanceOf[String] == "utf8" =>
                    js.Promise.resolve(file.content)
                  case _ =>
                    // Default to returning Uint8Array
                    js.Promise.resolve(
                      new Uint8Array(js.Array[Short](file.content.getBytes("UTF-8").map(b => (b & 0xff).toShort)*)),
                    )
              case Some(file) if file.isDirectory =>
                js.Promise.reject(new js.Error("Cannot read directory"))
              case Some(_) =>
                // This case should never happen as we've covered all MockFile cases above,
                // but we include it for exhaustiveness
                js.Promise.reject(new js.Error("Unknown file type"))
              case None =>
                js.Promise.reject(new js.Error(s"File not found: $path"))
          },
          stat = (path: String) => {
            logger.debug(s"Mock stat called with path: $path")
            val normalizedPath = normalizePath(path)
            logger.debug(s"Normalized path: $normalizedPath")
            files.get(normalizedPath) match
              case Some(file) =>
                js.Promise.resolve(
                  js.Dynamic.literal(
                    isDirectory = () => file.isDirectory,
                    size = file.size,
                  ),
                )
              case None =>
                js.Promise.reject(new js.Error(s"File not found: $path"))
          },
        ),
      )

  // Helper to normalize paths
  private def normalizePath(path: String): String =
    path.stripPrefix("./").stripPrefix("/")

  // Test data setup
  val testFiles = Map(
    "public/test.txt"          -> MockFile("Hello World"),
    "public/test.html"         -> MockFile("<html><body>Test</body></html>"),
    "public/subdir"            -> MockFile("", isDirectory = true),
    "public/subdir/index.html" -> MockFile("<html><body>Index</body></html>"),
    "public/test.json"         -> MockFile("""{"message":"test"}"""),
    "public/test.custom"       -> MockFile("custom content"),
  )

  "FileServing" - {
    "basic file serving" - {
      "should serve static files with correct content type" in {
        val router = Router()
        val mockFs = new MockFS(testFiles)

        // Replace global fs with mock
        val originalFs = js.Dynamic.global.global.fs
        js.Dynamic.global.global.fs = mockFs.createMockImpl()

        try {
          router.use(Middlewares.fileServing(
            path = "/static",
            root = "./public",
          ))

          // Test different file types
          val tests = List(
            ("/static/test.txt", "text/plain", "Hello World"),
            ("/static/test.html", "text/html", "<html><body>Test</body></html>"),
            ("/static/test.json", "application/json", """{"message":"test"}"""),
          )

          Future.sequence(
            tests.map { case (path, expectedType, expectedContent) =>
              val request = Request("GET", path, Map())
              router.handle(request).map { response =>
                response.status shouldBe 200
                response.headers("Content-Type") shouldBe expectedType
                response.body shouldBe expectedContent
              }
            },
          ).map(_ => succeed)

        } finally {
          js.Dynamic.global.global.fs = originalFs
        }
      }

      "should serve index.html for directory requests" in {
        val router = Router()
        val mockFs = new MockFS(testFiles)

        val originalFs = js.Dynamic.global.global.fs
        js.Dynamic.global.global.fs = mockFs.createMockImpl()

        try {
          router.use(Middlewares.fileServing(
            path = "/static",
            root = "./public",
          ))

          val request = Request("GET", "/static/subdir/", Map())

          router.handle(request).map { response =>
            response.status shouldBe 200
            response.headers("Content-Type") shouldBe "text/html"
            response.body shouldBe "<html><body>Index</body></html>"
          }
        } finally {
          js.Dynamic.global.global.fs = originalFs
        }
      }

      "should prevent directory traversal attacks" in {
        val router = Router()
        val mockFs = new MockFS(testFiles)

        val originalFs = js.Dynamic.global.global.fs
        js.Dynamic.global.global.fs = mockFs.createMockImpl()

        try {
          router.use(Middlewares.fileServing(
            path = "/static",
            root = "./public",
          ))

          // Test different traversal attempts
          val traversalPaths = List(
            "/static/../secrets.txt",
            "/static/subdir/../../config.json",
            "/static/%2e%2e/private.key",
          )

          Future.sequence(
            traversalPaths.map { path =>
              val request = Request("GET", path, Map())
              router.handle(request).map { response =>
                response.status shouldBe 403
                response.body should include("Forbidden")
              }
            },
          ).map(_ => succeed)
        } finally {
          js.Dynamic.global.global.fs = originalFs
        }
      }

      "should handle missing files correctly" in {
        val router = Router()
        val mockFs = new MockFS(testFiles)

        val originalFs = js.Dynamic.global.global.fs
        js.Dynamic.global.global.fs = mockFs.createMockImpl()

        try {
          router.use(Middlewares.fileServing(
            path = "/static",
            root = "./public",
          ))

          val request = Request("GET", "/static/nonexistent.txt", Map())

          router.handle(request).map { response =>
            response.status shouldBe 404
            response.body should include("Not Found")
          }
        } finally {
          js.Dynamic.global.global.fs = originalFs
        }
      }
    }

    "custom configuration" - {
      "should respect custom MIME types" in {
        val router = Router()
        val mockFs = new MockFS(testFiles)

        val originalFs = js.Dynamic.global.global.fs
        js.Dynamic.global.global.fs = mockFs.createMockImpl()

        try {
          router.use(Middlewares.fileServing(
            path = "/static",
            root = "./public",
            mimeTypes = Map(
              "custom" -> "application/x-custom",
            ),
          ))

          val request = Request("GET", "/static/test.custom", Map())

          router.handle(request).map { response =>
            response.status shouldBe 200
            response.headers("Content-Type") shouldBe "application/x-custom"
            response.body shouldBe "custom content"
          }
        } finally {
          js.Dynamic.global.global.fs = originalFs
        }
      }

      "should use custom index file name" in {
        val router = Router()
        val mockFs = new MockFS(testFiles + (
          "./public/subdir/welcome.html" -> MockFile("<html><body>Welcome</body></html>")
        ))

        val originalFs = js.Dynamic.global.global.fs
        js.Dynamic.global.global.fs = mockFs.createMockImpl()

        try {
          router.use(Middlewares.fileServing(
            path = "/static",
            root = "./public",
            index = "welcome.html",
          ))

          val request = Request("GET", "/static/subdir/", Map())

          router.handle(request).map { response =>
            response.status shouldBe 200
            response.headers("Content-Type") shouldBe "text/html"
            response.body shouldBe "<html><body>Welcome</body></html>"
          }
        } finally {
          js.Dynamic.global.global.fs = originalFs
        }
      }
    }
  }
