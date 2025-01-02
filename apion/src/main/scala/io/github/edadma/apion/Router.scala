package io.github.edadma.apion

import scala.annotation.tailrec
import scala.concurrent.Future
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

private sealed trait RouteSegment
private case class StaticSegment(value: String) extends RouteSegment
private case class ParamSegment(name: String)   extends RouteSegment
private case class WildcardSegment()            extends RouteSegment

trait Process

private case class Middleware(
    handler: Handler,
) extends Process

private case class Endpoint(
    method: String,
    segments: List[RouteSegment],
    handlers: List[Handler],
) extends Process

private case class Route(
    segments: List[RouteSegment],
    handler: Handler,
) extends Process

private case class SubRouter(
    path: String,
    router: Router,
) extends Process

object Router:
  private def parsePath(path: String): List[RouteSegment] =
    if path.isEmpty || path == "/" then Nil
    else
      path.split("/").filter(_.nonEmpty).map {
        case seg if seg.startsWith(":") => ParamSegment(seg.substring(1))
        case "*"                        => WildcardSegment()
        case seg                        => StaticSegment(seg)
      }.toList

private class Router extends Handler:
  private val routesBuffer = new scala.collection.mutable.ListBuffer[Process]
  private lazy val routes  = routesBuffer.toList

  def apply(request: Request): Future[Result] =
    processNext(routes, request, request.path.split("/").filter(_.nonEmpty).toList)

  private def processNext(ps: List[Process], req: Request, remainingPath: List[String]): Future[Result] =
    logger.debug(s"Processing next handler, path: /${remainingPath.mkString("/")}")
    ps match
      case Nil =>
        logger.debug("No more handlers")
        Future.successful(Skip)
      case p :: rest =>
        logger.debug(s"Processing handler of type: ${p.getClass.getSimpleName}")
        val result: Future[Result] = p match
          case SubRouter(path, router) =>
            matchSegments(Router.parsePath(path), remainingPath, Map()) match
              case Some((params, remaining)) =>
                val subRequest = req.copy(
                  path = "/" + remaining.mkString("/"),
                  params = req.params ++ params,
                  basePath = req.basePath + path,
                )
                router(subRequest)
              case None =>
                processNext(rest, req, remainingPath)

          case Endpoint(method, segments, handlers) =>
            logger.debug(s"Checking endpoint: $method /${segments.mkString("/")}")
            if (method != req.method) {
              processNext(rest, req, remainingPath)
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
                  processNext(rest, req, remainingPath)
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
                processNext(rest, req, remainingPath)

          case Middleware(handler) =>
            handler(req.copy(basePath = req.basePath)).map { result =>
              logger.debug(s"Middleware result: $result")
              result
            }

        result.flatMap {
          case Continue(newReq)   => processNext(rest, newReq, remainingPath)
          case Skip               => processNext(rest, req, remainingPath)
          case Complete(response) => Future.successful(InternalComplete(req, response))
          case result             => Future.successful(result)
        }

  @tailrec
  private def matchSegments(
      routeSegs: List[RouteSegment],
      pathSegs: List[String],
      params: Map[String, String],
  ): Option[(Map[String, String], List[String])] =
    (routeSegs, pathSegs) match
      case (Nil, remaining)                   => Some((params, remaining))
      case (WildcardSegment() :: rs, _ :: ps) => matchSegments(rs, ps, params)
      case (StaticSegment(exp) :: rs, act :: ps) if exp == act =>
        matchSegments(rs, ps, params)
      case (ParamSegment(name) :: rs, act :: ps) =>
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

  private def addEndpoint(method: String, path: String, handlers: List[Handler]): Router =
    routesBuffer += Endpoint(method, Router.parsePath(path), handlers)
    this

  def get(path: String, handlers: Handler*): Router    = addEndpoint("GET", path, handlers.toList)
  def post(path: String, handlers: Handler*): Router   = addEndpoint("POST", path, handlers.toList)
  def put(path: String, handlers: Handler*): Router    = addEndpoint("PUT", path, handlers.toList)
  def delete(path: String, handlers: Handler*): Router = addEndpoint("DELETE", path, handlers.toList)
  def patch(path: String, handlers: Handler*): Router  = addEndpoint("PATCH", path, handlers.toList)
