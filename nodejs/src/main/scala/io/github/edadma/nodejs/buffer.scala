package io.github.edadma.nodejs

import scala.scalajs.js
import scala.scalajs.js.annotation.*

// The Buffer class (globally available)
@js.native
@JSGlobal("Buffer")
class Buffer extends js.Object:
  // Properties
  def length: Int = js.native

  def byteLength: Int = js.native

  // Basic operations
  def toString(encoding: String = "utf8"): String                                                    = js.native
  def copy(target: Buffer, targetStart: Int = 0, sourceStart: Int = 0, sourceEnd: Int = length): Int = js.native
  def slice(start: Int = 0, end: Int = length): Buffer                                               = js.native
  def write(string: String, offset: Int = 0, encoding: String = "utf8"): Int                         = js.native

  // Array-like access
  def apply(index: Int): Int               = js.native
  def update(index: Int, value: Int): Unit = js.native

  // Comparison & search
  def equals(otherBuffer: Buffer): Boolean                                                       = js.native
  def compare(target: Buffer): Int                                                               = js.native
  def indexOf(value: String | Buffer | Int, byteOffset: Int = 0, encoding: String = "utf8"): Int = js.native

  // Modification
  def fill(value: String | Buffer | Int, offset: Int = 0, end: Int = length): this.type = js.native

// Companion object containing static methods
@js.native
trait BufferStatic extends js.Object:
  // Creation methods
  def from(data: js.typedarray.Uint8Array): Buffer             = js.native
  def from(str: String, encoding: String = "utf8"): Buffer     = js.native
  def from(buf: Buffer): Buffer                                = js.native
  def alloc(size: Int): Buffer                                 = js.native
  def allocUnsafe(size: Int): Buffer                           = js.native
  def concat(list: js.Array[Buffer]): Buffer                   = js.native
  def concat(list: js.Array[Buffer], totalLength: Int): Buffer = js.native

  // Utility methods
  def byteLength(str: String, encoding: String = "utf8"): Int = js.native
  def isBuffer(obj: Any): Boolean                             = js.native
  def isEncoding(encoding: String): Boolean                   = js.native

// The buffer module itself
@js.native
@JSImport("buffer", JSImport.Namespace)
object bufferMod extends js.Object:
  val Buffer: BufferStatic = js.native

object BufferEncoding:
  val UTF8: String    = "utf8"
  val UTF16LE: String = "utf16le"
  val BASE64: String  = "base64"
  val HEX: String     = "hex"
  val ASCII: String   = "ascii"
  val BINARY: String  = "binary"
