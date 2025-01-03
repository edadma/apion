package io.github.edadma.apion

import io.github.edadma.nodejs.Buffer

import scala.language.implicitConversions

sealed trait ResponseBody
object ResponseBody {
  implicit def stringToResponseBody(s: String): ResponseBody.Text = ResponseBody.Text(s)

  case class Text(content: String, encoding: String = "utf8") extends ResponseBody
  case class Binary(content: Buffer)                          extends ResponseBody
//  case class Stream(readable: ReadableStream)                 extends ResponseBody
  case object Empty extends ResponseBody
}
