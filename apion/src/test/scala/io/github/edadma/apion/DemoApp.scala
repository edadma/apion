package io.github.edadma.apion

import scala.concurrent.Future
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

object DemoApp {
  def run(): Unit = {
    // Create a simple logging middleware
    val loggingMiddleware: Handler = request => {
      println(s"${request.method} ${request.path}")
      Future.successful(Continue(request))
    }

    // Create some routes for users
    val usersRouter = new Router()
      .get(
        "/:id",
        request => {
          val userId = request.params("id")
          Future.successful(Complete(Response.json(Map("userId" -> userId, "name" -> s"User $userId"))))
        },
      )
      .post(
        "/",
        request =>
          Future.successful(Complete(Response.json(Map("message" -> "User created"), status = 201))),
      )

    // Create and configure the main server
    val server = Server()
      .use(loggingMiddleware) // Add logging middleware

      // Basic routes
      .get(
        "/",
        request =>
          Future.successful(Complete(Response.json(Map("message" -> "Welcome to the API!")))),
      )
      .get(
        "/hello/:name",
        request => {
          val name = request.params("name")
          Future.successful(Complete(Response.json(Map("message" -> s"Hello, $name!"))))
        },
      )

      // Mount the users router
      .use("/api/users", usersRouter)

      // Add error demo route
      .get(
        "/error",
        request =>
          Future.successful(Fail(ValidationError("This is a demo error"))),
      )

    // Start the server
    server.listen(3000) {
      println("Server running at http://localhost:3000")
    }
  }
}
