// File: nodejs/src/main/scala/io/github/edadma/nodejs/fetch.scala
package io.github.edadma.nodejs

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import scala.concurrent.{Future, Promise}
import scala.scalajs.js.typedarray.Uint8Array

@js.native
trait Response extends js.Object:
  def text(): js.Promise[String]            = js.native
  def json(): js.Promise[js.Any]            = js.native
  def arrayBuffer(): js.Promise[Uint8Array] = js.native
  val ok: Boolean                           = js.native
  val status: Int                           = js.native
  val statusText: String                    = js.native
  val headers: Headers                      = js.native

@js.native
@JSGlobal
object fetch extends js.Object:
  def apply(url: String): js.Promise[Response]                        = js.native
  def apply(url: String, options: FetchOptions): js.Promise[Response] = js.native

@js.native
trait Headers extends js.Object:
  def get(name: String): String | Null       = js.native
  def has(name: String): Boolean             = js.native
  def getAll(name: String): js.Array[String] = js.native

object FetchOptions:
  def apply(
      method: String = "GET",
      headers: js.Dictionary[String] = js.Dictionary(),
      body: js.Any = null,
  ): FetchOptions =
    val opts = js.Dynamic.literal(
      method = method,
      headers = headers,
    )
    if body != null then opts.updateDynamic("body")(body)
    opts.asInstanceOf[FetchOptions]

@js.native
trait FetchOptions extends js.Object:
  val method: String                 = js.native
  val headers: js.Dictionary[String] = js.native
  val body: js.UndefOr[js.Any]       = js.native
