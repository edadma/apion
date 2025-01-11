package io.github.edadma.apion

import scala.annotation.tailrec
import scala.concurrent.Future

private sealed trait RouteSegment
private case class StaticSegment(value: String) extends RouteSegment
private case class ParamSegment(name: String)   extends RouteSegment
private case class WildcardSegment()            extends RouteSegment

trait Process
private case class ErrorHandler(handler: (ServerError, Request) => Future[Result])                 extends Process
private case class Middleware(handler: Handler)                                                    extends Process
private case class Endpoint(method: String, segments: List[RouteSegment], handlers: List[Handler]) extends Process
private case class Route(segments: List[RouteSegment], handler: Handler)                           extends Process
private case class SubRouter(path: String, router: Router)                                         extends Process

object Router:
  private def parsePath(path: String): List[RouteSegment] =
    if path.isEmpty || path == "/" then Nil
    else
      path.split("/").filter(_.nonEmpty).map {
        case seg if seg.startsWith(":") => ParamSegment(seg.substring(1))
        case "*"                        => WildcardSegment()
        case seg                        => StaticSegment(seg)
      }.toList

class Router extends Handler:
  private val routesBuffer = new scala.collection.mutable.ListBuffer[Process]
  private lazy val routes  = routesBuffer.toList

  def apply(request: Request): Future[Result] =
    processNext(routes, request, request.path.split("/").filter(_.nonEmpty).toList, None)

//  private def processError(error: ServerError, request: Request): Future[Result] = {
//    def tryNextHandler(handlers: List[Process]): Future[Result] = handlers match {
//      case ErrorHandler(handler) :: rest =>
//        handler(error, request).flatMap {
//          case Skip               => tryNextHandler(rest)
//          case Complete(response) => Future.successful(InternalComplete(request, response))
//          case Fail(nextError) =>
//            tryNextHandler(rest).flatMap {
//              case Skip   => processError(nextError, request)
//              case result => Future.successful(result)
//            }
//          case InternalComplete(req, res) => Future.successful(InternalComplete(req, res))
//          case Continue(_)                => Future.failed(new Exception("Continue not valid in error handler"))
//        }
//      case _ :: rest => tryNextHandler(rest)
//      case Nil       =>
//        // Default error handler converts error to response
//        Future.successful(InternalComplete(request, error.toResponse))
//    }
//
//    tryNextHandler(routes)
//  }

  private def processError(error: ServerError, request: Request): Future[Result] = {
    logger.debug(s"processError called with error type: ${error.getClass.getSimpleName}")

    def tryNextHandler(handlers: List[Process]): Future[Result] = {
      logger.debug(s"tryNextHandler with ${handlers.length} handlers remaining")
      handlers match {
        case ErrorHandler(handler) :: rest =>
          logger.debug("Found error handler, attempting to handle error")
          handler(error, request).flatMap {
            case Skip =>
              logger.debug("Handler skipped, trying next")
              tryNextHandler(rest)
            case Complete(response) =>
              logger.debug("Handler completed with response")
              Future.successful(InternalComplete(request, response))
            case Fail(nextError) =>
              logger.debug(s"Handler generated new error: ${nextError.getClass.getSimpleName}")
              processError(nextError, request)
            case InternalComplete(req, res) =>
              logger.debug("Handler returned InternalComplete")
              Future.successful(InternalComplete(req, res))
            case Continue(_) =>
              logger.debug("Handler returned invalid Continue")
              Future.failed(new Exception("Continue not valid in error handler"))
          }
        case _ :: rest =>
          logger.debug("Skipping non-error handler")
          tryNextHandler(rest)
        case Nil =>
          logger.debug("No more handlers, using default error response")
          Future.successful(InternalComplete(request, error.toResponse))
      }
    }

    tryNextHandler(routes)
  }

  private def processNext(
      ps: List[Process],
      req: Request,
      remainingPath: List[String],
      currentError: Option[ServerError],
  ): Future[Result] =
    logger.debug(s"Processing next handler, path: /${remainingPath.mkString("/")}")
    ps match
      case Nil =>
        logger.debug("No more handlers")
        currentError match {
          case Some(error) => processError(error, req)
          case None        => Future.successful(Skip)
        }
      case p :: rest =>
        logger.debug(s"Processing handler of type: ${p.getClass.getSimpleName}")
        if (currentError.isDefined) {
          processError(currentError.get, req)
        } else {
          val result: Future[Result] =
            p match
//              case SubRouter(path, router) =>
//                matchSegments(Router.parsePath(path), remainingPath, Map()) match
//                  case Some((params, remaining)) =>
//                    val subRequest = req.copy(
//                      path = "/" + remaining.mkString("/"),
//                      params = req.params ++ params,
//                      basePath = req.basePath + path,
//                    )
//                    router(subRequest)
//                  case None =>
//                    processNext(rest, req, remainingPath, None)

              case SubRouter(path, router) =>
                val routeSegs = Router.parsePath(path) // e.g. ["sub"]
                val pathSegs  = remainingPath          // e.g. ["sub", "fail"]

                // Try to match just the subrouter prefix
                if pathSegs.nonEmpty && pathSegs.head == routeSegs.head.asInstanceOf[StaticSegment].value then
                  // Pass remaining segments to the subrouter
                  val remaining = pathSegs.drop(1)
                  router(req.copy(
                    path = "/" + remaining.mkString("/"),
                    params = req.params,
                    basePath = req.basePath + path,
                  ))
                else
                  processNext(rest, req, remainingPath, None)

              case Endpoint(method, segments, handlers) =>
                logger.debug(s"Checking endpoint: $method /${segments.mkString("/")}")
                if (method != req.method) {
                  processNext(rest, req, remainingPath, None)
                } else {
                  matchSegments(segments, remainingPath, Map()) match {
                    case Some((params, Nil)) =>
                      def runHandlers(hs: List[Handler], r: Request): Future[Result] =
                        hs match
                          case handler :: next =>
                            handler(r).flatMap {
                              case Continue(newReq)    => runHandlers(next, newReq)
                              case Skip                => runHandlers(next, r)
                              case Complete(response)  => Future.successful(InternalComplete(r, response))
                              case _: InternalComplete => sys.error(s"InternalComplete should not appear here")
                              case result              => Future.successful(result)
                            }
                          case Nil => Future.successful(Skip)

                      runHandlers(
                        handlers,
                        req.copy(
                          params = req.params ++ params,
                          basePath = req.basePath,
                        ),
                      )
                    case _ =>
                      processNext(rest, req, remainingPath, None)
                  }
                }

              case Route(segments, handler) =>
                matchSegments(segments, remainingPath, Map()) match
                  case Some((params, remaining)) =>
                    handler(req.copy(
                      path = "/" + remaining.mkString("/"),
                      params = req.params ++ params,
                      basePath = req.basePath,
                    ))
                  case None =>
                    processNext(rest, req, remainingPath, None)

              case Middleware(handler) =>
                handler(req.copy(basePath = req.basePath)).map { result =>
                  logger.debug(s"Middleware result: $result")
                  result
                }

              case ErrorHandler(handler) =>
                currentError match {
                  case Some(error) =>
                    handler(error, req).flatMap {
                      case Skip   => processNext(rest, req, remainingPath, Some(error))
                      case result => Future.successful(result)
                    }
                  case None =>
                    processNext(rest, req, remainingPath, None)
                }
            end match

          result.flatMap {
            case Continue(newReq)   => processNext(rest, newReq, remainingPath, None)
            case Skip               => processNext(rest, req, remainingPath, None)
            case Complete(response) => Future.successful(InternalComplete(req, response))
            case Fail(error)        => processNext(rest, req, remainingPath, Some(error))
            case result             => Future.successful(result)
          }
        }

