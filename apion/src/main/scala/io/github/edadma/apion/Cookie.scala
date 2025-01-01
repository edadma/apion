package io.github.edadma.apion

case class Cookie(
    value: String,
    domain: Option[String] = None,
    path: Option[String] = None,
    maxAge: Option[Int] = None,
    expires: Option[String] = None,
    secure: Boolean = false,
    httpOnly: Boolean = false,
    sameSite: Option[String] = None,
) {
  def toHeaderValue: String = {
    val attrs = List(
      Some(value),
      domain.map(d => s"Domain=$d"),
      path.map(p => s"Path=$p"),
      maxAge.map(age => s"Max-Age=$age"),
      expires.map(exp => s"Expires=$exp"),
      if (secure) Some("Secure") else None,
      if (httpOnly) Some("HttpOnly") else None,
      sameSite.map(s => s"SameSite=$s"),
    ).flatten.mkString("; ")

    attrs
  }
}

object Cookie {
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
