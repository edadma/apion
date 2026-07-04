package io.github.edadma.apion

import io.github.edadma.nodejs.{Buffer, ReadableStream, bufferMod}
import zio.json.*

import scala.language.implicitConversions

case class Response(
    status: Int = 200,
    headers: ResponseHeaders = ResponseHeaders.empty,
    body: ResponseBody = EmptyBody,
):
  def bodyText: String =
    body match
      case StringBody(content, _) => content
      case _: BufferBody          => sys.error(s"bodyText: binary body")
      case _: ReadableStreamBody  => sys.error(s"bodyText: stream body")
      case EmptyBody              => sys.error(s"bodyText: no body")

object Response:
  /** The out-of-the-box default headers applied to every response. */
  private val initialDefaultHeaders = Seq(
    "Server"        -> "Apion",
    "Cache-Control" -> "no-store, no-cache, must-revalidate, max-age=0",
    "Pragma"        -> "no-cache",
    "Expires"       -> "0",
    "X-Powered-By"  -> "Apion",
  )

  // Global configuration for default headers
  private var defaultHeaders = initialDefaultHeaders

  /** Configure global default headers. Any header whose name matches an existing
    * default (case-insensitively) replaces it, so callers can override built-ins
    * such as `Server` without producing duplicates.
    * @param headers
    *   Headers to set or override globally
    */
  def configure(headers: Seq[(String, String)]): Unit =
    defaultHeaders = headers.foldLeft(defaultHeaders) { (acc, header) =>
      acc.filterNot(_._1.equalsIgnoreCase(header._1)) :+ header
    }

  /** Reset default headers to their original out-of-the-box state. */
  def resetDefaultHeaders(): Unit =
    defaultHeaders = initialDefaultHeaders

  /** Create a streaming response */
  def stream(
      stream: ReadableStream,
      status: Int = 200,
      additionalHeaders: Seq[(String, String)] = Nil,
  ): Response = {
    // Don't set Content-Length for streams
    Response(
      status = status,
      headers = ResponseHeaders(standardHeaders.appendedAll(additionalHeaders)),
      body = ReadableStreamBody(stream),
    )
  }

  def noContent(additionalHeaders: Seq[(String, String)] = Nil): Response = Response(
    status = 204,
    headers = ResponseHeaders(standardHeaders.appendedAll(additionalHeaders)),
    body = EmptyBody,
  )

  /** Create a JSON response with standard headers
    * @param data
    *   Data to be JSON encoded
    * @param status
    *   HTTP status code (default 200)
    * @param additionalHeaders
    *   Optional additional headers
    */
  def json[A: JsonEncoder](
      data: A,
      status: Int = 200,
      additionalHeaders: Seq[(String, String)] = Nil,
      encoding: String = "utf8",
  ): Response =
    val isDevelopment = !sys.env.get("NODE_ENV").contains("production")
    val text          = if isDevelopment then data.toJsonPretty + '\n' else data.toJson
    val buffer        = bufferMod.Buffer.from(text, encoding)

    Response(
      status = status,
      headers =
        ResponseHeaders(standardHeaders
          .appended("Content-Type" -> s"application/json; charset=$encoding")
          .appended("Content-Length" -> buffer.length.toString)
          .appendedAll(additionalHeaders)),
      body = StringBody(text, buffer),
    )

  /** Create a plain text response with standard headers
    * @param content
    *   Text content
    * @param status
    *   HTTP status code (default 200)
    * @param additionalHeaders
    *   Optional additional headers
    */
  def text(
      content: String,
      status: Int = 200,
      encoding: String = "utf8",
      additionalHeaders: Seq[(String, String)] = Nil,
  ): Response =
    val buffer = bufferMod.Buffer.from(content, encoding)

    Response(
      status = status,
      headers =
        ResponseHeaders(
          standardHeaders
            .appended("Content-Type" -> s"text/plain; charset=$encoding")
            .appended("Content-Length" -> buffer.length.toString)
            .appendedAll(additionalHeaders),
        ),
      body = StringBody(content, buffer),
    )

  /** Create a binary response with standard headers
    *
    * @param content
    *   Binary content
    * @param status
    *   HTTP status code (default 200)
    * @param additionalHeaders
    *   Optional additional headers
    */
  def binary(
      content: Buffer,
      status: Int = 200,
      additionalHeaders: Seq[(String, String)] = Nil,
  ): Response =
    Response(
      status = status,
      headers =
        ResponseHeaders(
          standardHeaders
            .appended("Content-Type" -> "application/octet-stream")
            .appended("Content-Length" -> content.length.toString)
            .appendedAll(additionalHeaders),
        ),
      body = BufferBody(content),
    )

  /** Generate standard HTTP response headers Includes common headers like Date, Server, Cache-Control
    */
  private def standardHeaders: Seq[(String, String)] = {
    import java.time.{ZonedDateTime, ZoneOffset}
    import java.time.format.DateTimeFormatter
    import java.util.Locale

    val dateFormatter = DateTimeFormatter.RFC_1123_DATE_TIME
      .withLocale(Locale.CANADA)
      .withZone(ZoneOffset.UTC)

    defaultHeaders :+ ("Date" -> dateFormatter.format(ZonedDateTime.now(ZoneOffset.UTC)))
  }
