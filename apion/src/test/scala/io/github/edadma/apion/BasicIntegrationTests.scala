package io.github.edadma.apion

import org.scalatest.BeforeAndAfterAll

import scala.scalajs.js
import io.github.edadma.nodejs.{FetchOptions, fetch, Server as NodeServer}
import zio.json.*

import scala.compiletime.uninitialized

class BasicIntegrationTests extends AsyncBaseSpec with BeforeAndAfterAll {
  var server: Server         = uninitialized
  var httpServer: NodeServer = uninitialized
  val port                   = 3001 // Use the same port as before

  // Define case classes for JSON tests
  case class SimpleData(message: String) derives JsonEncoder, JsonDecoder
  case class SearchParams(query: String, page: Int) derives JsonEncoder, JsonDecoder

  override def beforeAll(): Unit = {
    server = Server()
      // Route parameter test
      .get(
        "/users/:id",
        request => {
          val userId = request.params("id")
          s"User ID: $userId".asText
        },
      )

      // Query parameter test
      .get(
        "/search",
        request => {
          val query = request.query.getOrElse("q", "")
          val page  = request.query.get("page").map(_.toInt).getOrElse(1)
          SearchParams(query, page).asJson
        },
      )

      // Multiple HTTP method test
      .get("/resource", _ => "GET response".asText)
      .post("/resource", _ => "POST response".asText)
      .put("/resource", _ => "PUT response".asText)

      // Request headers test
      .get(
        "/headers",
        request => {
          val userAgent = request.header("user-agent").getOrElse("Unknown")
          s"User-Agent: $userAgent".asText
        },
      )

      // Response status codes test
      .get("/created", _ => "Created".asText(201))
      .get("/no-content", _ => Response.noContent()
      .get("/bad-request", _ => badRequest

      // Basic body parsing test
      .use(BodyParser.json[SimpleData]())
      .post(
        "/echo",
        request => {
          request.context.get("body") match {
            case Some(data: SimpleData) => data.asJson
            case _                      => "Invalid request".asText(400)
          }
        },
      )

    // Start the server
    httpServer = server.listen(port) {}
  }

  override def afterAll(): Unit = {
    if (httpServer != null) {
      httpServer.close(() => ())
    }
  }

  "Server basic functionality" - {
    "should handle route parameters correctly" in {
      fetch(s"http://localhost:$port/users/123")
        .toFuture
        .flatMap(response => response.text().toFuture)
        .map(text => text shouldBe "User ID: 123")
    }

    "should parse query parameters" in {
      fetch(s"http://localhost:$port/search?q=test&page=2")
        .toFuture
        .flatMap(response => response.json().toFuture)
        .map { result =>
          val json = js.JSON.stringify(result)
          json should include("test")
          json should include("2")
        }
    }

    "should handle different HTTP methods on the same route" in {
      for {
        getResponse <- fetch(s"http://localhost:$port/resource").toFuture.flatMap(_.text().toFuture)
        postResponse <-
          fetch(s"http://localhost:$port/resource", FetchOptions(method = "POST")).toFuture.flatMap(_.text().toFuture)
        putResponse <-
          fetch(s"http://localhost:$port/resource", FetchOptions(method = "PUT")).toFuture.flatMap(_.text().toFuture)
      } yield {
        getResponse shouldBe "GET response"
        postResponse shouldBe "POST response"
        putResponse shouldBe "PUT response"
      }
    }

    "should read request headers" in {
      val options = FetchOptions(
        headers = js.Dictionary(
          "User-Agent" -> "TestClient/1.0",
        ),
      )

      fetch(s"http://localhost:$port/headers", options)
        .toFuture
        .flatMap(response => response.text().toFuture)
        .map(text => text shouldBe "User-Agent: TestClient/1.0")
    }

    "should support different response status codes" in {
      for {
        createdResponse    <- fetch(s"http://localhost:$port/created").toFuture
        noContentResponse  <- fetch(s"http://localhost:$port/no-content").toFuture
        badRequestResponse <- fetch(s"http://localhost:$port/bad-request").toFuture
      } yield {
        createdResponse.status shouldBe 201
        noContentResponse.status shouldBe 204
        badRequestResponse.status shouldBe 400
      }
    }

    "should handle basic request body parsing" in {
      val payload = SimpleData("Hello, World!")
      val options = FetchOptions(
        method = "POST",
        headers = js.Dictionary("Content-Type" -> "application/json"),
        body = payload.toJson,
      )

      fetch(s"http://localhost:$port/echo", options)
        .toFuture
        .flatMap(response => response.text().toFuture)
        .map { text =>
          text should include("Hello, World!")
        }
    }
  }
}
