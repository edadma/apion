package io.github.edadma.apion

import zio.json.*

import io.github.edadma.nodejs.ServerRequest

import scala.concurrent.{Promise, Future}

import scala.scalajs.js

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
  private var bodyPromise: Option[Promise[String]] = None

  def textBody: Future[String] = {
    if (bodyPromise.isEmpty) {
      bodyPromise = Some(Promise[String]())
      var body = ""

      rawRequest.on(
        "data",
        (chunk: js.Any) => {
          body += chunk.toString
        },
      )

      rawRequest.on(
        "end",
        () => {
          bodyPromise.get.success(body)
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

  def jsonBody[T: JsonDecoder]: Future[Option[T]] = {
    import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global
    textBody.map(body => body.fromJson[T].toOption)
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
