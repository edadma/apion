// File: nodejs/src/main/scala/io/github/edadma/nodejs/zlib.scala
package io.github.edadma.nodejs

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import scala.scalajs.js.typedarray.Uint8Array

@js.native
@JSImport("zlib", JSImport.Namespace)
object zlib extends js.Object:
  def gzip(buf: String | Buffer, callback: js.Function2[js.Error | Null, Buffer, Unit]): Unit = js.native
  def gunzip(buf: Buffer, callback: js.Function2[js.Error | Null, Buffer, Unit]): Unit        = js.native

  def deflate(buf: String | Buffer, callback: js.Function2[js.Error | Null, Buffer, Unit]): Unit = js.native
  def inflate(buf: Buffer, callback: js.Function2[js.Error | Null, Buffer, Unit]): Unit          = js.native

  def brotliCompress(buf: String | Buffer, callback: js.Function2[js.Error | Null, Buffer, Unit]): Unit = js.native
  def brotliDecompress(buf: Buffer, callback: js.Function2[js.Error | Null, Buffer, Unit]): Unit        = js.native

  // Optional compression parameters
  def gzip(buf: String | Buffer, options: ZlibOptions, callback: js.Function2[js.Error | Null, Buffer, Unit]): Unit =
    js.native
  def deflate(buf: String | Buffer, options: ZlibOptions, callback: js.Function2[js.Error | Null, Buffer, Unit]): Unit =
    js.native
  def brotliCompress(
      buf: String | Buffer,
      options: BrotliOptions,
      callback: js.Function2[js.Error | Null, Buffer, Unit],
  ): Unit = js.native

@js.native
trait Transform extends ReadableStream with WritableStream {
  // Inherits from both Readable and Writable
}

@js.native
trait ZlibOptions extends js.Object:
  val level: js.UndefOr[Int]      = js.native
  val memLevel: js.UndefOr[Int]   = js.native
  val strategy: js.UndefOr[Int]   = js.native
  val windowBits: js.UndefOr[Int] = js.native

object ZlibOptions:
  def apply(
      level: Option[Int] = None,
      memLevel: Option[Int] = None,
      strategy: Option[Int] = None,
      windowBits: Option[Int] = None,
  ): ZlibOptions =
    val opts = js.Dynamic.literal()
    level.foreach(l => opts.updateDynamic("level")(l))
    memLevel.foreach(m => opts.updateDynamic("memLevel")(m))
    strategy.foreach(s => opts.updateDynamic("strategy")(s))
    windowBits.foreach(w => opts.updateDynamic("windowBits")(w))
    opts.asInstanceOf[ZlibOptions]

@js.native
trait BrotliOptions extends js.Object:
  val params: js.UndefOr[js.Dictionary[Int]] = js.native

object BrotliOptions:
  def apply(params: Map[String, Int] = Map()): BrotliOptions =
    js.Dynamic.literal(
      params = js.Dictionary(params.toSeq*),
    ).asInstanceOf[BrotliOptions]
