package io.github.edadma.apion

import io.github.edadma.nodejs.bufferMod

class MultipartParserTests extends AsyncBaseSpec {

  private def makeBody(boundary: String, parts: String*): String =
    parts.map(p => s"--$boundary\r\n$p").mkString("") + s"--$boundary--\r\n"

  private def toBuffer(s: String) = bufferMod.Buffer.from(s, "utf-8")

  val boundary = "----FormBoundary123"

  // --- extractBoundary ---

  "extractBoundary" - {
    "should extract unquoted boundary" in {
      val ct = "multipart/form-data; boundary=----FormBoundary123"
      MultipartParser.extractBoundary(ct) shouldBe Some("----FormBoundary123")
    }

    "should extract quoted boundary" in {
      val ct = """multipart/form-data; boundary="----FormBoundary123""""
      MultipartParser.extractBoundary(ct) shouldBe Some("----FormBoundary123")
    }

    "should handle extra whitespace" in {
      val ct = "multipart/form-data;  boundary=abc"
      MultipartParser.extractBoundary(ct) shouldBe Some("abc")
    }

    "should return None for missing boundary" in {
      MultipartParser.extractBoundary("multipart/form-data") shouldBe None
    }

    "should return None for non-multipart content type" in {
      MultipartParser.extractBoundary("application/json") shouldBe None
    }

    "should handle boundary with special characters" in {
      val ct = "multipart/form-data; boundary=--Boundary_with-special.chars"
      MultipartParser.extractBoundary(ct) shouldBe Some("--Boundary_with-special.chars")
    }
  }

  // --- extractHeaderParam ---

  "extractHeaderParam" - {
    "should extract quoted parameter" in {
      val header = """form-data; name="myfield"; filename="test.txt""""
      MultipartParser.extractHeaderParam(header, "name") shouldBe Some("myfield")
      MultipartParser.extractHeaderParam(header, "filename") shouldBe Some("test.txt")
    }

    "should extract unquoted parameter" in {
      val header = "form-data; name=myfield"
      MultipartParser.extractHeaderParam(header, "name") shouldBe Some("myfield")
    }

    "should return None for missing parameter" in {
      val header = """form-data; name="field""""
      MultipartParser.extractHeaderParam(header, "filename") shouldBe None
    }

    "should be case-insensitive" in {
      val header = """form-data; Name="field""""
      MultipartParser.extractHeaderParam(header, "name") shouldBe Some("field")
    }

    "should handle empty quoted value" in {
      val header = """form-data; name="""""
      MultipartParser.extractHeaderParam(header, "name") shouldBe Some("")
    }
  }

  // --- parse: single text field ---

