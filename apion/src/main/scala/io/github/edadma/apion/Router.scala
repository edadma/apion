package io.github.edadma.apion

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class Router(
    private val basePath: String = "",
    private val parent: Option[Router] = None,
):
  private case class Route(
      method: String,
      pattern: String,
      endpoint: Endpoint,
  )

  private var routes: List[Route]                = Nil
  private var middleware: List[Middleware]       = Nil
  private var subrouters: List[(String, Router)] = Nil

  def route(path: String): Router =
    val subrouter = new Router(path, Some(this))
    subrouters = subrouters :+ (path, subrouter)
    logger.debug(s"Created new subrouter with path: $path", "Router")
    logger.debug(s"Updated subrouters list: ${subrouters.map(_._1).mkString(", ")}", "Router")
    subrouter

  def get(path: String, endpoint: Endpoint): Router =
    routes = routes :+ Route("GET", path, endpoint)
    logger.debug(s"Added GET route for path: $path", "Router")
    this

  def post(path: String, endpoint: Endpoint): Router =
    routes = routes :+ Route("POST", path, endpoint)
    this

  def put(path: String, endpoint: Endpoint): Router =
    routes = routes :+ Route("PUT", path, endpoint)
    this

  def delete(path: String, endpoint: Endpoint): Router =
    routes = routes :+ Route("DELETE", path, endpoint)
    this

  def patch(path: String, endpoint: Endpoint): Router =
    routes = routes :+ Route("PATCH", path, endpoint)
    this

  def head(path: String, endpoint: Endpoint): Router =
    routes = routes :+ Route("HEAD", path, endpoint)
    this

  def options(path: String, endpoint: Endpoint): Router =
    routes = routes :+ Route("OPTIONS", path, endpoint)
    this

  def use(mw: Middleware): Router =
    middleware = middleware :+ mw
    logger.debug("Added middleware", "Router")
    this

  def handle(request: Request): Future[Response] =
    logger.debug(s"Handling ${request.method} ${request.url} in router with basePath: $basePath", "Router")

    // First check if this request matches any of our subrouters
    findMatchingSubrouter(request.url) match
      case Some((prefix, subrouter)) =>
        logger.debug(s"Found matching subrouter for prefix: $prefix", "Router")
        // Strip the matched prefix from the URL when delegating
        val strippedUrl = request.url.substring(prefix.length)
        logger.debug(s"Delegating request with stripped URL: $strippedUrl", "Router")

        val delegatedRequest = request.copy(url = strippedUrl)

        // Apply our middleware before delegating
        val finalEndpoint = applyMiddleware(subrouter.handle)
        finalEndpoint(delegatedRequest)

      case None =>
        logger.debug("No matching subrouter found, handling locally", "Router")
        // No matching subrouter, try to handle locally
        handleLocal(request)

  private def handleLocal(request: Request): Future[Response] =
    val fullPattern = combinePaths(basePath, request.url)
    logger.debug(s"Handling local request. Full pattern: $fullPattern", "Router")

    // For matching, we combine the path pattern but not the request URL
    // since the URL was already stripped in the parent router
    val matchingRoutes = routes.filter(r => {
      val pattern = r.pattern
      logger.debug(s"Checking route pattern: $pattern", "Router")
      matchPath(pattern, request.url)
    })
    logger.debug(s"Found ${matchingRoutes.size} matching routes", "Router")
    matchingRoutes.foreach(r => logger.debug(s"Matching route: ${r.method} ${r.pattern}", "Router"))

    matchingRoutes.find(_.method == request.method) match
      case Some(route) =>
        logger.debug(s"Found matching route with method ${route.method}", "Router")
        // Found matching route - apply middleware and execute
        val params = extractPathParams(route.pattern, request.url)
        logger.debug(s"Extracted path params: $params", "Router")
        val reqWithParams = request.copy(
          context = request.context ++ params,
        )

        val finalEndpoint = applyMiddleware(route.endpoint)
        finalEndpoint(reqWithParams)

      case None if matchingRoutes.nonEmpty =>
        logger.debug(s"Path matches but method ${request.method} not allowed", "Router")
        // Path matches but method doesn't - 405
        Future.successful(Response(
          status = 405,
          body = "Method Not Allowed",
        ))

      case None =>
        logger.debug(s"No matching route found for ${request.url}", "Router")
        // No matching route - 404
        Future.successful(Response(
          status = 404,
          body = "Not Found",
        ))

  private def matchPrefix(prefix: String, url: String): Boolean =
    logger.debug(s"Checking if prefix '$prefix' matches URL '$url'", "Router")

    val prefixParts = prefix.split("/").filter(_.nonEmpty)
    val urlParts    = url.split("/").filter(_.nonEmpty)

    logger.debug(s"Prefix parts: ${prefixParts.mkString(", ")}", "Router")
    logger.debug(s"URL parts: ${urlParts.mkString(", ")}", "Router")

    if (urlParts.length < prefixParts.length) {
      logger.debug("URL is shorter than prefix, no match possible", "Router")
      false
    } else {
      val relevantUrlParts = urlParts.take(prefixParts.length)
      val matches = prefixParts.zip(relevantUrlParts).forall {
        case (prefixPart, urlPart) if prefixPart.startsWith(":") =>
          logger.debug(s"Parameter match in prefix: $prefixPart = $urlPart", "Router")
          true
        case (prefixPart, urlPart) =>
          logger.debug(s"Exact match attempt in prefix: $prefixPart = $urlPart", "Router")
          prefixPart == urlPart
      }
      logger.debug(s"Prefix match result: $matches", "Router")
      matches
    }

  private def findMatchingSubrouter(url: String): Option[(String, Router)] =
    logger.debug(s"Looking for subrouter matching URL: $url", "Router")
    logger.debug(s"Available subrouters: ${subrouters.map(_._1).mkString(", ")}", "Router")

    subrouters.find { case (prefix, _) =>
      val matches = matchPrefix(prefix, url)
      logger.debug(s"Testing prefix '$prefix' against '$url': $matches", "Router")
      matches
    }

  private def combinePaths(base: String, path: String): String =
    val result = if base.isEmpty then path
    else if base.endsWith("/") && path.startsWith("/") then
      base + path.substring(1)
    else if !base.endsWith("/") && !path.startsWith("/") then
      base + "/" + path
    else
      base + path
    logger.debug(s"Combined paths '$base' and '$path' to: $result", "Router")
    result

  private def matchPath(pattern: String, url: String): Boolean =
    logger.debug(s"Matching pattern '$pattern' against URL '$url'", "Router")
    val patternParts = pattern.split("/").filter(_.nonEmpty)
    val urlParts     = url.split("/").filter(_.nonEmpty)

    logger.debug(s"Pattern parts: ${patternParts.mkString(", ")}", "Router")
    logger.debug(s"URL parts: ${urlParts.mkString(", ")}", "Router")

    val matches = if patternParts.length != urlParts.length then
      logger.debug("Path segments length mismatch", "Router")
      false
    else
      val result = patternParts.zip(urlParts).forall {
        case (pattern, url) if pattern.startsWith(":") =>
          logger.debug(s"Parameter match: $pattern = $url", "Router")
          true
        case (pattern, url) =>
          logger.debug(s"Exact match attempt: $pattern = $url", "Router")
          pattern == url
      }
      logger.debug(s"Path match result: $result", "Router")
      result

    matches

  private def extractPathParams(pattern: String, url: String): Map[String, String] =
    logger.debug(s"Extracting params from URL '$url' using pattern '$pattern'", "Router")
    val patternParts = pattern.split("/").filter(_.nonEmpty)
    val urlParts     = url.split("/").filter(_.nonEmpty)

    logger.debug(s"Pattern parts for params: ${patternParts.mkString(", ")}", "Router")
    logger.debug(s"URL parts for params: ${urlParts.mkString(", ")}", "Router")

    val params = patternParts.zip(urlParts).foldLeft(Map.empty[String, String]) {
      case (params, (pattern, value)) if pattern.startsWith(":") =>
        val paramName = pattern.substring(1)
        logger.debug(s"Found param: $paramName = $value", "Router")
        params + (paramName -> value)
      case (params, (pattern, value)) =>
        logger.debug(s"Skipping non-param part: $pattern = $value", "Router")
        params
    }

    logger.debug(s"Final extracted parameters: $params", "Router")
    params

  private def getAllMiddleware: List[Middleware] =
    logger.debug(s"Getting all middleware from router with basePath: $basePath", "Router")
    val parentMiddleware = parent.map(_.getAllMiddleware).getOrElse(Nil)
    logger.debug(s"Parent provided ${parentMiddleware.length} middleware functions", "Router")
    logger.debug(s"This router has ${middleware.length} middleware functions", "Router")
    val allMiddleware = parentMiddleware ++ middleware
    logger.debug(s"Combined total of ${allMiddleware.length} middleware functions", "Router")
    allMiddleware

  private def applyMiddleware(endpoint: Endpoint): Endpoint =
    val allMiddleware = getAllMiddleware
    logger.debug(s"Applying ${allMiddleware.size} total middleware functions for basePath: $basePath", "Router")
    // Apply middleware in reverse order so they execute in the order they were added
    allMiddleware.reverse.foldLeft(endpoint) { (ep, mw) =>
      logger.debug("Applying next middleware in chain", "Router")
      mw(ep)
    }

object Router:
  def apply(): Router = new Router()
