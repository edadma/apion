package io.github.edadma.apion

/** HeaderCase handles case-insensitive header names while preserving original casing This is important because while
  * HTTP header names are case-insensitive, some clients or tools may expect specific casing.
  */
object HeaderCase:
  /** Canonical cases for common headers - this helps maintain consistent casing across the application while still
    * allowing case-insensitive matching
    */
  private val CanonicalCases = Map(
    "content-type"                     -> "Content-Type",
    "content-length"                   -> "Content-Length",
    "authorization"                    -> "Authorization",
    "user-agent"                       -> "User-Agent",
    "accept"                           -> "Accept",
    "accept-encoding"                  -> "Accept-Encoding",
    "cache-control"                    -> "Cache-Control",
    "connection"                       -> "Connection",
    "cookie"                           -> "Cookie",
    "host"                             -> "Host",
    "origin"                           -> "Origin",
    "referer"                          -> "Referer",
    "x-forwarded-for"                  -> "X-Forwarded-For",
    "x-frame-options"                  -> "X-Frame-Options",
    "x-content-type-options"           -> "X-Content-Type-Options",
    "x-xss-protection"                 -> "X-XSS-Protection",
    "access-control-allow-origin"      -> "Access-Control-Allow-Origin",
    "access-control-allow-methods"     -> "Access-Control-Allow-Methods",
    "access-control-allow-headers"     -> "Access-Control-Allow-Headers",
    "access-control-expose-headers"    -> "Access-Control-Expose-Headers",
    "access-control-max-age"           -> "Access-Control-Max-Age",
    "access-control-allow-credentials" -> "Access-Control-Allow-Credentials",
  )

  def normalize(header: String): String =
    val lower = header.toLowerCase
    CanonicalCases.getOrElse(
      lower,
      // If not in canonical cases, capitalize each word
      lower.split('-').map(_.capitalize).mkString("-"),
    )

/** ResponseHeaders wraps the header map to provide case-insensitive access while maintaining original header casing for
  * the outgoing response
  */
class ResponseHeaders private (private val headers: Map[String, List[(String, String)]]):
  // The tuple contains (originalCase, value)

  def get(header: String): Option[String] =
    headers.get(header.toLowerCase).flatMap(_.headOption.map(_._2))

  def getOriginalCase(header: String): Option[String] =
    headers.get(header.toLowerCase).flatMap(_.headOption.map(_._1))

  def contains(header: String): Boolean =
    headers.contains(header.toLowerCase)

  def add(header: String, value: String): ResponseHeaders =
    val lowerCase    = header.toLowerCase
    val originalCase = HeaderCase.normalize(header)

    new ResponseHeaders(
      headers.updatedWith(lowerCase) {
        case Some(existing) => Some((originalCase, value) :: existing)
        case None           => Some(List((originalCase, value)))
      },
    )

  def addAll(newHeaders: Seq[(String, String)]): ResponseHeaders =
    newHeaders.foldLeft(this) { case (headers, (key, value)) =>
      headers.add(key, value)
    }

  def remove(header: String): ResponseHeaders =
    new ResponseHeaders(headers - header.toLowerCase)

  def toMap: Map[String, List[String]] =
    headers.view.mapValues(_.map(_._2)).toMap

  def toOriginalCaseMap: Map[String, List[(String, String)]] =
    headers.view.mapValues(identity).toMap

object ResponseHeaders:
  def empty: ResponseHeaders = new ResponseHeaders(Map.empty)

  def apply(headers: Seq[(String, String)]): ResponseHeaders =
    empty.addAll(headers)

  def apply(header: String, value: String): ResponseHeaders = ResponseHeaders(Seq(header -> value))
