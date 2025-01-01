package io.github.edadma.apion

import io.github.edadma.nodejs.*
import zio.json.*

import scala.concurrent.Future
import scala.scalajs.js
import scala.util.Try

/** JSON Web Token (JWT) implementation supporting HS256 (HMAC-SHA256) algorithm.
  *
  * JWT structure: header.payload.signature
  *   - header: {"alg": "HS256", "typ": "JWT"}
  *   - payload: your custom claims as JSON
  *   - signature: HMAC-SHA256(base64Url(header) + "." + base64Url(payload), secret)
  *
  * Each section is base64url encoded and joined with dots.
  */
object JWT:
  /** JWT Header containing algorithm and token type. Currently only supports HS256 algorithm.
    *
    * @param alg
    *   Algorithm used for signing (HS256 = HMAC-SHA256)
    * @param typ
    *   Token type, always "JWT"
    */
  case class Header(
      alg: String = "HS256",
      typ: String = "JWT",
  )

  object Header:
    // Derive JSON codec for automatic serialization/deserialization
    given JsonEncoder[Header] = DeriveJsonEncoder.gen[Header]
    given JsonDecoder[Header] = DeriveJsonDecoder.gen[Header]

  /** Custom error type for JWT-related failures
    */
  case class JWTError(message: String) extends Exception(message)

  /** Verifies a JWT token and extracts its payload.
    *
    * @param token
    *   The JWT string to verify (header.payload.signature)
    * @param secret
    *   The secret key used to verify the signature
    * @tparam A
    *   The expected type of the payload (must have a JsonDecoder)
    * @return
    *   Either an error or the decoded payload
    */
  def verify[A: JsonDecoder](token: String, secret: String): Either[JWTError, A] =
    try
      token.split('.') match
        case Array(headerB64, payloadB64, signatureB64) =>
          // Decode and verify header
          val header = base64UrlDecode(headerB64).fromJson[Header] match
            case Right(h) => h
            case Left(e)  => throw JWTError(s"Invalid header: $e")

          // Check algorithm is supported
          if header.alg != "HS256" then
            throw JWTError(s"Unsupported algorithm: ${header.alg}")

          // Decode and verify payload before checking signature
          val payload = base64UrlDecode(payloadB64).fromJson[A] match
            case Right(p) => p
            case Left(e)  => throw JWTError(s"Invalid payload: $e")

          // Finally verify signature
          val data = s"$headerB64.$payloadB64"
          val expectedSignature = crypto
            .createHmac("sha256", secret)
            .update(data)
            .digest("base64")
            .replace('+', '-')
            .replace('/', '_')
            .replace("=", "")

          // Verify signature
          // Compare signatures directly in base64url format
          if signatureB64 != expectedSignature then
            throw JWTError("Invalid signature")

          // Decode payload if signature is valid
          base64UrlDecode(payloadB64).fromJson[A] match
            case Right(payload) => Right(payload)
            case Left(e)        => throw JWTError(s"Invalid payload: $e")

        case _ => throw JWTError("Invalid token format")
    catch
      case e: JWTError  => Left(e)
      case e: Exception => Left(JWTError(e.getMessage))

  /** Creates and signs a new JWT token.
    *
    * @param payload
    *   The data to encode in the token
    * @param secret
    *   The secret key used to sign the token
    * @tparam A
    *   The type of the payload (must have a JsonEncoder)
    * @return
    *   The complete JWT string (header.payload.signature)
    */
  def sign[A: JsonEncoder](payload: A, secret: String): String =
    // Create and encode header
    val header    = Header()
    val headerB64 = base64UrlEncode(header.toJson)

    // Encode payload
    val payloadB64 = base64UrlEncode(payload.toJson)

    // Create signature
    val data = s"$headerB64.$payloadB64"
    val signature = crypto
      .createHmac("sha256", secret)
      .update(data)
      .digest("base64")
      .replace('+', '-')
      .replace('/', '_')
      .replace("=", "")

    // Combine all parts
    s"$headerB64.$payloadB64.$signature"

  case class RefreshToken(
      jti: String, // Unique identifier for the refresh token
      sub: String, // Subject (user ID)
      exp: Long,   // Expiration timestamp
      issuedAt: Long = System.currentTimeMillis() / 1000,
  ) derives JsonEncoder, JsonDecoder

  /** Generate a refresh token
    *
    * @param subject
    *   User identifier
    * @param validityPeriod
    *   Refresh token validity in seconds (default 30 days)
    * @return
    *   Signed refresh token
    */
  def generateRefreshToken(
      subject: String,
      validityPeriod: Long = 30 * 24 * 3600,
      secretKey: String,
  ): String = {
    val jti = java.util.UUID.randomUUID().toString // Generate unique token ID
    val refreshTokenPayload = RefreshToken(
      jti = jti,
      sub = subject,
      exp = System.currentTimeMillis() / 1000 + validityPeriod,
    )
    sign(refreshTokenPayload, secretKey)
  }

  /** Validate and refresh access token using a refresh token
    *
    * @param refreshToken
    *   Existing refresh token
    * @param secretKey
    *   Secret key for verification
    * @param accessTokenPayloadGenerator
    *   Function to generate new access token payload
    * @return
    *   Either a new access token or an error
    */
  def refreshAccessToken[A: JsonEncoder: JsonDecoder](
      refreshToken: String,
      secretKey: String,
      accessTokenPayloadGenerator: String => A,
  ): Either[JWTError, String] = {
    verify[RefreshToken](refreshToken, secretKey) match {
      case Right(token) if token.exp > System.currentTimeMillis() / 1000 =>
        // Generate new access token using the subject from refresh token
        val newAccessTokenPayload = accessTokenPayloadGenerator(token.sub)
        Right(sign(newAccessTokenPayload, secretKey))
      case Right(_) =>
        Left(JWTError("Refresh token expired"))
      case Left(error) =>
        Left(error)
    }
  }

/** Example usage:
  *
  * case class TokenPayload( sub: String, // Subject (user id) roles: Set[String], exp: Long // Expiration time in
  * seconds since epoch ) object TokenPayload: given JsonEncoder[TokenPayload] = DeriveJsonEncoder.gen[TokenPayload]
  * given JsonDecoder[TokenPayload] = DeriveJsonDecoder.gen[TokenPayload]
  *
  * // Create a token val payload = TokenPayload("user123", Set("admin"), System.currentTimeMillis()/1000 + 3600) val
  * token = JWT.sign(payload, "secret")
  *
  * // Verify and decode a token JWT.verify[TokenPayload](token, "secret") match case Right(payload) if payload.exp >
  * System.currentTimeMillis()/1000 => // Token is valid and not expired case _ => // Token is invalid or expired
  */
