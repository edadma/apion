package io.github.edadma.apion

import io.github.edadma.nodejs.{Buffer, bufferMod, ServerRequest}

import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array
import scala.util.boundary
import scala.util.boundary.break

/**
 * A streaming multipart/form-data parser.
 * Pure Scala.js implementation — no npm dependencies.
 *
 * Multipart format (RFC 2046):
 * {{{
 * --<boundary>\r\n
 * Content-Disposition: form-data; name="field"; filename="file.txt"\r\n
 * Content-Type: text/plain\r\n
 * \r\n
 * <content bytes>\r\n
 * --<boundary>\r\n
 * Content-Disposition: form-data; name="text"\r\n
 * \r\n
 * <text value>\r\n
 * --<boundary>--\r\n
 * }}}
 */
object MultipartParser:

  /** A parsed part from the multipart body. */
  case class Part(
      name: String,
      filename: Option[String],
      contentType: String,
      encoding: String,
      data: Buffer,
  )

  /** Extract the boundary from a Content-Type header value. */
  def extractBoundary(contentType: String): Option[String] =
    contentType.split(";").map(_.trim).collectFirst {
      case s if s.toLowerCase.startsWith("boundary=") =>
        val value = s.substring(9).trim
        if value.startsWith("\"") && value.endsWith("\"") then value.substring(1, value.length - 1)
        else value
    }

  /**
   * Parse a multipart body from a complete Buffer.
   *
   * @param body     the complete request body as a Buffer
   * @param boundary the multipart boundary string
   * @return         list of parsed parts, or an error message
   */
  def parse(body: Buffer, boundary: String): Either[String, List[Part]] =
    val delimiter      = s"--$boundary"
    val bodyBytes      = bufferToArray(body)
    val delimiterBytes = delimiter.getBytes("utf-8")
    val positions      = findAllPositions(bodyBytes, delimiterBytes)

    if positions.isEmpty then Left("no boundary found in body")
    else parseParts(bodyBytes, delimiterBytes, positions)

  private def parseParts(
      bodyBytes: Array[Byte],
      delimiterBytes: Array[Byte],
      positions: List[Int],
  ): Either[String, List[Part]] =
    boundary:
      val parts = List.newBuilder[Part]

      for i <- 0 until positions.length - 1 do
        val start = positions(i) + delimiterBytes.length
        val end   = positions(i + 1)

        // Skip leading \r\n after delimiter
        val partStart =
          if start < bodyBytes.length - 1 && bodyBytes(start) == '\r' && bodyBytes(start + 1) == '\n'
          then start + 2
          else start

        // Remove trailing \r\n before next delimiter
        val partEnd =
          if end >= 2 && bodyBytes(end - 2) == '\r' && bodyBytes(end - 1) == '\n'
          then end - 2
          else end

        if partStart < partEnd then
          parseSinglePart(bodyBytes, partStart, partEnd) match
            case Right(part) => parts += part
            case Left(err)   => break(Left(s"part $i: $err"))

      Right(parts.result())

  /**
   * Parse a multipart body from a ServerRequest stream.
   * Collects all chunks then parses.
   *
   * @param request  the incoming request
   * @param boundary the multipart boundary
   * @param maxSize  maximum body size in bytes
   * @return         future list of parsed parts
   */
  def parseStream(
      request: ServerRequest,
      boundary: String,
      maxSize: Long = 50L * 1024 * 1024,
  ): Future[List[Part]] =
    val promise = Promise[List[Part]]()
    val chunks  = new scala.collection.mutable.ArrayBuffer[Buffer]()
    var total   = 0L

    request.on(
      "data",
      (chunk: Buffer) => {
        total += chunk.length
        if total > maxSize then
          if !promise.isCompleted then
            promise.failure(new Exception(s"multipart body exceeds maximum size of $maxSize bytes"))
          request.destroy(js.Error("Body too large"))
        else chunks += chunk
      },
    )

    request.on(
      "end",
      () => {
        if !promise.isCompleted then
          val body = bufferMod.Buffer.concat(js.Array(chunks.toArray*))
          parse(body, boundary) match
            case Right(parts) => promise.success(parts)
            case Left(err)    => promise.failure(new Exception(err))
      },
    )

    request.on(
      "error",
      (err: js.Error) => {
        if !promise.isCompleted then
          promise.failure(new Exception(s"stream error: ${err.message}"))
      },
    )

    promise.future

  // --- Internal parsing ---

  private def parseSinglePart(bytes: Array[Byte], start: Int, end: Int): Either[String, Part] =
    // Find the blank line separating headers from body (\r\n\r\n)
    val separator = "\r\n\r\n".getBytes("utf-8")
    val headerEnd = findSequence(bytes, separator, start, end)

    if headerEnd < 0 then Left("missing header/body separator")
    else
      val headerStr = new String(bytes.slice(start, headerEnd), "utf-8")
      val bodyStart = headerEnd + 4 // skip \r\n\r\n
      val bodyData  = bytesToBuffer(bytes, bodyStart, end)

      // Parse headers
      val headers = parseHeaders(headerStr)

      // Extract Content-Disposition fields
      val disposition = headers.getOrElse("content-disposition", "")
      val name        = extractHeaderParam(disposition, "name").getOrElse("")
      val filename    = extractHeaderParam(disposition, "filename")
      val contentType = headers.getOrElse("content-type", "application/octet-stream")
      val encoding    = headers.getOrElse("content-transfer-encoding", "binary")

      if name.isEmpty then Left("missing name in Content-Disposition")
      else Right(Part(name, filename, contentType, encoding, bodyData))

  private def parseHeaders(headerStr: String): Map[String, String] =
    headerStr
      .split("\r\n")
      .flatMap { line =>
        val colonIdx = line.indexOf(':')
        if colonIdx > 0 then Some(line.substring(0, colonIdx).trim.toLowerCase -> line.substring(colonIdx + 1).trim)
        else None
      }
      .toMap

  /** Extract a named parameter from a header value like: form-data; name="field"; filename="file.txt" */
  private[apion] def extractHeaderParam(header: String, param: String): Option[String] =
    val quoted = s"""(?i)$param\\s*=\\s*"([^"]*)"""".r
    quoted.findFirstMatchIn(header).map(_.group(1))
      .orElse {
        val unquoted = s"""(?i)$param\\s*=\\s*([^;\\s]+)""".r
        unquoted.findFirstMatchIn(header).map(_.group(1))
      }

  // --- Byte utilities ---

  private def bufferToArray(buf: Buffer): Array[Byte] =
    val dyn = buf.asInstanceOf[js.Dynamic]
    val arr = new Array[Byte](buf.length)
    for i <- 0 until buf.length do arr(i) = dyn.selectDynamic(i.toString).asInstanceOf[Int].toByte
    arr

  private def bytesToBuffer(bytes: Array[Byte], from: Int, to: Int): Buffer =
    val len   = to - from
    val uint8 = new Uint8Array(len)
    for i <- 0 until len do uint8(i) = (bytes(from + i) & 0xff).toShort
    bufferMod.Buffer.from(uint8)

  /** Find all positions of `needle` in `haystack`. */
  private[apion] def findAllPositions(haystack: Array[Byte], needle: Array[Byte]): List[Int] =
    val positions = List.newBuilder[Int]
    var i         = 0
    while i <= haystack.length - needle.length do
      if matchesAt(haystack, needle, i) then
        positions += i
        i += needle.length
      else i += 1
    positions.result()

  /** Find the first occurrence of `needle` in `haystack` between `from` and `to`. */
  private def findSequence(haystack: Array[Byte], needle: Array[Byte], from: Int, to: Int): Int =
    var i = from
    while i <= to - needle.length do
      if matchesAt(haystack, needle, i) then return i
      i += 1
    -1

  private def matchesAt(haystack: Array[Byte], needle: Array[Byte], offset: Int): Boolean =
    if offset + needle.length > haystack.length then false
    else
      var i     = 0
      var equal = true
      while i < needle.length && equal do
        if haystack(offset + i) != needle(i) then equal = false
        i += 1
      equal
