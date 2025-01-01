package io.github.edadma.apion

import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter

case class Cookie(
    name: String,
    value: String,
    domain: Option[String] = None,
    path: Option[String] = None,
    maxAge: Option[Int] = None,
    expires: Option[Instant] = None,
    secure: Boolean = false,
    httpOnly: Boolean = false,
    sameSite: Option[String] = None,
) {
  def toHeaderValue: String = {
    val attrs = List(
      Some(s"$name=$value"),
      domain.map(d => s"Domain=$d"),
      path.map(p => s"Path=$p"),
      maxAge.map(age => s"Max-Age=$age"),
      expires.map(exp => s"Expires=${Cookie.dateFormat.format(exp)}"),
      if (secure) Some("Secure") else None,
      if (httpOnly) Some("HttpOnly") else None,
      sameSite.map(s => s"SameSite=$s"),
    ).flatten.mkString("; ")

    attrs
  }
}

object Cookie {
  private val dateFormat = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC)

  def parse(cookieHeader: String): Map[String, String] = {
    cookieHeader
      .split(";")
      .map(_.trim)
      .map { cookie =>
        cookie.split("=", 2) match {
          case Array(name, value) => name.trim -> value.trim.stripPrefix("\"").stripSuffix("\"")
          case Array(name)        => name.trim -> ""
        }
      }
      .toMap
  }
}

// Extension methods for cookie operations
extension (response: Response) {
  def withCookie(cookie: Cookie): Response =
    response.copy(headers =
      response.headers.add(
        "Set-Cookie",
        formatCookieHeader(cookie),
      ),
    )

  def clearCookie(name: String, path: String = "/"): Response =
    withCookie(Cookie(
      name = name,
      value = "",
      path = Some(path),
      expires = Some(Instant.EPOCH),
    ))

  private def formatCookieHeader(cookie: Cookie): String = {
    val builder = new StringBuilder()
    builder.append(s"${encodeURIComponent(cookie.name)}=${encodeURIComponent(cookie.value)}")
    cookie.domain.foreach(d => builder.append(s"; Domain=$d"))
    cookie.path.foreach(p => builder.append(s"; Path=$p"))
    cookie.maxAge.foreach(age => builder.append(s"; Max-Age=$age"))
    cookie.expires.foreach(exp =>
      builder.append(s"; Expires=${exp.atZone(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME)}"),
    )
    if (cookie.secure) builder.append("; Secure")
    if (cookie.httpOnly) builder.append("; HttpOnly")
    cookie.sameSite.foreach(ss => builder.append(s"; SameSite=$ss"))
    builder.toString
  }
}
