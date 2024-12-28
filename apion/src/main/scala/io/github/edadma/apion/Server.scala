package io.github.edadma.apion

import io.github.edadma.nodejs.{http, ServerRequest, ServerResponse}
import scala.scalajs.js
import scala.concurrent.ExecutionContext.Implicits.global

class Server:
  private val router = Router()
  private val server = http.createServer((req: ServerRequest, res: ServerResponse) =>
    handleRequest(req, res),
  )

  def listen(port: Int)(callback: => Unit): Unit =
    server.listen(port, () => callback)

  def get(path: String, endpoint: Endpoint): Server =
    router.get(path, endpoint)
    this

  def post(path: String, endpoint: Endpoint): Server =
    router.post(path, endpoint)
    this

  def put(path: String, endpoint: Endpoint): Server =
    router.put(path, endpoint)
    this

  def use(middleware: Middleware): Server =
    router.use(middleware)
    this

  def route(prefix: String)(routeSetup: Router => Unit): Server =
    router.route(prefix)(routeSetup)
    this

  private def handleRequest(req: ServerRequest, res: ServerResponse): Unit =
    // Convert Node.js request to our Request type
    val request = Request(
      method = req.method,
      url = req.url,
      headers = req.headers.toMap,
      rawRequest = Some(req),
    )

    // Handle the request through our router
    router.handle(request).foreach { response =>
      // Write response headers
      res.writeHead(
        response.status,
        js.Dictionary(response.headers.toSeq.map(p => (p._1, p._2))*),
      )
      // Write body and end response
      res.end(response.body)
    }

object Server:
  def apply(): Server = new Server()
