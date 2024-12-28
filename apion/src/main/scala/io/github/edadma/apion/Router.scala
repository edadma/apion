package io.github.edadma.apion

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class Router:
  private case class Route(
                            method: String,
                            pattern: String,
                            endpoint: Endpoint
                          )

  private var routes: List[Route] = Nil
  private var middleware: List[Middleware] = Nil

  def get(path: String, endpoint: Endpoint): Router =
    routes = routes :+ Route("GET", path, endpoint)  // Append instead of prepend
    this

  def post(path: String, endpoint: Endpoint): Router =
    routes = routes :+ Route("POST", path, endpoint)  // Append instead of prepend
    this

  def use(mw: Middleware): Router =
    middleware = middleware :+ mw  // Append instead of prepend
    this

  def handle(request: Request): Future[Response] =
    println(s"Handling ${request.method} ${request.url}")
    println(s"Available routes: ${routes.map(r => s"${r.method} ${r.pattern}")}")

    // First find routes that match the path
    val matchingRoutes = routes.filter(r => matchPath(r.pattern, request.url))
    println(s"Matching routes: ${matchingRoutes.map(r => s"${r.method} ${r.pattern}")}")

    matchingRoutes.find(_.method == request.method) match
      case Some(route) =>
        // Found a route matching both path and method
        println(s"Found matching route: ${route.method} ${route.pattern}")
        val finalEndpoint = middleware.foldLeft(route.endpoint) {
          (ep, mw) => mw(ep)
        }

        val params = extractPathParams(route.pattern, request.url)
        val reqWithParams = request.copy(
          context = request.context ++ params
        )

        finalEndpoint(reqWithParams)

      case None if matchingRoutes.nonEmpty =>
        // Path matches but no matching method - return 405
        println("No matching method - returning 405")
        Future.successful(Response(
          status = 405,
          body = "Method Not Allowed"
        ))

      case None =>
        // No path matches - return 404
        println("No matching path - returning 404")
        Future.successful(Response(
          status = 404,
          body = "Not Found"
        ))

  private def matchPath(pattern: String, url: String): Boolean =
    // Split both pattern and URL into parts
    val patternParts = pattern.split("/")
    val urlParts = url.split("/")

    // If they have different number of parts, no match
    if patternParts.length != urlParts.length then return false

    // Check each part
    patternParts.zip(urlParts).forall {
      case (pattern, url) if pattern.startsWith(":") => true  // Parameter always matches
      case (pattern, url) => pattern == url  // Static part must match exactly
    }

  private def extractPathParams(pattern: String, url: String): Map[String, String] =
    val patternParts = pattern.split("/")
    val urlParts = url.split("/")

    patternParts.zip(urlParts).foldLeft(Map.empty[String, String]) {
      case (params, (pattern, value)) if pattern.startsWith(":") =>
        params + (pattern.substring(1) -> value)
      case (params, _) =>
        params
    }

object Router:
  def apply(): Router = new Router()