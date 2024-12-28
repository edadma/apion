package io.github.edadma.apion

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class Router:
  case class Group(
      prefix: String,
      middleware: List[Middleware],
  )

  private case class Route(
      method: String,
      pattern: String,
      endpoint: Endpoint,
      middleware: List[Middleware] = Nil,
  )

  private var routes: List[Route]         = Nil
  private var currentGroup: Option[Group] = None

  def get(path: String, endpoint: Endpoint): Router =
    val fullPath   = currentGroup.map(g => g.prefix + path).getOrElse(path)
    val middleware = currentGroup.map(_.middleware).getOrElse(Nil)
    routes = routes :+ Route("GET", fullPath, endpoint, middleware)
    this

  def post(path: String, endpoint: Endpoint): Router =
    val fullPath   = currentGroup.map(g => g.prefix + path).getOrElse(path)
    val middleware = currentGroup.map(_.middleware).getOrElse(Nil)
    routes = routes :+ Route("POST", fullPath, endpoint, middleware)
    this

  def put(path: String, endpoint: Endpoint): Router =
    val fullPath   = currentGroup.map(g => g.prefix + path).getOrElse(path)
    val middleware = currentGroup.map(_.middleware).getOrElse(Nil)
    routes = routes :+ Route("PUT", fullPath, endpoint, middleware)
    this

  def use(mw: Middleware): Router =
    currentGroup match
      case Some(group) =>
        currentGroup = Some(group.copy(middleware = group.middleware :+ mw))
      case None =>
        currentGroup = Some(Group("", List(mw)))
    this

  def route(prefix: String)(routeSetup: Router => Unit): Router =
    val previousGroup = currentGroup
    currentGroup = Some(Group(prefix, currentGroup.map(_.middleware).getOrElse(Nil)))

    routeSetup(this)

    // Restore previous group state
    currentGroup = previousGroup
    this

  def handle(request: Request): Future[Response] =
    logger.debug(s"Handling ${request.method} ${request.url}")
    logger.debug(s"Available routes: ${routes.map(r => s"${r.method} ${r.pattern}")}")

    // First find routes that match the path
    val matchingRoutes = routes.filter(r => matchPath(r.pattern, request.url))
    logger.debug(s"Matching routes: ${matchingRoutes.map(r => s"${r.method} ${r.pattern}")}")

    matchingRoutes.find(_.method == request.method) match
      case Some(route) =>
        // Found a route matching both path and method
        logger.debug(s"Found matching route: ${route.method} ${route.pattern}")
        // Apply the route's middleware chain to its endpoint
        val finalEndpoint = route.middleware.foldLeft(route.endpoint) {
          (ep, mw) => mw(ep)
        }

        val params = extractPathParams(route.pattern, request.url)
        val reqWithParams = request.copy(
          context = request.context ++ params,
        )

        finalEndpoint(reqWithParams)

      case None if matchingRoutes.nonEmpty =>
        // Path matches but no matching method - return 405
        logger.debug("No matching method - returning 405")
        Future.successful(Response(
          status = 405,
          body = "Method Not Allowed",
        ))

      case None =>
        // No path matches - return 404
        logger.debug("No matching path - returning 404")
        Future.successful(Response(
          status = 404,
          body = "Not Found",
        ))

  private def matchPath(pattern: String, url: String): Boolean =
    // Split both pattern and URL into parts
    val patternParts = pattern.split("/")
    val urlParts     = url.split("/")

    // If they have different number of parts, no match
    if patternParts.length != urlParts.length then return false

    // Check each part
    patternParts.zip(urlParts).forall {
      case (pattern, url) if pattern.startsWith(":") => true           // Parameter always matches
      case (pattern, url)                            => pattern == url // Static part must match exactly
    }

  private def extractPathParams(pattern: String, url: String): Map[String, String] =
    val patternParts = pattern.split("/")
    val urlParts     = url.split("/")

    patternParts.zip(urlParts).foldLeft(Map.empty[String, String]) {
      case (params, (pattern, value)) if pattern.startsWith(":") =>
        params + (pattern.substring(1) -> value)
      case (params, _) =>
        params
    }

object Router:
  def apply(): Router = new Router()
