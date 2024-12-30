package io.github.edadma.apion

import scala.concurrent.Future
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global
import zio.json.*

object ResponseDSL {
  // Extension methods for any value that has a JsonEncoder
  implicit class JsonResponseOps[A: JsonEncoder](val data: A) {
    def asJson: Future[Result]              = Future.successful(Complete(Response.json(data)))
    def asJson(status: Int): Future[Result] = Future.successful(Complete(Response.json(data, status)))
  }

  // Extension method for strings
  implicit class StringResponseOps(val text: String) {
    def asText: Future[Result]              = Future.successful(Complete(Response.text(text)))
    def asText(status: Int): Future[Result] = Future.successful(Complete(Response.text(text, status)))
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
    val headers = location.map(l => Map("Location" -> l)).getOrElse(Map.empty)
    Future.successful(Complete(Response.json(data, 201, headers)))
  }
}
