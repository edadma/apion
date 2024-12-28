package io.github.edadma.apion

@main def run(): Unit =
  val server = Server()

  server.listen(3000) {
    println("Server running at http://localhost:3000/")
  }
