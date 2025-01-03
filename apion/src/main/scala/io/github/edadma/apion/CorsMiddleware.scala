package io.github.edadma.apion

import scala.concurrent.Future
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

object CorsMiddleware:
  /** Configuration options for CORS middleware
    * @param origin
    *   Origin configuration - can be "*", a string, or a list of allowed origins
    * @param methods
    *   Allowed HTTP methods
    * @param allowedHeaders
    *   Allowed request headers
    * @param exposedHeaders
    *   Headers exposed to the client
    * @param credentials
    *   Allow credentials (cookies, authorization headers)
    * @param maxAge
    *   How long preflight results can be cached (seconds)
    * @param preflightSuccessStatus
    *   Status code for successful preflight requests
    * @param optionsSuccessStatus
    *   Status code for successful OPTIONS requests
    */
  case class Options(
      // Origin handling
      origin: Origin = Origin.Any,

      // Methods
      methods: Set[String] = Set("GET", "HEAD", "PUT", "PATCH", "POST", "DELETE"),

      // Headers
      allowedHeaders: Set[String] = Set("Content-Type", "Authorization"),
      exposedHeaders: Set[String] = Set.empty,

      // Credentials
      credentials: Boolean = false,

      // Preflight
      maxAge: Option[Int] = Some(86400), // 24 hours
      preflightSuccessStatus: Int = 204,
      optionsSuccessStatus: Int = 204,
  )

  sealed trait Origin
  object Origin:
    case object Any                           extends Origin // "*"
    case class Single(origin: String)         extends Origin // Single origin
    case class Multiple(origins: Set[String]) extends Origin // Multiple origins
    case class Pattern(regex: String)         extends Origin // Regex pattern
    case class Function(f: String => Boolean) extends Origin // Custom validation

    def apply(origin: String): Origin          = Single(origin)
    def apply(origins: Set[String]): Origin    = Multiple(origins)
    def pattern(regex: String): Origin         = Pattern(regex)
    def validate(f: String => Boolean): Origin = Function(f)

  /** Create CORS middleware with default options */
  def apply(options: Options = Options()): Handler = request => {
    // Origin validation
    def isOriginAllowed(requestOrigin: String): Boolean = options.origin match
      case Origin.Any               => true
      case Origin.Single(allowed)   => requestOrigin == allowed
      case Origin.Multiple(allowed) => allowed.contains(requestOrigin)
      case Origin.Pattern(pattern)  => requestOrigin.matches(pattern)
      case Origin.Function(f)       => f(requestOrigin)

    // Get origin from request
    val requestOrigin = request.header("origin")

    // Determine response origin
    val responseOrigin = (options.origin, requestOrigin) match
      case (Origin.Any, _)                              => "*"
      case (_, Some(origin)) if isOriginAllowed(origin) => origin
      case _                                            => ""

    // Don't set CORS headers if origin is not allowed
    if responseOrigin.isEmpty then
      Future.successful(Skip)
    else
      // Build CORS headers
      var corsHeaders = Seq(
        "Access-Control-Allow-Origin" -> responseOrigin,
      )

      // Add Vary header if not using "*"
      if responseOrigin != "*" then
        corsHeaders +:= "Vary" -> "Origin"

      // Add credentials if enabled and not using "*"
      if options.credentials && responseOrigin != "*" then
        corsHeaders +:= "Access-Control-Allow-Credentials" -> "true"

      // Add exposed headers if any
      if options.exposedHeaders.nonEmpty then
        corsHeaders +:= "Access-Control-Expose-Headers" -> options.exposedHeaders.mkString(", ")

      // Handle preflight requests
      if request.method == "OPTIONS" then
        // Add preflight-specific headers
        corsHeaders ++= Seq(
          "Access-Control-Allow-Methods" -> options.methods.mkString(", "),
          "Access-Control-Allow-Headers" -> options.allowedHeaders.mkString(", "),
        )

        // Add max-age if specified
        options.maxAge.foreach { age =>
          corsHeaders +:= ("Access-Control-Max-Age" -> age.toString)
        }

        // Return preflight response
        Future.successful(Complete(Response(
          status = options.preflightSuccessStatus,
          headers = ResponseHeaders(corsHeaders),
        )))
      else
        // Add CORS headers to actual response via finalizer
        val corsFinalizer: Finalizer = (req, res) =>
          Future.successful(res.copy(headers = res.headers.addAll(corsHeaders)))

        Future.successful(Continue(request.addFinalizer(corsFinalizer)))
  }

  /** Preset for development with permissive settings */
  def development(): Handler =
    apply(Options(
      origin = Origin.Any,
      credentials = true,
      exposedHeaders = Set("*"),
      maxAge = Some(86400),
    ))

  /** Preset for production with strict settings */
  def production(allowedOrigins: Set[String]): Handler =
    apply(Options(
      origin = Origin.Multiple(allowedOrigins),
      methods = Set("GET", "POST", "PUT", "DELETE"),
      allowedHeaders = Set("Content-Type", "Authorization"),
      credentials = true,
      maxAge = Some(7200), // 2 hours
    ))
