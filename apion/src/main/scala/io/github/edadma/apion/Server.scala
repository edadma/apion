package io.github.edadma.apion

import io.github.edadma.nodejs.{ServerRequest, ServerResponse, http, Server as NodeServer}

import scala.scalajs.js
import scala.concurrent.Future
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

import scala.language.postfixOps

class Server {
  private val router = new Router()
  private val server = http.createServer((req: ServerRequest, res: ServerResponse) =>
    handleRequest(req, res),
  )

  def listen(port: Int)(callback: => Unit): NodeServer = {
    server.listen(port, () => callback)
    server
  }

  // HTTP method handlers
  def get(path: String, handler: Handler): Server = {
    router.get(path, handler)
    this
  }

  def post(path: String, handler: Handler): Server = {
    router.post(path, handler)
    this
  }

  def put(path: String, handler: Handler): Server = {
    router.put(path, handler)
    this
  }

  def delete(path: String, handler: Handler): Server = {
    router.delete(path, handler)
    this
  }

  def patch(path: String, handler: Handler): Server = {
    router.patch(path, handler)
    this
  }

  // Middleware and routing
  def use(handler: Handler): Server = {
    router.use(handler)
    this
  }

  def use(path: String, handler: Handler): Server = {
    router.use(path, handler)
    this
  }

  def use(path: String, router: Router): Server = {
    this.router.use(path, router)
    this
  }

  private def handleRequest(req: ServerRequest, res: ServerResponse): Unit = {
    // Convert Node.js request to our Request type
    val request = Request.fromServerRequest(req)

    // Process the request through our router
    router(request).map {
      case Complete(response) =>
      case InternalComplete(finalReq, response) =>
        logger.debug(s"Processing ${finalReq.finalizers.length} finalizers")
        finalReq.finalizers.foldLeft(Future.successful(response)) {
          case (respFuture, finalizer) =>
            respFuture.flatMap { resp =>
              logger.debug("Executing finalizer")
              finalizer(finalReq, resp).map { finalResp =>
                logger.debug(s"Finalizer complete, headers: ${finalResp.headers.toMap}")
                finalResp
              }
            }
        }.map { finalResponse =>
          logger.debug(finalResponse.headers.toMap.flatMap { case (key, values) =>
            values.map(value => key -> value)
          }.toSeq)
          // Write response headers
          logger.debug(s"Writing response headers: ${finalResponse.headers.toMap}")
          // Convert headers to dictionary, preserving multiple values for Set-Cookie
          val headerDict = js.Dictionary[String | js.Array[String]]()
          finalResponse.headers.toMap.foreach { case (key, values) =>
            if (key.toLowerCase == "set-cookie" && values.length > 1) {
              headerDict(key) = js.Array(values*)
            } else {
              headerDict(key) = values.head
            }
          }
          res.writeHead(finalResponse.status, headerDict) // Write body and end response
          res.end(finalResponse.body)
        }

      case Skip =>
        // Handle 404 Not Found
        res.writeHead(
          404,
          js.Dictionary(
            "Content-Type" -> "application/json",
          ),
        )
        res.end("""{"error": "Not Found"}""")

      case Continue(request) =>
        // Handle unexpected Continue result at top level
        res.writeHead(
          500,
          js.Dictionary(
            "Content-Type" -> "application/json",
          ),
        )
        res.end("""{"error": "Internal Server Error - Unexpected Continue"}""")

      case Fail(error) =>
        // Handle errors
        val (status, message) = error match {
          case ValidationError(msg) => (400, msg)
          case AuthError(msg)       => (401, msg)
          case NotFoundError(msg)   => (404, msg)
        }

        res.writeHead(
          status,
          js.Dictionary(
            "Content-Type" -> "application/json",
          ),
        )
        res.end(s"""{"error": "$message"}""")
    }.recover {
      case e: Exception =>
        // Handle uncaught exceptions
        logger.error(s"Uncaught error processing request: ${e.getMessage}")
        e.printStackTrace()

        res.writeHead(
          500,
          js.Dictionary(
            "Content-Type" -> "application/json",
          ),
        )
        res.end("""{"error": "Internal Server Error"}""")
    }
  }
}
