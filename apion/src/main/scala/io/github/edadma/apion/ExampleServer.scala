import io.github.edadma.apion.*
import io.github.edadma.logger.LogLevel

object ExampleServer extends App:
  logger.setLogLevel(LogLevel.ALL)

  val server = Server()

  val staticRoute = server.route("/project")
  staticRoute.use(StaticMiddleware("./project"))

  server.listen(3000) {
    println("Server listening on port 3000")
  }
