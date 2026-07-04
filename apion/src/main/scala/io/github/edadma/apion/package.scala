package io.github.edadma.apion

import scala.util.Try
import scala.scalajs.js
import org.scalajs.macrotaskexecutor.MacrotaskExecutor
import scala.concurrent.ExecutionContext

import io.github.edadma.nodejs.*

import io.github.edadma.logger.LoggerFactory

implicit val executionContext: ExecutionContext = MacrotaskExecutor

val logger = LoggerFactory.newLogger

/** Encodes a string to base64url format. base64url is similar to base64 but uses URL-safe characters:
  *   - '+' becomes '-'
  *   - '/' becomes '_'
  *   - Padding '=' is removed
  */
def base64UrlEncode(str: String): String =
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
def base64UrlDecode(str: String): String =
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

/** Percent-decode a URI component using the JavaScript runtime, which decodes
  * multi-byte UTF-8 sequences correctly (unlike a naive byte-per-`%xx` decoder).
  * Malformed input is returned unchanged rather than throwing.
  *
  * This follows JS `decodeURIComponent` semantics: `+` is a literal plus, not a
  * space. For `application/x-www-form-urlencoded` data (query strings, form
  * bodies) use [[decodeFormComponent]] instead.
  */
def decodeURIComponent(s: String): String =
  Try(js.URIUtils.decodeURIComponent(s)).getOrElse(s)

/** Decode an `application/x-www-form-urlencoded` component, where `+` denotes a
  * space in addition to percent-encoding. Used for query strings and form bodies.
  */
def decodeFormComponent(s: String): String =
  decodeURIComponent(s.replace('+', ' '))

/** Percent-encode a URI component using the JavaScript runtime (UTF-8 aware). */
def encodeURIComponent(s: String): String =
  js.URIUtils.encodeURIComponent(s)

def generateUUID(): String = {
  val bytes = crypto.randomBytes(16)

  // Manually set version and variant bits
  bytes(6) = ((bytes(6) & 0x0f) | 0x40).toByte // Version 4
  bytes(8) = ((bytes(8) & 0x3f) | 0x80).toByte // Variant

  // Convert bytes to hex string
  val hex = bytes.toArray.map(b => f"${b & 0xff}%02x").mkString

  s"${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
}
