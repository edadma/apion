package io.github.edadma.nodejs

import scala.scalajs.js
import scala.scalajs.js.annotation.*

@js.native
@JSImport("buffer", JSImport.Namespace)
object bufferMod extends js.Object:
  val Buffer: BufferObject = js.native

@js.native
trait BufferObject extends js.Object:
  def from(str: String, encoding: String = "utf8"): Buffer = js.native
  def from(buf: Buffer): Buffer                            = js.native

@js.native
trait Buffer extends js.Object:
  def toString(encoding: String = "utf8"): String = js.native
  def byteLength: Int                             = js.native

//@js.native
//@JSGlobal
//class Uint8Array(elements: Short*) extends js.Object {
//  def this(length: Int) = this()
//  def this(array: js.Iterable[Short]) = this()
//  def this(typedArray: Uint8Array) = this()
//  def this(buffer: ArrayBuffer, byteOffset: Int = 0, length: Int = js.native) = this()
//}
//
//@js.native
//@JSGlobal
//class ArrayBuffer extends js.Object
