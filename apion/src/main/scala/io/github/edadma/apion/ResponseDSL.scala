package io.github.edadma.apion

import io.github.edadma.nodejs.Buffer

import scala.concurrent.Future
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global
import zio.json.*

import java.time.Instant

// Extension methods for any value that has a JsonEncoder
implicit class JsonResponseOps[A: JsonEncoder](val data: A) {
  def asJson: Future[Complete]              = Future.successful(Complete(Response.json(data)))
  def asJson(status: Int): Future[Complete] = Future.successful(Complete(Response.json(data, status)))
}

// Extension method for strings
implicit class StringResponseOps(val text: String) {
  def asText: Future[Complete]              = Future.successful(Complete(Response.text(text)))
  def asText(status: Int): Future[Complete] = Future.successful(Complete(Response.text(text, status)))
}

// Extension method for node Buffers
implicit class BufferResponseOps(val buffer: Buffer) {
  def asBinary: Future[Complete] = Future.successful(Complete(Response.binary(buffer)))

  def asBinary(status: Int): Future[Complete] = Future.successful(Complete(Response.binary(buffer, status)))
}

// Direct response methods
def json[A: JsonEncoder](data: A): Future[Result]              = data.asJson
def json[A: JsonEncoder](data: A, status: Int): Future[Result] = data.asJson(status)
def text(content: String): Future[Result]                      = content.asText
def text(content: String, status: Int): Future[Result]         = content.asText(status)

// Common responses
val NotFound: Future[Complete]   = Future.successful(Complete(Response(404, body = """{"error": "Not Found"}""")))
val BadRequest: Future[Complete] = Future.successful(Complete(Response(400, body = """{"error": "Bad Request"}""")))
val ServerError: Future[Complete] =
  Future.successful(Complete(Response(500, body = """{"error": "Internal Server Error"}""")))

// Created with optional location header
def Created[A: JsonEncoder](data: A, location: Option[String] = None): Future[Result] = {
  val headers = location.map(l => Seq("Location" -> l)).getOrElse(Nil)
  Future.successful(Complete(Response.json(data, 201, headers)))
}

extension (response: Response) {
  def withCookie(cookie: Cookie): Response = {
    // Add as a new Set-Cookie header, don't combine with existing ones
    response.copy(headers = response.headers.add("Set-Cookie", cookie.toHeaderValue))
  }

  def withCookie(
      name: String,
      value: String,
      domain: Option[String] = None,
      path: Option[String] = None,
      maxAge: Option[Int] = None,
      expires: Option[Instant] = None,
      secure: Boolean = false,
      httpOnly: Boolean = false,
      sameSite: Option[String] = None,
  ): Response = {
    withCookie(Cookie(name, value, domain, path, maxAge, expires, secure, httpOnly, sameSite))
  }

  def clearCookie(name: String, path: String = "/"): Response = {
    withCookie(Cookie(
      name = name,
      value = "",
      path = Some(path),
      expires = Some(Instant.EPOCH),
    ))
  }
}
