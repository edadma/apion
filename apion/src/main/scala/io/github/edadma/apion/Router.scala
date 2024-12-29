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

  def get(path: String, endpoint: Endpoint, middlewares: Middleware*): Router =
    val finalEndpoint = middlewares.foldRight(endpoint)((mw, ep) => mw(ep))
    routes = routes :+ Route("GET", path, finalEndpoint)
    this

  def post(path: String, endpoint: Endpoint, middlewares: Middleware*): Router =
    val finalEndpoint = middlewares.foldRight(endpoint)((mw, ep) => mw(ep))
    routes = routes :+ Route("POST", path, finalEndpoint)
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

    findMatchingSubrouter(request.url) match
      case Some((prefix, subrouter)) =>
        logger.debug(s"Found matching subrouter for prefix: $prefix", "Router")

        // Calculate the length of matched prefix parts
        val prefixParts     = prefix.split("/").filter(_.nonEmpty)
        val urlParts        = request.url.split("/").filter(_.nonEmpty)
        val matchedSegments = prefixParts.length

        // Join the remaining URL parts back together
        val remainingUrl = "/" + urlParts.drop(matchedSegments).mkString("/")
        logger.debug(s"Delegating request with remaining URL: $remainingUrl", "Router")

        val delegatedRequest = request.copy(url = remainingUrl)

        // Extract and pass along path parameters from the matched prefix
        val prefixParams = extractPathParams(prefix, request.url.split("/").take(matchedSegments + 1).mkString("/"))
        val requestWithParams = delegatedRequest.copy(
          context = delegatedRequest.context ++ prefixParams,
        )

        val finalEndpoint = applyMiddleware(subrouter.handle)
        finalEndpoint(requestWithParams)

      case None =>
        logger.debug("No matching subrouter found, handling locally", "Router")
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

    // Split both paths into segments and filter out empty segments
    val prefixParts = prefix.split("/").filter(_.nonEmpty)
    val urlParts    = url.split("/").filter(_.nonEmpty)

    logger.debug(s"Prefix parts: ${prefixParts.mkString(", ")}", "Router")
    logger.debug(s"URL parts: ${urlParts.mkString(", ")}", "Router")

    if (urlParts.length < prefixParts.length) {
      logger.debug("URL is shorter than prefix, no match possible", "Router")
      false
    } else {
      // Take only the relevant parts of the URL for comparison
      val relevantUrlParts = urlParts.take(prefixParts.length)

      // Compare each segment, treating parameters (starting with :) as wildcards
      val matches = prefixParts.zip(relevantUrlParts).forall {
        case (prefixPart, urlPart) if prefixPart.startsWith(":") =>
          // Parameter segment - matches any value
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
    logger.debug(s"[Router] Matching pattern '$pattern' against URL '$url'")

    // Split into segments and filter empty ones
    val patternParts = pattern.split("/").filter(_.nonEmpty)
    val urlParts     = url.split("/").filter(_.nonEmpty)

    logger.debug(s"[Router] Pattern parts: ${patternParts.mkString(", ")}")
    logger.debug(s"[Router] URL parts: ${urlParts.mkString(", ")}")

    if patternParts.lastOption.contains("*") then
      // For wildcard patterns, ensure the non-wildcard parts match
      val nonWildcardPattern = patternParts.init
      val urlPrefix          = urlParts.take(nonWildcardPattern.length)

      if urlParts.length >= nonWildcardPattern.length then
        val matches = nonWildcardPattern.zip(urlPrefix).forall {
          case (pattern, url) if pattern.startsWith(":") =>
            logger.debug(s"[Router] Parameter match: $pattern = $url")
            true
          case (pattern, url) =>
            logger.debug(s"[Router] Exact match attempt: $pattern = $url")
            pattern == url
        }
        logger.debug(s"[Router] Wildcard path match result: $matches")
        matches
      else
        logger.debug("[Router] URL too short for wildcard pattern")
        false
    else
      // For non-wildcard patterns, length must match exactly
      val matches = if patternParts.length != urlParts.length then
        logger.debug("[Router] Path segments length mismatch")
        false
      else
        val result = patternParts.zip(urlParts).forall {
          case (pattern, url) if pattern.startsWith(":") =>
            logger.debug(s"[Router] Parameter match: $pattern = $url")
            true
          case (pattern, url) =>
            logger.debug(s"[Router] Exact match attempt: $pattern = $url")
            pattern == url
        }
        logger.debug(s"[Router] Path match result: $result")
        result
      matches

  private def extractPathParams(pattern: String, url: String): Map[String, String] =
    logger.debug(s"[Router] Extracting params from URL '$url' using pattern '$pattern'")
    val patternParts = pattern.split("/").filter(_.nonEmpty)
    val urlParts     = url.split("/").filter(_.nonEmpty)

    // Handle wildcard pattern
    val (partsToMatch, remainingUrl) = if patternParts.lastOption.contains("*") then
      val nonWildcardPattern = patternParts.init
      val matchedUrlParts    = urlParts.take(nonWildcardPattern.length)
      val remainingUrlParts  = urlParts.drop(nonWildcardPattern.length)
      (nonWildcardPattern.zip(matchedUrlParts), Some(remainingUrlParts.mkString("/")))
    else
      (patternParts.zip(urlParts), None)

    val params = partsToMatch.foldLeft(Map.empty[String, String]) {
      case (params, (pattern, value)) if pattern.startsWith(":") =>
        val paramName = pattern.substring(1)
        logger.debug(s"[Router] Found param: $paramName = $value")
        params + (paramName -> value)
      case (params, (pattern, value)) =>
        logger.debug(s"[Router] Skipping non-param part: $pattern = $value")
        params
    }

    // Add wildcard part if present
    val finalParams = remainingUrl match
      case Some(rest) => params + ("*" -> rest)
      case None       => params

    logger.debug(s"[Router] Final extracted parameters: $finalParams")
    finalParams

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