//  @tailrec
//  private def matchSegments(
//      routeSegs: List[RouteSegment],
//      pathSegs: List[String],
//      params: Map[String, String],
//  ): Option[(Map[String, String], List[String])] =
//    (routeSegs, pathSegs) match
//      case (Nil, remaining)                   => Some((params, remaining))
//      case (WildcardSegment() :: rs, _ :: ps) => matchSegments(rs, ps, params)
//      case (StaticSegment(exp) :: rs, act :: ps) if exp == act =>
//        matchSegments(rs, ps, params)
//      case (ParamSegment(name) :: rs, act :: ps) =>
//        matchSegments(rs, ps, params + (name -> act))
//      case _ => None

  @tailrec
  private def matchSegments(
      routeSegs: List[RouteSegment],
      pathSegs: List[String],
      params: Map[String, String],
  ): Option[(Map[String, String], List[String])] =
    (routeSegs, pathSegs) match
      case (Nil, remaining) =>
        // When we run out of route segments, pass remaining path to subrouter
        Some((params, remaining))
      case (StaticSegment(exp) :: rs, act :: ps) if exp == act =>
        // For matching static segments, continue matching
        matchSegments(rs, ps, params)
      case (ParamSegment(name) :: rs, act :: ps) =>
        // For parameters, add to params map
        matchSegments(rs, ps, params + (name -> act))
      case _ => None

  def use(path: String, router: Router): Router =
    routesBuffer += SubRouter(path, router)
    this

  def use(handler: Handler): Router =
    routesBuffer += Middleware(handler)
    this

  def use(path: String, handler: Handler): Router =
    routesBuffer += Route(Router.parsePath(path), handler)
    this

  def use(handler: (ServerError, Request) => Future[Result]): Router =
    routesBuffer += ErrorHandler(handler)
    this

  private def addEndpoint(method: String, path: String, handlers: List[Handler]): Router =
    routesBuffer += Endpoint(method, Router.parsePath(path), handlers)
    this

  def get(path: String, handlers: Handler*): Router    = addEndpoint("GET", path, handlers.toList)
  def post(path: String, handlers: Handler*): Router   = addEndpoint("POST", path, handlers.toList)
  def put(path: String, handlers: Handler*): Router    = addEndpoint("PUT", path, handlers.toList)
  def delete(path: String, handlers: Handler*): Router = addEndpoint("DELETE", path, handlers.toList)
  def patch(path: String, handlers: Handler*): Router  = addEndpoint("PATCH", path, handlers.toList)
