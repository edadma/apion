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
    val builder = new StringBuilder()
    builder.append(s"${encodeURIComponent(name)}=${encodeURIComponent(value)}")
    domain.foreach(d => builder.append(s"; Domain=$d"))
    path.foreach(p => builder.append(s"; Path=$p"))
    maxAge.foreach(age => builder.append(s"; Max-Age=$age"))
    expires.foreach(exp =>
      builder.append(s"; Expires=${exp.atZone(ZoneOffset.UTC).format(Cookie.dateFormat)}"),
    )
    if (secure) builder.append("; Secure")
    if (httpOnly) builder.append("; HttpOnly")
    sameSite.foreach(ss => builder.append(s"; SameSite=$ss"))
    builder.toString
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
