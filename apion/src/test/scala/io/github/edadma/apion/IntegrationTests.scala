package io.github.edadma.apion

import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import io.github.edadma.nodejs.{http, Server => NodeServer, fetch, Response}
import scala.compiletime.uninitialized

class IntegrationTests extends AsyncBaseSpec with BeforeAndAfterAll {
  var server: Server         = uninitialized
  var httpServer: NodeServer = uninitialized
  val port                   = 3001 // Use different port than main server

  override def beforeAll(): Unit = {
    server = Server()
      .get("/test", _ => "test response".asText)

    // Start server and keep reference to http.Server
    httpServer = server.listen(port) {
      println(s"Test server running on port $port")
    }
  }

  override def afterAll(): Unit = {
    // Cleanup: close the server
    if (httpServer != null) {
      httpServer.close(() => println("Test server closed"))
    }
  }

  "Server" - {
    "should handle basic GET request" in {
      // Make request to test endpoint
      fetch(s"http://localhost:$port/test")
        .toFuture
        .flatMap(response => response.text().toFuture)
        .map(text => text shouldBe "test response")
    }
  }
}
