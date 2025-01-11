package io.github.edadma.apion

import io.github.edadma.logger.LogLevel

import scala.concurrent.Future

trait ServerError extends Throwable {
  def message: String

  def toResponse: Response

  def logLevel: LogLevel = LogLevel.ERROR
}

case class ValidationError(message: String) extends ServerError {
  def toResponse: Response = Response.json(
    Map("error" -> "validation_error", "message" -> message),
    400,
  )

  override def logLevel: LogLevel = LogLevel.WARN
}

case class AuthError(message: String) extends ServerError {
  def toResponse: Response = Response.json(
    Map("error" -> "auth_error", "message" -> message),
    401,
  )
}

case class NotFoundError(message: String) extends ServerError {
  def toResponse: Response = Response.json(
    Map("error" -> "not_found", "message" -> message),
    404,
  )

  override def logLevel: LogLevel = LogLevel.INFO
}

sealed trait Result
case class Continue(request: Request)                                            extends Result
case class Complete(response: Response)                                          extends Result
case class Fail(error: ServerError)                                              extends Result
case object Skip                                                                 extends Result
private[apion] case class InternalComplete(request: Request, response: Response) extends Result

type Handler = Request => Future[Result]
