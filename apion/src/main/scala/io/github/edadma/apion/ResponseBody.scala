package io.github.edadma.apion

import io.github.edadma.nodejs.{ReadableStream, Buffer}

sealed trait ResponseBody
case class StringBody(content: String, data: Buffer)    extends ResponseBody
case class BufferBody(content: Buffer)                  extends ResponseBody
case class ReadableStreamBody(readable: ReadableStream) extends ResponseBody
case object EmptyBody                                   extends ResponseBody
