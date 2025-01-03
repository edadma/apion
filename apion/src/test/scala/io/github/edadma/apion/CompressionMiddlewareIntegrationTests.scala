package io.github.edadma.apion

import scala.concurrent.Future
import scala.scalajs.js
import io.github.edadma.nodejs.{
  FetchOptions,
  ServerRequest,
  ServerResponse,
  bufferMod,
  fetch,
  http,
  Server as NodeServer,
}
import org.scalatest.BeforeAndAfterAll

import scala.compiletime.uninitialized

class CompressionMiddlewareIntegrationTests extends AsyncBaseSpec with BeforeAndAfterAll:
  var server: Server         = uninitialized
  var httpServer: NodeServer = uninitialized
  val port                   = 3006 // Different port for compression tests

  override def beforeAll(): Unit =
    server = Server()
      .use(CompressionMiddleware(CompressionMiddleware.Options(
        threshold = 10,                     // Low threshold for testing
        encodings = List("gzip", "deflate"), // Test both encodings
      )))
      .get("/large", _ => ("a" * 1000).asText) // Large enough to trigger compression
      .get("/small", _ => "tiny".asText)       // Too small to compress
      .get("/binary", _ => bufferMod.Buffer.from("some binary data").asBinary)

    httpServer = server.listen(port) {}

  override def afterAll(): Unit =
    if httpServer != null then
      httpServer.close(() => ())

  "CompressionMiddleware" - {
    "should compress large text responses" in {
      val options = FetchOptions(
        headers = js.Dictionary(
          "Accept-Encoding" -> "gzip",
        ),
      )

      fetch(s"http://localhost:$port/large", options)
        .toFuture
        .map { response =>
          response.headers.get("Content-Encoding") shouldBe "gzip"
          response.headers.has("Content-Length") shouldBe true
          response.headers.get("Vary") shouldBe "Accept-Encoding"
        }
    }

    "should not compress small responses" in {
      val options = FetchOptions(
        headers = js.Dictionary(
          "Accept-Encoding" -> "gzip",
        ),
      )

      fetch(s"http://localhost:$port/small", options)
        .toFuture
        .map { response =>
          response.headers.has("Content-Encoding") shouldBe false
        }
    }

    "should respect client's preferred encoding" in {
      val options = FetchOptions(
        headers = js.Dictionary(
          "Accept-Encoding" -> "deflate",
        ),
      )

      fetch(s"http://localhost:$port/large", options)
        .toFuture
        .map { response =>
          response.headers.get("Content-Encoding") shouldBe "deflate"
        }
    }

//    "should verify content is correctly decompressed" in {
//      val largeText = "a" * 1000
//      val options = FetchOptions(
//        headers = js.Dictionary(
//          "Accept-Encoding" -> "gzip",
//        ),
//      )
//
//      fetch(s"http://localhost:$port/large", options)
//        .toFuture
//        .flatMap(response => response.text().toFuture)
//        .map { text =>
//          text shouldBe largeText
//        }
//    }

    "should verify compression works" in withDebugLogging(
      "should verify compression works",
    ) {
      val options = FetchOptions(
        headers = js.Dictionary(
          "Accept-Encoding" -> "gzip",
          "Accept"          -> "*/*",
        ),
      )

      for {
        response <- fetch(s"http://localhost:$port/large", options).toFuture
        _ = logger.debug(s"Response headers: ${response.headers}")
        _ = logger.debug(s"Content-Encoding: ${response.headers.get("Content-Encoding")}")
        _ = logger.debug(s"Content-Length: ${response.headers.get("Content-Length")}")
        body <- response.arrayBuffer().toFuture // Use arrayBuffer instead of text
      } yield {
        response.headers.get("Content-Encoding") shouldBe "gzip"
        response.headers.has("Content-Length") shouldBe true
        body.byteLength should be > 0
      }
    }
  }
