package io.github.edadma.apion

/** ResponseHeaders wraps the header map to provide case-insensitive access while maintaining canonical header casing
  * for the outgoing response
  */
class ResponseHeaders private (private val headers: Map[String, List[String]]):
  def get(header: String): Option[String] = headers.get(header.toLowerCase).map(_.head)

  def contains(header: String): Boolean =
    headers.contains(header.toLowerCase)

  def add(header: String, value: String): ResponseHeaders =
    val lowerCase = header.toLowerCase

    new ResponseHeaders(
      if ResponseHeaders.multiHeader(lowerCase) then
        headers.updatedWith(lowerCase) {
          case Some(existing) => Some(value :: existing)
          case None           => Some(List(value))
        }
      else
        headers.updated(lowerCase, List(value)),
    )

  def addAll(newHeaders: Seq[(String, String)]): ResponseHeaders =
    newHeaders.foldLeft(this) { case (headers, (key, value)) =>
      headers.add(key, value)
    }

  def remove(header: String): ResponseHeaders =
    new ResponseHeaders(headers - header.toLowerCase)

  def toMap: Map[String, List[String]] = headers.map((k, v) => normalize(k) -> v)

  def normalize(header: String): String =
    val lower = header.toLowerCase

    lower.split('-').map(s => if ResponseHeaders.allCaps.contains(s) then s.toUpperCase else s.capitalize).mkString("-")

  override def toString: String = s"ResponseHeaders($headers)"

object ResponseHeaders:
  private val multiHeader = Set("set-cookie", "wwww-authenticate")

  private val allCaps = Set(
    "api",
    "cors",
    "csrf",
    "csp",
    "dns",
    "hsts",
    "http",
    "json",
    "jwt",
    "php",
    "rpc",
    "ssl",
    "tls",
    "uri",
    "url",
    "www",
    "xml",
    "xss",
    "xsrf",
  )
  def empty: ResponseHeaders = new ResponseHeaders(Map.empty)

  def apply(headers: Seq[(String, String)]): ResponseHeaders =
    empty.addAll(headers)

  def apply(header: String, value: String): ResponseHeaders = ResponseHeaders(Seq(header -> value))
