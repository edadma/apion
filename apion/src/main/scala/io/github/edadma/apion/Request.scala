package io.github.edadma.apion

import zio.json.*

import io.github.edadma.nodejs.{Buffer, ServerRequest, bufferMod}

import scala.concurrent.{Promise, Future}

import scala.scalajs.js

type Finalizer = (Request, Response) => Future[Response]

case class Request(
    method: String,
    url: String,
    path: String,
    headers: Map[String, String] = Map(),
    params: Map[String, String] = Map(),
    query: Map[String, Seq[String]] = Map(),
    context: Map[String, Any] = Map(),
    rawRequest: ServerRequest,
    basePath: String = "", // Track the accumulated base path
    finalizers: List[Finalizer] = Nil,
    cookies: Map[String, String] = Map(),
) /*extends RequestDSL*/ {
  // Connection information
  def ip: String               = rawRequest.socket.remoteAddress
  def secure: Boolean          = rawRequest.socket.encrypted.getOrElse(false)
  def protocol: String         = if secure then "https" else "http"
  def hostname: Option[String] = rawRequest.hostname.toOption
  def port: Option[Int]        = rawRequest.port.toOption

  // HTTP version and state
  def httpVersion: String = rawRequest.httpVersion
  def complete: Boolean   = rawRequest.complete
  def aborted: Boolean    = rawRequest.aborted

  // Raw headers exactly as received (preserves case and duplicates)
  def rawHeaders: List[String] = rawRequest.rawHeaders.toList

  /** Maximum body size in bytes. Default 50MB. Override via Request.maxBodySize. */
  private val maxBody: Long = context.get("maxBodySize").map(_.asInstanceOf[Long]).getOrElse(Request.maxBodySize)

  /** Read timeout in milliseconds. Default 30s. Override via Request.bodyTimeout. */
  private val bodyTimeoutMs: Int = context.get("bodyTimeout").map(_.asInstanceOf[Int]).getOrElse(Request.bodyTimeout)

  /** Raw request body as a Buffer.
    *
    * The body is streamed and memoized on the underlying connection (`rawRequest`),
    * not on this immutable value. Because every `copy`-derived request shares the
    * same `rawRequest`, reading the body after middleware has copied the request
    * still attaches the stream listeners and consumes the socket exactly once — no
    * matter which copy calls `body` first.
    */
  def body: Future[Buffer] = {
    val holder = rawRequest.asInstanceOf[js.Dynamic]

    if (js.isUndefined(holder.__apionBody)) {
      val promise = Promise[Buffer]()
      holder.__apionBody = promise.asInstanceOf[js.Any]

      val chunks    = new scala.collection.mutable.ArrayBuffer[Buffer]()
      var totalSize = 0L

      val timeoutTimer = js.timers.setTimeout(bodyTimeoutMs) {
        if (!promise.isCompleted) {
          promise.failure(new Exception("Body read timeout"))
          rawRequest.destroy(js.Error("Read timeout"))
        }
      }

      def clearTimer(): Unit = js.timers.clearTimeout(timeoutTimer)

      rawRequest.on(
        "data",
        (chunk: Buffer) => {
          totalSize += chunk.length
          if (totalSize > maxBody) {
            if (!promise.isCompleted) promise.failure(new Exception("Request body too large"))
            rawRequest.destroy(js.Error("Body too large"))
          } else {
            chunks += chunk
          }
        },
      )

      rawRequest.on(
        "end",
        () => {
          if (!promise.isCompleted) {
            val finalBuffer = bufferMod.Buffer.concat(js.Array(chunks.toArray*))
            promise.success(finalBuffer)
            clearTimer()
          }
        },
      )

      rawRequest.on(
        "error",
        (error: js.Error) => {
          if (!promise.isCompleted) promise.failure(new Exception(s"Body read error: ${error.message}"))
          clearTimer()
        },
      )

      promise.future
    } else {
      holder.__apionBody.asInstanceOf[Promise[Buffer]].future
    }
  }

  // Higher level helpers
  def text: Future[String] =
    // Parse Content-Type header
    val charset = header("content-type")
      .flatMap { ct =>
        // Look for charset in Content-Type (e.g. "text/plain; charset=iso-8859-1")
        ct.split(";")
          .map(_.trim)
          .find(_.startsWith("charset="))
          .map(_.substring(8).toLowerCase)
      }
      .getOrElse("utf8") // Default to UTF-8 if not specified

    body.map(_.toString(charset))

  def json[T: JsonDecoder]: Future[Option[T]] =
    text.map(_.fromJson[T].toOption)

  /** Parse an `application/x-www-form-urlencoded` body, preserving repeated keys. */
  def form: Future[Map[String, Seq[String]]] =
    text.map(Request.parseUrlEncoded)

  /** First value of a form field, if present. */
  def formField(name: String): Future[Option[String]] =
    form.map(_.get(name).flatMap(_.headOption))

  /** First value of a query-string parameter, if present. */
  def queryParam(name: String): Option[String] =
    query.get(name).flatMap(_.headOption)

  def header(h: String): Option[String] = headers.get(h.toLowerCase)

  def cookie(name: String): Option[String] = cookies.get(name)

  def addFinalizer(f: Finalizer): Request = copy(finalizers = f :: finalizers)
}

object Request {
  /** Default maximum body size: 50MB */
  var maxBodySize: Long = 50L * 1024 * 1024

  /** Default body read timeout: 30 seconds */
  var bodyTimeout: Int = 30000

  def fromServerRequest(req: ServerRequest): Request = {
    val (path, query) = parseUrl(req.url)
    val headers       = req.headers.map { case (k, v) => k.toLowerCase -> v }.toMap

    Request(
      method = req.method,
      url = req.url,
      path = path,
      headers = headers,
      query = parseUrlEncoded(query),
      rawRequest = req,
    )
  }

  private def parseUrl(url: String): (String, String) = {
    url.split("\\?", 2) match {
      case Array(path)        => (path, "")
      case Array(path, query) => (path, query)
      case _                  => ("", "") // Should never happen due to split limit of 2
    }
  }

  /** Parse an `application/x-www-form-urlencoded` string (query strings and form
    * bodies) into a multi-valued map, preserving the order of repeated keys.
    * A bare `key` with no `=` maps to a single empty-string value.
    */
  private[apion] def parseUrlEncoded(s: String): Map[String, Seq[String]] =
    if (s.isEmpty) Map.empty
    else
      s.split("&").toSeq.flatMap { param =>
        param.split("=", 2) match {
          case Array(key, value) => Some(decodeFormComponent(key) -> decodeFormComponent(value))
          case Array(key)        => Some(decodeFormComponent(key) -> "")
          case _                 => None
        }
      }.groupMap(_._1)(_._2)
}
