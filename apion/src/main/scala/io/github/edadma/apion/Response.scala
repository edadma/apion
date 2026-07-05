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
  /** Create a streaming response */
  def stream(
      stream: ReadableStream,
      status: Int = 200,
      additionalHeaders: Seq[(String, String)] = Nil,
  ): Response = {
    // Don't set Content-Length for streams
    Response(
      status = status,
      headers = ResponseHeaders(additionalHeaders),
      body = ReadableStreamBody(stream),
    )
  }

  def noContent(additionalHeaders: Seq[(String, String)] = Nil): Response = Response(
    status = 204,
    headers = ResponseHeaders(additionalHeaders),
    body = EmptyBody,
  )

  /** Create a JSON response (Content-Type and Content-Length set; server default
    * headers are applied later at the send boundary)
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
      headers = ResponseHeaders(
        Seq(
          "Content-Type"   -> s"application/json; charset=$encoding",
          "Content-Length" -> buffer.length.toString,
        ) ++ additionalHeaders,
      ),
      body = StringBody(text, buffer),
    )

  /** Create a plain text response (Content-Type and Content-Length set; server
    * default headers are applied later at the send boundary)
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
      headers = ResponseHeaders(
        Seq(
          "Content-Type"   -> s"text/plain; charset=$encoding",
          "Content-Length" -> buffer.length.toString,
        ) ++ additionalHeaders,
      ),
      body = StringBody(content, buffer),
    )

  /** Create a binary response (Content-Type and Content-Length set; server default
    * headers are applied later at the send boundary)
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
      headers = ResponseHeaders(
        Seq(
          "Content-Type"   -> "application/octet-stream",
          "Content-Length" -> content.length.toString,
        ) ++ additionalHeaders,
      ),
      body = BufferBody(content),
    )
