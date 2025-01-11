package io.github.edadma.apion

import io.github.edadma.nodejs.{PipeOptions, ServerRequest, ServerResponse, http, Server as NodeServer}

import scala.scalajs.js
import scala.concurrent.Future

import scala.language.postfixOps

class Server {
  private val router = new Router()
  private val server = http.createServer((req: ServerRequest, res: ServerResponse) =>
    handleRequest(req, res),
  )

  def close(cb: => Unit = ()): Unit = server.close(() => cb)

  def listen(port: Int)(callback: => Unit): NodeServer = {
    server.listen(port, () => callback)
    server
  }

  def get(path: String, handlers: Handler*): Server = {
    router.get(path, handlers*)
    this
  }

  def post(path: String, handlers: Handler*): Server = {
    router.post(path, handlers*)
    this
  }

  def put(path: String, handlers: Handler*): Server = {
    router.put(path, handlers*)
    this
  }

  def delete(path: String, handlers: Handler*): Server = {
    router.delete(path, handlers*)
    this
  }

  def patch(path: String, handlers: Handler*): Server = {
    router.patch(path, handlers*)
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
    router.use(path, router)
    this
  }

  def use(handler: (ServerError, Request) => Future[Result]): Server =
    router.use(handler)
    this

  private def handleRequest(req: ServerRequest, res: ServerResponse): Unit = {
    def sendResponse(finalReq: Request, response: Response): Unit = {
      logger.debug(s"Processing ${finalReq.finalizers.length} finalizers")
      finalReq.finalizers.reverse.foldLeft(Future.successful(response)) {
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
        // Convert headers to dictionary, preserving multiple values
        val headerDict = js.Dictionary[String | js.Array[String]]()
        finalResponse.headers.toMap.foreach { case (key, values) =>
          if (values.length > 1) {
            headerDict(key) = js.Array(values*)
          } else {
            headerDict(key) = values.head
          }
        }
        res.writeHead(finalResponse.status, headerDict) // Write body and end response

        finalResponse.body match
          case StringBody(_, data) => res.end(data)
          case BufferBody(content) => res.end(content)
          case ReadableStreamBody(stream) =>
            stream
              .pipe(res, PipeOptions()) // Don't end automatically
              .on(
                "error",
                (err: js.Error) => {
                  logger.error(s"Stream error: ${err.message}")
                  res.end() // End on error
                },
              )
              .on(
                "end",
                () => {
                  res.end() // End explicitly when stream completes
                },
              )
          case EmptyBody => res.end()
      }
    }

    // Convert Node.js request to our Request type
    val request = Request.fromServerRequest(req)
    logger.debug(s"handleRequest: $request")

    // Process the request through our router
    router(request).map {
      case Complete(_)                          => sys.error("Complete should be transformed to InternalComplete")
      case InternalComplete(finalReq, response) => sendResponse(finalReq, response)
      case Skip                                 => sendResponse(request, Response.text("Not Found", 404))
      case Continue(request) => sendResponse(request, Response.text("Internal Server Error - Unexpected Continue", 500))
      case Fail(error)       => sendResponse(request, error.toResponse)
    }.recover {
      case e: Exception =>
        // Handle uncaught exceptions
        logger.error(s"Uncaught error processing request: ${e.getMessage}")
        logger.error(e.getStackTrace.mkString("\n"))
        sendResponse(request, Response.text("Internal Server Error - Uncaught error", 500))
    }
  }
}
