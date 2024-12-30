package io.github.edadma.apion

import zio.json.*
import scala.concurrent.Future
import scala.util.{Try, Success, Failure}
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global
import ResponseDSL._
import ResultOps._

object AuthMiddleware:
  // Token payload structure
  case class TokenPayload(
      sub: String,        // Subject (user identifier)
      roles: Set[String], // User roles for authorization
      exp: Long,          // Expiration timestamp
  ) derives JsonEncoder, JsonDecoder

  case class ErrorResponse(error: String, message: String) derives JsonEncoder

  /** Creates authentication middleware
    *
    * @param requireAuth
    *   Whether authentication is required (true) or optional (false)
    * @param excludePaths
    *   Paths that bypass authentication completely
    * @param secretKey
    *   Secret key for JWT verification
    * @return
    *   Handler that processes authentication
    */
  def apply(
      requireAuth: Boolean = true,
      excludePaths: Set[String] = Set(),
      secretKey: String,
  ): Handler =
    request => {
      // Check if path should bypass auth
      val shouldExclude = excludePaths.exists(path => request.url.startsWith(path))

      if shouldExclude then
        // Skip auth for excluded paths
        request.skip
      else
        def handleAuthFailure(requireAuth: Boolean, message: String): Future[Result] =
          if requireAuth then
            ErrorResponse("Unauthorized", message)
              .asJson(401)
              .withHeader("WWW-Authenticate", """Bearer realm="api"""")
          else
            Future.successful(Skip)

        // Extract and validate Authorization header
        request.header("authorization") match
          case Some(header) if header.toLowerCase.startsWith("bearer ") =>
            val token = header.substring(7)

            // Verify JWT token
            Try(JWT.verify[TokenPayload](token, secretKey)) match
              case Success(Right(payload)) =>
                // Check token expiration
                if payload.exp > System.currentTimeMillis() / 1000 then
                  // Token valid - add auth info to request and continue
                  Future.successful(Continue(
                    request.copy(auth = Some(Auth(payload.sub, payload.roles))),
                  ))
                else
                  // Token expired
                  handleAuthFailure(requireAuth, "Token expired")

              case Success(Left(error)) =>
                // Invalid token
                handleAuthFailure(requireAuth, s"Invalid token: ${error.getMessage}")

              case Failure(error) =>
                // Token verification failed
                handleAuthFailure(requireAuth, s"Token verification failed: ${error.getMessage}")

          case Some(_) =>
            // Malformed Authorization header
            handleAuthFailure(requireAuth, "Invalid Authorization header format")

          case None =>
            // No Authorization header
            handleAuthFailure(requireAuth, "Missing Authorization header")
    }
