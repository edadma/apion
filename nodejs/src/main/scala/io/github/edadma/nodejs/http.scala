package io.github.edadma.nodejs

import scala.scalajs.js
import scala.scalajs.js.annotation.*

@js.native
@JSImport("http", JSImport.Namespace)
object http extends js.Object:
  def createServer(requestListener: js.Function2[ServerRequest, ServerResponse, Unit]): Server = js.native

@js.native
trait Server extends js.Object:
  def listen(port: Int, callback: js.Function0[Unit]): Unit = js.native

@js.native
trait ServerRequest extends js.Object:
  val url: String                    = js.native
  val method: String                 = js.native
  val headers: js.Dictionary[String] = js.native

@js.native
trait ServerResponse extends js.Object:
  def writeHead(statusCode: Int): Unit                                 = js.native
  def writeHead(statusCode: Int, headers: js.Dictionary[String]): Unit = js.native
  def write(chunk: String): Unit                                       = js.native
  def end(): Unit                                                      = js.native
  def end(data: String): Unit                                          = js.native