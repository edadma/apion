package io.github.edadma.apion

import zio.json.*

import io.github.edadma.nodejs.{Buffer, ServerRequest, bufferMod}

import scala.concurrent.{Promise, Future}

import scala.scalajs.js
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

type Finalizer = (Request, Response) => Future[Response]

case class Request(
    method: String,
    url: String,
    path: String,
    headers: Map[String, String] = Map(),
    params: Map[String, String] = Map(),
    query: Map[String, String] = Map(),
    context: Map[String, Any] = Map(),
    rawRequest: ServerRequest,
    basePath: String = "", // Track the accumulated base path
    finalizers: List[Finalizer] = Nil,
    cookies: Map[String, String] = Map(),
) /*extends RequestDSL*/ {
  // Connection information
  def ip: String = rawRequest.socket.remoteAddress

  def secure: Boolean = rawRequest.socket.encrypted.getOrElse(false)

  def protocol: String = if secure then "https" else "http"

  def hostname: Option[String] = rawRequest.hostname.toOption

  def port: Option[Int] = rawRequest.port.toOption

  // HTTP version and state
  def httpVersion: String = rawRequest.httpVersion

  def complete: Boolean = rawRequest.complete

  def aborted: Boolean = rawRequest.aborted

  // Raw headers exactly as received (preserves case and duplicates)
  def rawHeaders: List[String] = rawRequest.rawHeaders.toList

  private var bodyPromise: Option[Promise[Buffer]] = None

  // Get raw body as Buffer
  def body: Future[Buffer] = {
    if (bodyPromise.isEmpty) {
      bodyPromise = Some(Promise[Buffer]())
      val chunks = new scala.collection.mutable.ArrayBuffer[Buffer]()

      rawRequest.on(
        "data",
        (chunk: Buffer) => {
          // Accumulate chunks as Buffer objects
          chunks += chunk
        },
      )

      rawRequest.on(
        "end",
        () => {
          // Concatenate all chunks into final Buffer
          bodyPromise.get.success(bufferMod.Buffer.concat(js.Array(chunks.toArray*)))
        },
      )

      rawRequest.on(
        "error",
        (error: js.Error) => {
          bodyPromise.get.failure(new Exception(s"Body read error: ${error.message}"))
        },
      )
    }

    bodyPromise.get.future
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

  def form: Future[Map[String, String]] =
    text.map { content =>
      content.split("&").flatMap { param =>
        param.split("=", 2) match {
          case Array(key, value) =>
            Some(decodeURIComponent(key) -> decodeURIComponent(value))
          case _ => None
        }
      }.toMap
    }

  def header(h: String): Option[String] = headers.get(h.toLowerCase)

  def cookie(name: String): Option[String] = cookies.get(name)

  def addFinalizer(f: Finalizer): Request = copy(finalizers = f :: finalizers)
}

object Request {
  def fromServerRequest(req: ServerRequest): Request = {
    val (path, query) = parseUrl(req.url)
    val headers       = req.headers.map { case (k, v) => k.toLowerCase -> v }.toMap

    Request(
      method = req.method,
      url = req.url,
      path = path,
      headers = headers,
      query = parseQueryString(query),
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

  private def parseQueryString(query: String): Map[String, String] =
    if (query.isEmpty) Map.empty
    else {
      query.split("&").flatMap { param =>
        param.split("=", 2) match {
          case Array(key, value) =>
            Some(decodeURIComponent(key) -> decodeURIComponent(value))
          case _ => None
        }
      }.toMap
    }
}
