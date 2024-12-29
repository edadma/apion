package io.github.edadma.nodejs

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import scala.scalajs.js.typedarray.Uint8Array

@js.native
@JSImport("fs", JSImport.Namespace)
object fs extends js.Object:
  val promises: FSPromises = js.native

@js.native
trait FSPromises extends js.Object:
  def readFile(path: String): js.Promise[Uint8Array]                               = js.native
  def readFile(path: String, options: js.Dynamic): js.Promise[String | Uint8Array] = js.native
  def stat(path: String): js.Promise[Stats]                                        = js.native

@js.native
trait Stats extends js.Object:
  def isDirectory(): Boolean = js.native
  def size: Double           = js.native
