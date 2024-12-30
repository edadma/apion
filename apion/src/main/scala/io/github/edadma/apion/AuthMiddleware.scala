//package io.github.edadma.apion
//
//import zio.json.*
//import scala.util.{Try, Success, Failure}
//import scala.concurrent.Future
//import scala.concurrent.ExecutionContext.Implicits.global
//
//object AuthMiddleware:
//  // Expose the token payload for testing and external use
//  case class TokenPayload(
//      sub: String,
//      roles: Set[String],
//      exp: Long,
//  )
//
//  object TokenPayload:
//    given JsonEncoder[TokenPayload] = DeriveJsonEncoder.gen[TokenPayload]
//    given JsonDecoder[TokenPayload] = DeriveJsonDecoder.gen[TokenPayload]
//
//  /** Creates an authentication middleware
//    *
//    * @param requireAuth
//    *   Whether authentication is mandatory
//    * @param excludePaths
//    *   Paths that bypass authentication
//    * @param secretKey
//    *   Secret key for JWT verification
//    */
//  def apply(
//      requireAuth: Boolean = true,
//      excludePaths: Set[String] = Set(),
//      secretKey: String = "default-secret-key",
//  ): Middleware =
//    endpoint =>
//      request => {
//        logger.debug(s"[Auth] Starting auth check for ${request.method} ${request.url}")
//        logger.debug(s"[Auth] Excluded paths: $excludePaths")
//        logger.debug(s"[Auth] Require auth: $requireAuth")
//
//        // Log all headers for debugging
//        request.headers.foreach { case (k, v) =>
//          logger.debug(s"[Auth] Header: $k = $v")
//        }
//
//        // Check if path should bypass auth
//        val shouldExclude = excludePaths.exists(path => request.url.startsWith(path))
//
//        // Early return if path is excluded
//        if shouldExclude then
//          logger.debug(s"[Auth] Bypassing auth for excluded path: ${request.url}")
//          endpoint(request)
//        else
//          // Check for Authorization header (case-insensitive)
//          val authHeader = request.headers.find {
//            case (k, _) => k.toLowerCase == "authorization"
//          }
//
//          authHeader match
//            case Some((_, header)) if header.toLowerCase.startsWith("bearer ") =>
//              val token = header.substring(7)
//              logger.debug(s"[Auth] Received token: $token")
//              logger.debug(s"[Auth] Secret key: $secretKey")
//
//              // Attempt to verify the token
//              Try(JWT.verify[TokenPayload](token, secretKey)) match
//                case Success(Right(tokenPayload)) =>
//                  logger.debug(s"[Auth] Token payload verified: $tokenPayload")
//                  logger.debug(s"[Auth] Current time: ${System.currentTimeMillis() / 1000}")
//                  logger.debug(s"[Auth] Token expiration: ${tokenPayload.exp}")
//
//                  // Check token expiration
//                  if tokenPayload.exp > System.currentTimeMillis() / 1000 then
//                    // Token is valid and not expired
//                    val auth = Auth(tokenPayload.sub, tokenPayload.roles)
//                    logger.debug(s"[Auth] Token verified for user: ${auth.user}")
//                    endpoint(request.copy(auth = Some(auth)))
//                  else
//                    // Token expired
//                    logger.debug("[Auth] Token has expired")
//                    if requireAuth then
//                      Future.successful(Response(
//                        status = 401,
//                        body = "Unauthorized",
//                      ))
//                    else
//                      endpoint(request)
//
//                case Success(Left(jwtError)) =>
//                  // Token verification failed
//                  logger.debug(s"[Auth] Token verification failed (Success(Left)): ${jwtError.getMessage}")
//                  if requireAuth then
//                    Future.successful(Response(
//                      status = 401,
//                      body = "Unauthorized",
//                    ))
//                  else
//                    endpoint(request)
//
//                case Failure(exception) =>
//                  // Unexpected error during token verification
//                  logger.debug(s"[Auth] Unexpected error during token verification: ${exception.getMessage}")
//                  if requireAuth then
//                    Future.successful(Response(
//                      status = 401,
//                      body = "Unauthorized",
//                    ))
//                  else
//                    endpoint(request)
//
//            case None if requireAuth =>
//              // No token provided and auth is required
//              logger.debug("[Auth] No auth header when auth is required")
//              Future.successful(Response(
//                status = 401,
//                body = "Authorization header required",
//              ))
//
//            case None =>
//              // No token provided, but auth is optional
//              logger.debug("[Auth] No auth header, but auth is optional")
//              endpoint(request)
//
//            case Some(_) =>
//              // Malformed Authorization header
//              logger.debug("[Auth] Malformed Authorization header")
//              Future.successful(Response(
//                status = 401,
//                body = "Unauthorized",
//              ))
//      }
