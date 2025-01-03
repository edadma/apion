package io.github.edadma.apion

import io.github.edadma.nodejs.{Buffer, bufferMod}
import zio.json.*

import scala.language.implicitConversions

case class Response(
    status: Int = 200,
    headers: ResponseHeaders = ResponseHeaders.empty,
    body: ResponseBody = EmptyBody,
):
  def bodyText: String =
    body match
      case TextBody(content, _, _) => content
      case ContentBody(_)          => sys.error(s"bodyText: binary body")
      case EmptyBody               => sys.error(s"bodyText: no body")

object Response:
  // Global configuration for default headers
  private var defaultHeaders = Seq(
    "server"        -> "Apion",
    "cache-control" -> "no-store, no-cache, must-revalidate, max-age=0",
    "pragma"        -> "no-cache",
    "expires"       -> "0",
    "x-powered-by"  -> "Apion",
  )

  /** Configure global default headers
    * @param headers
    *   Map of headers to set globally
    */
  def configure(headers: Seq[(String, String)]): Unit =
    defaultHeaders = defaultHeaders ++ headers

  /** Reset default headers to original state */
  def resetDefaultHeaders(): Unit =
    defaultHeaders = Seq(
      "Server"        -> "Apion",
      "Cache-Control" -> "no-store, no-cache, must-revalidate, max-age=0",
      "Pragma"        -> "no-cache",
      "Expires"       -> "0",
      "X-Powered-By"  -> "Apion",
    )

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
    val text   = data.toJson
    val buffer = bufferMod.Buffer.from(text, encoding)

    Response(
      status = status,
      headers =
        ResponseHeaders(standardHeaders
          .appended("Content-Type" -> s"application/json; charset=$encoding")
          .appended("Content-Length" -> buffer.length.toString)
          .appendedAll(additionalHeaders)),
      body = TextBody(text, encoding, buffer),
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
      body = TextBody(content, encoding, buffer),
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
      body = ContentBody(content),
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
