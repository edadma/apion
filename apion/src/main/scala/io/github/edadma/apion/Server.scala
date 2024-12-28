package io.github.edadma.apion

import io.github.edadma.nodejs.{http, ServerRequest, ServerResponse}

import scala.scalajs.js
import scala.scalajs.js.Dictionary

class Server:
  private val server = http.createServer((req: ServerRequest, res: ServerResponse) => handleRequest(req, res))

  def listen(port: Int)(callback: => Unit): Unit =
    server.listen(port, () => callback)

  private def handleRequest(req: ServerRequest, res: ServerResponse): Unit =
    res.writeHead(200, Dictionary("Content-Type" -> "text/plain"))
    res.end("Hello from Apion!")

object Server:
  def apply(): Server = new Server()
