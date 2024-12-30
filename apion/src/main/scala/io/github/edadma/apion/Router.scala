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
  val prefix: String // Store the route prefix
}

private case class Middleware(
    handler: Handler,
    prefix: String = "",
) extends Process

private case class Endpoint(
    method: String,
    segments: List[RouteSegment],
    handler: Handler,
    prefix: String = "",
) extends Process

private case class Route(
    segments: List[RouteSegment],
    handler: Handler,
    prefix: String = "",
) extends Process

private case class SubRouter(
    path: String,
    router: Router,
    prefix: String = "",
) extends Process {
  val handler: Handler = router
}

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
    processNext(routes, request, request.path.split("/").filter(_.nonEmpty).toList)

  private def processNext(ps: List[Process], req: Request, remainingPath: List[String]): Future[Result] =
    ps match
      case Nil => Future.successful(Skip)
      case p :: rest =>
        val result: Future[Result] = p match
          case SubRouter(path, router, prefix) =>
            matchSegments(Router.parsePath(path), remainingPath, Map()) match
              case Some((params, remaining)) =>
                val subRequest = req.copy(
                  path = "/" + remaining.mkString("/"),
                  params = req.params ++ params,
                  basePath = req.basePath + prefix + path,
                )
                router(subRequest)
              case None =>
                processNext(rest, req, remainingPath)

          case Endpoint(method, segments, handler, prefix) if method == req.method =>
            matchSegments(segments, remainingPath, Map()) match
              case Some((params, Nil)) =>
                handler(req.copy(
                  params = req.params ++ params,
                  basePath = req.basePath + prefix,
                ))
              case _ =>
                processNext(rest, req, remainingPath)

          case Route(segments, handler, prefix) =>
            matchSegments(segments, remainingPath, Map()) match
              case Some((params, remaining)) =>
                handler(req.copy(
                  path = "/" + remaining.mkString("/"),
                  params = req.params ++ params,
                  basePath = req.basePath + prefix,
                ))
              case None =>
                processNext(rest, req, remainingPath)

          case Middleware(handler, prefix) =>
            handler(req.copy(basePath = req.basePath + prefix))

        result.flatMap {
          case Continue(newReq) => processNext(rest, newReq, remainingPath)
          case Skip             => processNext(rest, req, remainingPath)
          case result           => Future.successful(result)
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

  private def addEndpoint(method: String, path: String, handler: Handler): Router =
    routesBuffer += Endpoint(method, Router.parsePath(path), handler)
    this

  def get(path: String, handler: Handler): Router    = addEndpoint("GET", path, handler)
  def post(path: String, handler: Handler): Router   = addEndpoint("POST", path, handler)
  def put(path: String, handler: Handler): Router    = addEndpoint("PUT", path, handler)
  def delete(path: String, handler: Handler): Router = addEndpoint("DELETE", path, handler)
  def patch(path: String, handler: Handler): Router  = addEndpoint("PATCH", path, handler)
