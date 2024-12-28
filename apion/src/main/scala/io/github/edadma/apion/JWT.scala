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
