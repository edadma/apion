package io.github.edadma.apion

import io.github.edadma.nodejs.{ServerRequest, bufferMod}
import scala.scalajs.js
import scala.concurrent.Future

class RequestBodyTests extends AsyncBaseSpec {
  // A mock whose body is delivered exactly once, asynchronously, after the reader
  // has attached its listeners — modelling a real Node request stream that can only
  // be consumed a single time.
  private def onceEmittingRequest(body: String, contentType: String): Request = {
    var dataHandler: js.Function1[js.Any, Unit] = null
    var endHandler: js.Function1[js.Any, Unit]  = null
    var emitted                                 = false

    def maybeEmit(): Unit =
      if (!emitted && dataHandler != null && endHandler != null) {
        emitted = true
        js.timers.setTimeout(0) {
          dataHandler(bufferMod.Buffer.from(body))
          endHandler(())
        }
      }

    val req = js.Dynamic.literal(
      method = "POST",
      url = "/test",
      headers = js.Dictionary("content-type" -> contentType),
      on = { (event: String, handler: js.Function1[js.Any, Unit]) =>
        event match {
          case "data" => dataHandler = handler
          case "end"  => endHandler = handler
          case _      =>
        }
        maybeEmit()
        js.Dynamic.literal()
      },
      destroy = (_: js.Any) => js.Dynamic.literal(),
    )
    Request.fromServerRequest(req.asInstanceOf[ServerRequest])
  }

  "Request body" - {
    // Regression: the memoized body promise was a `var` on the case class, so every
    // `.copy()` reset it. A copied request re-attaching to an already-consumed stream
    // would hang. The body must be memoized on the shared connection instead.
    "should be readable once and shared across copied requests" in {
      val req  = onceEmittingRequest("hello world", "text/plain")
      val copy = req.copy(path = "/other", params = Map("x" -> "1"))
      for {
        a <- req.text
        b <- copy.text
      } yield {
        a shouldBe "hello world"
        b shouldBe "hello world"
      }
    }

    "should be readable when a copy consumes it before the original" in {
      val req  = onceEmittingRequest("payload", "text/plain")
      val copy = req.copy(basePath = "/api")
      for {
        b <- copy.text
        a <- req.text
      } yield {
        b shouldBe "payload"
        a shouldBe "payload"
      }
    }
  }

  "Form parsing" - {
    "should preserve repeated keys and decode '+' as space" in {
      val req = onceEmittingRequest("a=1&a=2&b=hello+world", "application/x-www-form-urlencoded")
      req.form.map { m =>
        m("a") shouldBe Seq("1", "2")
        m("b") shouldBe Seq("hello world")
      }
    }

    "formField should return the first value" in {
      val req = onceEmittingRequest("a=1&a=2", "application/x-www-form-urlencoded")
      req.formField("a").map(_ shouldBe Some("1"))
    }

    "form should be empty for an empty body" in {
      val req = onceEmittingRequest("", "application/x-www-form-urlencoded")
      req.form.map(_ shouldBe empty)
    }
  }
}
