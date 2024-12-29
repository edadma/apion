package io.github.edadma.apion

import scala.concurrent.{Future, ExecutionContext}
import scala.scalajs.js
import scala.scalajs.js.typedarray.{Int8Array, Uint8Array}

class FileServingTests extends AsyncBaseSpec:
  override implicit def executionContext: ExecutionContext = ExecutionContext.global

  case class MockFile(
      content: String,
      isDirectory: Boolean = false,
      size: Int = 1024,
  )

  class MockFS(files: Map[String, MockFile]):
    def createMockImpl(): js.Dynamic =
      logger.debug(s"[MockFS] Creating mock implementation with ${files.size} files")
      files.foreach { case (path, file) =>
        logger.debug(s"[MockFS] Registered file: $path (${file.content.length} bytes)")
      }

      js.Dynamic.literal(
        promises = js.Dynamic.literal(
          readFile = (path: String, options: js.UndefOr[js.Dynamic]) => {
            logger.debug(s"[MockFS] readFile called for: $path")
            val normalizedPath = normalizePath(path)
            logger.debug(s"[MockFS] Looking up normalized path: $normalizedPath")
            logger.debug(s"[MockFS] Available files: ${files.keys.mkString(", ")}")

            files.get(normalizedPath) match
              case Some(file) if !file.isDirectory =>
                logger.debug(s"[MockFS] Found file: $normalizedPath")
                options.toOption match
                  case Some(opts)
                      if js.typeOf(opts.encoding) == "string" && opts.encoding.asInstanceOf[String] == "utf8" =>
                    logger.debug(s"[MockFS] Returning content as UTF8 string: ${file.content}")
                    js.Promise.resolve(file.content)
                  case _ =>
                    logger.debug("[MockFS] Returning content as Uint8Array")
                    js.Promise.resolve(
                      new Uint8Array(js.Array[Short](file.content.getBytes("UTF-8").map(b => (b & 0xff).toShort)*)),
                    )
              case Some(file) =>
                logger.debug(s"[MockFS] Found directory: $normalizedPath")
                js.Promise.reject(new js.Error("EISDIR: illegal operation on a directory"))
              case None =>
                logger.debug(s"[MockFS] File not found: $normalizedPath")
                js.Promise.reject(new js.Error(s"ENOENT: no such file or directory, open '$path'"))
          },
          stat = (path: String) => {
            logger.debug(s"[MockFS] stat called for: $path")
            val normalizedPath = normalizePath(path)
            logger.debug(s"[MockFS] Looking up normalized path: $normalizedPath")
            logger.debug(s"[MockFS] Available files: ${files.keys.mkString(", ")}")

            files.get(normalizedPath) match
              case Some(file) =>
                logger.debug(s"[MockFS] Found file/directory: $normalizedPath (isDirectory: ${file.isDirectory})")
                js.Promise.resolve(
                  js.Dynamic.literal(
                    isDirectory = () => file.isDirectory,
                    size = file.size,
                  ),
                )
              case None =>
                logger.debug(s"[MockFS] Not found: $normalizedPath")
                js.Promise.reject(new js.Error(s"ENOENT: no such file or directory, stat '$path'"))
          },
        ),
      )

  private def normalizePath(path: String): String =
    val normalized = path
      .stripPrefix("./")
      .stripPrefix("/")
      .replaceAll("/+", "/")
    logger.debug(s"[MockFS] Normalized path '$path' to '$normalized'")
    normalized

  // Test data setup - using a single test file for now
  val testFiles = Map(
    "public/test.txt" -> MockFile("Hello World"),
  )

  "FileServing" - {
    "basic file serving" - {
      "should serve static files with correct content type" in withDebugLogging(
        "should serve static files with correct content type",
      ) {
        val router = Router()
        val mockFs = new MockFS(testFiles)

        logger.debug("[Test] Setting up mock filesystem")
        val originalFs = js.Dynamic.global.global.fs
        js.Dynamic.global.global.fs = mockFs.createMockImpl()

        try {
          logger.debug("[Test] Setting up file serving")
          // Changed root to match the mock file paths exactly
          val routerWithFiles = Middlewares.fileServing(
            path = "/static",
            root = "public",
          )(router)

          logger.debug("[Test] Starting test case")
          val request = Request("GET", "/static/test.txt", Map())

          routerWithFiles.handle(request).map { response =>
            logger.debug(s"[Test] Got response status: ${response.status}")
            logger.debug(s"[Test] Response headers: ${response.headers}")
            logger.debug(s"[Test] Response body: ${response.body}")

            response.status shouldBe 200
            response.headers("Content-Type") shouldBe "text/plain"
            response.body shouldBe "Hello World"
          }
        } finally {
          js.Dynamic.global.global.fs = originalFs
        }
      }

//      "should serve index.html for directory requests" in {
//        val router = Router()
//        val mockFs = new MockFS(testFiles)
//
//        val originalFs = js.Dynamic.global.global.fs
//        js.Dynamic.global.global.fs = mockFs.createMockImpl()
//
//        try {
//          router.use(Middlewares.fileServing(
//            path = "/static",
//            root = "./public",
//          ))
//
//          val request = Request("GET", "/static/subdir/", Map())
//
//          router.handle(request).map { response =>
//            response.status shouldBe 200
//            response.headers("Content-Type") shouldBe "text/html"
//            response.body shouldBe "<html><body>Index</body></html>"
//          }
//        } finally {
//          js.Dynamic.global.global.fs = originalFs
//        }
//      }

//      "should prevent directory traversal attacks" in {
//        val router = Router()
//        val mockFs = new MockFS(testFiles)
//
//        val originalFs = js.Dynamic.global.global.fs
//        js.Dynamic.global.global.fs = mockFs.createMockImpl()
//
//        try {
//          router.use(Middlewares.fileServing(
//            path = "/static",
//            root = "./public",
//          ))
//
//          // Test different traversal attempts
//          val traversalPaths = List(
//            "/static/../secrets.txt",
//            "/static/subdir/../../config.json",
//            "/static/%2e%2e/private.key",
//          )
//
//          Future.sequence(
//            traversalPaths.map { path =>
//              val request = Request("GET", path, Map())
//              router.handle(request).map { response =>
//                response.status shouldBe 403
//                response.body should include("Forbidden")
//              }
//            },
//          ).map(_ => succeed)
//        } finally {
//          js.Dynamic.global.global.fs = originalFs
//        }
//      }

//      "should handle missing files correctly" in {
//        val router = Router()
//        val mockFs = new MockFS(testFiles)
//
//        val originalFs = js.Dynamic.global.global.fs
//        js.Dynamic.global.global.fs = mockFs.createMockImpl()
//
//        try {
//          router.use(Middlewares.fileServing(
//            path = "/static",
//            root = "./public",
//          ))
//
//          val request = Request("GET", "/static/nonexistent.txt", Map())
//
//          router.handle(request).map { response =>
//            response.status shouldBe 404
//            response.body should include("Not Found")
//          }
//        } finally {
//          js.Dynamic.global.global.fs = originalFs
//        }
//      }
    }

    "custom configuration" - {
//      "should respect custom MIME types" in {
//        val router = Router()
//        val mockFs = new MockFS(testFiles)
//
//        val originalFs = js.Dynamic.global.global.fs
//        js.Dynamic.global.global.fs = mockFs.createMockImpl()
//
//        try {
//          router.use(Middlewares.fileServing(
//            path = "/static",
//            root = "./public",
//            mimeTypes = Map(
//              "custom" -> "application/x-custom",
//            ),
//          ))
//
//          val request = Request("GET", "/static/test.custom", Map())
//
//          router.handle(request).map { response =>
//            response.status shouldBe 200
//            response.headers("Content-Type") shouldBe "application/x-custom"
//            response.body shouldBe "custom content"
//          }
//        } finally {
//          js.Dynamic.global.global.fs = originalFs
//        }
//      }

//      "should use custom index file name" in {
//        val router = Router()
//        val mockFs = new MockFS(testFiles + (
//          "./public/subdir/welcome.html" -> MockFile("<html><body>Welcome</body></html>")
//        ))
//
//        val originalFs = js.Dynamic.global.global.fs
//        js.Dynamic.global.global.fs = mockFs.createMockImpl()
//
//        try {
//          router.use(Middlewares.fileServing(
//            path = "/static",
//            root = "./public",
//            index = "welcome.html",
//          ))
//
//          val request = Request("GET", "/static/subdir/", Map())
//
//          router.handle(request).map { response =>
//            response.status shouldBe 200
//            response.headers("Content-Type") shouldBe "text/html"
//            response.body shouldBe "<html><body>Welcome</body></html>"
//          }
//        } finally {
//          js.Dynamic.global.global.fs = originalFs
//        }
//      }
    }
  }
