package io.github.edadma.apion

import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import zio.json.*
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

object BodyParser:
  /** Creates middleware for parsing JSON request bodies
    * @tparam A
    *   The expected type of the parsed JSON body
    * @return
    *   Middleware that parses JSON bodies and adds them to request context
    */
  def json[A: JsonDecoder](): Handler =
    request => {
      val promise = Promise[Result]()
      var body    = ""

      request.rawRequest.on(
        "data",
        (chunk: js.Any) => {
          body += chunk.toString
        },
      )

      request.rawRequest.on(
        "end",
        () => {
          body.fromJson[A] match
            case Right(value) =>
              // Add body parser finalizer to log parsed data
              val bodyParserFinalizer: Finalizer = (req, res) =>
                Future.successful(res.copy(
                  headers = res.headers.add("X-Body-Parsed", "true"),
                ))

              promise.success(Continue(
                request
                  .copy(context = request.context + ("body" -> value))
                  .addFinalizer(bodyParserFinalizer),
              ))
            case Left(error) =>
              promise.success(Fail(ValidationError(s"JSON parse error: $error")))
        },
      )

      request.rawRequest.on(
        "error",
        (error: js.Error) => {
          promise.success(Fail(ValidationError(s"Request body read error: ${error.message}")))
        },
      )

      promise.future
    }

  /** Creates middleware for parsing URL-encoded form data
    * @return
    *   Middleware that parses form data and adds it to request context
    */
  def urlencoded(): Handler =
    request => {
      val promise = Promise[Result]()
      var body    = ""

      request.rawRequest.on(
        "data",
        (chunk: js.Any) => {
          body += chunk.toString
        },
      )

      request.rawRequest.on(
        "end",
        () => {
          try {
            val params = body.split("&").map { param =>
              param.split("=", 2) match {
                case Array(key, value) =>
                  (decodeURIComponent(key), decodeURIComponent(value))
                case Array(key) =>
                  (decodeURIComponent(key), "")
              }
            }.toMap

            promise.success(Continue(
              request.copy(context = request.context + ("form" -> params)),
            ))
          } catch {
            case e: Exception =>
              promise.success(Fail(ValidationError(s"Form data parse error: ${e.getMessage}")))
          }
        },
      )

      request.rawRequest.on(
        "error",
        (error: js.Error) => {
          promise.success(Fail(ValidationError(s"Request body read error: ${error.message}")))
        },
      )

      promise.future
    }

  /** Decodes URL-encoded components
    * @param s
    *   The URL-encoded string
    * @return
    *   The decoded string
    */
  private def decodeURIComponent(s: String): String = {
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
