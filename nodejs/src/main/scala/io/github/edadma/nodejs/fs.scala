package io.github.edadma.nodejs

import scala.scalajs.js
import scala.scalajs.js.annotation.*

@js.native
@JSImport("fs", JSImport.Namespace)
object fs extends js.Object:
  val promises: FSPromises = js.native

@js.native
trait FSPromises extends js.Object:
  def createReadStream(path: String): ReadableStream                                = js.native
  def readFile(path: String): js.Promise[Buffer]                                    = js.native
  def readFile(path: String, options: ReadFileOptions): js.Promise[String | Buffer] = js.native
  def stat(path: String): js.Promise[Stats]                                         = js.native

@js.native
trait ReadFileOptions extends js.Object:
  val encoding: js.UndefOr[String] = js.native
  val flag: js.UndefOr[String]     = js.native

object ReadFileOptions:
  def apply(
      encoding: String = "utf8",
      flag: Option[String] = None,
  ): ReadFileOptions =
    val opts = js.Dynamic.literal(encoding = encoding)
    flag.foreach(f => opts.updateDynamic("flag")(f))
    opts.asInstanceOf[ReadFileOptions]

@js.native
trait Stats extends js.Object:
  def isDirectory(): Boolean = js.native
  def isFile(): Boolean      = js.native
  def size: Double           = js.native
  def mode: Double           = js.native
  def mtime: js.Date         = js.native

@js.native
trait ReadableStream extends js.Object {
  def pipe(destination: WritableStream): Unit                            = js.native
  def on(event: String, callback: js.Function1[js.Any, Unit]): this.type = js.native
}

@js.native
trait WritableStream extends js.Object {
  def end(): Unit = js.native
}
