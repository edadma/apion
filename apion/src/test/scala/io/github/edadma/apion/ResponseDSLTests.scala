package io.github.edadma.apion

import zio.json.*

class ResponseDSLTests extends AsyncBaseSpec {

  case class TestData(name: String, value: Int) derives JsonEncoder, JsonDecoder

  "ResponseDSL" - {
    "text responses" - {
      "should create a text response" in {
        "hello world".asText.map { case Complete(response) =>
          response.status shouldBe 200
          response.bodyText shouldBe "hello world"
        }
      }

      "should create a text response with custom status" in {
        "not found".asText(404).map { case Complete(response) =>
          response.status shouldBe 404
          response.bodyText shouldBe "not found"
        }
      }

      "should handle empty string" in {
        "".asText.map { case Complete(response) =>
          response.bodyText shouldBe ""
        }
      }
    }

    "json responses" - {
      "should create a JSON response" in {
        TestData("test", 42).asJson.map { case Complete(response) =>
          response.status shouldBe 200
          response.bodyText should include("\"name\" : \"test\"")
          response.bodyText should include("\"value\" : 42")
        }
      }

      "should create a JSON response with custom status" in {
        TestData("created", 1).asJson(201).map { case Complete(response) =>
          response.status shouldBe 201
        }
      }
    }

    "top-level helpers" - {
      "text() should return a Result" in {
        text("hello").map {
          case Complete(response) =>
            response.bodyText shouldBe "hello"
          case other => fail(s"Expected Complete, got $other")
        }
      }

      "text() with status should return a Result" in {
        text("error", 500).map {
          case Complete(response) =>
            response.status shouldBe 500
            response.bodyText shouldBe "error"
          case other => fail(s"Expected Complete, got $other")
        }
      }

      "json() should return a Result" in {
        json(TestData("x", 1)).map {
          case Complete(response) =>
            response.bodyText should include("\"name\" : \"x\"")
          case other => fail(s"Expected Complete, got $other")
        }
      }

      "json() with status should return a Result" in {
        json(TestData("x", 1), 201).map {
          case Complete(response) =>
            response.status shouldBe 201
          case other => fail(s"Expected Complete, got $other")
        }
      }
    }

    "error helpers" - {
      "skip should return Skip" in {
        skip.map { result =>
          result shouldBe Skip
        }
      }

      "fail should return Fail with error" in {
        io.github.edadma.apion.fail(ValidationError("bad input")).map {
          case Fail(error) =>
            error shouldBe a[ValidationError]
            error.message shouldBe "bad input"
          case other => fail(s"Expected Fail, got $other")
        }
      }

      "failValidation should return Fail" in {
        io.github.edadma.apion.failValidation("invalid").map {
          case Fail(error) =>
            error shouldBe a[ValidationError]
          case other => fail(s"Expected Fail, got $other")
        }
      }

      "failAuth should return Fail" in {
        io.github.edadma.apion.failAuth("denied").map {
          case Fail(error) =>
            error shouldBe a[AuthError]
          case other => fail(s"Expected Fail, got $other")
        }
      }

      "failNotFound should return Fail" in {
        io.github.edadma.apion.failNotFound("missing").map {
          case Fail(error) =>
            error shouldBe a[NotFoundError]
          case other => fail(s"Expected Fail, got $other")
        }
      }
    }

    "common responses" - {
      "notFound should be 404" in {
        notFound.map { case Complete(response) =>
          response.status shouldBe 404
        }
      }

      "badRequest should be 400" in {
        badRequest.map { case Complete(response) =>
          response.status shouldBe 400
        }
      }

      "serverError should be 500" in {
        serverError.map { case Complete(response) =>
          response.status shouldBe 500
        }
      }
    }

    "created helper" - {
      "should return 201 with JSON body" in {
        created(TestData("new", 1)).map {
          case Complete(response) =>
            response.status shouldBe 201
            response.bodyText should include("\"name\" : \"new\"")
          case other => fail(s"Expected Complete, got $other")
        }
      }

      "should include Location header when provided" in {
        created(TestData("new", 1), Some("/items/1")).map {
          case Complete(response) =>
            response.status shouldBe 201
            response.headers.toMap.get("Location").flatMap(_.headOption) shouldBe Some("/items/1")
          case other => fail(s"Expected Complete, got $other")
        }
      }
    }
  }
}
