package io.github.edadma.apion

import scala.util.Try
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

def decodeURIComponent(s: String): String = {
  def hexToChar(hex: String): Char =
    Integer.parseInt(hex, 16).toChar

  val result = new StringBuilder
  var i      = 0
  while (i < s.length) {
    if (s(i) == '%' && i + 2 < s.length) {
      result.append(hexToChar(s.substring(i + 1, i + 3)))
      i += 3
    } else if (s(i) == '+') {
      result.append(' ')
      i += 1
    } else {
      result.append(s(i))
      i += 1
    }
  }
  result.toString
}

def encodeURIComponent(s: String): String = {
  def shouldEncode(c: Char): Boolean = {
    val allowedChars = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') ++ Set('-', '_', '.', '!', '~', '*', '\'', '(', ')')
    !allowedChars.contains(c)
  }

  val sb = new StringBuilder
  for (c <- s) {
    if (shouldEncode(c)) {
      val bytes = c.toString.getBytes("UTF-8")
      for (b <- bytes) {
        sb.append('%')
        sb.append(Character.forDigit((b >> 4) & 0xf, 16).toUpper)
        sb.append(Character.forDigit(b & 0xf, 16).toUpper)
      }
    } else {
      sb.append(c)
    }
  }
  sb.toString
}

def generateUUID(): String = {
  val bytes = crypto.randomBytes(16)

  // Manually set version and variant bits
  bytes(6) = ((bytes(6) & 0x0f) | 0x40).toByte // Version 4
  bytes(8) = ((bytes(8) & 0x3f) | 0x80).toByte // Variant

  // Convert bytes to hex string
  val hex = bytes.toArray.map(b => f"${b & 0xff}%02x").mkString

  s"${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
}
