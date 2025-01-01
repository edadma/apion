package io.github.edadma.apion

import scala.concurrent.Future
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global
import zio.json.*
import io.github.edadma.nodejs.crypto

object CookieMiddleware:
  /** Configuration options for cookie management */
  case class Options(
      // Signing options
      secret: Option[String] = None, // Secret for signed cookies

      // JSON serialization
      parseJSON: Boolean = false, // Parse JSON cookie values
  )

  private def parse(cookieHeader: String): Map[String, String] = {
    cookieHeader
      .split(";")
      .map(_.trim)
      .map { cookie =>
        cookie.split("=", 2) match {
          case Array(name, value) =>
            decodeURIComponent(name.trim) -> decodeURIComponent(value.trim.stripPrefix("\"").stripSuffix("\""))
          case Array(name) =>
            decodeURIComponent(name.trim) -> ""
        }
      }
      .toMap
  }

  /** Cookie signing utilities */
  sealed trait CookieSigner {
    def sign(value: String): String
    def unsign(signed: String): Option[String]
  }

  private class DefaultCookieSigner(secret: String) extends CookieSigner {
    def sign(value: String): String = {
      val hmac = crypto.createHmac("sha256", secret)
      hmac.update(value)
      val signature = hmac.digest("base64")
      s"$value.$signature"
    }

    def unsign(signed: String): Option[String] = {
      signed.split('.') match {
        case Array(value, signature) =>
          val hmac = crypto.createHmac("sha256", secret)
          hmac.update(value)
          val expected = hmac.digest("base64")
          if (signature == expected) Some(value) else None
        case _ => None
      }
    }
  }

  private class NoopCookieSigner extends CookieSigner {
    def sign(value: String): String            = value
    def unsign(signed: String): Option[String] = Some(signed)
  }

  /** JSON parsing utilities */
  class JsonCookieParser(parseJSON: Boolean, cookies: Map[String, String]) {
    def parse[A](name: String)(using decoder: JsonDecoder[A]): Option[A] =
      if (!parseJSON) None
      else cookies.get(name).flatMap { value =>
        value.fromJson[A].toOption
      }
  }

  /** Create cookie management middleware with default options */
  def apply(options: Options = Options()): Handler = request => {
    // Parse cookies from request headers
    val cookies = request.header("cookie").map(parse).getOrElse(Map.empty)

    // Create appropriate signer based on configuration
    val signer = options.secret match {
      case Some(secret) => new DefaultCookieSigner(secret)
      case None         => new NoopCookieSigner
    }

    // Create JSON parser
    val jsonParser = new JsonCookieParser(options.parseJSON, cookies)

    // Add parsed cookies and utilities to request
    val reqWithCookies = request.copy(
      cookies = cookies,
      context = request.context +
        ("cookieSigner"     -> signer) +
        ("jsonCookieParser" -> jsonParser),
    )

    Future.successful(Continue(reqWithCookies))
  }

  /** Cookie management extensions */
  implicit class CookieManagementOps(request: Request):
    /** Get a signed cookie value */
    def getSignedCookie(name: String): Option[String] =
      for {
        signer <- request.context.get("cookieSigner").map(_.asInstanceOf[CookieSigner])
        signed <- request.cookie(name)
        value  <- signer.unsign(signed)
      } yield value

    /** Get a JSON cookie value */
    def getJsonCookie[A](name: String)(using decoder: JsonDecoder[A]): Option[A] =
      request.context.get("jsonCookieParser")
        .map(_.asInstanceOf[JsonCookieParser])
        .flatMap(_.parse[A](name))

    /** Create a signed cookie to be used with Response.withCookie */
    def signCookie(name: String, value: String): Option[Cookie] =
      request.context.get("cookieSigner").map { signer =>
        val signed = signer.asInstanceOf[CookieSigner].sign(value)
        Cookie(name, signed)
      }

  /** Common configurations */
  object Presets:
    /** Configuration with cookie signing */
    def signed(secret: String): Options = Options(
      secret = Some(secret),
    )

    /** Configuration with JSON support */
    val json: Options = Options(
      parseJSON = true,
    )
