package io.github.edadma.apion

import zio.json.*

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

object AuthMiddleware:
  /** Configuration for authentication middleware */
  case class Config(
      secretKey: String,
      requireAuth: Boolean = true,
      excludePaths: Set[String] = Set.empty,
      tokenRefreshThreshold: Long = 300, // 5 minutes before expiration
      maxTokenLifetime: Long = 86400,    // 24 hours max token lifetime
      issuer: String = "apion-auth",
      audience: Option[String] = None,
  )

  /** Enhanced token payload with additional claims */
  case class TokenPayload(
      sub: String,         // Subject (user identifier)
      roles: Set[String],  // User roles for authorization
      exp: Long,           // Expiration timestamp
      iat: Long,           // Issued at timestamp
      iss: String,         // Token issuer
      aud: Option[String], // Audience
      jti: String,         // Unique token identifier
  ) derives JsonEncoder, JsonDecoder

  /** Token storage and validation trait */
  trait TokenStore:
    /** Check if a token has been revoked */
    def isTokenRevoked(jti: String): Future[Boolean]

    /** Revoke a token */
    def revokeToken(jti: String): Future[Unit]

  /** In-memory token store for demonstration */
  class InMemoryTokenStore extends TokenStore:
    private val revokedTokens = scala.collection.mutable.Set[String]()

    override def isTokenRevoked(jti: String): Future[Boolean] =
      Future.successful(revokedTokens.contains(jti))

    override def revokeToken(jti: String): Future[Unit] =
      Future.successful(revokedTokens.add(jti))

  /** Authentication context for requests */
  case class Auth(
      user: String,      // User identifier
      roles: Set[String], // User roles
  ):
    /** Check if user has any of the required roles */
    def hasRequiredRoles(requiredRoles: Set[String]): Boolean =
      requiredRoles.isEmpty || roles.intersect(requiredRoles).nonEmpty

  /** Error responses for authentication failures */
  case class ErrorResponse(
      error: String,
      message: String,
      details: Option[String] = None,
  ) derives JsonEncoder

  /** Create a new access token */
  def createAccessToken(
      subject: String,
      roles: Set[String],
      config: Config,
  ): String =
    val now = System.currentTimeMillis() / 1000
    val payload = TokenPayload(
      sub = subject,
      roles = roles,
      exp = now + config.maxTokenLifetime,
      iat = now,
      iss = config.issuer,
      aud = config.audience,
      jti = generateUUID(),
    )
    JWT.sign(payload, config.secretKey)

  /** Create a refresh token */
  def createRefreshToken(
      subject: String,
      config: Config,
      validityPeriod: Long = 30 * 24 * 3600, // 30 days
  ): String =
    val now = System.currentTimeMillis() / 1000
    val payload = TokenPayload(
      sub = subject,
      roles = Set.empty, // Refresh tokens don't carry roles
      exp = now + validityPeriod,
      iat = now,
      iss = config.issuer,
      aud = config.audience,
      jti = generateUUID(),
    )
    JWT.sign(payload, config.secretKey)

  /** Main authentication middleware */
  def apply(config: Config, tokenStore: TokenStore = new InMemoryTokenStore()): Handler = request =>
    // Check if path should bypass auth
    val shouldExclude = config.excludePaths.exists(path => request.url.startsWith(path))

    if shouldExclude then request.skip
    else
      def handleAuthFailure(
          requireAuth: Boolean,
          message: String,
          details: Option[String] = None,
      ): Future[Result] =
        if requireAuth then
          ErrorResponse("Unauthorized", message, details)
            .asJson(401)
            .withHeader("WWW-Authenticate", """Bearer realm="api"""")
        else Future.successful(Skip)

      // Extract and validate Authorization header
      request.header("authorization") match
        case Some(header) if header.toLowerCase.startsWith("bearer ") =>
          val token = header.substring(7)

          // Verify JWT token
          Try(JWT.verify[TokenPayload](token, config.secretKey)) match
            case Success(Right(payload)) =>
              // Check token is not revoked
              tokenStore.isTokenRevoked(payload.jti).flatMap { isRevoked =>
                if isRevoked then
                  handleAuthFailure(
                    config.requireAuth,
                    "Token has been revoked",
                    Some(payload.jti),
                  )
                else
                  val now                 = System.currentTimeMillis() / 1000
                  val timeUntilExpiration = payload.exp - now

                  logger.debug(s"Current time: $now")
                  logger.debug(s"Token expiration: ${payload.exp}")
                  logger.debug(s"Time until expiration: $timeUntilExpiration")
                  logger.debug(s"Refresh threshold: ${config.tokenRefreshThreshold}")

                  // Validate additional claims
                  val claimsValid =
                    payload.iss == config.issuer &&
                      config.audience.forall(aud => payload.aud.contains(aud)) &&
                      timeUntilExpiration > 0

                  if claimsValid then
                    // Prepare authentication finalizer with token refresh hint
                    val authFinalizer: Finalizer = (req, res) =>
                      val refreshHeaderOpt =
                        if timeUntilExpiration <= config.tokenRefreshThreshold then
                          logger.debug("Adding refresh header")
                          Some(("X-Token-Refresh", "true"))
                        else {
                          logger.debug("Not adding refresh header")
                          None
                        }

                      Future.successful(
                        refreshHeaderOpt.fold(res)(header =>
                          res.copy(headers = res.headers.add(header._1, header._2)),
                        ),
                      )

                    // Continue with authenticated request
                    Future.successful(Continue(
                      request
                        .copy(context = request.context + ("auth" -> Auth(payload.sub, payload.roles)))
                        .addFinalizer(authFinalizer),
                    ))
                  else
                    handleAuthFailure(
                      config.requireAuth,
                      "Invalid token claims",
                      Some(s"Issuer: ${payload.iss}, Audience: ${payload.aud}"),
                    )
              }

            case Success(Left(error)) =>
              handleAuthFailure(
                config.requireAuth,
                s"Invalid token: ${error.getMessage}",
              )

            case Failure(error) =>
              handleAuthFailure(
                config.requireAuth,
                s"Token verification failed: ${error.getMessage}",
              )

        case Some(_) =>
          handleAuthFailure(config.requireAuth, "Invalid Authorization header format")

        case None =>
          handleAuthFailure(config.requireAuth, "Missing Authorization header")

  /** Logout mechanism to revoke tokens */
  def logout(token: String, secretKey: String, tokenStore: TokenStore): Future[Result] =
    Try(JWT.verify[TokenPayload](token, secretKey)) match
      case Success(Right(payload)) =>
        tokenStore.revokeToken(payload.jti).map { _ =>
          Complete(Response.json(
            Map("message" -> "Successfully logged out"),
            200,
          ))
        }.recover { case _ =>
          Complete(Response.json(
            Map("error" -> "Logout failed"),
            500,
          ))
        }
      case _ =>
        Future.successful(
          Complete(Response.json(
            Map("error" -> "Invalid token"),
            400,
          )),
        )

  /** Refresh access token */
  def refreshToken(refreshToken: String, config: Config, tokenStore: TokenStore): Future[Result] =
    Try(JWT.verify[TokenPayload](refreshToken, config.secretKey)) match
      case Success(Right(payload)) =>
        tokenStore.isTokenRevoked(payload.jti).flatMap { isRevoked =>
          if isRevoked then
            Future.successful(
              Complete(Response.json(
                Map("error" -> "Token has been revoked"),
                401,
              )),
            )
          else
            val now = System.currentTimeMillis() / 1000
            if payload.exp > now then
              // Generate new access token
              val newAccessToken = createAccessToken(
                payload.sub,
                Set(), // You might want to fetch current roles
                config,
              )

              Future.successful(
                Complete(Response.json(
                  Map("access_token" -> newAccessToken),
                  200,
                )),
              )
            else
              Future.successful(
                Complete(Response.json(
                  Map("error" -> "Refresh token expired"),
                  401,
                )),
              )
        }
      case _ =>
        Future.successful(
          Complete(Response.json(
            Map("error" -> "Invalid refresh token"),
            400,
          )),
        )
