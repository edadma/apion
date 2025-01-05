package io.github.edadma.apion

import scala.concurrent.Future
import io.github.edadma.nodejs.{Buffer, bufferMod, ServerRequest}
import scala.scalajs.js

class RequestTests extends AsyncBaseSpec {
  // Helper to create a mock request that will emit string data with a specific content-type
  def mockRequestWithEncoding(data: String, contentType: String): Request = {
    val req = js.Dynamic.literal(
      method = "POST",
      url = "/test",
      headers = js.Dictionary("content-type" -> contentType),
      on = { (event: String, handler: js.Function1[js.Any, Unit]) =>
        if (event == "data") {
          // Convert string to Buffer using specified encoding
          val buffer = bufferMod.Buffer.from(data)
          handler(buffer)
        } else if (event == "end") {
          handler(())
        }
        js.Dynamic.literal()
      },
    )
    Request.fromServerRequest(req.asInstanceOf[ServerRequest])
  }

  "Request body parsing" - {
    "should handle different content-type charset specifications" in {
      val asciiString = "Hello, cafe"     // ASCII-safe characters only
      val latinString = "Hello, café"     // Latin-1 safe
      val utf8String  = "Hello, café € ¥" // Requires UTF-8 for full representation

      val requests = List(
        ("text/plain; charset=ascii", bufferMod.Buffer.from(asciiString, "ascii"), asciiString),
        ("text/plain;charset=latin1", bufferMod.Buffer.from(latinString, "latin1"), latinString),
        ("text/plain; charset=binary", bufferMod.Buffer.from(latinString, "binary"), latinString),
        // This one should work with current implementation
        ("text/plain", bufferMod.Buffer.from(utf8String, "utf8"), utf8String),
      )

      Future.sequence(
        requests.map { case (contentType, encodedData, expected) =>
          val request = mockRequestWithEncoding(encodedData, contentType)
          request.text.map { result =>
            result shouldBe expected
          }
        },
      ).map(_ => succeed)
    }

    // Helper update
    def mockRequestWithEncoding(data: Buffer, contentType: String): Request = {
      val req = js.Dynamic.literal(
        method = "POST",
        url = "/test",
        headers = js.Dictionary("content-type" -> contentType),
        on = { (event: String, handler: js.Function1[js.Any, Unit]) =>
          if (event == "data") {
            handler(data)
          } else if (event == "end") {
            handler(())
          }
          js.Dynamic.literal()
        },
      )
      Request.fromServerRequest(req.asInstanceOf[ServerRequest])
    }

    "should handle missing content-type header" in {
      val testString = "Hello, 世界"
      val req = js.Dynamic.literal(
        method = "POST",
        url = "/test",
        headers = js.Dictionary[String](),
        on = { (event: String, handler: js.Function1[js.Any, Unit]) =>
          if (event == "data") {
            handler(bufferMod.Buffer.from(testString))
          } else if (event == "end") {
            handler(())
          }
          js.Dynamic.literal()
        },
      )

      val request = Request.fromServerRequest(req.asInstanceOf[ServerRequest])
      request.text.map { result =>
        result shouldBe testString
      }
    }

    "should handle different chunk boundaries with UTF-8" in {
      // '世' is encoded as E4 B8 96 in UTF-8 (3 bytes)
      val testString = "Hello世界"
      val buffer     = bufferMod.Buffer.from(testString)

      // Split after "Hello" and in the middle of '世' (after first byte)
      val req = js.Dynamic.literal(
        method = "POST",
        url = "/test",
        headers = js.Dictionary("content-type" -> "text/plain; charset=utf-8"),
        on = { (event: String, handler: js.Function1[js.Any, Unit]) =>
          if (event == "data") {
            // First chunk: "Hello" + first byte of '世'
            handler(buffer.slice(0, 6)) // 5 bytes for "Hello" + 1 byte of '世'
            // Second chunk: rest of '世' + '界'
            handler(buffer.slice(6, buffer.length))
          } else if (event == "end") {
            handler(())
          }
          js.Dynamic.literal()
        },
      )

      val request = Request.fromServerRequest(req.asInstanceOf[ServerRequest])
      request.text.map { result =>
        result shouldBe testString
      }
    }
  }
}
