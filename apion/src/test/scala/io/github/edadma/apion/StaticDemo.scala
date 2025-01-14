package io.github.edadma.apion

object StaticDemo:
  def run(): Unit =
    val server = Server()
      .use(LoggingMiddleware())
      .use(StaticMiddleware("project"))

    server.listen(3000) {
      println("Static file server running at http://localhost:3000")
      println("Serving 'project' directory")
      println("Try: http://localhost:3000/plugins.sbt")
    }
