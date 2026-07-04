package io.github.edadma.apion

import scala.annotation.tailrec
import scala.concurrent.Future

private sealed trait RouteSegment
private case class StaticSegment(value: String) extends RouteSegment
private case class ParamSegment(name: String)   extends RouteSegment
private case object WildcardSegment             extends RouteSegment

private sealed trait Process
private case class Middleware(handler: Handler)                                       extends Process
private case class Route(segments: List[RouteSegment], handler: Handler)              extends Process
private case class Endpoint(method: String, segments: List[RouteSegment], handlers: List[Handler]) extends Process
private case class SubRouter(segments: List[RouteSegment], mount: String, router: Router)          extends Process
private case class ErrorHandler(handler: (ServerError, Request) => Future[Result])    extends Process

object Router:
  /** Split a path into segments, dropping empty segments so that leading, trailing
    * and doubled slashes are all normalised away (`/users/` and `/users` are equal).
    */
  private[apion] def splitPath(path: String): List[String] =
    path.split("/").iterator.filter(_.nonEmpty).toList

  private def parsePath(path: String): List[RouteSegment] =
    splitPath(path).map {
      case seg if seg.startsWith(":") => ParamSegment(seg.substring(1))
      case "*"                        => WildcardSegment
      case seg                        => StaticSegment(seg)
    }

/** An ordered pipeline of middleware, routes and sub-routers.
  *
  * Processing walks the pipeline in registration order (Express-style): each entry
  * may pass the request along (`Continue`), finish it (`Complete`), decline
  * (`Skip`), or raise an error (`Fail`). A raised error diverts to the registered
  * error handlers, which are tried in order from the top; an error handler may in
  * turn resolve, decline, or transform the error into another. If no handler
  * resolves it, the error renders itself via `ServerError.toResponse`.
  *
  * A `Router` is itself a `Handler`, so mounting one as a sub-router is just adding
  * a handler that runs its own pipeline against the remaining path.
  */
