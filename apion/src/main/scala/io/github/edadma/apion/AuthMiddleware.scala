package io.github.edadma.apion

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object AuthMiddleware:
  def apply(requireAuth: Boolean = true, excludePaths: Set[String] = Set()): Middleware =
    endpoint =>
      request => {
        logger.debug(s"[Auth] ======= Starting Auth Check =======")
        logger.debug(s"[Auth] Request method: ${request.method}")
        logger.debug(s"[Auth] Request URL: ${request.url}")
        logger.debug(s"[Auth] Request headers: ${request.headers}")
        logger.debug(s"[Auth] Excluded paths: $excludePaths")

        // Check if path should bypass auth
        val shouldExclude = excludePaths.exists { path =>
          val matches = request.url == path || request.url.startsWith(path + "/")
          logger.debug(s"[Auth] Checking path '$path' against '${request.url}': $matches")
          matches
        }
        logger.debug(s"[Auth] Final exclusion decision: $shouldExclude")

        if shouldExclude then
          logger.debug(s"[Auth] Bypassing auth check for excluded path: ${request.url}")
          endpoint(request)
        else
          logger.debug(s"[Auth] Requiring auth for path: ${request.url}")
          // Check for Authorization header and handle authentication
          request.headers.get("Authorization") match
            case Some(authHeader) if authHeader.startsWith("Bearer ") =>
              val token = authHeader.substring(7)
              logger.debug(s"[Auth] Got bearer token: $token")

              verifyToken(token).flatMap {
                case Some(auth) =>
                  logger.debug(s"[Auth] Token verified for user: ${auth.user}")
                  endpoint(request.copy(auth = Some(auth)))

                case None if requireAuth =>
                  logger.debug("[Auth] Invalid token and auth required")
                  Future.successful(Response(
                    status = 401,
                    body = "Unauthorized",
                  ))

                case None =>
                  logger.debug("[Auth] Invalid token but auth optional")
                  endpoint(request)
              }

            case None if requireAuth =>
              logger.debug("[Auth] No auth header and auth required")
              Future.successful(Response(
                status = 401,
                body = "Authorization header required",
              ))

            case None =>
              logger.debug("[Auth] No auth header but auth optional")
              endpoint(request)

            case Some(authHeader) =>
              logger.debug(s"[Auth] Invalid auth header format: $authHeader")
              Future.successful(Response(
                status = 401,
                body = "Authorization header must be Bearer token",
              ))
      }

  /** Simulated token verification. In a real app, this would verify JWT tokens, check a database, etc. */
  private def verifyToken(token: String): Future[Option[Auth]] =
    Future.successful(
      if token == "valid-token" then
        Some(Auth("example-user", Set("user")))
      else None,
    )

/** Example usage:
  *
  * val securedEndpoint: Endpoint = request => Future.successful(Response.text("Secret data"))
  *
  * // Add auth checking to the endpoint val withAuth: Endpoint = AuthMiddleware(requireAuth = true)(securedEndpoint)
  *
  * // Now withAuth is a new endpoint that checks auth before running securedEndpoint
  */
