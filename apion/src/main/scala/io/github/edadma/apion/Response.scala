package io.github.edadma.apion

import io.github.edadma.apion.ResponseBody.Text
import io.github.edadma.nodejs.Buffer
import zio.json.*

import scala.language.implicitConversions

case class Response(
    status: Int = 200,
    headers: ResponseHeaders = ResponseHeaders.empty,
    body: ResponseBody = ResponseBody.Empty,
):
  def bodyText: String = body match
    case Text(content, _) => content
    case _                => sys.error(s"Response body was not text: ${body}")

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
  ): Response =
    Response(
      status = status,
      headers =
        ResponseHeaders(standardHeaders.appended("Content-Type" -> "application/json").appendedAll(additionalHeaders)),
      body = Text(data.toJson),
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
      additionalHeaders: Seq[(String, String)] = Nil,
  ): Response =
    Response(
      status = status,
      headers =
        ResponseHeaders(standardHeaders.appended("Content-Type" -> "text/plain").appendedAll(additionalHeaders)),
      body = Text(content),
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
        ResponseHeaders(standardHeaders.appended("Content-Type" -> "text/plain").appendedAll(additionalHeaders)),
      body = ResponseBody.Binary(content),
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
