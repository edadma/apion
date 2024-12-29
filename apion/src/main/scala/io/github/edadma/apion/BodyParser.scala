package io.github.edadma.apion

import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import zio.json.*
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object BodyParser:
  def json[A: JsonDecoder](): Middleware =
    endpoint =>
      request =>
        request.rawRequest match
          case Some(raw) =>
            val promise = Promise[Request]()
            var body    = ""

            raw.on(
              "data",
              (chunk: js.Any) => {
                body += chunk.toString
              },
            )

            raw.on(
              "end",
              () => {
                body.fromJson[A] match
                  case Right(value) =>
                    promise.success(request.copy(
                      context = request.context + ("body" -> value),
                    ))
                  case Left(error) =>
                    promise.failure(new RuntimeException(s"JSON parse error: $error"))
              },
            )

            raw.on(
              "error",
              (error: js.Error) => {
                promise.failure(new RuntimeException(s"Body read error: ${error.message}"))
              },
            )

            // Chain the promise with the endpoint
            promise.future.flatMap(endpoint)
              .recover { case e: Exception =>
                Response(
                  status = 400,
                  body = e.getMessage,
                )
              }

          case None =>
            Future.successful(Response(
              status = 500,
              body = "Internal Server Error: No raw request available",
            ))
