package io.github.edadma.nodejs

import scala.scalajs.js
import scala.scalajs.js.annotation.*

@js.native
@JSImport("http", JSImport.Namespace)
object http extends js.Object:
  def createServer(requestListener: js.Function2[ServerRequest, ServerResponse, Unit]): Server = js.native

@js.native
trait Server extends js.Object:
  def listen(port: Int, callback: js.Function0[Unit]): this.type = js.native
  def close(callback: js.Function0[Unit]): this.type             = js.native

@js.native
trait Socket extends js.Object:
  val remoteAddress: String          = js.native
  val remotePort: Int                = js.native
  val localAddress: String           = js.native
  val localPort: Int                 = js.native
  val encrypted: js.UndefOr[Boolean] = js.native // For checking if connection is HTTPS

@js.native
trait ServerRequest extends js.Object:
  // Basic properties
  val url: String                    = js.native
  val method: String                 = js.native
  val headers: js.Dictionary[String] = js.native
  val socket: Socket                 = js.native

  // Additional standard Node.js request properties
  val httpVersion: String          = js.native
  val rawHeaders: js.Array[String] = js.native // Raw headers exactly as they were received
  val complete: Boolean            = js.native // Whether the request is completed
  val aborted: Boolean             = js.native // Whether the request was aborted

  // Connection information
  val connection: Socket = js.native // Alias for socket

  // Common Express-like properties (if using compatible middleware)
  val path: js.UndefOr[String]                 = js.native // URL path
  val hostname: js.UndefOr[String]             = js.native // Hostname from Host header
  val ip: js.UndefOr[String]                   = js.native // Remote IP
  val protocol: js.UndefOr[String]             = js.native // Protocol (http/https)
  val secure: js.UndefOr[Boolean]              = js.native // Is HTTPS?
  val port: js.UndefOr[Int]                    = js.native // Port number
  val query: js.UndefOr[js.Dictionary[String]] = js.native // Parsed query string

  // Stream methods
  def on(event: String, listener: js.Any): this.type = js.native
  def pipe(destination: js.Any): js.Any              = js.native
  def unpipe(destination: js.Any): this.type         = js.native
  def pause(): this.type                             = js.native
  def resume(): this.type                            = js.native

@js.native
trait ServerResponse extends WritableStream:
  // Basic response methods
  def writeHead(statusCode: Int): Unit                                                    = js.native
  def writeHead(statusCode: Int, headers: js.Dictionary[String | js.Array[String]]): Unit = js.native
  def write(chunk: String): Unit                                                          = js.native
  def end(data: String, encoding: String = "utf8"): Unit                                  = js.native
  def end(data: Buffer): Unit                                                             = js.native

  // Additional response properties/methods
  def getHeader(name: String): js.UndefOr[String | js.Array[String]]  = js.native
  def setHeader(name: String, value: String | js.Array[String]): Unit = js.native
  def removeHeader(name: String): Unit                                = js.native
  def hasHeader(name: String): Boolean                                = js.native

  // Status code and message
  var statusCode: Int                   = js.native
  var statusMessage: js.UndefOr[String] = js.native

  // Connection handling
  def setTimeout(msecs: Int, callback: js.Function0[Unit] = null): this.type = js.native

  // Stream methods
  def cork(): Unit         = js.native
  def uncork(): Unit       = js.native
  def flushHeaders(): Unit = js.native

  // Whether headers were sent
  val headersSent: Boolean = js.native
  val finished: Boolean    = js.native // Whether response was finished
