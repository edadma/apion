//package io.github.edadma.apion
//
//import scala.concurrent.Future
//import scala.scalajs.js
//import io.github.edadma.nodejs.{
//  stream,
//  fs,
//  Buffer,
//  bufferMod,
//  FetchOptions,
//  ReadableStream,
//  fetch,
//  Server as NodeServer,
//}
//import org.scalatest.BeforeAndAfterAll
//import scala.compiletime.uninitialized
//import zio.json.*
//
//class FileUploadIntegrationTests extends AsyncBaseSpec with BeforeAndAfterAll {
//  var server: Server         = uninitialized
//  var httpServer: NodeServer = uninitialized
//  val port                   = 3009 // Different port for file upload tests
//  val testFileContent        = "Test file content"
//
//  // Helper to create a mock file stream
//  def createMockFileStream(content: String): ReadableStream = stream.Readable.from(bufferMod.Buffer.from(content))
//
//  // Helper to create a multipart form body
//  def createMultipartBody(
//      fields: Map[String, String] = Map.empty,
//      files: Map[String, (String, String, String)] = Map.empty, // fieldname -> (filename, content, mimetype)
//  ): (String, Buffer) = {
//    val boundary  = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
//    val lineBreak = "\r\n"
//    val chunks    = new scala.collection.mutable.ArrayBuffer[String]()
//
//    // Add regular fields
//    fields.foreach { case (key, value) =>
//      chunks.append(
//        s"""--$boundary
//           |Content-Disposition: form-data; name="$key"
//           |
//           |$value""".stripMargin,
//      )
//    }
//
//    // Add files
//    files.foreach { case (fieldname, (filename, content, mimetype)) =>
//      chunks.append(
//        s"""--$boundary
//           |Content-Disposition: form-data; name="$fieldname"; filename="$filename"
//           |Content-Type: $mimetype
//           |
//           |$content""".stripMargin,
//      )
//    }
//
//    // Add final boundary
//    chunks.append(s"--$boundary--")
//
//    val body = chunks.mkString(lineBreak)
//    (boundary, bufferMod.Buffer.from(body))
//  }
//
//  case class FieldUploadResponse(file: Option[UploadedFile], fields: Map[String, String]) derives JsonEncoder
//
//  override def beforeAll(): Unit = {
//    server = Server()
//      .use(FileUploadMiddleware(FileUploadOptions(
//        maxFileSize = 5 * 1024 * 1024, // 5MB
//        maxFiles = 3,
//        allowedMimes = Set("text/plain", "application/json"),
//        tempDir = "/tmp/upload-tests",
//      )))
//      // Single file upload
//      .post(
//        "/upload/single",
//        request => {
//          request.file("document").flatMap {
//            case Some(file) => file.asJson
//            case None       => "No file uploaded".asText(400)
//          }
//        },
//      )
//      // Multiple file upload
//      .post(
//        "/upload/multiple",
//        request => {
//          request.files("documents").flatMap { files =>
//            if (files.isEmpty) "No files uploaded".asText(400)
//            else files.asJson
//          }
//        },
//      )
//      // File with field data
//      .post(
//        "/upload/with-fields",
//        request => {
//          for {
//            file   <- request.file("document")
//            fields <- request.form
//            result <- FieldUploadResponse(file, fields).asJson
//          } yield {
//            result
//          }
//        },
//      )
//
//    httpServer = server.listen(port) {}
//  }
//
//  override def afterAll(): Unit = {
//    if (httpServer != null) {
//      httpServer.close(() => ())
//    }
//  }
//
//  "FileUploadMiddleware" - {
//    "single file upload" - {
//      "should handle basic file upload" in {
//        val (boundary, body) = createMultipartBody(
//          files = Map("document" -> ("test.txt", testFileContent, "text/plain")),
//        )
//
//        val options = FetchOptions(
//          method = "POST",
//          headers = js.Dictionary(
//            "Content-Type" -> s"multipart/form-data; boundary=$boundary",
//          ),
//          body = body,
//        )
//
//        fetch(s"http://localhost:$port/upload/single", options)
//          .toFuture
//          .flatMap { response =>
//            response.status shouldBe 200
//            response.json().toFuture
//          }
//          .map { result =>
//            val json = js.JSON.stringify(result)
//            json should include("test.txt")
//            json should include("text/plain")
//            json should include(testFileContent.length.toString)
//          }
//      }
//
//      "should handle missing file" in {
//        val (boundary, body) = createMultipartBody(
//          fields = Map("name" -> "test"), // No file
//        )
//
//        val options = FetchOptions(
//          method = "POST",
//          headers = js.Dictionary(
//            "Content-Type" -> s"multipart/form-data; boundary=$boundary",
//          ),
//          body = body,
//        )
//
//        fetch(s"http://localhost:$port/upload/single", options)
//          .toFuture
//          .map { response =>
//            response.status shouldBe 400
//          }
//      }
//
//      "should reject file with wrong mime type" in {
//        val (boundary, body) = createMultipartBody(
//          files = Map("document" -> ("test.jpg", "fake image content", "image/jpeg")),
//        )
//
//        val options = FetchOptions(
//          method = "POST",
//          headers = js.Dictionary(
//            "Content-Type" -> s"multipart/form-data; boundary=$boundary",
//          ),
//          body = body,
//        )
//
//        fetch(s"http://localhost:$port/upload/single", options)
//          .toFuture
//          .map { response =>
//            response.status shouldBe 400
//          }
//      }
//    }
//
//    "multiple file upload" - {
//      "should handle multiple files" in {
//        val (boundary, body) = createMultipartBody(
//          files = Map(
//            "documents" -> ("test1.txt", "content 1", "text/plain"),
//            "documents" -> ("test2.txt", "content 2", "text/plain"),
//          ),
//        )
//
//        val options = FetchOptions(
//          method = "POST",
//          headers = js.Dictionary(
//            "Content-Type" -> s"multipart/form-data; boundary=$boundary",
//          ),
//          body = body,
//        )
//
//        fetch(s"http://localhost:$port/upload/multiple", options)
//          .toFuture
//          .flatMap { response =>
//            response.status shouldBe 200
//            response.json().toFuture
//          }
//          .map { result =>
//            val json = js.JSON.stringify(result)
//            json should include("test1.txt")
//            json should include("test2.txt")
//          }
//      }
//
//      "should enforce file limit" in {
//        val (boundary, body) = createMultipartBody(
//          files = Map(
//            "documents" -> ("test1.txt", "content 1", "text/plain"),
//            "documents" -> ("test2.txt", "content 2", "text/plain"),
//            "documents" -> ("test3.txt", "content 3", "text/plain"),
//            "documents" -> ("test4.txt", "content 4", "text/plain"),
//          ),
//        )
//
//        val options = FetchOptions(
//          method = "POST",
//          headers = js.Dictionary(
//            "Content-Type" -> s"multipart/form-data; boundary=$boundary",
//          ),
//          body = body,
//        )
//
//        fetch(s"http://localhost:$port/upload/multiple", options)
//          .toFuture
//          .map { response =>
//            response.status shouldBe 400
//          }
//      }
//    }
//
//    "file upload with fields" - {
//      "should handle mixed file and field data" in {
//        val (boundary, body) = createMultipartBody(
//          fields = Map(
//            "name"        -> "test document",
//            "description" -> "test description",
//          ),
//          files = Map(
//            "document" -> ("test.txt", testFileContent, "text/plain"),
//          ),
//        )
//
//        val options = FetchOptions(
//          method = "POST",
//          headers = js.Dictionary(
//            "Content-Type" -> s"multipart/form-data; boundary=$boundary",
//          ),
//          body = body,
//        )
//
//        fetch(s"http://localhost:$port/upload/with-fields", options)
//          .toFuture
//          .flatMap { response =>
//            response.status shouldBe 200
//            response.json().toFuture
//          }
//          .map { result =>
//            val json = js.JSON.stringify(result)
//            json should include("test.txt")
//            json should include("test document")
//            json should include("test description")
//          }
//      }
//    }
//
//    "cleanup" - {
//      "should clean up files after request handling" in {
//        val (boundary, body) = createMultipartBody(
//          files = Map("document" -> ("test.txt", testFileContent, "text/plain")),
//        )
//
//        val options = FetchOptions(
//          method = "POST",
//          headers = js.Dictionary(
//            "Content-Type" -> s"multipart/form-data; boundary=$boundary",
//          ),
//          body = body,
//        )
//
//        for {
//          response <- fetch(s"http://localhost:$port/upload/single", options).toFuture
//          json     <- response.json().toFuture
//          path = js.JSON.stringify(json).fromJson[UploadedFile].toOption.get.path
//          // Wait a bit for cleanup
//          _ <- after(100) {
//            // Try to access the file - should fail
//            val stats = fs.promises.stat(path)
//            stats.toFuture.failed.map(_ => succeed)
//          }
//        } yield succeed
//      }
//    }
//  }
//}
