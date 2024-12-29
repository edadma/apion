package io.github.edadma.apion

import scala.annotation.tailrec

sealed trait RouteSegment
case class StaticSegment(value: String) extends RouteSegment
case class ParamSegment(name: String)   extends RouteSegment
case class WildcardSegment()            extends RouteSegment

case class Route(
    method: String,
    segments: List[RouteSegment],
    handler: Endpoint,
)

object Router:
  def parsePath(path: String): List[RouteSegment] =
    if path.isEmpty || path == "/" then Nil
    else
      path.split("/").filter(_.nonEmpty).map {
        case seg if seg.startsWith(":") => ParamSegment(seg.substring(1))
        case "*"                        => WildcardSegment()
        case seg                        => StaticSegment(seg)
      }.toList

class Router:
  private val routes = scala.collection.mutable.ListBuffer[Route]()

  private def addRoute(method: String, path: String, handler: Endpoint): Router =
    routes += Route(method, Router.parsePath(path), handler)
    this

  def get(path: String, handler: Endpoint): Router    = addRoute("GET", path, handler)
  def post(path: String, handler: Endpoint): Router   = addRoute("POST", path, handler)
  def put(path: String, handler: Endpoint): Router    = addRoute("PUT", path, handler)
  def delete(path: String, handler: Endpoint): Router = addRoute("DELETE", path, handler)
  def patch(path: String, handler: Endpoint): Router  = addRoute("PATCH", path, handler)

  private[apion] def matchRoute(method: String, path: String): Option[(Endpoint, Map[String, String], List[String])] =
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
