package io.github.edadma.apion

import scala.annotation.tailrec
import scala.concurrent.Future

import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

private sealed trait RouteSegment
private case class StaticSegment(value: String) extends RouteSegment
private case class ParamSegment(name: String)   extends RouteSegment
private case class WildcardSegment()            extends RouteSegment

trait Process {
  val handler: Handler
}

private case class Middleware(
    handler: Handler,
) extends Process

private case class Endpoint(
    method: String,
    segments: List[RouteSegment],
    handler: Handler,
) extends Process

private case class Route(
    segments: List[RouteSegment],
    handler: Handler,
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
  private val routesBuffer = scala.collection.mutable.ListBuffer[Process]()
  private lazy val routes  = routesBuffer.toList

  def apply(request: Request): Future[Result] =
    def processNext(ps: List[Process], req: Request): Future[Result] =
      val pathSegments = req.path.split("/").filter(_.nonEmpty).toList

      ps match
        case Nil => Future.successful(Skip)
        case p :: rest =>
          val result: Future[Result] =
            p match
              case Endpoint(method, segments, handler) if method == req.method =>
                matchSegments(segments, pathSegments, Map()) match
                  case Some((pathParams, Nil)) => handler(req.copy(params = pathParams))
                  case _                       => processNext(rest, req)
              case Route(segments, handler) =>
                matchSegments(segments, pathSegments, Map()) match
                  case Some((pathParams, remaining)) => handler(req.copy(params = pathParams))
                  case _                             => processNext(rest, req)
              case Middleware(handler) => handler(req)

          result.flatMap {
            case Continue(newReq) => processNext(rest, newReq)
            case Skip             => processNext(rest, req)
            case result           => Future.successful(result)
          }

    processNext(routes, request)

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

  private def addEndpoint(method: String, path: String, handler: Handler): Router =
    routesBuffer += Endpoint(method, Router.parsePath(path), handler)
    this

  def get(path: String, handler: Handler): Router    = addEndpoint("GET", path, handler)
  def post(path: String, handler: Handler): Router   = addEndpoint("POST", path, handler)
  def put(path: String, handler: Handler): Router    = addEndpoint("PUT", path, handler)
  def delete(path: String, handler: Handler): Router = addEndpoint("DELETE", path, handler)
  def patch(path: String, handler: Handler): Router  = addEndpoint("PATCH", path, handler)
  def use(path: String, handler: Handler): Router =
    routesBuffer += Route(Router.parsePath(path), handler)
    this
  def use(handler: Handler): Router =
    routesBuffer += Middleware(handler)
    this
