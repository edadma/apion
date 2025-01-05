package io.github.edadma.nodejs

import scala.scalajs.js
import scala.scalajs.js.annotation.*

@js.native
trait ReadableStream extends js.Object {
  def pipe(destination: WritableStream): Unit                            = js.native
  def on(event: String, callback: js.Function1[js.Any, Unit]): this.type = js.native
}

@js.native
trait WritableStream extends js.Object {
  def write(chunk: String | Buffer): Unit = js.native
  def end(): Unit                         = js.native
}

@js.native
@JSImport("stream", JSImport.Namespace)
object stream extends js.Object {
  val Readable: ReadableStaticInterface = js.native
}

@js.native
trait ReadableStaticInterface extends js.Object {
  def from(data: String | Buffer): ReadableStream = js.native
}
