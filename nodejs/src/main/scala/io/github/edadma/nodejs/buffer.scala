package io.github.edadma.nodejs

import scala.scalajs.js
import scala.scalajs.js.annotation.*

@js.native
@JSImport("buffer", JSImport.Namespace)
object bufferMod extends js.Object:
  val Buffer: BufferObject = js.native

@js.native
trait BufferObject extends js.Object:
  def apply(data: js.typedarray.Uint8Array): Buffer           = js.native
  def from(str: String, encoding: String = "utf8"): Buffer    = js.native
  def from(buf: Buffer): Buffer                               = js.native
  def byteLength(str: String, encoding: String = "utf8"): Int = js.native

@js.native
@JSGlobal
class Buffer extends js.Object:
  def this(data: js.typedarray.Uint8Array) = this()
  def toString(encoding: String = "utf8"): String = js.native
  def byteLength: Int                             = js.native
  def length: Int                                 = js.native // alias for byteLength
