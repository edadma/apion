package io.github.edadma.apion

import scala.concurrent.Future
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global
import scala.scalajs.js

case class Cookie(
    name: String,
    value: String,
    domain: Option[String] = None,
    path: Option[String] = None,
    maxAge: Option[Int] = None,
    expires: Option[js.Date] = None,
    secure: Boolean = false,
    httpOnly: Boolean = true,
    sameSite: Option[String] = Some("Lax"), // Lax, Strict, or None
)

object CookieMiddleware {
  case class Options(
      secret: String,                   // For signed cookies
      path: String = "/",               // Default cookie path
      secure: Boolean = false,          // Require HTTPS
      httpOnly: Boolean = true,         // Prevent JavaScript access
      sameSite: String = "Lax",         // CSRF protection
      signed: Boolean = true,           // Enable cookie signing
      maxAge: Option[Int] = Some(86400), // 24 hours default
  )

  def apply(options: Options): Handler = { request =>
    // Parse cookies from request
    val cookies = parseCookies(request.header("cookie").getOrElse(""))

    // Add parsed cookies to request context
    val reqWithCookies = request.copy(
      context = request.context + ("cookies" -> cookies),
    )

    // Add cookie management methods via finalizer
    val cookieFinalizer: Finalizer = (req, res) => {
      // Get any cookies set during request handling
      val newCookies = req.context.get("newCookies")
        .collect {
          case cookies: List[_] if cookies.forall(_.isInstanceOf[Cookie]) =>
            cookies.collect { case c: Cookie => c }
        }
        .getOrElse(Nil)

      // Add Set-Cookie headers for new cookies
      val cookieHeaders = newCookies.map(serializeCookie(_, options))
      Future.successful(
        res.copy(headers = cookieHeaders.foldLeft(res.headers) {
          case (headers, cookie) => headers.add("Set-Cookie", cookie)
        }),
      )
    }

    Future.successful(Continue(reqWithCookies.addFinalizer(cookieFinalizer)))
  }

  private def parseCookies(cookieHeader: String): Map[String, String] = {
    if (cookieHeader.isEmpty) Map.empty
    else {
      cookieHeader.split(";").map(_.trim).flatMap { cookie =>
        cookie.split("=", 2) match {
          case Array(key, value) =>
            Some(decodeURIComponent(key) -> decodeURIComponent(value.stripPrefix("\"").stripSuffix("\"")))
          case _ => None
        }
      }.toMap
    }
  }

  private def serializeCookie(cookie: Cookie, options: Options): String = {
    val builder = new StringBuilder()

    // Required name-value pair
    builder.append(s"${encodeURIComponent(cookie.name)}=${encodeURIComponent(cookie.value)}")

    // Optional attributes
    cookie.domain.foreach(d => builder.append(s"; Domain=$d"))
    cookie.path.orElse(Some(options.path)).foreach(p => builder.append(s"; Path=$p"))
    cookie.maxAge.orElse(options.maxAge).foreach(age => builder.append(s"; Max-Age=$age"))
    cookie.expires.foreach(exp => builder.append(s"; Expires=${exp.toUTCString()}"))

    if (cookie.secure || options.secure) builder.append("; Secure")
    if (cookie.httpOnly || options.httpOnly) builder.append("; HttpOnly")

    cookie.sameSite.orElse(Some(options.sameSite)).foreach { sameSite =>
      builder.append(s"; SameSite=$sameSite")
    }

    builder.toString
  }

  private def encodeURIComponent(s: String): String = {
    val sb = new StringBuilder
    for (c <- s) {
      if (shouldEncode(c)) {
        val bytes = c.toString.getBytes("UTF-8")
        for (b <- bytes) {
          sb.append('%')
          sb.append(Character.forDigit((b >> 4) & 0xf, 16).toUpper)
          sb.append(Character.forDigit(b & 0xf, 16).toUpper)
        }
      } else {
        sb.append(c)
      }
    }
    sb.toString
  }

  private def shouldEncode(c: Char): Boolean = {
    val allowedChars = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') ++ Set('-', '_', '.', '!', '~', '*', '\'', '(', ')')
    !allowedChars.contains(c)
  }
}

// Extension methods for cookie operations
extension (request: Request) {
  def cookies: Map[String, String] =
    request.context.get("cookies").map(_.asInstanceOf[Map[String, String]]).getOrElse(Map.empty)

  def cookie(name: String): Option[String] = cookies.get(name)

  def setCookie(cookie: Cookie): Request = {
    val newCookies = request.context.get("newCookies")
      .collect {
        case cookies: List[_] if cookies.forall(_.isInstanceOf[Cookie]) =>
          cookie :: cookies.collect { case c: Cookie => c }
      }
      .getOrElse(List(cookie))
    request.copy(context = request.context + ("newCookies" -> newCookies))
  }

  def clearCookie(name: String, path: String = "/"): Request = {
    setCookie(Cookie(
      name = name,
      value = "",
      path = Some(path),
      expires = Some(new js.Date(0.0)),
    ))
  }
}
