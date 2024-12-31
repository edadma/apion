package io.github.edadma.apion

import scala.concurrent.Future
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

object SecurityMiddleware:
  case class Options(
      // Content Security Policy options
      contentSecurityPolicy: Boolean = true,
      cspDirectives: Map[String, String] = Map(
        "default-src"               -> "'self'",
        "base-uri"                  -> "'self'",
        "font-src"                  -> "'self' https: data:",
        "form-action"               -> "'self'",
        "frame-ancestors"           -> "'self'",
        "img-src"                   -> "'self' data: https:",
        "object-src"                -> "'none'",
        "script-src"                -> "'self'",
        "script-src-attr"           -> "'none'",
        "style-src"                 -> "'self' https: 'unsafe-inline'",
        "upgrade-insecure-requests" -> "",
      ),

      // Cross-Origin options
      crossOriginEmbedderPolicy: Boolean = true,
      crossOriginOpenerPolicy: Boolean = true,
      crossOriginResourcePolicy: Boolean = true,

      // DNS Prefetch Control
      dnsPrefetchControl: Boolean = true,

      // Expect-CT header
      expectCt: Boolean = true,
      expectCtMaxAge: Int = 86400,
      expectCtEnforce: Boolean = true,

      // Frameguard options
      frameguard: Boolean = true,
      frameguardAction: String = "DENY", // DENY, SAMEORIGIN, or ALLOW-FROM

      // HSTS options
      hsts: Boolean = true,
      hstsMaxAge: Int = 15552000, // 180 days
      hstsIncludeSubDomains: Boolean = true,
      hstsPreload: Boolean = false,

      // IE No Open
      ieNoOpen: Boolean = true,

      // No Sniff
      noSniff: Boolean = true,

      // Origin-Agent-Cluster
      originAgentCluster: Boolean = true,

      // Permitted Cross-Domain Policies
      permittedCrossDomainPolicies: String = "none",

      // Referrer Policy
      referrerPolicy: Boolean = true,
      referrerPolicyDirective: String = "no-referrer",

      // XSS Filter
      xssFilter: Boolean = true,
      xssFilterMode: String = "1; mode=block",
  )

  def apply(options: Options = Options()): Handler = request =>
    val securityHeadersFinalizer: Finalizer = (req, res) => {
      var headers = res.headers

      // Content-Security-Policy
      if options.contentSecurityPolicy then
        val csp = options.cspDirectives
          .map { case (key, value) =>
            if value.isEmpty then key else s"$key $value"
          }
          .mkString("; ")
        headers += "Content-Security-Policy" -> csp

      // Cross-Origin-Embedder-Policy
      if options.crossOriginEmbedderPolicy then
        headers += "Cross-Origin-Embedder-Policy" -> "require-corp"

      // Cross-Origin-Opener-Policy
      if options.crossOriginOpenerPolicy then
        headers += "Cross-Origin-Opener-Policy" -> "same-origin"

      // Cross-Origin-Resource-Policy
      if options.crossOriginResourcePolicy then
        headers += "Cross-Origin-Resource-Policy" -> "same-origin"

      // X-DNS-Prefetch-Control
      if options.dnsPrefetchControl then
        headers += "X-DNS-Prefetch-Control" -> "off"

      // Expect-CT
      if options.expectCt then
        val expectCt = s"max-age=${options.expectCtMaxAge}" +
          (if options.expectCtEnforce then ", enforce" else "")
        headers += "Expect-CT" -> expectCt

      // X-Frame-Options
      if options.frameguard then
        headers += "X-Frame-Options" -> options.frameguardAction

      // Strict-Transport-Security
      if options.hsts then
        val hsts = s"max-age=${options.hstsMaxAge}" +
          (if options.hstsIncludeSubDomains then "; includeSubDomains" else "") +
          (if options.hstsPreload then "; preload" else "")
        headers += "Strict-Transport-Security" -> hsts

      // X-Download-Options
      if options.ieNoOpen then
        headers += "X-Download-Options" -> "noopen"

      // X-Content-Type-Options
      if options.noSniff then
        headers += "X-Content-Type-Options" -> "nosniff"

      // Origin-Agent-Cluster
      if options.originAgentCluster then
        headers += "Origin-Agent-Cluster" -> "?1"

      // X-Permitted-Cross-Domain-Policies
      headers += "X-Permitted-Cross-Domain-Policies" -> options.permittedCrossDomainPolicies

      // Referrer-Policy
      if options.referrerPolicy then
        headers += "Referrer-Policy" -> options.referrerPolicyDirective

      // X-XSS-Protection
      if options.xssFilter then
        headers += "X-XSS-Protection" -> options.xssFilterMode

      Future.successful(res.copy(headers = headers))
    }

    Future.successful(Continue(request.addFinalizer(securityHeadersFinalizer)))

  /** Creates middleware with only essential security headers enabled
    */
  def essential(): Handler =
    apply(Options(
      contentSecurityPolicy = true,
      frameguard = true,
      hsts = true,
      noSniff = true,
      xssFilter = true,
      referrerPolicy = true,
      // Disable other features
      crossOriginEmbedderPolicy = false,
      crossOriginOpenerPolicy = false,
      crossOriginResourcePolicy = false,
      dnsPrefetchControl = false,
      expectCt = false,
      ieNoOpen = false,
      originAgentCluster = false,
    ))

  /** Creates middleware configured for API servers
    */
  def api(): Handler =
    apply(Options(
      // Disable browser-specific protections
      frameguard = false,
      ieNoOpen = false,
      xssFilter = false,
      // Enable strict CORS policies
      crossOriginEmbedderPolicy = true,
      crossOriginOpenerPolicy = true,
      crossOriginResourcePolicy = true,
      // Strict CSP for APIs
      cspDirectives = Map(
        "default-src"     -> "'none'",
        "frame-ancestors" -> "'none'",
      ),
    ))
