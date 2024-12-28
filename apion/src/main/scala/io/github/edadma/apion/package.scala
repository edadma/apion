package io.github.edadma.apion

import scala.util.Try

import io.github.edadma.nodejs.*

import io.github.edadma.logger.LoggerFactory

val logger = LoggerFactory.newLogger

/** Encodes a string to base64url format. base64url is similar to base64 but uses URL-safe characters:
  *   - '+' becomes '-'
  *   - '/' becomes '_'
  *   - Padding '=' is removed
  */
private def base64UrlEncode(str: String): String =
  bufferMod.Buffer
    .from(str)
    .toString("base64")
    .replace('+', '-') // Make URL safe
    .replace('/', '_') // Make URL safe
    .replace("=", "")  // Remove padding

/** Decodes a base64url string back to original format. Reverses the base64url encoding:
  *   - '-' becomes '+'
  *   - '_' becomes '/'
  *   - Adds back padding if needed
  */
private def base64UrlDecode(str: String): String =
  Try {
    bufferMod.Buffer
      .from(
        str.replace('-', '+')
          .replace('_', '/')
          .padTo(str.length + (4 - str.length % 4) % 4, '='),
        "base64",
      )
      .toString("utf8")
  }.getOrElse(throw JWT.JWTError("Invalid base64url encoding"))
