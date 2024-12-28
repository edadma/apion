package io.github.edadma.apion

import zio.json.*
import scala.concurrent.Future
import io.github.edadma.nodejs.{ServerRequest}

case class Request(
    method: String,
    url: String,
    headers: Map[String, String],
    auth: Option[Auth] = None,
    context: Map[String, Any] = Map(),
    rawRequest: Option[ServerRequest] = None,
)

case class Response(
    status: Int = 200,
    headers: Map[String, String] = Map("Content-Type" -> "application/json"),
    body: String,
)

case class Auth(
    user: String,
    roles: Set[String] = Set(),
)

object Auth:
  given JsonEncoder[Auth] = DeriveJsonEncoder.gen[Auth]
  given JsonDecoder[Auth] = DeriveJsonDecoder.gen[Auth]

type Endpoint   = Request => Future[Response]
type Middleware = Endpoint => Endpoint

object Response:
  def json[A: JsonEncoder](data: A, status: Int = 200): Response =
    Response(
      status = status,
      headers = Map("Content-Type" -> "application/json"),
      body = data.toJson,
    )

  def text(content: String, status: Int = 200): Response =
    Response(
      status = status,
      headers = Map("Content-Type" -> "text/plain"),
      body = content,
    )