  "parse" - {
    "should parse a single text field" in {
      val body = makeBody(
        boundary,
        "Content-Disposition: form-data; name=\"field1\"\r\n\r\nvalue1\r\n",
      )
      val result = MultipartParser.parse(toBuffer(body), boundary)
      result.isRight shouldBe true
      val parts = result.toOption.get
      parts should have length 1
      parts.head.name shouldBe "field1"
      parts.head.filename shouldBe None
      parts.head.data.toString("utf-8") shouldBe "value1"
    }

    "should parse multiple text fields" in {
      val body = makeBody(
        boundary,
        "Content-Disposition: form-data; name=\"field1\"\r\n\r\nvalue1\r\n",
        "Content-Disposition: form-data; name=\"field2\"\r\n\r\nvalue2\r\n",
      )
      val result = MultipartParser.parse(toBuffer(body), boundary)
      result.isRight shouldBe true
      val parts = result.toOption.get
      parts should have length 2
      parts(0).name shouldBe "field1"
      parts(0).data.toString("utf-8") shouldBe "value1"
      parts(1).name shouldBe "field2"
      parts(1).data.toString("utf-8") shouldBe "value2"
    }

    "should parse file upload with filename and content type" in {
      val body = makeBody(
        boundary,
        "Content-Disposition: form-data; name=\"file\"; filename=\"test.txt\"\r\nContent-Type: text/plain\r\n\r\nhello world\r\n",
      )
      val result = MultipartParser.parse(toBuffer(body), boundary)
      result.isRight shouldBe true
      val part = result.toOption.get.head
      part.name shouldBe "file"
      part.filename shouldBe Some("test.txt")
      part.contentType shouldBe "text/plain"
      part.data.toString("utf-8") shouldBe "hello world"
    }

    "should parse mixed fields and files" in {
      val body = makeBody(
        boundary,
        "Content-Disposition: form-data; name=\"description\"\r\n\r\nA test file\r\n",
        "Content-Disposition: form-data; name=\"file\"; filename=\"data.bin\"\r\nContent-Type: application/octet-stream\r\n\r\nbinarydata\r\n",
      )
      val result = MultipartParser.parse(toBuffer(body), boundary)
      result.isRight shouldBe true
      val parts = result.toOption.get
      parts should have length 2
      parts(0).name shouldBe "description"
      parts(0).filename shouldBe None
      parts(1).name shouldBe "file"
      parts(1).filename shouldBe Some("data.bin")
      parts(1).contentType shouldBe "application/octet-stream"
    }

    "should handle empty body" in {
      val body = s"------FormBoundary123------FormBoundary123--\r\n"
      val result = MultipartParser.parse(toBuffer(body), boundary)
      // Should parse without error but produce no parts
      result.isRight shouldBe true
    }

    "should fail with wrong boundary" in {
      val body = makeBody(
        "wrong-boundary",
        "Content-Disposition: form-data; name=\"field\"\r\n\r\nvalue\r\n",
      )
      val result = MultipartParser.parse(toBuffer(body), boundary)
      result.isLeft shouldBe true
      result.left.toOption.get should include("no boundary found")
    }

    "should handle multiline text value" in {
      val body = makeBody(
        boundary,
        "Content-Disposition: form-data; name=\"text\"\r\n\r\nline1\nline2\nline3\r\n",
      )
      val result = MultipartParser.parse(toBuffer(body), boundary)
      result.isRight shouldBe true
      result.toOption.get.head.data.toString("utf-8") shouldBe "line1\nline2\nline3"
    }

    "should handle binary data in file upload" in {
      // Create a body with binary bytes
      val textPart = s"--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"bin\"\r\nContent-Type: application/octet-stream\r\n\r\n"
      val binaryData = Array[Byte](0, 1, 2, 127, -128, -1)
      val ending = s"\r\n--$boundary--\r\n"

      val textBuf   = bufferMod.Buffer.from(textPart, "utf-8")
      val dataBuf   = bufferMod.Buffer.from(new scala.scalajs.js.typedarray.Uint8Array(
        scala.scalajs.js.Array(binaryData.map(b => (b & 0xff).toShort)*),
      ))
      val endBuf    = bufferMod.Buffer.from(ending, "utf-8")
      val fullBody  = bufferMod.Buffer.concat(scala.scalajs.js.Array(textBuf, dataBuf, endBuf))

      val result = MultipartParser.parse(fullBody, boundary)
      result.isRight shouldBe true
      val part = result.toOption.get.head
      part.filename shouldBe Some("bin")
      part.data.length shouldBe 6
    }

    "should default content type to application/octet-stream" in {
      val body = makeBody(
        boundary,
        "Content-Disposition: form-data; name=\"file\"; filename=\"noct\"\r\n\r\ndata\r\n",
      )
      val result = MultipartParser.parse(toBuffer(body), boundary)
      result.toOption.get.head.contentType shouldBe "application/octet-stream"
    }

    "should parse content-transfer-encoding header" in {
      val body = makeBody(
        boundary,
        "Content-Disposition: form-data; name=\"file\"; filename=\"f\"\r\nContent-Transfer-Encoding: base64\r\n\r\nZGF0YQ==\r\n",
      )
      val result = MultipartParser.parse(toBuffer(body), boundary)
      result.toOption.get.head.encoding shouldBe "base64"
    }

    "should default encoding to binary" in {
      val body = makeBody(
        boundary,
        "Content-Disposition: form-data; name=\"file\"; filename=\"f\"\r\n\r\ndata\r\n",
      )
      val result = MultipartParser.parse(toBuffer(body), boundary)
      result.toOption.get.head.encoding shouldBe "binary"
    }

    "should fail if Content-Disposition has no name" in {
      val body = makeBody(
        boundary,
        "Content-Disposition: form-data\r\n\r\nvalue\r\n",
      )
      val result = MultipartParser.parse(toBuffer(body), boundary)
      result.isLeft shouldBe true
      result.left.toOption.get should include("missing name")
    }

    "should handle multiple files with same field name" in {
      val body = makeBody(
        boundary,
        "Content-Disposition: form-data; name=\"files\"; filename=\"a.txt\"\r\nContent-Type: text/plain\r\n\r\naaa\r\n",
        "Content-Disposition: form-data; name=\"files\"; filename=\"b.txt\"\r\nContent-Type: text/plain\r\n\r\nbbb\r\n",
      )
      val result = MultipartParser.parse(toBuffer(body), boundary)
      result.isRight shouldBe true
      val parts = result.toOption.get
      parts should have length 2
      parts(0).filename shouldBe Some("a.txt")
      parts(1).filename shouldBe Some("b.txt")
    }

    "should handle large number of parts" in {
      val partStrings = (1 to 20).map { i =>
        s"Content-Disposition: form-data; name=\"field$i\"\r\n\r\nvalue$i\r\n"
      }
      val body = makeBody(boundary, partStrings*)
      val result = MultipartParser.parse(toBuffer(body), boundary)
      result.isRight shouldBe true
      result.toOption.get should have length 20
    }
  }

  // --- findAllPositions ---

  "findAllPositions" - {
    "should find all occurrences" in {
      val haystack = "abcXYZdefXYZghi".getBytes("utf-8")
      val needle   = "XYZ".getBytes("utf-8")
      MultipartParser.findAllPositions(haystack, needle) shouldBe List(3, 9)
    }

    "should return empty for no match" in {
      val haystack = "abcdef".getBytes("utf-8")
      val needle   = "XYZ".getBytes("utf-8")
      MultipartParser.findAllPositions(haystack, needle) shouldBe Nil
    }

    "should handle needle at start" in {
      val haystack = "XYZabc".getBytes("utf-8")
      val needle   = "XYZ".getBytes("utf-8")
      MultipartParser.findAllPositions(haystack, needle) shouldBe List(0)
    }

    "should handle needle at end" in {
      val haystack = "abcXYZ".getBytes("utf-8")
      val needle   = "XYZ".getBytes("utf-8")
      MultipartParser.findAllPositions(haystack, needle) shouldBe List(3)
    }

    "should handle empty haystack" in {
      val haystack = Array.empty[Byte]
      val needle   = "XYZ".getBytes("utf-8")
      MultipartParser.findAllPositions(haystack, needle) shouldBe Nil
    }
  }
}
