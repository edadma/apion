package io.github.edadma.apion

import io.github.edadma.nodejs.{ReadableStream, Buffer}

sealed trait ResponseBody
case class StringBody(content: String, data: Buffer)    extends ResponseBody
case class BufferBody(content: Buffer)                  extends ResponseBody
case class ReadableStreamBody(readable: ReadableStream) extends ResponseBody
case object EmptyBody                                   extends ResponseBody

//case class StreamOptions(
//    highWaterMark: Int = 64 * 1024, // Buffer size
//    timeout: Int = 30000,           // Timeout in ms
//    maxChunkSize: Int = 1024 * 1024, // Max chunk size
//)
