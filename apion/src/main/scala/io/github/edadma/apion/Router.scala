package io.github.edadma.apion

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class Router:
  private case class Route(
      method: String,
      pattern: String,
      endpoint: Endpoint,
  )

  private var routes: List[Route]          = Nil
  private var middleware: List[Middleware] = Nil

  def get(path: String, endpoint: Endpoint): Router =
    routes = Route("GET", path, endpoint) :: routes
    this

  def post(path: String, endpoint: Endpoint): Router =
    routes = Route("POST", path, endpoint) :: routes
    this

  def use(mw: Middleware): Router =
    middleware = mw :: middleware
    this

  def handle(request: Request): Future[Response] =
    // Find matching route
    routes.find(matchRoute(request, _)) match
      case Some(route) =>
        // Apply middleware chain
        val finalEndpoint = middleware.foldLeft(route.endpoint) {
          (ep, mw) => mw(ep)
        }

        // Extract path parameters if any
        val params = extractPathParams(route.pattern, request.url)
        val reqWithParams = request.copy(
          context = request.context ++ params,
        )

        finalEndpoint(reqWithParams)

      case None =>
        // Handle 404 Not Found
        Future.successful(Response(
          status = 404,
          body = "Not Found",
        ))

  private def matchRoute(request: Request, route: Route): Boolean =
    if request.method != route.method then
      false
    else
      // Convert route pattern to regex
      val pattern = route.pattern
        .split("/")
        .map {
          case s if s.startsWith(":") => """[^/]+"""                      // Match any chars except /
          case s                      => java.util.regex.Pattern.quote(s) // Escape special chars
        }
        .mkString("/")

      request.url.matches(pattern)

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
