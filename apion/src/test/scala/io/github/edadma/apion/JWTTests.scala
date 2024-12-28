package io.github.edadma.apion

import zio.json.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import io.github.edadma.nodejs.*

class JWTTests extends AnyFreeSpec with Matchers:
  case class TestPayload(
      sub: String,
      roles: Set[String],
      exp: Long,
  )

  object TestPayload:
    given JsonEncoder[TestPayload] = DeriveJsonEncoder.gen[TestPayload]
    given JsonDecoder[TestPayload] = DeriveJsonDecoder.gen[TestPayload]

  // Test constants
  val secret = "test-secret-key-1234"
  val validPayload = TestPayload(
    sub = "user123",
    roles = Set("admin", "user"),
    exp = System.currentTimeMillis() / 1000 + 3600, // 1 hour from now
  )
  val expiredPayload = validPayload.copy(
    exp = System.currentTimeMillis() / 1000 - 3600, // 1 hour ago
  )

  "JWT" - {
    "signing" - {
      "should create a valid JWT token" in {
        val token = JWT.sign(validPayload, secret)

        // JWT should be 3 base64url-encoded sections separated by dots
        val parts = token.split('.')
        parts.length shouldBe 3

        // Each part should be base64url encoded (no +, /, or = characters)
        // Fix character testing syntax
        parts.foreach { part =>
          part should not include "+"
          part should not include "/"
          part should not include "="
        }
      }

      "should create different signatures for different secrets" in {
        val token1 = JWT.sign(validPayload, secret)
        val token2 = JWT.sign(validPayload, "different-secret")

        token1 should not be token2

        // But the header and payload should be identical
        val parts1 = token1.split('.')
        val parts2 = token2.split('.')

        parts1(0) shouldBe parts2(0)      // Headers should match
        parts1(1) shouldBe parts2(1)      // Payloads should match
        parts1(2) should not be parts2(2) // Signatures should differ
      }
    }

    "verification" - {
      "should successfully verify a valid token" in {
        val token = JWT.sign(validPayload, secret)

        JWT.verify[TestPayload](token, secret) match
          case Right(payload) =>
            payload shouldBe validPayload
          case Left(error) =>
            fail(s"Should have verified: $error")
      }

      "should fail verification with wrong secret" in {
        val token = JWT.sign(validPayload, secret)

        JWT.verify[TestPayload](token, "wrong-secret") match
          case Right(_) =>
            fail("Should not verify with wrong secret")
          case Left(error) =>
            error.message shouldBe "Invalid signature"
      }

      "should fail verification with tampered payload" in {
        val token = JWT.sign(validPayload, secret)
        val parts = token.split('.')

        // Modify the payload section (middle part)
        val tamperedToken = s"${parts(0)}.${parts(1)}x.${parts(2)}"

        JWT.verify[TestPayload](tamperedToken, secret) match
          case Right(_) =>
            fail("Should not verify tampered token")
          case Left(error) =>
            error.message should include("Invalid")
      }

      "should fail for malformed tokens" in {
        val badTokens = List(
          "",                  // Empty
          "header.payload",    // Missing signature
          "header",            // Single segment
          "head.pay.sig.extra", // Too many segments
        )

        badTokens.foreach { token =>
          JWT.verify[TestPayload](token, secret) match
            case Right(_) =>
              fail(s"Should not verify malformed token: $token")
            case Left(error) =>
              error.message should include("Invalid")
        }
      }

      "should fail for non-HS256 algorithms" in {
        // Manually create a header claiming a different algorithm
        case class CustomHeader(alg: String = "RS256", typ: String = "JWT")
        given JsonEncoder[CustomHeader] = DeriveJsonEncoder.gen[CustomHeader]

        val header = CustomHeader()
        val token  = JWT.sign(validPayload, secret)
        val parts  = token.split('.')

        // Replace the header with our custom one
        val customAlgToken = s"${bufferMod.Buffer
            .from(header.toJson)
            .toString("base64")}.${parts(1)}.${parts(2)}"

        JWT.verify[TestPayload](customAlgToken, secret) match
          case Right(_) =>
            fail("Should not verify non-HS256 token")
          case Left(error) =>
            error.message should include("Unsupported algorithm")
      }

      "should fail with invalid JSON in payload" in {
        val token = JWT.sign(validPayload, secret)
        val parts = token.split('.')
        // Corrupt the payload JSON but keep it as valid base64url
        val corruptToken = s"${parts(0)}.${base64UrlEncode("{not-valid-json}")}.${parts(2)}"

        JWT.verify[TestPayload](corruptToken, secret) match
          case Right(_)    => fail("Should not verify with corrupt payload")
          case Left(error) => error.message should include("Invalid payload")
      }

      "should fail with invalid JSON in header" in {
        val token = JWT.sign(validPayload, secret)
        val parts = token.split('.')
        // Corrupt the header JSON but keep it as valid base64url
        val corruptToken = s"${base64UrlEncode("{bad-header}")}.${parts(1)}.${parts(2)}"

        JWT.verify[TestPayload](corruptToken, secret) match
          case Right(_)    => fail("Should not verify with corrupt header")
          case Left(error) => error.message should include("Invalid header")
      }

      "should handle special characters in payload" in {
        val specialPayload = validPayload.copy(sub = "user+with/special=chars")
        val token          = JWT.sign(specialPayload, secret)

        JWT.verify[TestPayload](token, secret) match
          case Right(payload) => payload shouldBe specialPayload
          case Left(error)    => fail(s"Should verify with special characters: $error")
      }

      "should handle large payloads" in {
        val largePayload = validPayload.copy(
          sub = "x" * 1000, // 1KB of data
          roles = Set("role" * 100),
        )
        val token = JWT.sign(largePayload, secret)

        JWT.verify[TestPayload](token, secret) match
          case Right(payload) => payload shouldBe largePayload
          case Left(error)    => fail(s"Should verify large payload: $error")
      }

      "should handle empty strings in payload" in {
        val emptyPayload = validPayload.copy(sub = "")
        val token        = JWT.sign(emptyPayload, secret)

        JWT.verify[TestPayload](token, secret) match
          case Right(payload) => payload shouldBe emptyPayload
          case Left(error)    => fail(s"Should verify with empty string: $error")
      }
    }
  }
