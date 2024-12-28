package io.github.edadma.apion

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object AuthMiddleware:
  /** Creates authentication middleware.
    *
    * Type breakdown:
    *   - Middleware = Endpoint => Endpoint
    *   - Endpoint = Request => Future[Response]
    *
    * So expanded: Middleware = (Request => Future[Response]) => (Request => Future[Response])
    *
    * @param requireAuth
    *   If true, requests without valid auth will be rejected
    * @return
    *   A function that transforms endpoints by adding auth checking
    */
  def apply(requireAuth: Boolean = true): Middleware =
    // This outer function takes an endpoint and returns a new endpoint
    (endpoint: Endpoint) =>
      // This is our new endpoint function that will replace the original
      // It needs to have type Request => Future[Response] to be a valid Endpoint
      (request: Request) => {
        // Check for Authorization header and handle authentication
        request.headers.get("Authorization") match
          case Some(authHeader) if authHeader.startsWith("Bearer ") =>
            val token = authHeader.substring(7)

            // verifyToken returns Future[Option[Auth]]
            // We use flatMap because we need to chain this with the endpoint call
            verifyToken(token).flatMap {
              case Some(auth) =>
                // Auth successful - add auth info to request and continue to endpoint
                // endpoint(request) returns Future[Response]
                endpoint(request.copy(auth = Some(auth)))

              case None if requireAuth =>
                // Invalid token and auth required - return 401 without calling endpoint
                Future.successful(Response(
                  status = 401,
                  body = "Unauthorized",
                ))

              case None =>
                // Invalid token but auth optional - continue to endpoint
                endpoint(request)
            }

          case None if requireAuth =>
            // No auth header and auth required - return 401 without calling endpoint
            Future.successful(Response(
              status = 401,
              body = "Authorization header required",
            ))

          case None =>
            // No auth header but auth optional - continue to endpoint
            endpoint(request)

          case Some(authHeader) =>
            // Authorization header exists but isn't a Bearer token
            // Fail immediately without calling the endpoint since we only support Bearer auth
            Future.successful(Response(
              status = 401,
              body = "Authorization header must be Bearer token",
            ))
      }

  /** Simulated token verification. In a real app, this would verify JWT tokens, check a database, etc.
    */
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
