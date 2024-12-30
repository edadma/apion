package io.github.edadma.apion

import ResponseDSL._

object DemoApp {
  def run(): Unit = {
    // Create a simple logging middleware
    val loggingMiddleware: Handler = request => {
      println(s"${request.method} ${request.path}")
      request.continue
    }

    // Create some routes for users
    val usersRouter = new Router()
      .get(
        "/:id",
        request => {
          val userId = request.params("id")
          Map("userId" -> userId, "name" -> s"User $userId").asJson
        },
      )
      .post(
        "/",
        request =>
          Created(Map("message" -> "User created")),
      )

    // Create and configure the main server
    val server = Server()
      .use(loggingMiddleware)

      // Basic routes
      .get(
        "/",
        request =>
          Map("message" -> "Welcome to the API!").asJson,
      )
      .get(
        "/hello/:name",
        request => {
          val name = request.params("name")
          Map("message" -> s"Hello, $name!").asJson
        },
      )

      // Mount the users router
      .use("/api/users", usersRouter)

      // Add error demo route
      .get(
        "/error",
        _.failValidation("This is a demo error"),
      )

    // Start the server
    server.listen(3000) {
      println("Server running at http://localhost:3000")
    }
  }
}
