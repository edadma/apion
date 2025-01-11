package io.github.edadma.nodejs

import scala.scalajs.js
import scala.scalajs.js.annotation.*

trait PipeOptions extends js.Object {
  val end: js.UndefOr[Boolean]
}

object PipeOptions {
  def apply(end: Boolean = true): PipeOptions =
    js.Dynamic.literal(end = end).asInstanceOf[PipeOptions]
}

@js.native
trait ReadableStream extends js.Object {
  def pipe(destination: WritableStream): this.type                       = js.native
  def pipe(destination: WritableStream, options: PipeOptions): this.type = js.native
  def on(event: String, callback: js.Any): this.type                     = js.native
  def resume(): this.type                                                = js.native
  def pause(): this.type                                                 = js.native
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
