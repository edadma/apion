package io.github.edadma.apion

import scala.annotation.tailrec
import scala.concurrent.Future

import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

private sealed trait RouteSegment
private case class StaticSegment(value: String) extends RouteSegment
private case class ParamSegment(name: String)   extends RouteSegment
private case class WildcardSegment()            extends RouteSegment

private case class Route(
    method: String,
    segments: List[RouteSegment],
    handler: Handler,
)

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
  private val routes = scala.collection.mutable.ListBuffer[Route]()

  def apply(r: Request): Future[Result] = Future(Skip)

  private def addRoute(method: String, path: String, handler: Handler): Router =
    routes += Route(method, Router.parsePath(path), handler)
    this

  def get(path: String, handler: Handler): Router    = addRoute("GET", path, handler)
  def post(path: String, handler: Handler): Router   = addRoute("POST", path, handler)
  def put(path: String, handler: Handler): Router    = addRoute("PUT", path, handler)
  def delete(path: String, handler: Handler): Router = addRoute("DELETE", path, handler)
  def patch(path: String, handler: Handler): Router  = addRoute("PATCH", path, handler)

  private[apion] def matchRoute(method: String, path: String): Option[(Handler, Map[String, String], List[String])] =
    val segments = path.split("/").filter(_.nonEmpty).toList

    @tailrec
    def matchSegments(
        routeSegs: List[RouteSegment],
        pathSegs: List[String],
        params: Map[String, String],
    ): Option[(Map[String, String], List[String])] =
      (routeSegs, pathSegs) match
        case (Nil, remaining)                                    => Some((params, remaining))
        case (WildcardSegment() :: rs, _ :: ps)                  => matchSegments(rs, ps, params)
        case (StaticSegment(exp) :: rs, act :: ps) if exp == act => matchSegments(rs, ps, params)
        case (ParamSegment(name) :: rs, act :: ps)               => matchSegments(rs, ps, params + (name -> act))
        case _                                                   => None

    routes.find(r => r.method == method).flatMap { route =>
      matchSegments(route.segments, segments, Map()).map((params, rem) => (route.handler, params, rem))
    }
