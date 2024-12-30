package io.github.edadma.apion

import scala.concurrent.Future
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

trait RequestDSL {
  self: Request =>

  def continue: Future[Result] = Future.successful(Continue(this))
  def skip: Future[Result]     = Future.successful(Skip)

  def fail(error: ServerError): Future[Result]    = Future.successful(Fail(error))
  def failValidation(msg: String): Future[Result] = Future.successful(Fail(ValidationError(msg)))
  def failAuth(msg: String): Future[Result]       = Future.successful(Fail(AuthError(msg)))
  def failNotFound(msg: String): Future[Result]   = Future.successful(Fail(NotFoundError(msg)))
}
