package io.github.edadma.apion

import io.github.edadma.nodejs.ServerRequest

case class Auth(user: String, roles: Set[String])

case class Request(
    method: String,
    url: String,
    path: String,
    headers: Map[String, String] = Map(),
    params: Map[String, String] = Map(),
    query: Map[String, String] = Map(),
    context: Map[String, Any] = Map(),
    auth: Option[Auth] = None,
    rawRequest: ServerRequest,
    basePath: String = "", // Track the accumulated base path
) extends RequestDSL {
  def header(h: String): Option[String] = headers.get(h.toLowerCase)
}

object Request {
  def fromServerRequest(req: ServerRequest): Request = {
    val (path, query) = parseUrl(req.url)
    Request(
      method = req.method,
      url = req.url,
      path = path,
      headers = req.headers.map { case (k, v) => k.toLowerCase -> v }.toMap,
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

  private def decodeURIComponent(s: String): String = {
    def hexToChar(hex: String): Char =
      Integer.parseInt(hex, 16).toChar

    val result = new StringBuilder
    var i      = 0
    while (i < s.length) {
      if (s(i) == '%' && i + 2 < s.length) {
        result.append(hexToChar(s.substring(i + 1, i + 3)))
        i += 3
      } else if (s(i) == '+') {
        result.append(' ')
        i += 1
      } else {
        result.append(s(i))
        i += 1
      }
    }
    result.toString
  }
}
