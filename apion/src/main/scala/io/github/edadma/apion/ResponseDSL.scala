package io.github.edadma.apion

import io.github.edadma.nodejs.Buffer

import scala.concurrent.Future
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global
import zio.json.*

import java.time.Instant

def skip: Future[Result]                        = Future.successful(Skip)
def fail(error: ServerError): Future[Result]    = Future.successful(Fail(error))
def failValidation(msg: String): Future[Result] = Future.successful(Fail(ValidationError(msg)))
def failAuth(msg: String): Future[Result]       = Future.successful(Fail(AuthError(msg)))
def failNotFound(msg: String): Future[Result]   = Future.successful(Fail(NotFoundError(msg)))

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
def noContent                                                  = Future.successful(Complete(Response.noContent()))

// Common responses
val notFound: Future[Complete]    = "Not Found".asText(404)
val badRequest: Future[Complete]  = "Bad Request".asText(400)
val serverError: Future[Complete] = "Internal Server Error".asText(500)

// Created with optional location header
def created[A: JsonEncoder](data: A, location: Option[String] = None): Future[Result] = {
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
