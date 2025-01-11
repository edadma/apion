package io.github.edadma.apion

import io.github.edadma.apion._
import zio.json._
import scala.concurrent.Future
import scala.scalajs.js

case class UserData(name: String, email: String) derives JsonEncoder, JsonDecoder

case class ApiResponse(message: String, user: UserData) derives JsonEncoder
case class ErrorResponse(error: String) derives JsonEncoder

object DemoApp {
  def run(): Unit = {
    // Create handlers
    val greetingHandler: Handler =
      _ => "Hello, World!".asText

    val echoNameHandler: Handler =
      request => {
        val name = request.params.getOrElse("name", "stranger")
        s"Hello, $name!".asText
      }

    // Handler that uses the parsed body
    val createUserHandler: Handler =
      request => {
        request.json[UserData].flatMap {
          case Some(userData) =>
            ApiResponse(
              message = "User created successfully",
              user = userData,
            ).asJson(201)

          case _ =>
            ErrorResponse("Invalid request body").asJson(400)
        }
      }

    // Create and start server with chained configuration
    Server()
      .use(requestLogger)
      .get("/", greetingHandler)
      .get("/greet/:name", echoNameHandler)
      .post("/api/users", createUserHandler)
      .listen(3000) { println("Server running at http://localhost:3000") }
  }

  private def requestLogger: Handler =
    request => {
      println(s"${request.method} ${request.url}")
      skip
    }
}
