---
title: JWT & Custom Tokens
description: Direct JWT operations for custom authentication flows
---

Apion includes a `JWT` module for signing and verifying JSON Web Tokens (HS256). While `AuthMiddleware` handles most authentication needs, the `JWT` module is available for custom flows.

## Signing Tokens

```scala
import io.github.edadma.apion.JWT

case class TokenPayload(
  sub: String,
  roles: Set[String],
  exp: Long,
) derives JsonEncoder, JsonDecoder

// Create payload
val payload = TokenPayload(
  sub = "user123",
  roles = Set("admin"),
  exp = System.currentTimeMillis() / 1000 + 3600, // 1 hour
)

// Sign
val token: String = JWT.sign(payload, "secret-key")
```

## Verifying Tokens

```scala
JWT.verify[TokenPayload](token, "secret-key") match {
  case Right(payload) =>
    // Token is valid
    println(s"User: ${payload.sub}, Roles: ${payload.roles}")
  case Left(error) =>
    // Token is invalid, expired, or signature mismatch
    println(s"Error: ${error.message}")
}
```

## Refresh Tokens

Generate a refresh token:

```scala
val refreshToken = JWT.generateRefreshToken(
  subject = "user123",
  validityPeriod = 30 * 24 * 3600, // 30 days
  secretKey = "secret-key",
)
```

Refresh an access token:

```scala
JWT.refreshAccessToken[TokenPayload](
  refreshToken = refreshToken,
  secretKey = "secret-key",
  accessTokenPayloadGenerator = subject => TokenPayload(
    sub = subject,
    roles = getUserRoles(subject),
    exp = System.currentTimeMillis() / 1000 + 3600,
  ),
) match {
  case Right(newAccessToken) => // Use new token
  case Left(error)           => // Refresh failed
}
```

## Error Handling

```scala
case class JWTError(message: String) extends Exception
```

Common error cases:
- Invalid token format (not three base64url segments)
- Signature mismatch
- Token expired (`exp` claim in the past)
- JSON decode failure

## Token Format

Apion JWTs use the standard format:

```
header.payload.signature
```

- **Header**: `{"alg":"HS256","typ":"JWT"}` (base64url)
- **Payload**: Your case class serialized as JSON (base64url)
- **Signature**: HMAC-SHA256 of `header.payload` with your secret

## Custom Authentication Middleware

Build a custom auth flow using `JWT` directly:

```scala
case class MyPayload(userId: String, tier: String, exp: Long)
  derives JsonEncoder, JsonDecoder

val customAuth: Handler = request =>
  request.header("authorization") match {
    case Some(h) if h.startsWith("Bearer ") =>
      JWT.verify[MyPayload](h.substring(7), "secret") match {
        case Right(payload) =>
          Future.successful(Continue(request.copy(
            context = request.context +
              ("userId" -> payload.userId) +
              ("tier" -> payload.tier)
          )))
        case Left(err) =>
          failAuth(err.message)
      }
    case _ =>
      failAuth("Missing token")
  }
```
