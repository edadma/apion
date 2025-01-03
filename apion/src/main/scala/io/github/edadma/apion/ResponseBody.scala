package io.github.edadma.apion

import io.github.edadma.nodejs.Buffer

import scala.language.implicitConversions

sealed trait ResponseBody
//implicit def stringToResponseBody(s: String): ResponseBody = Response.text(s, "utf-8", )

case class TextBody(content: String, encoding: String, data: Buffer) extends ResponseBody
case class ContentBody(content: Buffer)                              extends ResponseBody
//  case class Stream(readable: ReadableStream)                 extends ResponseBody
case object EmptyBody extends ResponseBody
