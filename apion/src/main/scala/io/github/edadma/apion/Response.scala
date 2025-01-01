package io.github.edadma.apion

import zio.json.*
import scala.concurrent.Future

case class Response(
    status: Int = 200,
    headers: ResponseHeaders = ResponseHeaders(Seq("Content-Type" -> "application/json")),
    body: String,
)

object Response:
  // Global configuration for default headers
  private var _defaultHeaders = ResponseHeaders(Seq(
    "Server"        -> "Apion",
    "Cache-Control" -> "no-store, no-cache, must-revalidate, max-age=0",
    "Pragma"        -> "no-cache",
    "Expires"       -> "0",
    "X-Powered-By"  -> "Apion",
  ))

  /** Configure global default headers
    * @param headers
    *   Map of headers to set globally
    */
  def configure(headers: Seq[(String, String)]): Unit =
    _defaultHeaders = _defaultHeaders.addAll(headers)

  /** Reset default headers to original state */
  def resetDefaultHeaders(): Unit =
    _defaultHeaders = ResponseHeaders(Seq(
      "Server"        -> "Apion",
      "Cache-Control" -> "no-store, no-cache, must-revalidate, max-age=0",
      "Pragma"        -> "no-cache",
      "Expires"       -> "0",
    ))

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
      headers = standardHeaders
        .add("Content-Type", "application/json")
        .addAll(additionalHeaders),
      body = data.toJson,
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
      headers = standardHeaders
        .add("Content-Type", "text/plain")
        .addAll(additionalHeaders),
      body = content,
    )

  /** Generate standard HTTP response headers Includes common headers like Date, Server, Cache-Control
    */
  private def standardHeaders: ResponseHeaders = {
    import java.time.{ZonedDateTime, ZoneOffset}
    import java.time.format.DateTimeFormatter
    import java.util.Locale

    val dateFormatter = DateTimeFormatter.RFC_1123_DATE_TIME
      .withLocale(Locale.CANADA)
      .withZone(ZoneOffset.UTC)

    _defaultHeaders.add(
      "Date",
      dateFormatter.format(ZonedDateTime.now(ZoneOffset.UTC)),
    )
  }
