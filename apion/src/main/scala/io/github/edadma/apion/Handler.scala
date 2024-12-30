package io.github.edadma.apion

import scala.concurrent.Future

sealed trait ServerError                extends RuntimeException
case class ValidationError(msg: String) extends ServerError
case class AuthError(msg: String)       extends ServerError
case class NotFoundError(msg: String)   extends ServerError

type ErrorHandler = ServerError => Response

sealed trait Result
case class Continue(request: Request)   extends Result
case class Complete(response: Response) extends Result
case class Fail(error: ServerError)     extends Result
case object Skip                        extends Result

type Handler = Request => Future[Result]
