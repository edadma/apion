package io.github.edadma.nodejs

import scala.scalajs.js
import scala.scalajs.js.annotation.*

@js.native
@JSImport("fs", JSImport.Namespace)
object fs extends js.Object:
  val promises: FSPromises                                                       = js.native
  def createReadStream(path: String): ReadableStream                             = js.native
  def createReadStream(path: String, options: ReadStreamOptions): ReadableStream = js.native
  def createWriteStream(path: String): WritableStream                            = js.native
  def unlink(path: String, callback: js.Function1[js.Error | Null, Unit]): Unit  = js.native
  def unlinkSync(path: String): Unit                                             = js.native

@js.native
trait FSPromises extends js.Object:
  def readFile(path: String): js.Promise[Buffer]                                                         = js.native
  def readFile(path: String, options: ReadFileOptions): js.Promise[String | Buffer]                      = js.native
  def writeFile(path: String, data: String): js.Promise[Unit]                                            = js.native
  def writeFile(path: String, data: Buffer): js.Promise[Unit]                                            = js.native
  def writeFile(path: String, data: String, options: WriteFileOptions): js.Promise[Unit]                 = js.native
  def mkdir(path: String): js.Promise[js.UndefOr[String]]                                                = js.native
  def mkdir(path: String, options: MkdirOptions): js.Promise[js.UndefOr[String]]                         = js.native
  def readdir(path: String): js.Promise[js.Array[String]]                                                = js.native
  def readdir(path: String, options: ReaddirOptions): js.Promise[js.Array[String | Buffer | js.Object]]  = js.native
  def rm(path: String): js.Promise[Unit]                                                                 = js.native
  def rm(path: String, options: RmOptions): js.Promise[Unit]                                             = js.native
  def rename(oldPath: String, newPath: String): js.Promise[Unit]                                         = js.native
  def copyFile(src: String, dest: String): js.Promise[Unit]                                              = js.native
  def access(path: String): js.Promise[Unit]                                                             = js.native
  def stat(path: String): js.Promise[Stats]                                                              = js.native

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
trait WriteFileOptions extends js.Object:
  val encoding: js.UndefOr[String] = js.native
  val flag: js.UndefOr[String]     = js.native

object WriteFileOptions:
  def apply(encoding: String = "utf8", flag: Option[String] = None): WriteFileOptions =
    val opts = js.Dynamic.literal(encoding = encoding)
    flag.foreach(f => opts.updateDynamic("flag")(f))
    opts.asInstanceOf[WriteFileOptions]

@js.native
trait MkdirOptions extends js.Object:
  val recursive: js.UndefOr[Boolean] = js.native

object MkdirOptions:
  def apply(recursive: Boolean = false): MkdirOptions =
    js.Dynamic.literal(recursive = recursive).asInstanceOf[MkdirOptions]

@js.native
trait ReaddirOptions extends js.Object:
  val withFileTypes: js.UndefOr[Boolean] = js.native

object ReaddirOptions:
  def apply(withFileTypes: Boolean = false): ReaddirOptions =
    js.Dynamic.literal(withFileTypes = withFileTypes).asInstanceOf[ReaddirOptions]

@js.native
trait RmOptions extends js.Object:
  val recursive: js.UndefOr[Boolean] = js.native
  val force: js.UndefOr[Boolean]     = js.native

object RmOptions:
  def apply(recursive: Boolean = false, force: Boolean = false): RmOptions =
    js.Dynamic.literal(recursive = recursive, force = force).asInstanceOf[RmOptions]

@js.native
trait Stats extends js.Object:
  def isDirectory(): Boolean = js.native
  def isFile(): Boolean      = js.native
  def size: Double           = js.native
  def mode: Double           = js.native
  def mtime: js.Date         = js.native

@js.native
trait ReadStreamOptions extends js.Object {
  val highWaterMark: js.UndefOr[Int]
  val encoding: js.UndefOr[String]
  val start: js.UndefOr[Double]
  val end: js.UndefOr[Double]
}

object ReadStreamOptions {
  def apply(
      highWaterMark: Int = 64 * 1024,
      encoding: String = null,
      start: Option[Double] = None,
      end: Option[Double] = None,
  ): ReadStreamOptions = {
    val opts = js.Dynamic.literal()
    opts.updateDynamic("highWaterMark")(highWaterMark)
    if (encoding != null) opts.updateDynamic("encoding")(encoding)
    start.foreach(s => opts.updateDynamic("start")(s))
    end.foreach(e => opts.updateDynamic("end")(e))
    opts.asInstanceOf[ReadStreamOptions]
  }
}