class Router extends Handler:
  private var processes: Vector[Process] = Vector.empty

  private def add(p: Process): Router =
    processes = processes :+ p
    this

  def apply(request: Request): Future[Result] =
    walk(processes.toList, request, Router.splitPath(request.path))

  // -- Registration -----------------------------------------------------------

  def use(handler: Handler): Router                                 = add(Middleware(handler))
  def use(path: String, handler: Handler): Router                   = add(Route(Router.parsePath(path), handler))
  def use(path: String, router: Router): Router                     = add(SubRouter(Router.parsePath(path), path, router))
  def use(handler: (ServerError, Request) => Future[Result]): Router = add(ErrorHandler(handler))

  private def endpoint(method: String, path: String, handlers: Seq[Handler]): Router =
    add(Endpoint(method, Router.parsePath(path), handlers.toList))

  def get(path: String, handlers: Handler*): Router     = endpoint("GET", path, handlers)
  def post(path: String, handlers: Handler*): Router    = endpoint("POST", path, handlers)
  def put(path: String, handlers: Handler*): Router     = endpoint("PUT", path, handlers)
  def delete(path: String, handlers: Handler*): Router  = endpoint("DELETE", path, handlers)
  def patch(path: String, handlers: Handler*): Router   = endpoint("PATCH", path, handlers)
  def head(path: String, handlers: Handler*): Router    = endpoint("HEAD", path, handlers)
  def options(path: String, handlers: Handler*): Router = endpoint("OPTIONS", path, handlers)

  /** Register handlers that match the path on any HTTP method. */
  def all(path: String, handlers: Handler*): Router = endpoint("*", path, handlers)

  // -- Processing -------------------------------------------------------------

  private def walk(ps: List[Process], req: Request, path: List[String]): Future[Result] =
    ps match
      case Nil => Future.successful(Skip)
      case p :: rest =>
        p match
          case Middleware(handler) =>
            handler(req).flatMap(step(rest, req, path))

          case Route(segments, handler) =>
            // A path-mounted handler sees the request with the matched prefix stripped
            // from its path (like a sub-router), so it can resolve relative to the mount.
            matchPrefix(segments, path, Map.empty) match
              case Some((params, remaining)) =>
                handler(req.copy(
                  path = "/" + remaining.mkString("/"),
                  params = req.params ++ params,
                )).flatMap(step(rest, req, path))
              case None => walk(rest, req, path)

          case Endpoint(method, segments, handlers) =>
            if method != "*" && method != req.method then walk(rest, req, path)
            else
              matchFull(segments, path) match
                case Some(params) =>
                  runHandlers(handlers, req.copy(params = req.params ++ params)).flatMap {
                    case Skip  => walk(rest, req, path)
                    case other => Future.successful(other)
                  }
                case None => walk(rest, req, path)

          case SubRouter(segments, mount, router) =>
            matchPrefix(segments, path, Map.empty) match
              case Some((params, remaining)) =>
                router(req.copy(
                  path = "/" + remaining.mkString("/"),
                  params = req.params ++ params,
                  basePath = req.basePath + mount,
                )).flatMap {
                  case Skip  => walk(rest, req, path)
                  case other => Future.successful(other)
                }
              case None => walk(rest, req, path)

          case ErrorHandler(_) =>
            // Error handlers are inert unless an error is being processed.
            walk(rest, req, path)

  /** Continuation applied to the result of a middleware/route handler. */
  private def step(rest: List[Process], req: Request, path: List[String]): Result => Future[Result] =
    case Continue(newReq)         => walk(rest, newReq, path)
    case Skip                     => walk(rest, req, path)
    case Complete(response)       => Future.successful(InternalComplete(req, response))
    case InternalComplete(r, res) => Future.successful(InternalComplete(r, res))
    case Fail(error)              => handleError(error, req)

  private def runHandlers(hs: List[Handler], req: Request): Future[Result] =
    hs match
      case handler :: next =>
        handler(req).flatMap {
          case Continue(newReq)         => runHandlers(next, newReq)
          case Skip                     => runHandlers(next, req)
          case Complete(response)       => Future.successful(InternalComplete(req, response))
          case InternalComplete(r, res) => Future.successful(InternalComplete(r, res))
          case Fail(error)              => handleError(error, req)
        }
      case Nil => Future.successful(Skip)

  /** Divert to the registered error handlers, tried in registration order from the
    * top. A handler may resolve the error (`Complete`), decline it (`Skip`), or
    * transform it into another error (`Fail`), which restarts the search. If none
    * resolves it, the error renders itself.
    */
  private def handleError(error: ServerError, req: Request): Future[Result] =
    def tryHandlers(ps: List[Process]): Future[Result] =
      ps match
        case ErrorHandler(handler) :: rest =>
          handler(error, req).flatMap {
            case Skip                     => tryHandlers(rest)
            case Complete(response)       => Future.successful(InternalComplete(req, response))
            case InternalComplete(r, res) => Future.successful(InternalComplete(r, res))
            case Fail(next)               => handleError(next, req)
            case Continue(_)              => Future.failed(new Exception("Continue is not a valid result from an error handler"))
          }
        case _ :: rest => tryHandlers(rest)
        case Nil       => Future.successful(InternalComplete(req, error.toResponse))

    tryHandlers(processes.toList)

  // -- Segment matching -------------------------------------------------------

  /** Match a route's segments as a prefix of the path, returning the captured
    * parameters and the unmatched remainder (used for sub-routers and path-scoped
    * middleware).
    */
  @tailrec
  private def matchPrefix(
      segments: List[RouteSegment],
      path: List[String],
      params: Map[String, String],
  ): Option[(Map[String, String], List[String])] =
    (segments, path) match
      case (Nil, remaining)                            => Some((params, remaining))
      case (StaticSegment(v) :: ss, p :: ps) if v == p => matchPrefix(ss, ps, params)
      case (ParamSegment(n) :: ss, p :: ps)            => matchPrefix(ss, ps, params + (n -> p))
      case (WildcardSegment :: ss, _ :: ps)            => matchPrefix(ss, ps, params)
      case _                                           => None

  /** Match a route's segments against the entire path (used for endpoints). */
  private def matchFull(segments: List[RouteSegment], path: List[String]): Option[Map[String, String]] =
    matchPrefix(segments, path, Map.empty) match
      case Some((params, Nil)) => Some(params)
      case _                   => None
