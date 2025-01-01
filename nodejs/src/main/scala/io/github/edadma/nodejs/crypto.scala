package io.github.edadma.nodejs

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import scala.scalajs.js.typedarray.Uint8Array

@js.native
@JSImport("crypto", JSImport.Namespace)
object crypto extends js.Object:
  def createHmac(algorithm: String, key: String | Uint8Array): Hmac = js.native
  def randomBytes(size: Int): Uint8Array                            = js.native

@js.native
trait Hmac extends js.Object:
  def update(data: String): Hmac       = js.native
  def digest(encoding: String): String = js.native

/** Enhanced Buffer type for crypto operations */
@js.native
trait CryptoBuffer extends Buffer:
  def apply(index: Int): Int               = js.native
  def update(index: Int, value: Int): Unit = js.native
