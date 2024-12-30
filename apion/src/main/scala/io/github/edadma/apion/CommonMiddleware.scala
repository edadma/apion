package io.github.edadma.apion

import scala.concurrent.Future
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

object CommonMiddleware:
  /** CORS (Cross-Origin Resource Sharing) middleware
    *
    * @param allowOrigin
    *   Origins to allow (default "*")
    * @param allowMethods
    *   HTTP methods to allow
    * @param allowHeaders
    *   Headers to allow
    */
  def cors(
      allowOrigin: String = "*",
      allowMethods: Set[String] = Set("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"),
      allowHeaders: Set[String] = Set("Content-Type", "Authorization"),
  ): Handler = request =>
    // Handle preflight requests
    if request.method == "OPTIONS" then
      Future.successful(Complete(Response(
        status = 204,
        headers = Map(
          "Access-Control-Allow-Origin"  -> allowOrigin,
          "Access-Control-Allow-Methods" -> allowMethods.mkString(", "),
          "Access-Control-Allow-Headers" -> allowHeaders.mkString(", "),
          "Access-Control-Max-Age"       -> "86400",
        ),
        body = "",
      )))
    else
      // Add CORS headers to response
      Future.successful(Continue(request.copy(
        context = request.context + ("cors" -> Map(
          "Access-Control-Allow-Origin"  -> allowOrigin,
          "Access-Control-Allow-Methods" -> allowMethods.mkString(", "),
          "Access-Control-Allow-Headers" -> allowHeaders.mkString(", "),
        )),
      )))

  /** Common security headers middleware */
  def securityHeaders(): Handler = request =>
    Future.successful(Continue(request.copy(
      context = request.context + ("security-headers" -> Map(
        "X-Content-Type-Options"    -> "nosniff",
        "X-Frame-Options"           -> "DENY",
        "X-XSS-Protection"          -> "1; mode=block",
        "Referrer-Policy"           -> "no-referrer",
        "Strict-Transport-Security" -> "max-age=31536000; includeSubDomains",
      )),
    )))

  /** Request logging middleware */
  def requestLogger(): Handler = request =>
    val start = System.currentTimeMillis()
    logger.info(s"[${request.method}] ${request.url}")
    request.headers.foreach((k, v) => logger.debug(s"[$k] $v"))

    Future.successful(Continue(request.copy(
      context = request.context + ("request-start" -> start),
    )))

  /** Final response handler that adds headers from middleware context */
  def finalHandler(): Handler = request =>
    val headerBuilder = Map.newBuilder[String, String]

    // Add CORS headers if present
    request.context.get("cors").foreach {
      case corsMap: Map[_, _] =>
        val corsHeaders = corsMap.asInstanceOf[Map[String, String]]
        corsHeaders.foreach { case (k, v) => headerBuilder.addOne(k -> v) }
    }

    // Add security headers if present
    request.context.get("security-headers").foreach {
      case secMap: Map[_, _] =>
        val securityHeaders = secMap.asInstanceOf[Map[String, String]]
        securityHeaders.foreach { case (k, v) => headerBuilder.addOne(k -> v) }
    }

    // Log response time if start time was recorded
    request.context.get("request-start").foreach {
      case start: Long =>
        val duration = System.currentTimeMillis() - start
        logger.info(s"[${request.method}] ${request.url} completed in ${duration}ms")
    }

    Future.successful(Continue(request.copy(
      context = request.context + ("response-headers" -> headerBuilder.result()),
    )))
