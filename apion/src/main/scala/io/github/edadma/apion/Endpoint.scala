package io.github.edadma.apion

import zio.json.*
import scala.concurrent.Future
import io.github.edadma.nodejs.{ServerRequest}

case class Request(
    method: String,
    url: String,
    headers: Map[String, String],
    auth: Option[Auth] = None,
    context: Map[String, Any] = Map(),
    rawRequest: Option[ServerRequest] = None,
):
  def header(h: String): Option[String] = headers.get(h.toLowerCase)

case class Response(
    status: Int = 200,
    headers: Map[String, String] = Map("Content-Type" -> "application/json"),
    body: String,
)

case class Auth(
    user: String,
    roles: Set[String] = Set(),
)

object Auth:
  given JsonEncoder[Auth] = DeriveJsonEncoder.gen[Auth]
  given JsonDecoder[Auth] = DeriveJsonDecoder.gen[Auth]

type Endpoint   = Request => Future[Response]
type Middleware = Endpoint => Endpoint

object Response:
  // Global configuration for default headers
  private var _defaultHeaders: Map[String, String] = Map(
    "Server"        -> "Apion",
    "Cache-Control" -> "no-store, no-cache, must-revalidate, max-age=0",
    "Pragma"        -> "no-cache",
    "Expires"       -> "0",
    "X-Powered-By"  -> "Apion",
  )

  /** Configure global default headers
    *
    * @param headers
    *   Map of headers to set globally
    */
  def configure(headers: Map[String, String]): Unit = {
    _defaultHeaders = _defaultHeaders ++ headers
  }

  /** Reset default headers to original state
    */
  def resetDefaultHeaders(): Unit = {
    _defaultHeaders = Map(
      "Server"        -> "Apion",
      "Cache-Control" -> "no-store, no-cache, must-revalidate, max-age=0",
      "Pragma"        -> "no-cache",
      "Expires"       -> "0",
    )
  }

  /** Create a JSON response with standard headers
    *
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
      additionalHeaders: Map[String, String] = Map.empty,
  ): Response =
    Response(
      status = status,
      headers = standardHeaders ++
        Map("Content-Type" -> "application/json") ++
        additionalHeaders,
      body = data.toJson,
    )

  /** Create a plain text response with standard headers
    *
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
      additionalHeaders: Map[String, String] = Map.empty,
  ): Response =
    Response(
      status = status,
      headers = standardHeaders ++
        Map("Content-Type" -> "text/plain") ++
        additionalHeaders,
      body = content,
    )

  /** Generate standard HTTP response headers Includes common headers like Date, Server, Cache-Control
    */
  private def standardHeaders: Map[String, String] = {
    // Use Java's DateTimeFormatter for RFC 1123 date format
    import java.time.{ZonedDateTime, ZoneOffset}
    import java.time.format.DateTimeFormatter
    import java.util.Locale

    val dateFormatter = DateTimeFormatter.RFC_1123_DATE_TIME
      .withLocale(Locale.CANADA)
      .withZone(ZoneOffset.UTC)

    _defaultHeaders ++ Map(
      "Date" -> dateFormatter.format(ZonedDateTime.now(ZoneOffset.UTC)),
    )
  }
